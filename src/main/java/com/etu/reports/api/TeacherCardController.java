package com.etu.reports.api;

import com.etu.reports.domain.DisciplineCard;
import com.etu.reports.repository.TeachingCardRepository;
import com.etu.reports.util.AcademicYearValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Teacher cards", description = "Карточки дисциплин и группы преподавателя")
public class TeacherCardController {

    private final TeachingCardRepository repo;

    public TeacherCardController(TeachingCardRepository repo) {
        this.repo = repo;
    }

    @Operation(
            summary = "Карточки дисциплин преподавателя",
            description = "Агрегация по discipline_name + course + semester. groupsCount — кол-во уникальных групп с неотчисленными студентами.",
            responses = { @ApiResponse(responseCode = "200", description = "Список карточек") }
    )
    @GetMapping("/teachers/{lastName}/disciplines/cards")
    public List<DisciplineCard> disciplineCards(
            @Parameter(example = "Иванов") @PathVariable String lastName,
            @Parameter(required = true, example = "2024-2025", description = "Учебный год (формат YYYY-YYYY)") @RequestParam("academicYear") String academicYear
    ) {
        return repo.findDisciplineCardsByLastName(lastName, AcademicYearValidator.requireValid(academicYear));
    }

    @Operation(
            summary = "Все уникальные группы преподавателя",
            description = "Список group_name по кафедре, только группы с неотчисленными студентами.",
            responses = { @ApiResponse(responseCode = "200", description = "Список групп") }
    )
    @GetMapping("/teachers/{lastName}/groups")
    public List<String> teacherGroups(
            @Parameter(example = "Иванов") @PathVariable String lastName
    ) {
        return repo.findAllGroupsByLastName(lastName);
    }

    @Operation(
            summary = "Группы по дисциплине (planRowId)",
            description = "По planRowId определяется discipline+course+semester и возвращаются уникальные группы по этой связке.",
            responses = { @ApiResponse(responseCode = "200", description = "Список групп") }
    )
    @GetMapping("/teachers/{lastName}/discipline/{planRowId}/groups")
    public List<String> disciplineGroups(
            @Parameter(example = "Иванов") @PathVariable String lastName,
            @Parameter(example = "12345") @PathVariable int planRowId
    ) {
        return repo.findGroupsByPlanRowId(lastName, planRowId);
    }
}
