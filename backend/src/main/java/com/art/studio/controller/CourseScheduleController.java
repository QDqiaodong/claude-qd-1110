package com.art.studio.controller;

import java.util.List;

import com.art.studio.dto.CourseScheduleView;
import com.art.studio.entity.CourseSchedule;
import com.art.studio.service.AfterClassRecordService;
import com.art.studio.service.CourseScheduleService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course-schedules")
public class CourseScheduleController {

    private final CourseScheduleService service;
    private final AfterClassRecordService recordService;

    public CourseScheduleController(CourseScheduleService service, AfterClassRecordService recordService) {
        this.service = service;
        this.recordService = recordService;
    }

    @GetMapping
    public com.art.studio.common.ApiResponse<List<CourseScheduleView>> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recordStatus,
            @RequestParam(required = false) Boolean completedOnly) {
        return com.art.studio.common.ApiResponse.success(
                recordService.listSchedules(studentId, status, recordStatus, completedOnly));
    }

    @PostMapping
    public com.art.studio.common.ApiResponse<CourseSchedule> create(@RequestBody CourseSchedule input) {
        return com.art.studio.common.ApiResponse.success(service.create(input));
    }

    @PostMapping("/{id}/cancel")
    public com.art.studio.common.ApiResponse<CourseSchedule> cancel(@PathVariable Long id) {
        return com.art.studio.common.ApiResponse.success(service.cancel(id));
    }

    @PostMapping("/{id}/checkin")
    public com.art.studio.common.ApiResponse<CourseSchedule> checkin(@PathVariable Long id) {
        return com.art.studio.common.ApiResponse.success(service.checkin(id));
    }
}
