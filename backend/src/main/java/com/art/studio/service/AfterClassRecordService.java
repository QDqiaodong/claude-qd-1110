package com.art.studio.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.art.studio.common.BizException;
import com.art.studio.dto.AfterClassRecordDetail;
import com.art.studio.dto.CourseScheduleView;
import com.art.studio.entity.AfterClassRecord;
import com.art.studio.entity.CourseSchedule;
import com.art.studio.entity.Student;
import com.art.studio.repository.AfterClassRecordRepository;
import com.art.studio.repository.CourseScheduleRepository;
import com.art.studio.repository.StudentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterClassRecordService {

    public static final String DRAFT = "草稿";
    public static final String SUBMITTED = "已提交";
    public static final String ARCHIVED = "已归档";

    private final AfterClassRecordRepository recordRepo;
    private final CourseScheduleRepository scheduleRepo;
    private final StudentRepository studentRepo;

    public AfterClassRecordService(AfterClassRecordRepository recordRepo,
                                   CourseScheduleRepository scheduleRepo,
                                   StudentRepository studentRepo) {
        this.recordRepo = recordRepo;
        this.scheduleRepo = scheduleRepo;
        this.studentRepo = studentRepo;
    }

    @Transactional(readOnly = true)
    public AfterClassRecordDetail getBySchedule(Long scheduleId) {
        CourseSchedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new BizException("排课记录不存在：" + scheduleId));
        AfterClassRecord record = recordRepo.findByScheduleId(scheduleId).orElse(null);
        return toDetail(schedule, record);
    }

    @Transactional(readOnly = true)
    public List<CourseScheduleView> listSchedules(Long studentId, String status, String recordStatus, Boolean completedOnly) {
        List<CourseSchedule> schedules = scheduleRepo.findAll();
        List<AfterClassRecord> records = recordRepo.findAll();
        Map<Long, AfterClassRecord> recordMap = new HashMap<>();
        for (AfterClassRecord r : records) {
            recordMap.put(r.scheduleId, r);
        }

        Set<Long> studentIds = new HashSet<>();
        for (CourseSchedule s : schedules) {
            studentIds.add(s.studentId);
        }
        Map<Long, Student> studentMap = new HashMap<>();
        for (Student s : studentRepo.findAllById(studentIds)) {
            studentMap.put(s.id, s);
        }

        List<CourseScheduleView> result = new ArrayList<>();
        for (CourseSchedule s : schedules) {
            AfterClassRecord r = recordMap.get(s.id);
            boolean completed = isCompleted(r);
            if (studentId != null && !studentId.equals(s.studentId)) {
                continue;
            }
            if (status != null && !status.isBlank() && !status.equals(s.status)) {
                continue;
            }
            if (recordStatus != null && !recordStatus.isBlank()) {
                String current = r == null ? "无记录" : r.status;
                if (!recordStatus.equals(current)) {
                    continue;
                }
            }
            if (Boolean.TRUE.equals(completedOnly) && !completed) {
                continue;
            }
            result.add(toScheduleView(s, studentMap.get(s.studentId), r));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AfterClassRecordDetail> listByStudent(Long studentId) {
        if (!studentRepo.existsById(studentId)) {
            throw new BizException("学员不存在：" + studentId);
        }
        List<AfterClassRecord> records = recordRepo.findByStudentIdOrderByUpdatedAtDesc(studentId);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> scheduleIds = records.stream().map(r -> r.scheduleId).toList();
        List<Long> scheduleIdList = new ArrayList<>(scheduleIds);
        Map<Long, CourseSchedule> scheduleMap = new HashMap<>();
        for (CourseSchedule s : scheduleRepo.findAllById(scheduleIdList)) {
            scheduleMap.put(s.id, s);
        }
        Student student = studentRepo.findById(studentId).orElse(null);
        List<AfterClassRecordDetail> result = new ArrayList<>();
        for (AfterClassRecord r : records) {
            CourseSchedule schedule = scheduleMap.get(r.scheduleId);
            if (schedule != null) {
                result.add(toDetail(schedule, r, student));
            }
        }
        return result;
    }

    @Transactional
    public AfterClassRecordDetail saveDraft(Long scheduleId, AfterClassRecord input) {
        CourseSchedule schedule = lockAttendedSchedule(scheduleId);
        Student student = studentRepo.findById(schedule.studentId)
                .orElseThrow(() -> new BizException("学员档案不存在或已删除，不能保存课后记录"));
        AfterClassRecord record = recordRepo.findByScheduleIdForUpdate(scheduleId).orElse(null);
        if (record != null && !DRAFT.equals(record.status)) {
            throw new BizException("课后记录已" + record.status + "，不能继续编辑；归档后内容只读");
        }

        LocalDateTime now = LocalDateTime.now();
        if (record == null) {
            record = new AfterClassRecord();
            record.scheduleId = schedule.id;
            record.studentId = schedule.studentId;
            record.status = DRAFT;
            record.createdAt = now;
        }
        applyInput(record, input);
        record.updatedAt = now;
        return toDetail(schedule, recordRepo.saveAndFlush(record), student);
    }

    @Transactional
    public AfterClassRecordDetail submit(Long scheduleId, AfterClassRecord input) {
        CourseSchedule schedule = lockAttendedSchedule(scheduleId);
        Student student = studentRepo.findById(schedule.studentId)
                .orElseThrow(() -> new BizException("学员档案不存在或已删除，不能提交课后记录"));

        AfterClassRecord existing = recordRepo.findByScheduleIdForUpdate(scheduleId).orElse(null);
        if (existing != null && !DRAFT.equals(existing.status)) {
            // 提交接口天然幂等：重复点击、网络重试、并发后的第二个请求都返回同一份已收口记录。
            return toDetail(schedule, existing, student);
        }

        if (input == null) {
            throw new BizException("提交内容不能为空");
        }
        validateComplete(input);

        AfterClassRecord record = existing == null ? new AfterClassRecord() : existing;
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            record.scheduleId = schedule.id;
            record.studentId = schedule.studentId;
            record.createdAt = now;
        }
        applyInput(record, input);
        record.status = SUBMITTED;
        record.submittedAt = now;
        record.archivedAt = null;
        record.updatedAt = now;
        return toDetail(schedule, recordRepo.saveAndFlush(record), student);
    }

    @Transactional
    public AfterClassRecordDetail archive(Long scheduleId) {
        CourseSchedule schedule = lockAttendedSchedule(scheduleId);
        Student student = studentRepo.findById(schedule.studentId)
                .orElseThrow(() -> new BizException("学员档案不存在或已删除，不能归档课后记录"));
        AfterClassRecord record = recordRepo.findByScheduleIdForUpdate(scheduleId)
                .orElseThrow(() -> new BizException("课后记录不存在，不能归档"));
        if (DRAFT.equals(record.status)) {
            throw new BizException("草稿尚未补齐并提交，不能归档");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!ARCHIVED.equals(record.status)) {
            record.status = ARCHIVED;
            record.archivedAt = now;
            record.updatedAt = now;
            record = recordRepo.save(record);
        }
        return toDetail(schedule, record, student);
    }

    @Transactional
    public void deleteDraft(Long scheduleId) {
        CourseSchedule schedule = lockAttendedSchedule(scheduleId);
        AfterClassRecord record = recordRepo.findByScheduleIdForUpdate(scheduleId)
                .orElseThrow(() -> new BizException("草稿不存在，无需删除"));
        if (!DRAFT.equals(record.status)) {
            throw new BizException("课后记录已" + record.status + "，不能删除");
        }
        recordRepo.delete(record);
    }

    private CourseSchedule lockAttendedSchedule(Long scheduleId) {
        if (scheduleId == null) {
            throw new BizException("必须指定课程课次");
        }
        CourseSchedule schedule = scheduleRepo.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BizException("排课记录不存在：" + scheduleId));
        if (!"已上课".equals(schedule.status)) {
            throw new BizException("只有「已上课」的课次才能记录课后成果"
                    + "（当前课次为「" + schedule.status + "」）");
        }
        return schedule;
    }

    private void validateComplete(AfterClassRecord input) {
        List<String> missing = new ArrayList<>();
        if (isBlank(input.artworkTheme)) {
            missing.add("作品主题");
        }
        if (input.completionPercent == null) {
            missing.add("完成度");
        }
        if (isBlank(input.teacherComment)) {
            missing.add("教师点评");
        }
        if (!missing.isEmpty()) {
            throw new BizException("提交前必须补齐：" + String.join("、", missing));
        }
    }

    private void applyInput(AfterClassRecord target, AfterClassRecord input) {
        if (input == null) {
            return;
        }
        target.artworkTheme = normalize(input.artworkTheme, 128, "作品主题");
        target.completionPercent = normalizeCompletion(input.completionPercent);
        target.teacherComment = normalize(input.teacherComment, 1000, "教师点评");
        target.nextPractice = normalize(input.nextPractice, 500, "下次练习");
    }

    private String normalize(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BizException(field + "不能超过 " + maxLength + " 字");
        }
        return trimmed;
    }

    private Integer normalizeCompletion(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > 100) {
            throw new BizException("完成度必须是 0 到 100 之间的百分比");
        }
        return value;
    }

    private boolean isCompleted(AfterClassRecord record) {
        return record != null && (SUBMITTED.equals(record.status) || ARCHIVED.equals(record.status));
    }

    private CourseScheduleView toScheduleView(CourseSchedule schedule, Student student, AfterClassRecord record) {
        CourseScheduleView v = new CourseScheduleView();
        v.id = schedule.id;
        v.studentId = schedule.studentId;
        v.courseName = schedule.courseName;
        v.date = schedule.date;
        v.startMin = schedule.startMin;
        v.endMin = schedule.endMin;
        v.status = schedule.status;
        v.createdAt = schedule.createdAt;
        if (student != null) {
            v.studentName = student.name;
            v.studentCode = student.code;
            v.studentStatus = student.status;
        }
        if (record != null) {
            v.recordId = record.id;
            v.recordStatus = record.status;
            v.recordUpdatedAt = record.updatedAt;
            v.recordCompleted = isCompleted(record);
        }
        return v;
    }

    private AfterClassRecordDetail toDetail(CourseSchedule schedule, AfterClassRecord record) {
        Student student = studentRepo.findById(schedule.studentId).orElse(null);
        return toDetail(schedule, record, student);
    }

    private AfterClassRecordDetail toDetail(CourseSchedule schedule, AfterClassRecord record, Student student) {
        AfterClassRecordDetail d = new AfterClassRecordDetail();
        d.scheduleId = schedule.id;
        d.studentId = schedule.studentId;
        d.courseName = schedule.courseName;
        d.scheduleDate = schedule.date;
        d.startMin = schedule.startMin;
        d.endMin = schedule.endMin;
        d.scheduleStatus = schedule.status;
        if (student != null) {
            d.studentName = student.name;
            d.studentCode = student.code;
            d.studentStatus = student.status;
        }
        if (record != null) {
            d.id = record.id;
            d.artworkTheme = record.artworkTheme;
            d.completionPercent = record.completionPercent;
            d.teacherComment = record.teacherComment;
            d.nextPractice = record.nextPractice;
            d.status = record.status;
            d.submittedAt = record.submittedAt;
            d.archivedAt = record.archivedAt;
            d.createdAt = record.createdAt;
            d.updatedAt = record.updatedAt;
            d.completed = isCompleted(record);
            d.editable = DRAFT.equals(record.status) && "已上课".equals(schedule.status);
            d.archivable = SUBMITTED.equals(record.status) && "已上课".equals(schedule.status);
        } else {
            d.editable = "已上课".equals(schedule.status);
        }
        return d;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
