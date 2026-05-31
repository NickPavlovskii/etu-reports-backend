package com.etu.reports.api;

import com.etu.reports.dto.student.StudentDto;
import com.etu.reports.repository.StudentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Студент по id (ФИО из справочника)")
public class StudentController {

    private final StudentRepository studentRepo;

    public StudentController(StudentRepository studentRepo) {
        this.studentRepo = studentRepo;
    }

    @Operation(summary = "Студент по id", description = "По id (lk_id) возвращает студента с ФИО из справочника student_fio, если есть")
    @GetMapping("/{id}")
    public StudentDto get(@PathVariable int id) {
        return studentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: id=" + id));
    }
}
