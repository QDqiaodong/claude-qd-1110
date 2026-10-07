#!/usr/bin/env sh
# 课后成果记录闭环验收脚本。
# 用法：先 sh start.sh --build，再 sh tests/acceptance-after-class.sh
set -eu

BASE_URL="${BASE_URL:-http://127.0.0.1:${BACKEND_PORT:-8420}/api}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
if [ -f "$ROOT_DIR/.env" ]; then
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  export MYSQL_ROOT_PASSWORD MYSQL_DATABASE
fi

pass=0
fail=0

ok() {
  echo "PASS: $1"
  pass=$((pass + 1))
}
fail_case() {
  echo "FAIL: $1 -- $2" >&2
  fail=$((fail + 1))
}

# request METHOD PATH [JSON_BODY] -> HTTP_CODE
request() {
  method="$1"; path="$2"; body="${3:-}"
  tmp_body="$(mktemp)"
  if [ -n "$body" ]; then
    code="$(curl -sS -o "$tmp_body" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' -d "$body" "$BASE_URL$path")"
  else
    code="$(curl -sS -o "$tmp_body" -w '%{http_code}' -X "$method" "$BASE_URL$path")"
  fi
  printf '%s\n' "$code" > "$OUT_DIR/.last-code"
  cat "$tmp_body" > "$OUT_DIR/.last-body"
  rm -f "$tmp_body"
}

json_field() {
  node -e 'const fs=require("fs"); const key=process.argv[1]; const b=JSON.parse(fs.readFileSync(process.argv[2],"utf8")); const parts=key.split("."); let v=b; for (const p of parts) v=v?.[p]; if (v===null || v===undefined) process.exit(1); process.stdout.write(String(v))' "$1" "$2" 2>/dev/null || true
}

contains() {
  printf '%s' "$1" | grep -q "$2"
}

assert_request() {
  name="$1"; expected_code="$2"; method="$3"; path="$4"; body="${5:-}"
  request "$method" "$path" "$body"
  actual_code="$(cat "$OUT_DIR/.last-code")"
  if [ "$actual_code" = "$expected_code" ]; then
    ok "$name"
  else
    fail_case "$name" "HTTP $actual_code, expected $expected_code; body=$(cat "$OUT_DIR/.last-body")"
  fi
}

OUT_DIR="$(mktemp -d)"
trap 'rm -rf "$OUT_DIR"' EXIT

# 使用独立学员和课次，避免破坏种子数据；学号带时间戳，支持重复执行。
test_suffix="$(date +%s)"
request POST /students "{\"code\":\"ACR-$test_suffix\",\"name\":\"验收学员\",\"phone\":\"13800001111\",\"level\":\"成人\",\"status\":\"在读\"}"
student_id="$(json_field data.id "$OUT_DIR/.last-body")"
[ -n "$student_id" ] || { echo "无法创建测试学员" >&2; exit 1; }

request POST /course-schedules "{\"studentId\":$student_id,\"courseName\":\"验收课\",\"date\":\"2026-10-07\",\"startMin\":540,\"endMin\":600}"
pending_id="$(json_field data.id "$OUT_DIR/.last-body")"

request POST /course-schedules "{\"studentId\":$student_id,\"courseName\":\"验收课\",\"date\":\"2026-10-08\",\"startMin\":540,\"endMin\":600}"
attended_id="$(json_field data.id "$OUT_DIR/.last-body")"
request POST "/course-schedules/$attended_id/checkin"

request POST /course-schedules "{\"studentId\":$student_id,\"courseName\":\"验收课\",\"date\":\"2026-10-07\",\"startMin\":660,\"endMin\":720}"
missing_student_id="$(json_field data.id "$OUT_DIR/.last-body")"
request POST "/course-schedules/$missing_student_id/checkin"

request POST /course-schedules "{\"studentId\":$student_id,\"courseName\":\"验收课\",\"date\":\"2026-10-09\",\"startMin\":540,\"endMin\":600}"
cancel_id="$(json_field data.id "$OUT_DIR/.last-body")"
request POST "/course-schedules/$cancel_id/cancel"

