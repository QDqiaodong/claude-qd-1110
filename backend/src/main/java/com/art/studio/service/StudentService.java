package com.art.studio.service;

import java.time.LocalDateTime;
import java.util.List;

import com.art.studio.common.BizException;
import com.art.studio.common.Dict;
import com.art.studio.entity.Student;
import com.art.studio.repository.StudentRepository;

import org.springframework.stereotype.Service;

@Service
public class StudentService {

    private final StudentRepository repo;

    public StudentService(StudentRepository repo) {
        this.repo = repo;
    }

    public List<Student> list(String status, String level) {
        if (status != null && level != null) {
            return repo.findByStatusAndLevel(status, level);
        }
        if (status != null) {
            return repo.findByStatus(status);
        }
        if (level != null) {
            return repo.findByLevel(level);
        }
        return repo.findAll();
    }

    public Student get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("学员不存在：" + id));
    }

    public Student create(Student input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("学号不能为空");
        }
        if (repo.findByCode(input.code).isPresent()) {
            throw new BizException("学号已存在：" + input.code);
        }
        if (input.name == null || input.name.isBlank()) {
            throw new BizException("学员姓名不能为空");
        }
        if (input.phone != null && !input.phone.isBlank()
                && !input.phone.matches("1[3-9]\\d{9}")) {
            throw new BizException("手机号格式不正确（应为 1[3-9] 开头的 11 位号码）");
        }
        String level = input.level == null ? Dict.STUDENT_LEVELS.get(0) : input.level;
        if (!Dict.isValidStudentLevel(level)) {
            throw new BizException("学员级别不合法：" + level);
        }
        String status = input.status == null ? "在读" : input.status;
        if (!Dict.isValidStudentStatus(status)) {
            throw new BizException("学员状态不合法：" + status);
        }
        Student s = new Student();
        s.code = input.code;
        s.name = input.name;
        s.phone = input.phone;
        s.level = level;
        s.status = status;
        s.createdAt = LocalDateTime.now();
        return repo.save(s);
    }

    public Student update(Long id, Student input) {
        Student s = get(id);
        if (input.code != null) {
            if (!input.code.equals(s.code) && repo.findByCode(input.code).isPresent()) {
                throw new BizException("学号已存在：" + input.code);
            }
            s.code = input.code;
        }
        if (input.name != null) {
            s.name = input.name;
        }
        if (input.phone != null) {
            if (!input.phone.isBlank() && !input.phone.matches("1[3-9]\\d{9}")) {
                throw new BizException("手机号格式不正确（应为 1[3-9] 开头的 11 位号码）");
            }
            s.phone = input.phone;
        }
        if (input.level != null) {
            if (!Dict.isValidStudentLevel(input.level)) {
                throw new BizException("学员级别不合法：" + input.level);
            }
            s.level = input.level;
        }
        if (input.status != null) {
            if (!Dict.isValidStudentStatus(input.status)) {
                throw new BizException("学员状态不合法：" + input.status);
            }
            s.status = input.status;
        }
        return repo.save(s);
    }
}
