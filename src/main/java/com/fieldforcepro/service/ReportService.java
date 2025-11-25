package com.fieldforcepro.service;

import com.fieldforcepro.model.AttendanceRecord;
import com.fieldforcepro.model.Invoice;
import com.fieldforcepro.model.InvoiceItem;
import com.fieldforcepro.model.SalesOrder;
import com.fieldforcepro.repository.AttendanceRecordRepository;
import com.fieldforcepro.repository.InvoiceItemRepository;
import com.fieldforcepro.repository.InvoiceRepository;
import com.fieldforcepro.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
public class ReportService {

    public record SalesReportRow(
            String invoiceId,
            String invoiceNo,
            String agentId,
            String agentName,
            String customerName,
            String productName,
            BigDecimal total,
            String status,
            LocalDate invoiceDate
    ) {}

    public record AttendanceReportRow(
            String agentId,
            String agentName,
            LocalDate date,
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime,
            Long totalDurationMinutes,
            String status
    ) {}

    public record OrdersReportRow(
            String orderId,
            String orderNumber,
            String customerName,
            BigDecimal amount,
            String status,
            LocalDate createdDate
    ) {}

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SalesOrderRepository salesOrderRepository;

    public ReportService(
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            SalesOrderRepository salesOrderRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.salesOrderRepository = salesOrderRepository;
    }

