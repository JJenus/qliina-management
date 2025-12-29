package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.reporting.dto.ExportReportRequest;
import com.jjenus.qliina_management.reporting.dto.RevenueReportDTO;
import com.jjenus.qliina_management.reporting.dto.ProfitLossDTO;
import com.jjenus.qliina_management.reporting.dto.AgingReportDTO;
import com.jjenus.qliina_management.reporting.dto.TaxReportDTO;
import com.jjenus.qliina_management.reporting.dto.SalesByServiceDTO;
import com.jjenus.qliina_management.reporting.dto.EmployeePerfDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.jjenus.qliina_management.reporting.dto.RevenueReportRequest;
import com.jjenus.qliina_management.reporting.dto.DateRangeRequest;
import com.jjenus.qliina_management.reporting.dto.TaxReportRequest;
import com.jjenus.qliina_management.reporting.dto.SalesByServiceRequest;
import com.jjenus.qliina_management.reporting.dto.EmployeePerfRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {
    
    private final RevenueReportService revenueService;
    private final FinancialReportService financialService;
    private final AgingReportService agingService;
    private final TaxReportService taxService;
    private final SalesReportService salesService;
    private final EmployeeReportService employeeService;
    
    public byte[] exportReport(UUID businessId, ExportReportRequest request) {
        switch (request.getReportType()) {
            case "REVENUE":
                return exportRevenueReport(businessId, request);
            case "PROFIT_LOSS":
                return exportProfitLossReport(businessId, request);
            case "AGING":
                return exportAgingReport(businessId, request);
            case "TAX":
                return exportTaxReport(businessId, request);
            case "SALES_BY_SERVICE":
                return exportSalesByServiceReport(businessId, request);
            case "EMPLOYEE_PERF":
                return exportEmployeePerformanceReport(businessId, request);
            default:
                throw new IllegalArgumentException("Unsupported report type: " + request.getReportType());
        }
    }
    
    private byte[] exportRevenueReport(UUID businessId, ExportReportRequest request) {
        // Parse request parameters
        RevenueReportRequest reportRequest = new RevenueReportRequest();
        reportRequest.setStartDate((LocalDate) request.getParameters().get("startDate"));
        reportRequest.setEndDate((LocalDate) request.getParameters().get("endDate"));
        reportRequest.setShopId((UUID) request.getParameters().get("shopId"));
        reportRequest.setGroupBy((String) request.getParameters().get("groupBy"));
        
        RevenueReportDTO report = revenueService.generateRevenueReport(businessId, reportRequest);
        
        switch (request.getFormat().toUpperCase()) {
            case "CSV":
                return exportRevenueToCSV(report);
            case "EXCEL":
                return exportRevenueToExcel(report);
            case "PDF":
                return exportRevenueToPDF(report);
            default:
                throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        }
    }
    
    private byte[] exportRevenueToCSV(RevenueReportDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                 .withHeader("Period", "Revenue", "Orders", "Average Order Value"))) {
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            // Summary section
            csvPrinter.printRecord("REVENUE REPORT");
            csvPrinter.printRecord("Period", 
                report.getPeriod().getStart().format(formatter) + " to " + 
                report.getPeriod().getEnd().format(formatter));
            csvPrinter.printRecord("Total Revenue", report.getTotalRevenue());
            csvPrinter.printRecord("Total Orders", report.getTotalOrders());
            csvPrinter.printRecord("Average Order Value", report.getAverageOrderValue());
            csvPrinter.println();
            
            // Period breakdown
            csvPrinter.printRecord("PERIOD BREAKDOWN");
            for (RevenueReportDTO.PeriodSummaryDTO period : report.getByPeriod()) {
                csvPrinter.printRecord(
                    period.getPeriod(),
                    period.getRevenue(),
                    period.getOrders(),
                    period.getAov()
                );
            }
            csvPrinter.println();
            
            // Payment method breakdown
            csvPrinter.printRecord("PAYMENT METHOD BREAKDOWN");
            for (RevenueReportDTO.PaymentMethodSummaryDTO method : report.getByPaymentMethod()) {
                csvPrinter.printRecord(
                    method.getMethod(),
                    method.getAmount(),
                    String.format("%.2f%%", method.getPercentage())
                );
            }
            csvPrinter.println();
            
            // Service type breakdown
            csvPrinter.printRecord("SERVICE TYPE BREAKDOWN");
            for (RevenueReportDTO.ServiceSummaryDTO service : report.getByServiceType()) {
                csvPrinter.printRecord(
                    service.getService(),
                    service.getAmount(),
                    service.getOrders()
                );
            }
            
            csvPrinter.flush();
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }
    }
    
    private byte[] exportRevenueToExcel(RevenueReportDTO report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Revenue Report");
            
            // Create styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
            
            int rowNum = 0;
            
            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("REVENUE REPORT");
            titleCell.setCellStyle(headerStyle);
            
            rowNum++; // Empty row
            
            // Summary
            Row periodRow = sheet.createRow(rowNum++);
            periodRow.createCell(0).setCellValue("Period:");
            periodRow.createCell(1).setCellValue(
                report.getPeriod().getStart().toString() + " to " + 
                report.getPeriod().getEnd().toString());
            
            Row revenueRow = sheet.createRow(rowNum++);
            revenueRow.createCell(0).setCellValue("Total Revenue:");
            Cell revenueCell = revenueRow.createCell(1);
            revenueCell.setCellValue(report.getTotalRevenue().doubleValue());
            revenueCell.setCellStyle(currencyStyle);
            
            Row ordersRow = sheet.createRow(rowNum++);
            ordersRow.createCell(0).setCellValue("Total Orders:");
            ordersRow.createCell(1).setCellValue(report.getTotalOrders());
            
            Row aovRow = sheet.createRow(rowNum++);
            aovRow.createCell(0).setCellValue("Average Order Value:");
            Cell aovCell = aovRow.createCell(1);
            aovCell.setCellValue(report.getAverageOrderValue().doubleValue());
            aovCell.setCellStyle(currencyStyle);
            
            rowNum++; // Empty row
            
            // Period breakdown header
            Row periodHeader = sheet.createRow(rowNum++);
            periodHeader.createCell(0).setCellValue("Period");
            periodHeader.createCell(1).setCellValue("Revenue");
            periodHeader.createCell(2).setCellValue("Orders");
            periodHeader.createCell(3).setCellValue("AOV");
            
            for (Cell cell : periodHeader) {
                cell.setCellStyle(headerStyle);
            }
            
            // Period breakdown data
            for (RevenueReportDTO.PeriodSummaryDTO period : report.getByPeriod()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(period.getPeriod());
                
                Cell revCell = row.createCell(1);
                revCell.setCellValue(period.getRevenue().doubleValue());
                revCell.setCellStyle(currencyStyle);
                
                row.createCell(2).setCellValue(period.getOrders());
                
                Cell aovDataCell = row.createCell(3);
                aovDataCell.setCellValue(period.getAov().doubleValue());
                aovDataCell.setCellStyle(currencyStyle);
            }
            
            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
    
    private byte[] exportRevenueToPDF(RevenueReportDTO report) {
        // Simple text-based PDF simulation
        StringBuilder pdf = new StringBuilder();
        pdf.append("REVENUE REPORT\n");
        pdf.append("==============\n\n");
        pdf.append("Period: ").append(report.getPeriod().getStart())
           .append(" to ").append(report.getPeriod().getEnd()).append("\n");
        pdf.append("Total Revenue: $").append(report.getTotalRevenue()).append("\n");
        pdf.append("Total Orders: ").append(report.getTotalOrders()).append("\n");
        pdf.append("Average Order Value: $").append(report.getAverageOrderValue()).append("\n\n");
        
        return pdf.toString().getBytes();
    }
    
    private byte[] exportProfitLossReport(UUID businessId, ExportReportRequest request) {
        // Parse request parameters
        DateRangeRequest dateRequest = new DateRangeRequest();
        dateRequest.setStartDate((LocalDate) request.getParameters().get("startDate"));
        dateRequest.setEndDate((LocalDate) request.getParameters().get("endDate"));
        
        ProfitLossDTO report = financialService.generateProfitLoss(businessId, dateRequest);
        
        if ("CSV".equalsIgnoreCase(request.getFormat())) {
            return exportProfitLossToCSV(report);
        }
        return new byte[0];
    }
    
    private byte[] exportProfitLossToCSV(ProfitLossDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            
            csvPrinter.printRecord("PROFIT & LOSS STATEMENT");
            csvPrinter.printRecord("Period", 
                report.getPeriod().getStart() + " to " + report.getPeriod().getEnd());
            csvPrinter.println();
            
            csvPrinter.printRecord("REVENUE");
            csvPrinter.printRecord("Total Revenue", report.getRevenue().getTotal());
            csvPrinter.println();
            
            csvPrinter.printRecord("EXPENSES");
            for (ProfitLossDTO.ExpenseCategoryDTO exp : report.getExpenses().getCategories()) {
                csvPrinter.printRecord(exp.getCategory(), exp.getAmount(), exp.getPercentage() + "%");
            }
            csvPrinter.printRecord("Total Expenses", report.getExpenses().getTotal());
            csvPrinter.println();
            
            csvPrinter.printRecord("PROFIT");
            csvPrinter.printRecord("Gross Profit", report.getGrossProfit());
            csvPrinter.printRecord("Gross Margin", report.getGrossMargin() + "%");
            csvPrinter.printRecord("Net Profit", report.getNetProfit());
            csvPrinter.printRecord("Net Margin", report.getNetMargin() + "%");
            
            csvPrinter.flush();
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }
    }
    
    private byte[] exportAgingReport(UUID businessId, ExportReportRequest request) {
        AgingReportDTO report = agingService.generateAgingReport(businessId);
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                 .withHeader("Customer", "Total Due", "Current", "1-30 Days", "31-60 Days", "61-90 Days", "90+ Days"))) {
            
            for (AgingReportDTO.CustomerAgingDTO customer : report.getByCustomer()) {
                csvPrinter.printRecord(
                    customer.getCustomerName(),
                    customer.getTotalDue(),
                    customer.getCurrent(),
                    customer.getDays30(),
                    customer.getDays60(),
                    customer.getDays90(),
                    customer.getDays90Plus()
                );
            }
            
            csvPrinter.flush();
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }
    }
    
    private byte[] exportTaxReport(UUID businessId, ExportReportRequest request) {
        TaxReportRequest taxRequest = new TaxReportRequest();
        taxRequest.setStartDate((LocalDate) request.getParameters().get("startDate"));
        taxRequest.setEndDate((LocalDate) request.getParameters().get("endDate"));
        
        TaxReportDTO report = taxService.generateTaxReport(businessId, taxRequest);
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                 .withHeader("Date", "Invoice", "Customer", "Amount", "Tax"))) {
            
            for (TaxReportDTO.TaxDetailDTO detail : report.getDetails()) {
                csvPrinter.printRecord(
                    detail.getDate(),
                    detail.getInvoiceNumber(),
                    detail.getCustomerName(),
                    detail.getAmount(),
                    detail.getTax()
                );
            }
            
            csvPrinter.println();
            csvPrinter.printRecord("SUMMARY");
            csvPrinter.printRecord("Total Sales", report.getTotalSales());
            csvPrinter.printRecord("Taxable Sales", report.getTaxableSales());
            csvPrinter.printRecord("Tax Rate", report.getTaxRate() + "%");
            csvPrinter.printRecord("Tax Collected", report.getTaxCollected());
            
            csvPrinter.flush();
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }
    }
    
    private byte[] exportSalesByServiceReport(UUID businessId, ExportReportRequest request) {
        SalesByServiceRequest salesRequest = new SalesByServiceRequest();
        salesRequest.setStartDate((LocalDate) request.getParameters().get("startDate"));
        salesRequest.setEndDate((LocalDate) request.getParameters().get("endDate"));
        
        SalesByServiceDTO report = salesService.generateSalesByServiceReport(businessId, salesRequest);
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                 .withHeader("Service", "Orders", "Items", "Revenue", "Percentage", "AOV"))) {
            
            for (SalesByServiceDTO.ServiceSalesDTO service : report.getServices()) {
                csvPrinter.printRecord(
                    service.getServiceName(),
                    service.getOrderCount(),
                    service.getItemCount(),
                    service.getRevenue(),
                    String.format("%.2f%%", service.getPercentage()),
                    service.getAverageOrderValue()
                );
            }
            
            csvPrinter.flush();
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }
    }
    
    private byte[] exportEmployeePerformanceReport(UUID businessId, ExportReportRequest request) {
        EmployeePerfRequest perfRequest = new EmployeePerfRequest();
        perfRequest.setStartDate((LocalDate) request.getParameters().get("startDate"));
        perfRequest.setEndDate((LocalDate) request.getParameters().get("endDate"));
        perfRequest.setShopId((UUID) request.getParameters().get("shopId"));
        
        List<EmployeePerfDTO> reports = employeeService.generateEmployeePerformanceReport(businessId, perfRequest);
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                 .withHeader("Employee", "Role", "Rank", "Orders", "Items", "Revenue", "Quality", "Attendance", "Productivity"))) {
            
            for (EmployeePerfDTO perf : reports) {
                csvPrinter.printRecord(
                    perf.getEmployeeName(),
                    perf.getRole(),
                    perf.getRank(),
                    perf.getMetrics().getOrdersProcessed(),
                    perf.getMetrics().getItemsProcessed(),
                    perf.getMetrics().getRevenueHandled(),
                    String.format("%.2f%%", perf.getMetrics().getQualityScore()),
                    String.format("%.2f%%", perf.getMetrics().getAttendanceRate()),
                    String.format("%.2f", perf.getMetrics().getProductivity())
                );
            }
            
            csvPrinter.flush();
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }
    }
}
