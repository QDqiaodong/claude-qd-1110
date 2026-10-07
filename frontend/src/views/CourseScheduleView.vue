<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import AfterClassRecordDialog from '../components/AfterClassRecordDialog.vue'

const emit = defineEmits(['changed'])

const loading = ref(false)
const schedules = ref([])
const students = ref([])
const studentMap = ref({})
const studentId = ref(null)
const status = ref('')
const recordStatus = ref('')
const completedOnly = ref(false)

const recordDialogVisible = ref(false)
const activeScheduleId = ref(null)

const STATUS = ['待上课', '已上课', '已取消']
const RECORD_STATUS = [
  { label: '无记录', value: '无记录' },
  { label: '草稿（未结课）', value: '草稿' },
  { label: '已提交', value: '已提交' },
  { label: '已归档', value: '已归档' }
]

const fmtMin = (n) => {
  if (n == null) return ''
  const h = Math.floor(n / 60); const m = n % 60
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0')
}
const fmtTime = (t) => t ? String(t).replace('T', ' ').slice(0, 16) : '—'
const statusType = (s) => ({ 待上课: 'warning', 已上课: 'success', 已取消: 'info' }[s] || '')
const recordType = (s) => ({ 草稿: 'warning', 已提交: 'success', 已归档: 'info' }[s] || 'info')

async function loadStudents() {
  students.value = await api.listStudents()
  studentMap.value = Object.fromEntries(students.value.map((s) => [s.id, s.name]))
}
async function load() {
  loading.value = true
  try {
    schedules.value = await api.listCourseSchedules(
      studentId.value || null,
      status.value || null,
      recordStatus.value || null,
      completedOnly.value || null
    )
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
function resetFilters() {
  studentId.value = null
  status.value = ''
  recordStatus.value = ''
  completedOnly.value = false
  load()
}

async function cancel(row) {
  try { await api.cancelCourseSchedule(row.id); ElMessage.success('已取消'); await load(); emit('changed') } catch (e) { ElMessage.error(e.message) }
}
async function checkin(row) {
  try { await api.checkinCourseSchedule(row.id); ElMessage.success('已签到上课'); await load(); emit('changed') } catch (e) { ElMessage.error(e.message) }
}
function explainRecord(row) {
  if (row.status !== '已上课') {
    ElMessage.warning(`课次当前为「${row.status}」，只有「已上课」后才能新建或提交课后记录。`)
    return
  }
  activeScheduleId.value = row.id
  recordDialogVisible.value = true
}
async function onRecordSaved() {
  await load()
  emit('changed')
}

onMounted(async () => { await loadStudents(); await load() })
</script>

<template>
  <div>
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="studentId" placeholder="学员" clearable filterable style="width:160px" @change="load">
          <el-option v-for="s in students" :key="s.id" :label="s.name + '（' + s.code + '）'" :value="s.id" />
        </el-select>
        <el-select v-model="status" placeholder="课次状态" clearable style="width:120px" @change="load">
          <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="recordStatus" placeholder="记录状态" clearable style="width:150px" @change="load">
          <el-option v-for="s in RECORD_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-checkbox v-model="completedOnly" @change="load">仅看已完成课程</el-checkbox>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <span class="hint">已提交或已归档才算结课；草稿不会出现在已完成课程中</span>
    </div>

    <el-table :data="schedules" v-loading="loading" border stripe>
      <el-table-column label="学员" width="120">
        <template #default="{ row }">{{ studentMap[row.studentId] || row.studentName || row.studentId }}</template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程" min-width="130" />
      <el-table-column prop="date" label="日期" width="110" />
      <el-table-column label="时段" width="120">
        <template #default="{ row }">{{ fmtMin(row.startMin) }}–{{ fmtMin(row.endMin) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="课次状态" width="95">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="课后记录" min-width="190">
        <template #default="{ row }">
          <el-tag v-if="row.recordStatus" :type="recordType(row.recordStatus)">
            {{ row.recordStatus }}<template v-if="row.recordCompleted"> · 已结课</template>
          </el-tag>
          <span v-else class="muted">—</span>
          <div class="updated">最后更新：{{ fmtTime(row.recordUpdatedAt) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="220">
        <template #default="{ row }">
          <el-button v-if="row.status === '待上课'" link type="success" @click="checkin(row)">签到上课</el-button>
          <el-button v-if="row.status === '待上课'" link type="danger" @click="cancel(row)">取消</el-button>
          <el-button link type="primary" @click="explainRecord(row)">
            {{ row.recordStatus ? '查看记录' : '课后记录' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <AfterClassRecordDialog
      v-model="recordDialogVisible"
      :schedule-id="activeScheduleId"
      @saved="onRecordSaved"
    />
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.hint { color: var(--muted); font-size: 13px; }
.muted { color: var(--muted); }
.updated { color: var(--muted); font-size: 12px; margin-top: 4px; }
</style>
