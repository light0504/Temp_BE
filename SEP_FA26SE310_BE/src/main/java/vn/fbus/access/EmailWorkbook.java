package vn.fbus.access;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.fbus.common.ApiException;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Component
public class EmailWorkbook {
    public static final int MAX_ROWS = 2000;
    public record Entry(int row, String email, String fullName, String role) {}
    public record RowError(int row, String email, String message) {}
    public record Parsed(int totalRows, List<Entry> entries, List<RowError> errors) {}
    private record EmailValue(@NotBlank @Email @Size(max=254) String value) {}
    private final Validator validator;
    private static final List<String> ROLES = List.of("STUDENT","LECTURER","DRIVER","BUS_ATTENDANT");
    static { ZipSecureFile.setMaxEntrySize(32L * 1024 * 1024); }
    public EmailWorkbook(Validator validator) { this.validator=validator; }

    public byte[] template() {
        try (var book=new XSSFWorkbook(); var output=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("Emails");
            sheet.createFreezePane(0,1); sheet.setDisplayGridlines(false);
            sheet.setColumnWidth(0,38*256); sheet.setColumnWidth(1,30*256); sheet.setColumnWidth(2,25*256);
            var font=book.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            var headerStyle=book.createCellStyle(); headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var header=sheet.createRow(0); header.setHeightInPoints(28);
            String[] titles={"Email","Họ tên","Vai trò"};
            for (int column=0; column<titles.length; column++) { var cell=header.createCell(column); cell.setCellValue(titles[column]); cell.setCellStyle(headerStyle); }
            var text=book.createCellStyle(); text.setDataFormat(book.createDataFormat().getFormat("@"));
            for (int column=0; column<3; column++) sheet.setDefaultColumnStyle(column,text);
            sheet.setAutoFilter(new CellRangeAddress(0,MAX_ROWS,0,2));
            var helper=sheet.getDataValidationHelper();
            var roleValidation=helper.createValidation(helper.createExplicitListConstraint(ROLES.toArray(String[]::new)),new CellRangeAddressList(1,MAX_ROWS,2,2));
            roleValidation.setEmptyCellAllowed(true); roleValidation.setShowErrorBox(true);
            roleValidation.createErrorBox("Vai trò không hợp lệ","Chọn STUDENT, LECTURER, DRIVER hoặc BUS_ATTENDANT.");
            roleValidation.setShowPromptBox(true); roleValidation.createPromptBox("Vai trò","Để trống: STUDENT.");
            sheet.addValidationData(roleValidation);
            var emailPrompt=helper.createValidation(helper.createTextLengthConstraint(DataValidationConstraint.OperatorType.BETWEEN,"3","254"),new CellRangeAddressList(1,MAX_ROWS,0,0));
            emailPrompt.setShowPromptBox(true); emailPrompt.createPromptBox("Email bắt buộc","Mỗi dòng một email. Tối đa 2.000 dòng.");
            emailPrompt.setShowErrorBox(true); emailPrompt.createErrorBox("Email không hợp lệ","Nhập địa chỉ email từ 3 đến 254 ký tự.");
            sheet.addValidationData(emailPrompt);
            var guide=book.createSheet("Hướng dẫn");
            String[][] notes={
                {"Cột","Quy định"},
                {"Email","Bắt buộc. Mỗi dòng một email; không phân biệt chữ hoa/thường."},
                {"Họ tên","Tùy chọn, tối đa 150 ký tự."},
                {"Vai trò","Tùy chọn. Để trống = STUDENT. Giá trị: STUDENT, LECTURER, DRIVER, BUS_ATTENDANT."},
                {"Tệp","Nhập dữ liệu ở sheet Emails, từ dòng 2; tối đa 2.000 dòng và 2 MB. Không dùng công thức."},
                {"Email trùng","Cùng vai trò: bỏ qua; khác vai trò: báo lỗi. Không sửa tài khoản hoặc email đã có."},
                {"Đăng nhập","Danh sách cấp quyền email, không tạo mật khẩu. Google OAuth chưa được tích hợp."}
            };
            guide.setColumnWidth(0,20*256); guide.setColumnWidth(1,95*256); guide.setDisplayGridlines(false);
            var wrap=book.createCellStyle(); wrap.setWrapText(true); wrap.setVerticalAlignment(VerticalAlignment.TOP);
            for (int r=0; r<notes.length; r++) {
                var row=guide.createRow(r); row.setHeightInPoints(r==0 ? 28 : 40);
                for (int c=0; c<2; c++) { var cell=row.createCell(c); cell.setCellValue(notes[r][c]); cell.setCellStyle(r==0 ? headerStyle : wrap); }
            }
            book.write(output); return output.toByteArray();
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot generate email template",error); }
    }

    public Parsed parse(MultipartFile file) {
        require(!file.isEmpty() && file.getSize()<=2L*1024*1024,400,"Tệp Excel phải từ 1 byte đến 2 MB.");
        String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        require(name.endsWith(".xlsx") || name.endsWith(".xls"),400,"Chọn tệp .xlsx hoặc .xls.");
        try (var stream=file.getInputStream(); var book=WorkbookFactory.create(stream)) {
            require(book.getNumberOfSheets()>0,400,"Tệp Excel không có sheet dữ liệu.");
            var sheet=book.getSheet("Emails"); if (sheet==null) sheet=book.getSheetAt(0);
            require(sheet.getLastRowNum()<=MAX_ROWS,400,"Tối đa 2.000 dòng dữ liệu, bắt đầu từ dòng 2.");
            var header=sheet.getRow(0);
            require(header!=null && header.getLastCellNum()>0 && header.getLastCellNum()<=20,400,"Dòng 1 phải chứa tên cột; hãy dùng file mẫu.");
            var columns=new HashMap<String,Integer>();
            for (int c=0; c<header.getLastCellNum(); c++) {
                String label=text(header.getCell(c)).trim(); if (label.isEmpty()) continue;
                String key=headerKey(label);
                require(key!=null,400,"Cột không hợp lệ: "+label+". Hãy dùng file mẫu.");
                require(columns.putIfAbsent(key,c)==null,400,"Cột bị trùng: "+label+".");
            }
            require(columns.containsKey("email"),400,"Thiếu cột Email ở dòng 1.");
            var entries=new ArrayList<Entry>(); var errors=new ArrayList<RowError>(); int total=0;
            for (int r=1; r<=sheet.getLastRowNum(); r++) {
                var row=sheet.getRow(r); if (row==null || empty(row)) continue;
                total++; String email="";
                try {
                    for (var cell : row) require(cell.getCellType()!=CellType.FORMULA,400,"Không dùng công thức.");
                    email=value(row,columns.get("email")).trim().toLowerCase(Locale.ROOT);
                    String fullName=value(row,columns.get("name")).trim();
                    String role=value(row,columns.get("role")).trim().toUpperCase(Locale.ROOT);
                    if (role.isEmpty()) role="STUDENT";
                    require(validator.validate(new EmailValue(email)).isEmpty(),400,"Email không hợp lệ.");
                    require(fullName.length()<=150,400,"Họ tên tối đa 150 ký tự.");
                    require(ROLES.contains(role),400,"Vai trò phải là STUDENT, LECTURER, DRIVER hoặc BUS_ATTENDANT.");
                    for (var cell : row) require(columns.containsValue(cell.getColumnIndex()) || blank(cell),400,"Có dữ liệu ngoài các cột đã khai báo.");
                    entries.add(new Entry(r+1,email,fullName,role));
                } catch (ApiException error) { errors.add(new RowError(r+1,email,error.getMessage())); }
            }
            require(total>0,400,"Tệp chưa có email. Nhập dữ liệu từ dòng 2.");
            return new Parsed(total,entries,errors);
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw new ApiException(400,"Không đọc được Excel. Tệp phải hợp lệ, không có mật khẩu và không vượt giới hạn giải nén."); }
    }
    private String headerKey(String text) {
        String key=Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").replace("đ","d").replace("Đ","D").toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
        return switch (key) { case "email" -> "email"; case "hoten","fullname" -> "name"; case "vaitro","role" -> "role"; default -> null; };
    }
    private String value(Row row, Integer column) { return column==null ? "" : text(row.getCell(column)); }
    private String text(Cell cell) {
        if (cell==null || cell.getCellType()==CellType.BLANK) return "";
        require(cell.getCellType()==CellType.STRING,400,"Các ô phải là văn bản, không dùng công thức.");
        return cell.getStringCellValue();
    }
    private boolean blank(Cell cell) { return cell.getCellType()==CellType.BLANK || cell.getCellType()==CellType.STRING && cell.getStringCellValue().isBlank(); }
    private boolean empty(Row row) { for (var cell : row) if (!blank(cell)) return false; return true; }
}
