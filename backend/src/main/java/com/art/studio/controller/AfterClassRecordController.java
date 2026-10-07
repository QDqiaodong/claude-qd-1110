package com.art.studio.controller;

import java.util.List;

import com.art.studio.common.ApiResponse;
import com.art.studio.dto.AfterClassRecordDetail;
import com.art.studio.entity.AfterClassRecord;
import com.art.studio.service.AfterClassRecordService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AfterClassRecordController {

    private final AfterClassRecordService service;

    public AfterClassRecordController(AfterClassRecordService service) {
        this.service = service;
    }

    @GetMapping("/course-schedules/{scheduleId}/after-class-record")
    public ApiResponse<AfterClassRecordDetail> detail(@PathVariable Long scheduleId) {
        return ApiResponse.success(service.getBySchedule(scheduleId));
    }

    @PostMapping("/course-schedules/{scheduleId}/after-class-record/draft")
    public ApiResponse<AfterClassRecordDetail> saveDraft(@PathVariable Long scheduleId,
                                                         @RequestBody(required = false) AfterClassRecord input) {
        return ApiResponse.success(service.saveDraft(scheduleId, input == null ? new AfterClassRecord() : input));
    }

    @PostMapping("/course-schedules/{scheduleId}/after-class-record/submit")
    public ApiResponse<AfterClassRecordDetail> submit(@PathVariable Long scheduleId,
                                                      @RequestBody(required = false) AfterClassRecord input) {
        return ApiResponse.success(service.submit(scheduleId, input));
    }

    @PostMapping("/course-schedules/{scheduleId}/after-class-record/archive")
    public ApiResponse<AfterClassRecordDetail> archive(@PathVariable Long scheduleId) {
        return ApiResponse.success(service.archive(scheduleId));
    }

    @DeleteMapping("/course-schedules/{scheduleId}/after-class-record/draft")
    public ApiResponse<Void> deleteDraft(@PathVariable Long scheduleId) {
        service.deleteDraft(scheduleId);
        return ApiResponse.success("草稿已删除", null);
    }

    @GetMapping("/students/{studentId}/after-class-records")
    public ApiResponse<List<AfterClassRecordDetail>> listByStudent(@PathVariable Long studentId) {
        return ApiResponse.success(service.listByStudent(studentId));
    }
}
