package com.etu.reports.api;

import com.etu.reports.domain.Teaching;
import com.etu.reports.repository.TeachingRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TeacherController {

    private final TeachingRepository repo;

    public TeacherController(TeachingRepository repo) {
        this.repo = repo;
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
        if (profile == null) return Map.of("ok", false);

        String fio = profile.getLastName() + " " +
                (!safe(profile.getFirstName()).isEmpty() ? safe(profile.getFirstName()).charAt(0) + "." : "") +
                (!safe(profile.getPatronymic()).isEmpty() ? safe(profile.getPatronymic()).charAt(0) + "." : "");

        return Map.of(
                "ok", true,
                "lastName", safe(profile.getLastName()),
                "firstName", safe(profile.getFirstName()),
                "patronymic", safe(profile.getPatronymic()),
                "fioShort", fio,
                "position", safe(profile.getPosition()),
                "rank", safe(profile.getRank()),
                "degree", safe(profile.getDegree()),
                "role", "teacher",
                "department", safe(profile.getDepartmentName())
        );
    }
}
