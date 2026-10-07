-- 既有环境增量迁移：新增课后成果记录表，不删除、不改写任何历史排课/签到/领用数据。
-- 全新镜像已经内置 mysql/schema.sql，无需执行本文件。
USE art_studio;

CREATE TABLE IF NOT EXISTS after_class_record (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id        BIGINT NOT NULL COMMENT '课次 id',
    student_id         BIGINT NOT NULL COMMENT '学员 id（快照为课次学员，供档案索引）',
    artwork_theme      VARCHAR(128) COMMENT '本次作品主题',
    completion_percent INT COMMENT '完成度（0-100）',
    teacher_comment    VARCHAR(1000) COMMENT '教师点评',
    next_practice      VARCHAR(500) COMMENT '下次练习',
    status             VARCHAR(16) NOT NULL COMMENT '草稿 / 已提交 / 已归档',
    submitted_at       DATETIME COMMENT '提交时间',
    archived_at        DATETIME COMMENT '归档时间',
    created_at         DATETIME NOT NULL COMMENT '创建时间',
    updated_at         DATETIME NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_acr_schedule (schedule_id),
    KEY idx_acr_student_updated (student_id, updated_at),
    CONSTRAINT fk_acr_schedule FOREIGN KEY (schedule_id) REFERENCES course_schedule (id),
    CONSTRAINT fk_acr_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
