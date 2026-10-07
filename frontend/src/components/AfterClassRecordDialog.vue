<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  scheduleId: { type: [Number, String], default: null },
  appendToBody: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const loading = ref(false)
const saving = ref(false)
const detail = ref(null)
const form = reactive({
  artworkTheme: '',
  completionPercent: null,
  teacherComment: '',
  nextPractice: ''
})

const recordStatusType = (s) => ({ 草稿: 'warning', 已提交: 'success', 已归档: 'info' }[s] || 'info')
const fmtMin = (n) => {
  if (n == null) return ''
  const h = Math.floor(n / 60); const m = n % 60
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0')
}
const fmtTime = (t) => t ? String(t).replace('T', ' ').slice(0, 19) : '—'

const canEdit = () => detail.value?.editable === true && detail.value?.status === '草稿' && !!detail.value?.studentName
const canCreateDraft = () => detail.value?.scheduleStatus === '已上课' && !detail.value?.status && !!detail.value?.studentName
const isReadonly = () => detail.value?.status === '已提交' || detail.value?.status === '已归档'

function close() {
  emit('update:modelValue', false)
}

function fillForm(data) {
  Object.assign(form, {
    artworkTheme: data.artworkTheme || '',
    completionPercent: data.completionPercent ?? null,
    teacherComment: data.teacherComment || '',
    nextPractice: data.nextPractice || ''
  })
}

async function load() {
  if (!props.scheduleId) return
  loading.value = true
  try {
    detail.value = await api.getAfterClassRecord(props.scheduleId)
    fillForm(detail.value)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function run(action, successMessage, confirmText) {
  if (saving.value) return
  if (confirmText) {
    try { await ElMessageBox.confirm(confirmText, '操作确认', { type: 'warning' }) }
    catch { return }
  }
  saving.value = true
  try {
    detail.value = await action()
    fillForm(detail.value)
    ElMessage.success(successMessage)
    emit('saved')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

const saveDraft = () => run(
  () => api.saveAfterClassDraft(props.scheduleId, { ...form }),
  '草稿已保存（作业信息可稍后补齐）'
)
const submit = () => run(
  () => api.submitAfterClassRecord(props.scheduleId, { ...form }),
  '课后记录已提交，课次已结课'
)
const archive = () => run(
  () => api.archiveAfterClassRecord(props.scheduleId),
  '已归档，记录现在只读',
  '归档后课后记录将变为只读，确认归档？'
)
async function removeDraft() {
  try {
    await ElMessageBox.confirm('删除后不会影响课次、库存和学员状态，确认删除这份草稿？', '删除草稿', { type: 'warning' })
  } catch { return }
  saving.value = true
  try {
    await api.deleteAfterClassDraft(props.scheduleId)
    ElMessage.success('草稿已删除')
    emit('saved')
    close()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

watch(() => props.modelValue, (visible) => {
  if (visible && props.scheduleId) load()
})
watch(() => props.scheduleId, (id) => {
  if (props.modelValue && id) load()
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="课后成果记录"
    width="640px"
    :close-on-click-modal="false"
    :append-to-body="appendToBody"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <el-alert
          v-if="!detail.studentName"
          title="未找到学员档案，不能新建或提交课后记录；请先恢复或确认学员档案。"
          type="error"
          :closable="false"
          show-icon
          class="mb-12"
        />

        <el-alert
          v-if="detail.scheduleStatus !== '已上课'"
          :title="`当前课次为「${detail.scheduleStatus}」，只有「已上课」课次才能新建或提交课后记录。`"
          type="warning"
          :closable="false"
          show-icon
          class="mb-12"
        />

        <el-descriptions :column="2" border size="small" class="mb-12">
          <el-descriptions-item label="学员">{{ detail.studentName || detail.studentId }}</el-descriptions-item>
          <el-descriptions-item label="学员状态">
            <el-tag size="small" :type="detail.studentStatus === '在读' ? 'success' : 'warning'">
              {{ detail.studentStatus || '未知' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="课程">{{ detail.courseName }}</el-descriptions-item>
          <el-descriptions-item label="课次">
            {{ detail.scheduleDate }} {{ fmtMin(detail.startMin) }}–{{ fmtMin(detail.endMin) }}
          </el-descriptions-item>
          <el-descriptions-item label="记录状态">
            <el-tag v-if="detail.status" :type="recordStatusType(detail.status)">{{ detail.status }}</el-tag>
            <span v-else class="muted">尚未记录</span>
          </el-descriptions-item>
          <el-descriptions-item label="最后更新">{{ fmtTime(detail.updatedAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.status === '草稿'"
          title="草稿可只快速记录教师点评；提交前必须补齐作品主题、完成度和点评。草稿不会计入已完成课程。"
          type="info"
          :closable="false"
          class="mb-12"
        />
        <el-alert
          v-if="detail.status === '已提交'"
          title="记录已提交并计入已完成课程；归档后将转为只读。"
          type="success"
          :closable="false"
          class="mb-12"
        />
        <el-alert
          v-if="detail.status === '已归档'"
          title="记录已归档，内容只读。"
          type="info"
          :closable="false"
          class="mb-12"
        />

        <el-form label-position="top" :disabled="isReadonly() || saving">
          <el-form-item label="本次作品主题（提交必填）" required>
            <el-input v-model="form.artworkTheme" maxlength="128" show-word-limit :disabled="!canEdit() && !canCreateDraft()" placeholder="如：秋日树林水彩写生" />
          </el-form-item>
          <el-form-item label="完成度（提交必填，0–100%）" required>
            <el-input-number v-model="form.completionPercent" :min="0" :max="100" :step="5" controls-position="right" :disabled="!canEdit() && !canCreateDraft()" />
          </el-form-item>
          <el-form-item label="教师点评（提交必填，草稿可先快速记录）" required>
            <el-input v-model="form.teacherComment" type="textarea" :rows="3" maxlength="1000" show-word-limit :disabled="!canEdit() && !canCreateDraft()" placeholder="构图、色彩、用笔等反馈" />
          </el-form-item>
          <el-form-item label="下次练习（选填）">
            <el-input v-model="form.nextPractice" type="textarea" :rows="2" maxlength="500" show-word-limit :disabled="!canEdit() && !canCreateDraft()" placeholder="如：练习 3 组明暗渐变" />
          </el-form-item>
        </el-form>
      </template>
    </div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
      <template v-if="detail && detail.scheduleStatus === '已上课' && detail.studentName">
        <el-button v-if="detail.status === '草稿'" type="danger" plain :loading="saving" @click="removeDraft">删除草稿</el-button>
        <el-button v-if="!detail.status || detail.status === '草稿'" :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button v-if="!detail.status || detail.status === '草稿'" type="primary" :loading="saving" @click="submit">提交并结课</el-button>
        <el-button v-if="detail.status === '已提交'" type="primary" :loading="saving" @click="archive">归档</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.muted { color: var(--muted); }
</style>
