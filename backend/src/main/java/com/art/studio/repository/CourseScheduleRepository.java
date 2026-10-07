package com.art.studio.repository;

import java.time.LocalDate;
import java.util.List;

import com.art.studio.entity.CourseSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByStudentId(Long studentId);

    List<CourseSchedule> findByStatus(String status);

    List<CourseSchedule> findByStudentIdAndStatus(Long studentId, String status);

    List<CourseSchedule> findByStudentIdAndDate(Long studentId, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseSchedule c where c.id = :id")
    java.util.Optional<CourseSchedule> findByIdForUpdate(@Param("id") Long id);
}
