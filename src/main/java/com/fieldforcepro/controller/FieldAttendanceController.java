package com.fieldforcepro.controller;

import com.fieldforcepro.model.AttendanceRecord;
import com.fieldforcepro.repository.AttendanceRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/attendance/field")
@Tag(name = "Field Attendance")
public class FieldAttendanceController {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final Path uploadRoot;

    public FieldAttendanceController(
            AttendanceRecordRepository attendanceRecordRepository,
            @Value("${fieldforcepro.attendance.upload-dir:attendance-uploads}") String uploadDir
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (Exception ignored) {
        }
    }

    public record FieldCheckinResponse(
            String id,
            String agentId,
            String agentName,
            LocalDate date,
            String status,
            String workType,
            Double latitude,
            Double longitude,
            String imageUrl
    ) { }

    public record PunchRecordDto(
            String id,
            String agentId,
            String agentName,
            LocalDate date,
            String status,
            String workType,
            String punchInTime,
            String punchOutTime,
            String imageUrl,
            String reason
    ) { }

    public record PunchOutRequest(
            String agentId,
            String agentName,
            String reason
    ) { }

    @PostMapping(path = "/checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Mark Work From Field attendance with optional image and location")
    @Transactional
    public ResponseEntity<FieldCheckinResponse> fieldCheckin(
            @RequestParam("agentId") String agentId,
            @RequestParam("agentName") String agentName,
            @RequestParam(value = "status", required = false, defaultValue = "PRESENT") String status,
            @RequestParam(value = "workType", required = false, defaultValue = "FIELD") String workType,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        if (agentId == null || agentId.isBlank() || agentName == null || agentName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate effectiveDate = (date != null) ? date : LocalDate.now(zoneId);
        Instant fromInstant = effectiveDate.atStartOfDay(zoneId).toInstant();
        Instant toInstant = effectiveDate.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceRecord> existingForDay = attendanceRecordRepository
                .findByAgentIdAndCheckInTimeBetween(agentId, fromInstant, toInstant);

        AttendanceRecord record;
        if (!existingForDay.isEmpty()) {
            record = existingForDay.get(0);
        } else {
            LocalDateTime ldt = effectiveDate.atStartOfDay();
            Instant checkIn = ldt.atZone(zoneId).toInstant();
            record = AttendanceRecord.builder()
                    .agentId(agentId)
                    .agentName(agentName)
                    .checkInTime(checkIn)
                    .status(status)
                    .build();
        }

        record.setAgentName(agentName);
        record.setStatus(status);
        record.setWorkType(workType);
        record.setLatitude(latitude);
        record.setLongitude(longitude);

        if (image != null && !image.isEmpty()) {
            try {
                String ext = "";
                String original = image.getOriginalFilename();
                if (original != null && original.contains(".")) {
                    ext = original.substring(original.lastIndexOf('.'));
                }
                String filename = UUID.randomUUID() + (ext.isEmpty() ? ".jpg" : ext);
                Path target = uploadRoot.resolve(filename);
                Files.copy(image.getInputStream(), target);
                String url = "/attendance/field/images/file/" + filename;
                record.setImageUrl(url);
            } catch (Exception ignored) {
            }
        }

        AttendanceRecord saved = attendanceRecordRepository.save(record);

        FieldCheckinResponse body = new FieldCheckinResponse(
                saved.getId(),
                saved.getAgentId(),
                saved.getAgentName(),
                LocalDateTime.ofInstant(saved.getCheckInTime(), zoneId).toLocalDate(),
                saved.getStatus(),
                saved.getWorkType(),
                saved.getLatitude(),
                saved.getLongitude(),
                saved.getImageUrl()
        );

        return ResponseEntity.ok(body);
    }

    @PostMapping(path = "/punch-in", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Punch in with mandatory image and auto time (admin)")
    @Transactional
    public ResponseEntity<PunchRecordDto> punchIn(
            @RequestParam("agentId") String agentId,
            @RequestParam("agentName") String agentName,
            @RequestParam(value = "workType", required = false, defaultValue = "FIELD") String workType,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestPart("image") MultipartFile image
    ) {
        if (agentId == null || agentId.isBlank() || agentName == null || agentName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        Instant fromInstant = today.atStartOfDay(zoneId).toInstant();
        Instant toInstant = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceRecord> existingForDay = attendanceRecordRepository
                .findByAgentIdAndCheckInTimeBetween(agentId, fromInstant, toInstant);

        AttendanceRecord record;
        if (!existingForDay.isEmpty()) {
            record = existingForDay.get(0);
        } else {
            record = AttendanceRecord.builder()
                    .agentId(agentId)
                    .agentName(agentName)
                    .checkInTime(Instant.now())
                    .status("Present")
                    .reason(reason)
                    .build();
        }

        if (record.getCheckInTime() == null) {
            record.setCheckInTime(Instant.now());
        }
        record.setAgentName(agentName);
        record.setStatus("Present");
        record.setWorkType(workType);
        record.setReason(reason);
        record.setLatitude(latitude);
        record.setLongitude(longitude);

        try {
            String ext = "";
            String original = image.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
            }
            String filename = UUID.randomUUID() + (ext.isEmpty() ? ".jpg" : ext);
            Path target = uploadRoot.resolve(filename);
            Files.copy(image.getInputStream(), target);
            String url = "/attendance/field/images/file/" + filename;
            record.setImageUrl(url);
        } catch (Exception ignored) {
        }

        AttendanceRecord saved = attendanceRecordRepository.save(record);

        PunchRecordDto dto = toPunchDto(saved, zoneId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping(path = "/punch-out", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Punch out with auto time (admin)")
    @Transactional
    public ResponseEntity<PunchRecordDto> punchOut(@RequestBody PunchOutRequest request) {
        if (request.agentId() == null || request.agentId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        Instant fromInstant = today.atStartOfDay(zoneId).toInstant();
        Instant toInstant = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceRecord> existingForDay = attendanceRecordRepository
                .findByAgentIdAndCheckInTimeBetween(request.agentId(), fromInstant, toInstant);

        AttendanceRecord record;
        if (!existingForDay.isEmpty()) {
            record = existingForDay.get(0);
        } else {
            record = AttendanceRecord.builder()
                    .agentId(request.agentId())
                    .agentName(request.agentName())
                    .checkInTime(Instant.now())
                    .status("Present")
                    .reason(request.reason())
                    .build();
        }

        record.setAgentName(request.agentName());
        record.setStatus("Present");
        record.setReason(request.reason());
        record.setCheckOutTime(Instant.now());

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        PunchRecordDto dto = toPunchDto(saved, zoneId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/records")
    @Operation(summary = "Get punch records for an agent for a month")
    public List<PunchRecordDto> getRecordsForMonth(
            @RequestParam("agentId") String agentId,
            @RequestParam("month") String month // format: yyyy-MM
    ) {
        ZoneId zoneId = ZoneId.systemDefault();
        YearMonth ym = YearMonth.parse(month);
        LocalDate fromDate = ym.atDay(1);
        LocalDate toDate = ym.atEndOfMonth();

        Instant fromInstant = fromDate.atStartOfDay(zoneId).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceRecord> records = attendanceRecordRepository
                .findByAgentIdAndCheckInTimeBetween(agentId, fromInstant, toInstant);

        List<PunchRecordDto> result = new ArrayList<>();
        for (AttendanceRecord r : records) {
            result.add(toPunchDto(r, zoneId));
        }
        return result;
    }

    private PunchRecordDto toPunchDto(AttendanceRecord r, ZoneId zoneId) {
        LocalDateTime inLdt = LocalDateTime.ofInstant(r.getCheckInTime(), zoneId);
        LocalDate date = inLdt.toLocalDate();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        String punchIn = fmt.format(inLdt);
        String punchOut = null;
        if (r.getCheckOutTime() != null) {
            LocalDateTime outLdt = LocalDateTime.ofInstant(r.getCheckOutTime(), zoneId);
            punchOut = fmt.format(outLdt);
        }

        return new PunchRecordDto(
                r.getId(),
                r.getAgentId(),
                r.getAgentName(),
                date,
                r.getStatus(),
                r.getWorkType(),
                punchIn,
                punchOut,
                r.getImageUrl(),
                r.getReason()
        );
    }

    public record AttendanceImageDto(
            String id,
            String agentId,
            String agentName,
            LocalDate date,
            String status,
            String workType,
            Double latitude,
            Double longitude,
            String imageUrl
    ) { }

    @GetMapping("/images")
    @Operation(summary = "Get field attendance image for an agent on a date")
    public ResponseEntity<AttendanceImageDto> getImageForAgent(
            @RequestParam("agentId") String agentId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = date.atStartOfDay(zoneId).toInstant();
        Instant toInstant = date.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceRecord> records = attendanceRecordRepository
                .findByAgentIdAndCheckInTimeBetween(agentId, fromInstant, toInstant);

        if (records.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AttendanceRecord r = records.get(0);
        AttendanceImageDto dto = new AttendanceImageDto(
                r.getId(),
                r.getAgentId(),
                r.getAgentName(),
                LocalDateTime.ofInstant(r.getCheckInTime(), zoneId).toLocalDate(),
                r.getStatus(),
                r.getWorkType(),
                r.getLatitude(),
                r.getLongitude(),
                r.getImageUrl()
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/images/all")
    @Operation(summary = "Get all field attendance images for a date")
    public List<AttendanceImageDto> getAllImagesForDate(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = date.atStartOfDay(zoneId).toInstant();
        Instant toInstant = date.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceRecord> all = attendanceRecordRepository.findAll();
        List<AttendanceImageDto> result = new ArrayList<>();
        for (AttendanceRecord r : all) {
            if (r.getCheckInTime() == null) continue;
            if (r.getCheckInTime().isBefore(fromInstant) || !r.getCheckInTime().isBefore(toInstant)) continue;

            result.add(new AttendanceImageDto(
                    r.getId(),
                    r.getAgentId(),
                    r.getAgentName(),
                    LocalDateTime.ofInstant(r.getCheckInTime(), zoneId).toLocalDate(),
                    r.getStatus(),
                    r.getWorkType(),
                    r.getLatitude(),
                    r.getLongitude(),
                    r.getImageUrl()
            ));
        }
        return result;
    }

    @GetMapping("/images/file/{filename}")
    @Operation(summary = "Serve stored attendance image file")
    public ResponseEntity<Resource> serveImage(@PathVariable("filename") String filename) throws MalformedURLException {
        Path file = uploadRoot.resolve(filename).normalize();
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toUri());
        String contentType = "image/jpeg";
        try {
            String probe = Files.probeContentType(file);
            if (probe != null) {
                contentType = probe;
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + filename)
                .body(resource);
    }
}
