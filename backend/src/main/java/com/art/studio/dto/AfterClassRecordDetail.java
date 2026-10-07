package com.art.studio.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AfterClassRecordDetail {

    public Long id;
    public Long scheduleId;
    public Long studentId;
    public String studentName;
    public String studentCode;
    public String studentStatus;
    public String courseName;
    public LocalDate scheduleDate;
    public Integer startMin;
    public Integer endMin;
    public String scheduleStatus;
    public String artworkTheme;
    public Integer completionPercent;
    public String teacherComment;
    public String nextPractice;
    public String status;
    public LocalDateTime submittedAt;
    public LocalDateTime archivedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public boolean completed;
    public boolean editable;
    public boolean archivable;
}
