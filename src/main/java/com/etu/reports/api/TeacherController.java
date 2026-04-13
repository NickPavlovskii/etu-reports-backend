package com.etu.reports.api;

import com.etu.reports.domain.Teaching;
import com.etu.reports.repository.TeachingRepository;
import com.etu.reports.repository.TeacherProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import static java.util.Map.entry;

@RestController
@RequestMapping("/api")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);
    private final TeachingRepository repo;
    private final TeacherProfileRepository teacherProfileRepo;

    public TeacherController(TeachingRepository repo, TeacherProfileRepository teacherProfileRepo) {
        this.repo = repo;
        this.teacherProfileRepo = teacherProfileRepo;
    }

    @GetMapping("/teachers/{lastName}")
    public List<Teaching> rows(@PathVariable String lastName) {
        return repo.findByLastName(lastName);
    }

    @GetMapping("/teachers/{lastName}/disciplines")
    public List<String> disciplines(@PathVariable String lastName) {
        return repo.findDistinctDisciplinesByLastName(lastName);
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    @GetMapping("/auth/login")
    public Map<String, Object> login(@RequestParam String lastName) {
        Teaching profile = repo.findTeacherProfileByLastName(lastName);
        if (profile == null) {
            log.info("Auth login: {} — не найден, ответ ok=false", lastName);
            return Map.of("ok", false);
        }

        String fio = profile.getLastName() + " " +
                (!safe(profile.getFirstName()).isEmpty() ? safe(profile.getFirstName()).charAt(0) + "." : "") +
                (!safe(profile.getPatronymic()).isEmpty() ? safe(profile.getPatronymic()).charAt(0) + "." : "");

        String role = teacherProfileRepo.getRole(lastName);
        if (role == null || role.isBlank()) role = "TEACHER";
        String roleDisplay = "ADMIN".equalsIgnoreCase(role) ? "Администратор" : "Преподаватель";

        // Дисциплины преподавателя берём из teaching_load_csv (как и остальные справочные поля).
        List<String> disciplines = repo.findDistinctDisciplinesByLastName(lastName);

        Map<String, Object> result = Map.ofEntries(
                entry("ok", true),
                entry("lastName", safe(profile.getLastName())),
                entry("firstName", safe(profile.getFirstName())),
                entry("patronymic", safe(profile.getPatronymic())),
                entry("fioShort", fio),
                entry("position", safe(profile.getPosition())),
                entry("rank", safe(profile.getRank())),
                entry("degree", safe(profile.getDegree())),
                entry("role", role.trim().toUpperCase()),
                entry("roleDisplay", roleDisplay),
                entry("department", safe(profile.getDepartmentName())),
                entry("disciplines", disciplines)
        );
        log.info("Auth login: ответ для {} -> {}", lastName, result);
        return result;
    }
}
