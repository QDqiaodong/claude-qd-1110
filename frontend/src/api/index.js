import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error?.response?.data?.message || error?.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    throw new Error((body && body.message) || '请求失败')
  }
  return body.data
}

const params = (o) => ({ params: o })

export default {
  // ---------------- 学员 ----------------
  listStudents: (status, level) => call(http.get('/students', params(
    status ? { status, ...(level ? { level } : {}) }
            : (level ? { level } : {})))),
  createStudent: (d) => call(http.post('/students', d)),
  updateStudent: (id, d) => call(http.put(`/students/${id}`, d)),

  // ---------------- 画材 ----------------
  listArtMaterials: (category, status) => call(http.get('/art-materials', params(
    category ? { category, ...(status ? { status } : {}) }
              : (status ? { status } : {})))),
  createArtMaterial: (d) => call(http.post('/art-materials', d)),
  updateArtMaterial: (id, d) => call(http.put(`/art-materials/${id}`, d)),

  // ---------------- 课程排期 ----------------
  listCourseSchedules: (studentId, status) => call(http.get('/course-schedules', params(
    studentId ? { studentId, ...(status ? { status } : {}) }
              : (status ? { status } : {})))),
  createCourseSchedule: (d) => call(http.post('/course-schedules', d)),
  cancelCourseSchedule: (id) => call(http.post(`/course-schedules/${id}/cancel`)),
  checkinCourseSchedule: (id) => call(http.post(`/course-schedules/${id}/checkin`)),

  // ---------------- 领用记录 ----------------
  listMaterialUses: () => call(http.get('/material-uses')),
  createMaterialUse: (d) => call(http.post('/material-uses', d))
}
