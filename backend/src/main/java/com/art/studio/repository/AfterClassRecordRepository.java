package com.art.studio.repository;

import java.util.List;
import java.util.Optional;

import com.art.studio.entity.AfterClassRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AfterClassRecordRepository extends JpaRepository<AfterClassRecord, Long> {

    Optional<AfterClassRecord> findByScheduleId(Long scheduleId);

    List<AfterClassRecord> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    boolean existsByScheduleId(Long scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AfterClassRecord r where r.scheduleId = :scheduleId")
    Optional<AfterClassRecord> findByScheduleIdForUpdate(@Param("scheduleId") Long scheduleId);
}
