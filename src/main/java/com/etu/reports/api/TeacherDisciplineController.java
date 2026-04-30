package com.etu.reports.api;

import com.etu.reports.domain.DisciplineCard;
import com.etu.reports.dto.ControlScheduleDto;
import com.etu.reports.dto.report.MoodleLinkDto;
import com.etu.reports.dto.report.ReportDto;
import com.etu.reports.dto.report.ReportFile;
import com.etu.reports.dto.report.SaveMoodleLinkRequest;
import com.etu.reports.dto.student.StudentRow;
import com.etu.reports.repository.ControlScheduleRepository;
import com.etu.reports.repository.DisciplineTeacherOverrideRepository;
import com.etu.reports.repository.MoodleLinkRepository;
import com.etu.reports.repository.TeachingCardRepository;
import com.etu.reports.service.StudentReportService;
import com.etu.reports.util.AcademicYearValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Teacher discipline", description = "Страница дисциплины: карточка, студенты, загрузки")
public class TeacherDisciplineController {

    private final TeachingCardRepository teachingRepo;
    private final StudentReportService reportService;
    private final ControlScheduleRepository controlRepo;
    private final MoodleLinkRepository moodleLinkRepo;
    private final DisciplineTeacherOverrideRepository disciplineTeacherOverrideRepo;

    public TeacherDisciplineController(
            TeachingCardRepository teachingRepo,
            StudentReportService reportService,
            ControlScheduleRepository controlRepo,
            MoodleLinkRepository moodleLinkRepo,
            DisciplineTeacherOverrideRepository disciplineTeacherOverrideRepo
    ) {
        this.teachingRepo = teachingRepo;
        this.reportService = reportService;
        this.controlRepo = controlRepo;
        this.moodleLinkRepo = moodleLinkRepo;
        this.disciplineTeacherOverrideRepo = disciplineTeacherOverrideRepo;
    }

    // ===== карточка дисциплины =====
    @GetMapping("/teachers/{lastName}/discipline/{planRowId}")
    public DisciplineCard discipline(
            @PathVariable String lastName,
            @PathVariable int planRowId,
            @RequestParam("academicYear") String academicYear
    ) {
        return teachingRepo.findDisciplineCardByPlanRowId(
                lastName, planRowId, AcademicYearValidator.requireValid(academicYear));
    }

    // ===== студенты по дисциплине =====
    @GetMapping("/teachers/{lastName}/discipline/{planRowId}/students")
    public List<StudentRow> students(
            @PathVariable String lastName,
            @PathVariable int planRowId
    ) {
        return teachingRepo.findStudentsByPlanRowId(lastName, planRowId);
    }

    // ===== загруженные работы =====
    @Operation(
            summary = "Список загруженных работ (строго по plan_row_id и teacher_last_name)",
            responses = {@ApiResponse(responseCode = "200", description = "Список работ")}
    )
    @GetMapping("/teachers/{lastName}/discipline/{planRowId}/reports")
    public List<ReportDto> reports(@PathVariable String lastName, @PathVariable int planRowId) {
        return reportService.findReportsByTeacherAndDiscipline(lastName, planRowId);
    }

