package com.fieldforcepro.controller;

import com.fieldforcepro.service.ReportService;
import com.fieldforcepro.service.ReportService.AttendanceReportRow;
import com.fieldforcepro.service.ReportService.OrdersReportRow;
import com.fieldforcepro.service.ReportService.SalesReportRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    public record SalesReportRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            String agentId
    ) {}

    public record AttendanceReportRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            String agentId
    ) {}

    public record OrdersReportRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            String status
    ) {}

    public enum ReportType { SALES, ATTENDANCE, ORDERS }

    public record ExportRequest(
            ReportType type,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            String agentId,
            String status
    ) {}

    @PostMapping("/sales")
    @Operation(summary = "Get sales performance report from invoices for the given date range and optional agent")
    public ResponseEntity<List<SalesReportRow>> getSalesReport(@RequestBody SalesReportRequest request) {
        if (request.fromDate() == null || request.toDate() == null) {
            return ResponseEntity.badRequest().build();
        }
        List<SalesReportRow> rows = reportService.getSalesReport(request.fromDate(), request.toDate(), request.agentId());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/attendance")
    @Operation(summary = "Get attendance and visit compliance report for the given date range and optional agent")
    public ResponseEntity<List<AttendanceReportRow>> getAttendanceReport(@RequestBody AttendanceReportRequest request) {
        if (request.fromDate() == null || request.toDate() == null) {
            return ResponseEntity.badRequest().build();
        }
        List<AttendanceReportRow> rows = reportService.getAttendanceReport(request.fromDate(), request.toDate(), request.agentId());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/orders")
    @Operation(summary = "Get pipeline / orders report for the given date range and optional status")
    public ResponseEntity<List<OrdersReportRow>> getOrdersReport(@RequestBody OrdersReportRequest request) {
        if (request.fromDate() == null || request.toDate() == null) {
            return ResponseEntity.badRequest().build();
        }
        List<OrdersReportRow> rows = reportService.getOrdersReport(request.fromDate(), request.toDate(), request.status());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/export/excel")
    @Operation(summary = "Export selected report to Excel (XLSX)")
    public ResponseEntity<byte[]> exportExcel(@RequestBody ExportRequest request) throws Exception {
        if (request.fromDate() == null || request.toDate() == null || request.type() == null) {
            return ResponseEntity.badRequest().build();
        }
        String filename;
        byte[] data;
        switch (request.type()) {
            case SALES -> {
                List<SalesReportRow> rows = reportService.getSalesReport(request.fromDate(), request.toDate(), request.agentId());
                data = reportService.exportSalesToExcel(rows);
                filename = "sales-report.xlsx";
            }
            case ATTENDANCE -> {
                List<AttendanceReportRow> rows = reportService.getAttendanceReport(request.fromDate(), request.toDate(), request.agentId());
                data = reportService.exportAttendanceToExcel(rows);
                filename = "attendance-report.xlsx";
            }
            case ORDERS -> {
                List<OrdersReportRow> rows = reportService.getOrdersReport(request.fromDate(), request.toDate(), request.status());
                data = reportService.exportOrdersToExcel(rows);
                filename = "orders-report.xlsx";
            }
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @PostMapping("/export/pdf")
    @Operation(summary = "Export selected report to PDF")
    public ResponseEntity<byte[]> exportPdf(@RequestBody ExportRequest request) throws Exception {
        if (request.fromDate() == null || request.toDate() == null || request.type() == null) {
            return ResponseEntity.badRequest().build();
        }
        String title;
        String filename;
        String dateRange = "From " + request.fromDate() + " to " + request.toDate();
        byte[] data;
        switch (request.type()) {
            case SALES -> {
                List<SalesReportRow> rows = reportService.getSalesReport(request.fromDate(), request.toDate(), request.agentId());
                data = reportService.exportSalesToPdf(rows, "Sales Performance Report", dateRange);
                filename = "sales-report.pdf";
            }
            case ATTENDANCE -> {
                List<AttendanceReportRow> rows = reportService.getAttendanceReport(request.fromDate(), request.toDate(), request.agentId());
                data = reportService.exportAttendanceToPdf(rows, "Attendance & Visit Report", dateRange);
                filename = "attendance-report.pdf";
            }
            case ORDERS -> {
                List<OrdersReportRow> rows = reportService.getOrdersReport(request.fromDate(), request.toDate(), request.status());
                data = reportService.exportOrdersToPdf(rows, "Orders / Pipeline Report", dateRange);
                filename = "orders-report.pdf";
            }
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