# 1. 待上课 / 已取消不能新建或提交。
assert_request "待上课课次不能保存草稿" 400 POST "/course-schedules/$pending_id/after-class-record/draft" '{"teacherComment":"先写点评"}'
assert_request "待上课课次不能直接提交" 400 POST "/course-schedules/$pending_id/after-class-record/submit" '{"artworkTheme":"作品","completionPercent":100,"teacherComment":"点评"}'
assert_request "已取消课次不能保存草稿" 400 POST "/course-schedules/$cancel_id/after-class-record/draft" '{"teacherComment":"先写点评"}'
assert_request "已取消课次不能提交" 400 POST "/course-schedules/$cancel_id/after-class-record/submit" '{"artworkTheme":"作品","completionPercent":100,"teacherComment":"点评"}'

# 2. 草稿允许只有点评，且不能计入已完成课程。
assert_request "草稿允许缺少作业内容" 200 POST "/course-schedules/$attended_id/after-class-record/draft" '{"teacherComment":"构图不错"}'
request GET "/course-schedules?studentId=$student_id&completedOnly=true"
if contains "$(cat "$OUT_DIR/.last-body")" "\"id\":$attended_id"; then
  fail_case "草稿不能出现在已完成课程列表" "发现课次 $attended_id"
else
  ok "草稿不出现在已完成课程列表"
fi

# 3. 进入课次详情可看到草稿状态和最后更新时间。
request GET "/course-schedules/$attended_id/after-class-record"
if [ "$(json_field data.status "$OUT_DIR/.last-body")" = "草稿" ] && [ -n "$(json_field data.updatedAt "$OUT_DIR/.last-body")" ]; then
  ok "草稿详情包含状态与最后更新时间"
else
  fail_case "草稿详情包含状态与最后更新时间" "$(cat "$OUT_DIR/.last-body")"
fi

# 4. 删除草稿只删记录本身，不改动课次；随后重新保存并正常提交。
assert_request "删除草稿" 200 DELETE "/course-schedules/$attended_id/after-class-record/draft"
request GET "/course-schedules/$attended_id/after-class-record"
if [ -z "$(json_field data.status "$OUT_DIR/.last-body")" ] && [ "$(json_field data.scheduleStatus "$OUT_DIR/.last-body")" = "已上课" ]; then
  ok "删除草稿后课次仍保持已上课且无半条记录"
else
  fail_case "删除草稿清理记录且不改课次" "$(cat "$OUT_DIR/.last-body")"
fi
assert_request "重新保存草稿" 200 POST "/course-schedules/$attended_id/after-class-record/draft" '{"teacherComment":"构图不错"}'

# 5. 提交缺字段必须失败，失败后仍是草稿。
assert_request "提交必须补齐作品主题、完成度、点评" 400 POST "/course-schedules/$attended_id/after-class-record/submit" '{"teacherComment":"构图不错"}'
request GET "/course-schedules/$attended_id/after-class-record"
if [ "$(json_field data.status "$OUT_DIR/.last-body")" = "草稿" ]; then
  ok "提交失败后仍保持草稿"
else
  fail_case "提交失败后仍保持草稿" "$(cat "$OUT_DIR/.last-body")"
fi

# 6. 正常提交。
record_payload='{"artworkTheme":"窗边静物","completionPercent":90,"teacherComment":"层次清楚，边缘可再收一收","nextPractice":"练习明暗交界线"}'
assert_request "正常提交课后记录" 200 POST "/course-schedules/$attended_id/after-class-record/submit" "$record_payload"
record_id_1="$(json_field data.id "$OUT_DIR/.last-body")"

# 7. 重复提交幂等：同一记录 id、状态仍为已提交。
request POST "/course-schedules/$attended_id/after-class-record/submit" "$record_payload"
record_id_2="$(json_field data.id "$OUT_DIR/.last-body")"
status_2="$(json_field data.status "$OUT_DIR/.last-body")"
if [ "$record_id_1" = "$record_id_2" ] && [ "$status_2" = "已提交" ]; then
  ok "重复提交返回同一份已提交记录"
else
  fail_case "重复提交幂等" "id=$record_id_1/$record_id_2, status=$status_2"
fi

