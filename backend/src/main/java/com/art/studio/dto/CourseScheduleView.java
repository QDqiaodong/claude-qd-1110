package com.art.studio.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CourseScheduleView {

    public Long id;
    public Long studentId;
    public String studentName;
    public String studentCode;
    public String studentStatus;
    public String courseName;
    public LocalDate date;
    public Integer startMin;
    public Integer endMin;
    public String status;
    public LocalDateTime createdAt;
    public Long recordId;
    public String recordStatus;
    public LocalDateTime recordUpdatedAt;
    public boolean recordCompleted;
}
