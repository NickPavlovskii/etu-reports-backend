package com.etu.reports.api;

import com.etu.reports.domain.DisciplineCard;
import com.etu.reports.dto.ControlScheduleDto;
import com.etu.reports.dto.report.ReportDto;
import com.etu.reports.dto.report.ReportFile;
import com.etu.reports.dto.student.StudentRow;
import com.etu.reports.repository.ControlScheduleRepository;
import com.etu.reports.repository.TeachingCardRepository;
import com.etu.reports.service.StudentReportService;
import com.etu.reports.util.AcademicYearValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Teacher discipline", description = "Страница дисциплины: карточка, студенты, загрузки")
public class TeacherDisciplineController {

    private final TeachingCardRepository teachingRepo;
    private final StudentReportService reportService;
    private final ControlScheduleRepository controlRepo;

    public TeacherDisciplineController(
            TeachingCardRepository teachingRepo,
            StudentReportService reportService,
            ControlScheduleRepository controlRepo
    ) {
        this.teachingRepo = teachingRepo;
        this.reportService = reportService;
        this.controlRepo = controlRepo;
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
}
