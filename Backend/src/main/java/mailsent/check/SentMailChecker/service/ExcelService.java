package mailsent.check.SentMailChecker.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;


@Service
public class ExcelService {

    private final GmailAutomationService gmailAutomationService;

    // Constructor injection (Spring Boot will auto-wire your GmailAutomationService)
    public ExcelService(GmailAutomationService gmailAutomationService) {
        this.gmailAutomationService = gmailAutomationService;
    }

    public ByteArrayInputStream processExcel(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new RuntimeException("Filename not present");

        if (filename.toLowerCase().endsWith(".csv")) {
            return processCSV(file);
        } else if (filename.toLowerCase().endsWith(".xlsx")) {
            return processXLSX(file);
        } else {
            throw new RuntimeException("Unsupported file format: " + filename);
        }
    }

    // --- Process Excel (.xlsx) files ---
    private ByteArrayInputStream processXLSX(MultipartFile file) throws Exception {
        List<List<String>> filteredRows = new ArrayList<>();
        Set<String> processedEmails = new HashSet<>();
        List<String> headers = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();

            if (iterator.hasNext()) {
                Row headerRow = iterator.next();
                for (Cell cell : headerRow) headers.add(cell.toString().trim().toLowerCase());
                filteredRows.add(headers);
            }

            int emailCol = headers.indexOf("email");
            if (emailCol == -1) throw new RuntimeException("No 'email' column found.");

            while (iterator.hasNext()) {
                Row row = iterator.next();
                Cell emailCell = row.getCell(emailCol);

                if (emailCell == null) continue;

                String email = emailCell.toString().trim().toLowerCase();
                if (email.isEmpty() || processedEmails.contains(email)) continue;

                boolean isSent = gmailAutomationService.checkEmailSent(email);
                if (!isSent) {
                    List<String> rowData = new ArrayList<>();
                    for (Cell cell : row) rowData.add(cell.toString());
                    filteredRows.add(rowData);
                }

                processedEmails.add(email);
            }
        }

        return writeToExcel(filteredRows);
    }

    // --- Process CSV files ---
    private ByteArrayInputStream processCSV(MultipartFile file) throws Exception {
        List<List<String>> filteredRows = new ArrayList<>();
        Set<String> processedEmails = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int emailIndex = -1;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);  // -1 keeps empty columns
                List<String> values = Arrays.asList(parts);

                if (isHeader) {
                    filteredRows.add(values);
                    emailIndex = values.stream()
                            .map(String::toLowerCase)
                            .toList().indexOf("email");
                    if (emailIndex == -1) {
                        throw new RuntimeException("No 'email' column found.");
                    }
                    isHeader = false;
                    continue;
                }

                String email = values.get(emailIndex).trim().toLowerCase();
                if (email.isEmpty() || processedEmails.contains(email)) continue;

                boolean isSent = gmailAutomationService.checkEmailSent(email);
                if (!isSent) {
                    filteredRows.add(values);
                }

                processedEmails.add(email);
            }
        }

        return writeToExcel(filteredRows);
    }

    // --- Create new Excel (.xlsx) content from filtered data ---
    private ByteArrayInputStream writeToExcel(List<List<String>> rows) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Filtered");

        for (int i = 0; i < rows.size(); i++) {
            List<String> rowData = rows.get(i);
            Row row = sheet.createRow(i);
            for (int j = 0; j < rowData.size(); j++) {
                row.createCell(j).setCellValue(rowData.get(j));
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new ByteArrayInputStream(out.toByteArray());
    }
}