    public List<SalesReportRow> getSalesReport(LocalDate from, LocalDate to, String agentId) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = from.atStartOfDay(zoneId).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Invoice> invoices = invoiceRepository.findAll();
        return invoices.stream()
                .filter(inv -> inv.getCreatedAt() != null
                        && !inv.getCreatedAt().isBefore(fromInstant)
                        && inv.getCreatedAt().isBefore(toInstant))
                .filter(inv -> agentId == null || agentId.isBlank() || agentId.equals(inv.getAgentId()))
                .flatMap(inv -> {
                    List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(inv.getId());
                    if (items.isEmpty()) {
                        return List.of(toSalesRow(inv, null)).stream();
                    }
                    return items.stream().map(it -> toSalesRow(inv, it));
                })
                .collect(Collectors.toList());
    }

    private SalesReportRow toSalesRow(Invoice invoice, InvoiceItem item) {
        String customerName = null;
        if (invoice.getCustomerSnapshotJson() != null && invoice.getCustomerSnapshotJson().contains("\"name\"")) {
            // Very lightweight extraction; frontend already parses full JSON when needed
            int idx = invoice.getCustomerSnapshotJson().indexOf("\"name\"");
            if (idx >= 0) {
                int colon = invoice.getCustomerSnapshotJson().indexOf(':', idx);
                int quoteStart = invoice.getCustomerSnapshotJson().indexOf('"', colon + 1);
                int quoteEnd = invoice.getCustomerSnapshotJson().indexOf('"', quoteStart + 1);
                if (quoteStart > 0 && quoteEnd > quoteStart) {
                    customerName = invoice.getCustomerSnapshotJson().substring(quoteStart + 1, quoteEnd);
                }
            }
        }
        String productName = item != null ? item.getName() : null;
        LocalDate invoiceDate = LocalDateTime.ofInstant(invoice.getInvoiceDate(), ZoneId.systemDefault()).toLocalDate();
        return new SalesReportRow(
                invoice.getId(),
                invoice.getInvoiceNo(),
                invoice.getAgentId(),
                invoice.getAgentName(),
                customerName,
                productName,
                invoice.getTotal(),
                invoice.getStatus(),
                invoiceDate
        );
    }

    public List<AttendanceReportRow> getAttendanceReport(LocalDate from, LocalDate to, String agentId) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = from.atStartOfDay(zoneId).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceRecord> records = attendanceRecordRepository.findAll();
        List<AttendanceReportRow> rows = new ArrayList<>();
        for (AttendanceRecord r : records) {
            if (r.getCheckInTime() == null) continue;
            if (r.getCheckInTime().isBefore(fromInstant) || !r.getCheckInTime().isBefore(toInstant)) continue;
            if (agentId != null && !agentId.isBlank() && !agentId.equals(r.getAgentId())) continue;

            LocalDateTime in = LocalDateTime.ofInstant(r.getCheckInTime(), zoneId);
            LocalDateTime out = r.getCheckOutTime() != null ? LocalDateTime.ofInstant(r.getCheckOutTime(), zoneId) : null;
            Long minutes = null;
            if (out != null) {
                minutes = java.time.Duration.between(in, out).toMinutes();
            }
            rows.add(new AttendanceReportRow(
                    r.getAgentId(),
                    r.getAgentName(),
                    in.toLocalDate(),
                    in,
                    out,
                    minutes,
                    r.getStatus()
            ));
        }
        return rows;
    }

    public List<OrdersReportRow> getOrdersReport(LocalDate from, LocalDate to, String status) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = from.atStartOfDay(zoneId).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<SalesOrder> orders = salesOrderRepository.findAll();
        List<OrdersReportRow> rows = new ArrayList<>();
        for (SalesOrder o : orders) {
            if (o.getCreatedAt() == null) continue;
            if (o.getCreatedAt().isBefore(fromInstant) || !o.getCreatedAt().isBefore(toInstant)) continue;
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(o.getStatus())) continue;

            LocalDate createdDate = LocalDateTime.ofInstant(o.getCreatedAt(), zoneId).toLocalDate();
            rows.add(new OrdersReportRow(
                    o.getId(),
                    o.getOrderNumber(),
                    o.getCustomerName(),
                    o.getAmount(),
                    o.getStatus(),
                    createdDate
            ));
        }
        return rows;
    }

    public byte[] exportSalesToExcel(List<SalesReportRow> rows) throws java.io.IOException {
        String[] headers = {"Invoice No", "Agent", "Customer", "Product", "Total", "Status", "Invoice Date"};
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sales");
        createHeaderRow(wb, sheet, headers);
        int r = 1;
        for (SalesReportRow row : rows) {
            Row excelRow = sheet.createRow(r++);
            excelRow.createCell(0).setCellValue(row.invoiceNo());
            excelRow.createCell(1).setCellValue(row.agentName() != null ? row.agentName() : row.agentId());
            excelRow.createCell(2).setCellValue(row.customerName() != null ? row.customerName() : "");
            excelRow.createCell(3).setCellValue(row.productName() != null ? row.productName() : "");
            excelRow.createCell(4).setCellValue(row.total() != null ? row.total().doubleValue() : 0d);
            excelRow.createCell(5).setCellValue(row.status());
            excelRow.createCell(6).setCellValue(row.invoiceDate().toString());
        }
        autosize(sheet, headers.length);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        wb.write(bos);
        wb.close();
        return bos.toByteArray();
    }

    public byte[] exportAttendanceToExcel(List<AttendanceReportRow> rows) throws java.io.IOException {
        String[] headers = {"Agent", "Date", "Check-in", "Check-out", "Duration (min)", "Status"};
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Attendance");
        createHeaderRow(wb, sheet, headers);
        int r = 1;
        for (AttendanceReportRow row : rows) {
            Row excelRow = sheet.createRow(r++);
            excelRow.createCell(0).setCellValue(row.agentName() != null ? row.agentName() : row.agentId());
            excelRow.createCell(1).setCellValue(row.date().toString());
            excelRow.createCell(2).setCellValue(row.checkInTime() != null ? row.checkInTime().toString() : "");
            excelRow.createCell(3).setCellValue(row.checkOutTime() != null ? row.checkOutTime().toString() : "");
            excelRow.createCell(4).setCellValue(row.totalDurationMinutes() != null ? row.totalDurationMinutes() : 0L);
            excelRow.createCell(5).setCellValue(row.status());
        }
        autosize(sheet, headers.length);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        wb.write(bos);
        wb.close();
        return bos.toByteArray();
    }

    public byte[] exportOrdersToExcel(List<OrdersReportRow> rows) throws java.io.IOException {
        String[] headers = {"Order No", "Customer", "Amount", "Status", "Created"};
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Orders");
        createHeaderRow(wb, sheet, headers);
        int r = 1;
        for (OrdersReportRow row : rows) {
            Row excelRow = sheet.createRow(r++);
            excelRow.createCell(0).setCellValue(row.orderNumber());
            excelRow.createCell(1).setCellValue(row.customerName());
            excelRow.createCell(2).setCellValue(row.amount() != null ? row.amount().doubleValue() : 0d);
            excelRow.createCell(3).setCellValue(row.status());
            excelRow.createCell(4).setCellValue(row.createdDate().toString());
        }
        autosize(sheet, headers.length);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        wb.write(bos);
        wb.close();
        return bos.toByteArray();
    }

    private void createHeaderRow(Workbook wb, Sheet sheet, String[] headers) {
        Row header = sheet.createRow(0);
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
        headerFont.setBold(true);
        style.setFont(headerFont);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    public byte[] exportSalesToPdf(List<SalesReportRow> rows, String title, String dateRange) throws java.io.IOException {
        Document document = new Document(PageSize.A4.rotate());
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        PdfWriter.getInstance(document, bos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        document.add(new Paragraph("Candor Water Tech - " + title, titleFont));
        document.add(new Paragraph(dateRange));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(7);
        addHeaderCell(table, "Invoice No");
        addHeaderCell(table, "Agent");
        addHeaderCell(table, "Customer");
        addHeaderCell(table, "Product");
        addHeaderCell(table, "Total");
        addHeaderCell(table, "Status");
        addHeaderCell(table, "Invoice Date");

        for (SalesReportRow row : rows) {
            table.addCell(row.invoiceNo());
            table.addCell(row.agentName() != null ? row.agentName() : row.agentId());
            table.addCell(row.customerName() != null ? row.customerName() : "");
            table.addCell(row.productName() != null ? row.productName() : "");
            table.addCell(row.total() != null ? row.total().toPlainString() : "0");
            table.addCell(row.status());
            table.addCell(row.invoiceDate().toString());
        }

        document.add(table);
        document.close();
        return bos.toByteArray();
    }

    public byte[] exportAttendanceToPdf(List<AttendanceReportRow> rows, String title, String dateRange) throws java.io.IOException {
        Document document = new Document(PageSize.A4.rotate());
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        PdfWriter.getInstance(document, bos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        document.add(new Paragraph("Candor Water Tech - " + title, titleFont));
        document.add(new Paragraph(dateRange));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(6);
        addHeaderCell(table, "Agent");
        addHeaderCell(table, "Date");
        addHeaderCell(table, "Check-in");
        addHeaderCell(table, "Check-out");
        addHeaderCell(table, "Duration (min)");
        addHeaderCell(table, "Status");

        for (AttendanceReportRow row : rows) {
            table.addCell(row.agentName() != null ? row.agentName() : row.agentId());
            table.addCell(row.date().toString());
            table.addCell(row.checkInTime() != null ? row.checkInTime().toString() : "");
            table.addCell(row.checkOutTime() != null ? row.checkOutTime().toString() : "");
            table.addCell(row.totalDurationMinutes() != null ? row.totalDurationMinutes().toString() : "");
            table.addCell(row.status());
        }

        document.add(table);
        document.close();
        return bos.toByteArray();
    }

    public byte[] exportOrdersToPdf(List<OrdersReportRow> rows, String title, String dateRange) throws java.io.IOException {
        Document document = new Document(PageSize.A4.rotate());
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        PdfWriter.getInstance(document, bos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        document.add(new Paragraph("Candor Water Tech - " + title, titleFont));
        document.add(new Paragraph(dateRange));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(5);
        addHeaderCell(table, "Order No");
        addHeaderCell(table, "Customer");
        addHeaderCell(table, "Amount");
        addHeaderCell(table, "Status");
        addHeaderCell(table, "Created");

        for (OrdersReportRow row : rows) {
            table.addCell(row.orderNumber());
            table.addCell(row.customerName());
            table.addCell(row.amount() != null ? row.amount().toPlainString() : "0");
            table.addCell(row.status());
            table.addCell(row.createdDate().toString());
        }

        document.add(table);
        document.close();
        return bos.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text) {
        Font font = new Font(Font.HELVETICA, 10, Font.BOLD);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        table.addCell(cell);
    }
}