    // ===== загрузка отчёта =====
    @Operation(
            summary = "Загрузить работу студента",
            description = """
                    multipart/form-data:
                    - file
                    - studentId
                    - groupName
                    - topic (optional)
                    - controlType  (REQUIRED)
                    - workType
                    - workTitle
                    - academicYear
                    - autoCheck
                    - check (optional)
                    - status
                    - uploadedBy
                    """,
            responses = {@ApiResponse(responseCode = "200", description = "Созданная запись отчёта")}
    )
    @PostMapping(
            value = "/teachers/{lastName}/discipline/{planRowId}/reports",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ReportDto upload(
            @PathVariable String lastName,
            @PathVariable int planRowId,

            @RequestParam("studentId") int studentId,
            @RequestParam("groupName") String groupName,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam("controlType") String controlType,
            @RequestParam("workType") String workType,
            @RequestParam("workTitle") String workTitle,
            @RequestParam("academicYear") String academicYear,

            @RequestParam("autoCheck") boolean autoCheck,
            @RequestParam(value = "check", required = false) Integer check,

            @RequestParam("status") String status,
            @RequestParam("uploadedBy") String uploadedBy,

            @RequestPart("file") MultipartFile file
    ) {
        return reportService.uploadReport(
                planRowId, studentId, groupName, lastName,
                topic, controlType, workType, workTitle, academicYear,
                autoCheck, check, status, uploadedBy, file);
    }

    // ===== скачать файл отчёта =====
    @GetMapping("/reports/{reportId}/download")
    public ResponseEntity<byte[]> download(@PathVariable int reportId) {
        ReportFile f = reportService.getReportFile(reportId);
        String contentDisposition = StudentReportService.buildContentDisposition(f.fileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(f.bytes());
    }

    // ===== контроль/темы по группе для дисциплины =====
    @GetMapping("/teachers/{lastName}/discipline/{planRowId}/controls")
    public List<ControlScheduleDto> controls(
            @PathVariable String lastName,
            @PathVariable int planRowId
    ) {
        return controlRepo.findControlsByTeacherAndPlanRowId(lastName, planRowId);
    }

    // ===== ссылки Moodle для мультизагрузки =====
    @Operation(summary = "Список ссылок Moodle для дисциплины")
    @GetMapping("/teachers/{lastName}/discipline/{planRowId}/moodle-links")
    public List<MoodleLinkDto> moodleLinks(
            @PathVariable String lastName,
            @PathVariable int planRowId
    ) {
        ensureTeacherOwnsPlanRow(lastName, planRowId);
        try {
            return moodleLinkRepo.findByPlanRowId(planRowId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Выполните миграцию docs/migration/V6__moodle_links.sql: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "Сохранить ссылку Moodle для группы/контроля/темы")
    @PutMapping("/teachers/{lastName}/discipline/{planRowId}/moodle-links")
    public MoodleLinkDto saveMoodleLink(
            @PathVariable String lastName,
            @PathVariable int planRowId,
            @RequestBody SaveMoodleLinkRequest body
    ) {
        ensureTeacherOwnsPlanRow(lastName, planRowId);
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тело запроса пустое");
        }
        String groupName = required(body.getGroupName(), "groupName");
        String controlType = required(body.getControlType(), "controlType");
        String topic = required(body.getTopic(), "topic");
        String academicYear = AcademicYearValidator.requireValid(required(body.getAcademicYear(), "academicYear"));
        String moodleUrl = required(body.getMoodleUrl(), "moodleUrl");
        try {
            moodleLinkRepo.upsert(planRowId, groupName, controlType, topic, academicYear, moodleUrl, body.getUpdatedBy());
            return moodleLinkRepo.findOne(planRowId, groupName, controlType, topic, academicYear)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать сохранённую ссылку"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Выполните миграцию docs/migration/V6__moodle_links.sql: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "Удалить ссылку Moodle")
    @DeleteMapping("/teachers/{lastName}/discipline/{planRowId}/moodle-links")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMoodleLink(
            @PathVariable String lastName,
            @PathVariable int planRowId,
            @RequestParam String groupName,
            @RequestParam String controlType,
            @RequestParam String topic,
            @RequestParam String academicYear
    ) {
        ensureTeacherOwnsPlanRow(lastName, planRowId);
        String safeYear = AcademicYearValidator.requireValid(required(academicYear, "academicYear"));
        int n;
        try {
            n = moodleLinkRepo.delete(planRowId, required(groupName, "groupName"), required(controlType, "controlType"), required(topic, "topic"), safeYear);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Выполните миграцию docs/migration/V6__moodle_links.sql: " + e.getMessage(), e);
        }
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ссылка не найдена");
        }
    }

    private void ensureTeacherOwnsPlanRow(String lastName, int planRowId) {
        boolean allowed = disciplineTeacherOverrideRepo.teacherAssignedToPlanRow(lastName, planRowId);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Дисциплина planRowId=" + planRowId + " не найдена для преподавателя " + lastName);
        }
    }

    private static String required(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите " + field);
        }
        return v.trim();
    }
}