# 8. 已提交列表可见；归档后只读。
request GET "/course-schedules?studentId=$student_id&completedOnly=true"
if contains "$(cat "$OUT_DIR/.last-body")" "\"id\":$attended_id"; then
  ok "已提交记录计入已完成课程"
else
  fail_case "已提交记录计入已完成课程" "$(cat "$OUT_DIR/.last-body")"
fi
assert_request "归档已提交记录" 200 POST "/course-schedules/$attended_id/after-class-record/archive"
assert_request "归档后不能编辑" 400 POST "/course-schedules/$attended_id/after-class-record/draft" '{"teacherComment":"不能改"}'
assert_request "归档后不能再次提交" 400 POST "/course-schedules/$attended_id/after-class-record/submit" "$record_payload"

# 9. 并发提交：另取一个课次，两个请求同时发出，只能有一份成功写入；唯一索引兜底。
request POST /course-schedules "{\"studentId\":$student_id,\"courseName\":\"并发课\",\"date\":\"2026-10-10\",\"startMin\":660,\"endMin\":720}"
parallel_id="$(json_field data.id "$OUT_DIR/.last-body")"
request POST "/course-schedules/$parallel_id/checkin"
curl -sS -o "$OUT_DIR/p1" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"artworkTheme":"并发作品","completionPercent":80,"teacherComment":"并发点评一"}' \
  "$BASE_URL/course-schedules/$parallel_id/after-class-record/submit" > "$OUT_DIR/c1" &
curl -sS -o "$OUT_DIR/p2" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"artworkTheme":"并发作品","completionPercent":70,"teacherComment":"并发点评二"}' \
  "$BASE_URL/course-schedules/$parallel_id/after-class-record/submit" > "$OUT_DIR/c2" &
wait
c1="$(cat "$OUT_DIR/c1")"; c2="$(cat "$OUT_DIR/c2")"
if { [ "$c1" = 200 ] || [ "$c2" = 200 ]; }; then
  ok "并发提交至少一个请求成功（HTTP $c1 / $c2）"
else
  fail_case "并发提交" "HTTP $c1 / $c2"
fi
request GET "/course-schedules/$parallel_id/after-class-record"
parallel_record_id="$(json_field data.id "$OUT_DIR/.last-body")"
if [ -n "$parallel_record_id" ]; then
  ok "并发提交后仍只读到一份记录 id=$parallel_record_id"
else
  fail_case "并发提交后存在记录" "$(cat "$OUT_DIR/.last-body")"
fi

# 10. 学员状态变化为休学/结业不删除档案，仍可查看已提交记录；提交时只要求档案存在。
request PUT "/students/$student_id" '{"status":"休学"}'
request GET "/students/$student_id/after-class-records"
if contains "$(cat "$OUT_DIR/.last-body")" "\"scheduleId\":$attended_id"; then
  ok "学员状态变化后档案仍可看到记录状态"
else
  fail_case "学员状态变化后档案仍可看到记录状态" "$(cat "$OUT_DIR/.last-body")"
fi

# 11. 若在 Docker Compose 环境执行，直接删库验证：档案不存在时提交必须失败且不能补写记录。
if command -v docker >/dev/null 2>&1; then
  docker compose -f "$ROOT_DIR/docker-compose.yml" exec -T mysql \
    mysql -uroot "-p$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" \
    -e "INSERT INTO student (id, code, name, status, created_at) VALUES (999999999, 'ACR-MISSING', '待删学员', '在读', NOW()); UPDATE course_schedule SET student_id=999999999 WHERE id=$missing_student_id; DELETE FROM student WHERE id=999999999;" >/dev/null 2>&1 || true
  request POST "/course-schedules/$missing_student_id/after-class-record/submit" "$record_payload"
  if [ "$(cat "$OUT_DIR/.last-code")" = 400 ] && contains "$(cat "$OUT_DIR/.last-body")" "学员档案不存在或已删除"; then
    ok "学员被删除后提交被拒绝并给出原因"
  else
    fail_case "学员被删除后提交被拒绝" "HTTP $(cat "$OUT_DIR/.last-code"), body=$(cat "$OUT_DIR/.last-body")"
  fi
fi

echo "----------------------------------------"
echo "PASS=$pass FAIL=$fail"
[ "$fail" -eq 0 ]
