package com.etu.reports.service;

import com.etu.reports.dto.report.ArchiveReportDto;
import com.etu.reports.dto.report.ReportDto;
import com.etu.reports.dto.report.ReportFile;
import com.etu.reports.exception.ReportNotFoundException;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.StudentReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

/**
 * Сервис работы со студенческими отчётами.
 *
 * <p>Инкапсулирует всю бизнес-логику:
 * <ul>
 *   <li>Санитизация входных данных</li>
 *   <li>Вычисление версии файла</li>
 *   <li>Формирование имени файла и запись на диск</li>
 *   <li>Запись/удаление записей в БД через {@link StudentReportRepository}</li>
 *   <li>Аудит-логирование через {@link AuditLogRepository}</li>
 * </ul>
 *
 * <p>Контроллеры общаются только с этим сервисом; репозиторий используется
 * исключительно для SQL-операций.
 */
@Service
public class StudentReportService {

    private static final Logger log = LoggerFactory.getLogger(StudentReportService.class);

    private final StudentReportRepository reportRepo;
    private final AuditLogRepository auditLogRepo;

    @Value("${reports.storage.dir:reports_storage}")
    private String storageDir;

    public StudentReportService(StudentReportRepository reportRepo,
                                AuditLogRepository auditLogRepo) {
        this.reportRepo = reportRepo;
        this.auditLogRepo = auditLogRepo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Загрузка отчёта
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Сохраняет работу студента: санитизирует параметры, вычисляет версию,
     * записывает файл на диск, создаёт запись в БД и логирует событие.
     *
     * @return созданный объект {@link ReportDto}
     * @throws IllegalArgumentException если groupName пустое или равно "undefined"
     * @throws RuntimeException         при ошибке файловой системы
     */
    @Transactional
    public ReportDto uploadReport(
            int planRowId,
            int studentId,
            String groupName,
            String teacherLastName,
            String topic,
            String controlType,
            String workType,
            String workTitle,
            String academicYear,
            boolean autoCheck,
            Integer checkPercent,
            String status,
            String uploadedBy,
            MultipartFile file
    ) {
        // Санитизация
        String safeGroup = groupName == null ? "" : groupName.trim();
        if (safeGroup.isEmpty() || "undefined".equalsIgnoreCase(safeGroup)) {
            throw new IllegalArgumentException("groupName is required");
        }
        String safeTopic = topic == null ? "" : topic.trim();
        String safeControlType = controlType == null ? "" : controlType.trim();
        if ("undefined".equalsIgnoreCase(safeControlType)) safeControlType = "";
        String safeWorkType = workType == null ? "" : workType.trim();
        String safeWorkTitle = workTitle == null ? "" : workTitle.trim();
        String safeAcademicYear = academicYear == null ? "" : academicYear.trim();
        String safeStatus = (status == null || status.isBlank()) ? "Загружен" : status.trim();
        String safeUploadedBy = uploadedBy == null ? "" : uploadedBy.trim();
        String safeTeacher = teacherLastName == null ? "" : teacherLastName.trim();

        // Вычисляем следующую версию
        int version = reportRepo.getNextVersion(
                planRowId, studentId, safeGroup, safeTeacher,
                safeTopic, safeControlType, safeWorkType, safeAcademicYear);

        // Создаём директорию хранилища
        Path storageDirPath = Paths.get(storageDir);
        try {
            Files.createDirectories(storageDirPath);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create storage dir: " + storageDirPath, e);
        }

        // Формируем имя файла: <planRowId>__<group>__<studentId>__<date>__v<version>__<original>
        String originalName = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "file" : file.getOriginalFilename();
        String safeOriginal = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String storedFileName = planRowId + "__" + safeGroup + "__" + studentId
                + "__" + LocalDate.now() + "__v" + version + "__" + safeOriginal;

        // Записываем файл на диск
        Path target = storageDirPath.resolve(storedFileName).normalize();
        try {
            Files.write(target, file.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Cannot write file: " + target, e);
        }
        log.info("Report saved: {}", target.toAbsolutePath());

        // Вставляем запись в БД
        ReportDto saved = reportRepo.insert(
                planRowId, studentId, safeGroup, safeTeacher,
                safeTopic, safeControlType, safeWorkType, safeWorkTitle, safeAcademicYear,
                version, checkPercent, safeStatus, safeUploadedBy,
                originalName, target.toString());

        // Аудит
        String actor = safeUploadedBy.isBlank() ? safeTeacher : safeUploadedBy;
        auditLogRepo.log("REPORT_UPLOADED", "report", String.valueOf(saved.id()),
                actor, "file: " + originalName);

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Удаление отчёта
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Откатывает загрузку: удаляет результаты валидации, запись в БД и файл с диска.
     *
     * @return {@code true} если запись была найдена и удалена
     */
    @Transactional
    public boolean deleteReport(int reportId) {
        try {
            String filePath = reportRepo.findFilePathById(reportId).orElse(null);
            reportRepo.deleteValidationResultsByReportId(reportId);
            int deleted = reportRepo.deleteById(reportId);
            if (deleted > 0 && filePath != null && !filePath.isBlank()) {
                try {
                    Path p = Paths.get(filePath);
                    if (Files.exists(p)) Files.delete(p);
                } catch (Exception e) {
                    log.warn("Could not delete report file: {}", filePath, e);
                }
            }
            return deleted > 0;
        } catch (Exception e) {
            log.warn("deleteReport failed for id={}", reportId, e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Скачивание файла
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Читает файл с диска по пути из БД и возвращает {@link ReportFile}.
     *
     * @throws ReportNotFoundException если запись или файл не найдены
     */
    public ReportFile getReportFile(int reportId) {
        StudentReportRepository.FileRef ref = reportRepo.findFileRefById(reportId)
                .orElseThrow(() -> new ReportNotFoundException("Report not found: " + reportId));

        if (ref.filePath() == null || ref.filePath().isBlank()) {
            throw new ReportNotFoundException("Report " + reportId + " has no file path");
        }

        Path p = Paths.get(ref.filePath());
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(p);
        } catch (Exception e) {
            throw new ReportNotFoundException("Cannot read file for report " + reportId, e);
        }

        String contentType = null;
        try {
            contentType = Files.probeContentType(p);
        } catch (Exception ignored) {}
        if (contentType == null) contentType = "application/octet-stream";

        return new ReportFile(ref.fileName(), contentType, bytes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Делегирующие методы запросов
    // ─────────────────────────────────────────────────────────────────────────

    /** Список работ преподавателя по конкретной дисциплине. */
    public List<ReportDto> findReportsByTeacherAndDiscipline(String teacherLastName, int planRowId) {
        return reportRepo.findByTeacherAndDiscipline(teacherLastName, planRowId);
    }

    /** Архив — только последние версии по конкретному преподавателю. */
    public List<ArchiveReportDto> findArchiveByTeacher(String teacherLastName) {
        return reportRepo.findArchiveLatestByTeacher(teacherLastName);
    }

    /** Архив — только последние версии по всей кафедре (опциональный фильтр по фамилии). */
    public List<ArchiveReportDto> findArchiveAll(String teacherLastNameFilter) {
        return reportRepo.findArchiveLatestAll(teacherLastNameFilter);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Утилита: Content-Disposition
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Формирует заголовок {@code Content-Disposition} с поддержкой кириллицы (RFC 5987).
     *
     * <p>Если имя файла содержит символы вне ASCII, добавляется параметр
     * {@code filename*=UTF-8''...} с percent-encoding.
     */
    public static String buildContentDisposition(String fileName) {
        if (fileName == null) fileName = "download";
        boolean needsRfc5987 = fileName.chars().anyMatch(c -> c > 127);
        if (!needsRfc5987) {
            return "attachment; filename=\"" + fileName.replace("\"", "\\\"") + "\"";
        }
        String asciiFallback = fileName.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = UriUtils.encode(fileName, StandardCharsets.UTF_8);
        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }
}
