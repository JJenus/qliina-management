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

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
            case "EMPLOYEE_PERFORMANCE":
                return exportEmployeePerformanceReport(businessId, request);
            default:
                throw new IllegalArgumentException("Unsupported report type: " + request.getReportType());
        }
    }
    
    // ======================== REVENUE REPORT ========================
    
    private byte[] exportRevenueReport(UUID businessId, ExportReportRequest request) {
        RevenueReportRequest reportRequest = new RevenueReportRequest();
        reportRequest.setStartDate(asLocalDate(param(request, "startDate")));
        reportRequest.setEndDate(asLocalDate(param(request, "endDate")));
        reportRequest.setShopId(asUUID(param(request, "shopId")));
        reportRequest.setGroupBy(asString(param(request, "groupBy")));
        
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
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Period", "Revenue", "Orders", "AOV");
            List<List<Object>> rows = new ArrayList<>();
            if (report.getByPeriod() != null) {
                for (RevenueReportDTO.PeriodSummaryDTO p : report.getByPeriod()) {
                    rows.add(List.of(
                        p.getPeriod(),
                        p.getRevenue(),
                        p.getOrders(),
                        p.getAov()
                    ));
                }
            }
            
            List<String[]> summary = List.of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()},
                new String[]{"Total Revenue", "$" + report.getTotalRevenue()},
                new String[]{"Total Orders", String.valueOf(report.getTotalOrders())},
                new String[]{"Average Order Value", "$" + report.getAverageOrderValue()}
            );
            
            buildExcel(out, "REVENUE REPORT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
    
    private byte[] exportRevenueToPDF(RevenueReportDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Period", "Revenue", "Orders", "AOV");
            List<List<String>> rows = new ArrayList<>();
            if (report.getByPeriod() != null) {
                for (RevenueReportDTO.PeriodSummaryDTO p : report.getByPeriod()) {
                    rows.add(List.of(
                        p.getPeriod(),
                        "$" + p.getRevenue(),
                        String.valueOf(p.getOrders()),
                        "$" + p.getAov()
                    ));
                }
            }
            if (rows.isEmpty()) {
                rows.add(List.of("No data", "", "", ""));
            }
            
            List<String[]> summary = List.of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()},
                new String[]{"Total Revenue", "$" + report.getTotalRevenue()},
                new String[]{"Total Orders", String.valueOf(report.getTotalOrders())},
                new String[]{"Average Order Value", "$" + report.getAverageOrderValue()}
            );
            
            buildPdf(out, "REVENUE REPORT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    
    // ======================== PROFIT & LOSS REPORT ========================
    
    private byte[] exportProfitLossReport(UUID businessId, ExportReportRequest request) {
        DateRangeRequest dateRequest = new DateRangeRequest();
        dateRequest.setStartDate(asLocalDate(param(request, "startDate")));
        dateRequest.setEndDate(asLocalDate(param(request, "endDate")));
        
        ProfitLossDTO report = financialService.generateProfitLoss(businessId, dateRequest);
        
        switch (request.getFormat().toUpperCase()) {
            case "CSV":
                return exportProfitLossToCSV(report);
            case "EXCEL":
                return exportProfitLossToExcel(report);
            case "PDF":
                return exportProfitLossToPDF(report);
            default:
                throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        }
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
    
    private byte[] exportProfitLossToExcel(ProfitLossDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Category", "Amount", "Percentage");
            List<List<Object>> rows = new ArrayList<>();
            if (report.getExpenses() != null && report.getExpenses().getCategories() != null) {
                for (ProfitLossDTO.ExpenseCategoryDTO exp : report.getExpenses().getCategories()) {
                    rows.add(List.of(
                        exp.getCategory(),
                        exp.getAmount(),
                        exp.getPercentage() + "%"
                    ));
                }
            }
            
            List<String[]> summary = List.of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()},
                new String[]{"Total Revenue", "$" + report.getRevenue().getTotal()},
                new String[]{"Total Expenses", "$" + report.getExpenses().getTotal()},
                new String[]{"Gross Profit", "$" + report.getGrossProfit()},
                new String[]{"Gross Margin", report.getGrossMargin() + "%"},
                new String[]{"Net Profit", "$" + report.getNetProfit()},
                new String[]{"Net Margin", report.getNetMargin() + "%"}
            );
            
            buildExcel(out, "PROFIT & LOSS STATEMENT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
    
    private byte[] exportProfitLossToPDF(ProfitLossDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Category", "Amount", "Percentage");
            List<List<String>> rows = new ArrayList<>();
            if (report.getExpenses() != null && report.getExpenses().getCategories() != null) {
                for (ProfitLossDTO.ExpenseCategoryDTO exp : report.getExpenses().getCategories()) {
                    rows.add(List.of(
                        exp.getCategory(),
                        "$" + exp.getAmount(),
                        exp.getPercentage() + "%"
                    ));
                }
            }
            if (rows.isEmpty()) {
                rows.add(List.of("No data", "", ""));
            }
            
            List<String[]> summary = List.of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()},
                new String[]{"Total Revenue", "$" + report.getRevenue().getTotal()},
                new String[]{"Total Expenses", "$" + report.getExpenses().getTotal()},
                new String[]{"Gross Profit", "$" + report.getGrossProfit()},
                new String[]{"Gross Margin", report.getGrossMargin() + "%"},
                new String[]{"Net Profit", "$" + report.getNetProfit()},
                new String[]{"Net Margin", report.getNetMargin() + "%"}
            );
            
            buildPdf(out, "PROFIT & LOSS STATEMENT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    
    // ======================== AGING REPORT ========================
    
    private byte[] exportAgingReport(UUID businessId, ExportReportRequest request) {
        AgingReportDTO report = agingService.generateAgingReport(businessId);
        
        switch (request.getFormat().toUpperCase()) {
            case "CSV":
                return exportAgingToCSV(report);
            case "EXCEL":
                return exportAgingToExcel(report);
            case "PDF":
                return exportAgingToPDF(report);
            default:
                throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        }
    }
    
    private byte[] exportAgingToCSV(AgingReportDTO report) {
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
    
    private byte[] exportAgingToExcel(AgingReportDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Customer", "Total Due", "Current", "1-30 Days", "31-60 Days", "61-90 Days", "90+ Days");
            List<List<Object>> rows = new ArrayList<>();
            if (report.getByCustomer() != null) {
                for (AgingReportDTO.CustomerAgingDTO c : report.getByCustomer()) {
                    rows.add(List.of(
                        c.getCustomerName(),
                        c.getTotalDue(),
                        c.getCurrent(),
                        c.getDays30(),
                        c.getDays60(),
                        c.getDays90(),
                        c.getDays90Plus()
                    ));
                }
            }
            
            List<String[]> summary = List.of(
                new String[]{"As Of Date", String.valueOf(report.getAsOfDate())},
                new String[]{"Total Receivables", "$" + report.getTotalReceivables()}
            );
            
            buildExcel(out, "AGING REPORT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
    
    private byte[] exportAgingToPDF(AgingReportDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Customer", "Total Due", "Current", "1-30 Days", "31-60 Days", "61-90 Days", "90+ Days");
            List<List<String>> rows = new ArrayList<>();
            if (report.getByCustomer() != null) {
                for (AgingReportDTO.CustomerAgingDTO c : report.getByCustomer()) {
                    rows.add(List.of(
                        c.getCustomerName(),
                        "$" + c.getTotalDue(),
                        "$" + c.getCurrent(),
                        "$" + c.getDays30(),
                        "$" + c.getDays60(),
                        "$" + c.getDays90(),
                        "$" + c.getDays90Plus()
                    ));
                }
            }
            if (rows.isEmpty()) {
                rows.add(List.of("No data", "", "", "", "", "", ""));
            }
            
            List<String[]> summary = List.of(
                new String[]{"As Of Date", String.valueOf(report.getAsOfDate())},
                new String[]{"Total Receivables", "$" + report.getTotalReceivables()}
            );
            
            buildPdf(out, "AGING REPORT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    
    // ======================== TAX REPORT ========================
    
    private byte[] exportTaxReport(UUID businessId, ExportReportRequest request) {
        TaxReportRequest taxRequest = new TaxReportRequest();
        taxRequest.setStartDate(asLocalDate(param(request, "startDate")));
        taxRequest.setEndDate(asLocalDate(param(request, "endDate")));
        
        TaxReportDTO report = taxService.generateTaxReport(businessId, taxRequest);
        
        switch (request.getFormat().toUpperCase()) {
            case "CSV":
                return exportTaxToCSV(report);
            case "EXCEL":
                return exportTaxToExcel(report);
            case "PDF":
                return exportTaxToPDF(report);
            default:
                throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        }
    }
    
    private byte[] exportTaxToCSV(TaxReportDTO report) {
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
    
    private byte[] exportTaxToExcel(TaxReportDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Date", "Invoice", "Customer", "Amount", "Tax");
            List<List<Object>> rows = new ArrayList<>();
            if (report.getDetails() != null) {
                for (TaxReportDTO.TaxDetailDTO d : report.getDetails()) {
                    rows.add(List.of(
                        String.valueOf(d.getDate()),
                        d.getInvoiceNumber(),
                        d.getCustomerName(),
                        d.getAmount(),
                        d.getTax()
                    ));
                }
            }
            
            List<String[]> summary = List.of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()},
                new String[]{"Total Sales", "$" + report.getTotalSales()},
                new String[]{"Taxable Sales", "$" + report.getTaxableSales()},
                new String[]{"Tax Rate", report.getTaxRate() + "%"},
                new String[]{"Tax Collected", "$" + report.getTaxCollected()}
            );
            
            buildExcel(out, "TAX REPORT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
    
    private byte[] exportTaxToPDF(TaxReportDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Date", "Invoice", "Customer", "Amount", "Tax");
            List<List<String>> rows = new ArrayList<>();
            if (report.getDetails() != null) {
                for (TaxReportDTO.TaxDetailDTO d : report.getDetails()) {
                    rows.add(List.of(
                        String.valueOf(d.getDate()),
                        d.getInvoiceNumber(),
                        d.getCustomerName(),
                        "$" + d.getAmount(),
                        "$" + d.getTax()
                    ));
                }
            }
            if (rows.isEmpty()) {
                rows.add(List.of("No data", "", "", "", ""));
            }
            
            List<String[]> summary = List.of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()},
                new String[]{"Total Sales", "$" + report.getTotalSales()},
                new String[]{"Taxable Sales", "$" + report.getTaxableSales()},
                new String[]{"Tax Rate", report.getTaxRate() + "%"},
                new String[]{"Tax Collected", "$" + report.getTaxCollected()}
            );
            
            buildPdf(out, "TAX REPORT", headers, rows, summary);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    
    // ======================== SALES BY SERVICE REPORT ========================
    
    private byte[] exportSalesByServiceReport(UUID businessId, ExportReportRequest request) {
        SalesByServiceRequest salesRequest = new SalesByServiceRequest();
        salesRequest.setStartDate(asLocalDate(param(request, "startDate")));
        salesRequest.setEndDate(asLocalDate(param(request, "endDate")));
        
        SalesByServiceDTO report = salesService.generateSalesByServiceReport(businessId, salesRequest);
        
        switch (request.getFormat().toUpperCase()) {
            case "CSV":
                return exportSalesByServiceToCSV(report);
            case "EXCEL":
                return exportSalesByServiceToExcel(report);
            case "PDF":
                return exportSalesByServiceToPDF(report);
            default:
                throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        }
    }
    
    private byte[] exportSalesByServiceToCSV(SalesByServiceDTO report) {
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
    
    private byte[] exportSalesByServiceToExcel(SalesByServiceDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Service", "Orders", "Items", "Revenue", "Percentage", "AOV");
            List<List<Object>> rows = new ArrayList<>();
            if (report.getServices() != null) {
                for (SalesByServiceDTO.ServiceSalesDTO s : report.getServices()) {
                    rows.add(List.of(
                        s.getServiceName(),
                        s.getOrderCount(),
                        s.getItemCount(),
                        s.getRevenue(),
                        String.format("%.2f%%", s.getPercentage()),
                        s.getAverageOrderValue()
                    ));
                }
            }
            
            List<String[]> summary = List.<String[]>of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()}
            );
            
            buildExcel(out, "SALES BY SERVICE", headers, rows, summary);
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
    
    private byte[] exportSalesByServiceToPDF(SalesByServiceDTO report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Service", "Orders", "Items", "Revenue", "Percentage", "AOV");
            List<List<String>> rows = new ArrayList<>();
            if (report.getServices() != null) {
                for (SalesByServiceDTO.ServiceSalesDTO s : report.getServices()) {
                    rows.add(List.of(
                        s.getServiceName(),
                        String.valueOf(s.getOrderCount()),
                        String.valueOf(s.getItemCount()),
                        "$" + s.getRevenue(),
                        String.format("%.2f%%", s.getPercentage()),
                        "$" + s.getAverageOrderValue()
                    ));
                }
            }
            if (rows.isEmpty()) {
                rows.add(List.of("No data", "", "", "", "", ""));
            }
            
            List<String[]> summary = List.<String[]>of(
                new String[]{"Period", report.getPeriod().getStart() + " to " + report.getPeriod().getEnd()}
            );
            
            buildPdf(out, "SALES BY SERVICE", headers, rows, summary);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    
    // ======================== EMPLOYEE PERFORMANCE REPORT ========================
    
    private byte[] exportEmployeePerformanceReport(UUID businessId, ExportReportRequest request) {
        EmployeePerfRequest perfRequest = new EmployeePerfRequest();
        perfRequest.setStartDate(asLocalDate(param(request, "startDate")));
        perfRequest.setEndDate(asLocalDate(param(request, "endDate")));
        perfRequest.setShopId(asUUID(param(request, "shopId")));
        
        List<EmployeePerfDTO> reports = employeeService.generateEmployeePerformanceReport(businessId, perfRequest);
        
        switch (request.getFormat().toUpperCase()) {
            case "CSV":
                return exportEmployeePerformanceToCSV(reports);
            case "EXCEL":
                return exportEmployeePerformanceToExcel(reports);
            case "PDF":
                return exportEmployeePerformanceToPDF(reports);
            default:
                throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        }
    }
    
    private byte[] exportEmployeePerformanceToCSV(List<EmployeePerfDTO> reports) {
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
    
    private byte[] exportEmployeePerformanceToExcel(List<EmployeePerfDTO> reports) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Employee", "Role", "Rank", "Orders", "Items", "Revenue", "Quality", "Attendance", "Productivity");
            List<List<Object>> rows = new ArrayList<>();
            for (EmployeePerfDTO perf : reports) {
                rows.add(List.of(
                    perf.getEmployeeName(),
                    perf.getRole(),
                    perf.getRank(),
                    perf.getMetrics().getOrdersProcessed(),
                    perf.getMetrics().getItemsProcessed(),
                    perf.getMetrics().getRevenueHandled(),
                    String.format("%.2f%%", perf.getMetrics().getQualityScore()),
                    String.format("%.2f%%", perf.getMetrics().getAttendanceRate()),
                    String.format("%.2f", perf.getMetrics().getProductivity())
                ));
            }
            
            buildExcel(out, "EMPLOYEE PERFORMANCE", headers, rows, List.of());
            return out.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
    
    private byte[] exportEmployeePerformanceToPDF(List<EmployeePerfDTO> reports) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            List<String> headers = List.of("Employee", "Role", "Rank", "Orders", "Items", "Revenue", "Quality", "Attendance", "Productivity");
            List<List<String>> rows = new ArrayList<>();
            for (EmployeePerfDTO perf : reports) {
                rows.add(List.of(
                    perf.getEmployeeName(),
                    perf.getRole(),
                    String.valueOf(perf.getRank()),
                    String.valueOf(perf.getMetrics().getOrdersProcessed()),
                    String.valueOf(perf.getMetrics().getItemsProcessed()),
                    "$" + perf.getMetrics().getRevenueHandled(),
                    String.format("%.2f%%", perf.getMetrics().getQualityScore()),
                    String.format("%.2f%%", perf.getMetrics().getAttendanceRate()),
                    String.format("%.2f", perf.getMetrics().getProductivity())
                ));
            }
            if (rows.isEmpty()) {
                rows.add(List.of("No data", "", "", "", "", "", "", "", ""));
            }
            
            buildPdf(out, "EMPLOYEE PERFORMANCE", headers, rows, List.of());
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    
    // ======================== PDF HELPER ========================
    
    private void buildPdf(ByteArrayOutputStream out, String title, List<String> headers, List<List<String>> rows, List<String[]> summary) throws Exception {
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc);
        
        doc.add(new Paragraph(title).setBold().setFontSize(16));
        
        if (summary != null && !summary.isEmpty()) {
            addSummarySection(doc, summary);
            doc.add(new Paragraph("\n"));
        }
        
        float[] columnWidths = new float[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            columnWidths[i] = 100f / headers.size();
        }
        Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth();
        
        for (String header : headers) {
            com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell().add(new Paragraph(header).setBold());
            table.addHeaderCell(cell);
        }
        
        for (List<String> row : rows) {
            for (String value : row) {
                table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(value != null ? value : "")));
            }
        }
        
        doc.add(table);
        doc.close();
    }
    
    private void addSummarySection(Document doc, List<String[]> keyValues) {
        for (String[] kv : keyValues) {
            doc.add(new Paragraph(kv[0] + ": " + (kv.length > 1 ? kv[1] : "")).setBold());
        }
    }
    
    // ======================== EXCEL HELPER ========================
    
    private void buildExcel(ByteArrayOutputStream out, String title, List<String> headers, List<List<Object>> rows, List<String[]> summary) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(title);
            
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
            
            int rowNum = 0;
            
            // Title row
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(headerStyle);
            rowNum++; // empty row
            
            // Summary rows
            if (summary != null) {
                for (String[] kv : summary) {
                    Row sRow = sheet.createRow(rowNum++);
                    sRow.createCell(0).setCellValue(kv[0]);
                    sRow.createCell(1).setCellValue(kv.length > 1 ? kv[1] : "");
                }
                rowNum++; // empty row after summary
            }
            
            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Data rows
            for (List<Object> row : rows) {
                Row dataRow = sheet.createRow(rowNum++);
                for (int i = 0; i < row.size(); i++) {
                    Cell cell = dataRow.createCell(i);
                    Object val = row.get(i);
                    if (val instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
        }
    }
    
    // ======================== HELPERS ========================
    
    private Object param(ExportReportRequest request, String key) {
        return request.getParameters() != null ? request.getParameters().get(key) : null;
    }

    private LocalDate asLocalDate(Object raw) {
        if (raw == null) {
            return LocalDate.now();
        }
        if (raw instanceof LocalDate localDate) {
            return localDate;
        }
        if (raw instanceof String s && !s.isBlank()) {
            return LocalDate.parse(s);
        }
        return LocalDate.now();
    }

    private UUID asUUID(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String s && !s.isBlank()) {
            return UUID.fromString(s);
        }
        return null;
    }

    private String asString(Object raw) {
        return raw instanceof String s ? s : null;
    }
}
