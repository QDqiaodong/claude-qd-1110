<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const loading = ref(false)
const schedules = ref([])
const students = ref([])
const studentMap = ref({})
const studentId = ref(null)
const status = ref('')

const STATUS = ['待上课', '已上课', '已取消']

const fmtMin = (n) => {
  if (n == null) return ''
  const h = Math.floor(n / 60); const m = n % 60
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0')
}
const statusType = (s) => ({ '待上课': 'warning', '已上课': 'success', '已取消': 'info' }[s] || '')

async function loadStudents() {
  students.value = await api.listStudents()
  studentMap.value = Object.fromEntries(students.value.map((s) => [s.id, s.name]))
}
async function load() {
  loading.value = true
  try { schedules.value = await api.listCourseSchedules(studentId.value || null, status.value || null) }
  catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
function resetFilters() { studentId.value = null; status.value = ''; load() }

async function cancel(row) {
  try { await api.cancelCourseSchedule(row.id); ElMessage.success('已取消'); await load(); emit('changed') } catch (e) { ElMessage.error(e.message) }
}
async function checkin(row) {
  try { await api.checkinCourseSchedule(row.id); ElMessage.success('已签到上课'); await load(); emit('changed') } catch (e) { ElMessage.error(e.message) }
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
        <el-select v-model="status" placeholder="状态" clearable style="width:120px" @change="load">
          <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <span class="hint">排课请到「学员档案」双击学员 → 右侧抽屉 → 排课</span>
    </div>

    <el-table :data="schedules" v-loading="loading" border stripe>
      <el-table-column label="学员" width="120">
        <template #default="{ row }">{{ studentMap[row.studentId] || row.studentId }}</template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程" />
      <el-table-column prop="date" label="日期" width="120" />
      <el-table-column label="时段" width="130">
        <template #default="{ row }">{{ fmtMin(row.startMin) }}–{{ fmtMin(row.endMin) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="160">
        <template #default="{ row }">
          <el-button v-if="row.status === '待上课'" link type="success" @click="checkin(row)">签到上课</el-button>
          <el-button v-if="row.status === '待上课'" link type="danger" @click="cancel(row)">取消</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.filters { display: flex; gap: 8px; }
.hint { color: var(--muted); font-size: 13px; }
.muted { color: var(--muted); }
</style>
