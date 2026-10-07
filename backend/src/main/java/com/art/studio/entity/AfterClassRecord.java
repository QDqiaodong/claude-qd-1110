package com.art.studio.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "after_class_record")
public class AfterClassRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "schedule_id", nullable = false, unique = true)
    public Long scheduleId;

    @Column(name = "student_id", nullable = false)
    public Long studentId;

    @Column(name = "artwork_theme", length = 128)
    public String artworkTheme;

    @Column(name = "completion_percent")
    public Integer completionPercent;

    @Column(name = "teacher_comment", length = 1000)
    public String teacherComment;

    @Column(name = "next_practice", length = 500)
    public String nextPractice;

    /** 草稿 / 已提交 / 已归档 */
    @Column(name = "status", nullable = false, length = 16)
    public String status;

    @Column(name = "submitted_at")
    public LocalDateTime submittedAt;

    @Column(name = "archived_at")
    public LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
