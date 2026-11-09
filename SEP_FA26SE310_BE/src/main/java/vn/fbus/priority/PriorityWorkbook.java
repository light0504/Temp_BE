package vn.fbus.priority;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
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
public class PriorityWorkbook {
 public static final int MAX_ROWS=2000;
 public record RowEntry(int row,String email,String group){}
 public record RowError(int row,String email,String message){}
 public record Parsed(int totalRows,List<RowEntry> entries,List<RowError> errors){}
 private final Validator validator;
 public PriorityWorkbook(Validator validator){this.validator=validator;}
 public byte[] template(){
  try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
   var sheet=book.createSheet("Priority");sheet.createFreezePane(0,1);sheet.setDisplayGridlines(false);sheet.setColumnWidth(0,42*256);sheet.setColumnWidth(1,28*256);
   var font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());var style=book.createCellStyle();style.setFont(font);style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
   var row=sheet.createRow(0);row.setHeightInPoints(28);String[] headers={"Email","Nhóm ưu tiên"};for(int i=0;i<2;i++){var cell=row.createCell(i);cell.setCellValue(headers[i]);cell.setCellStyle(style);}
   var text=book.createCellStyle();text.setDataFormat(book.createDataFormat().getFormat("@"));for(int i=0;i<2;i++)sheet.setDefaultColumnStyle(i,text);
   var helper=sheet.getDataValidationHelper();var validation=helper.createValidation(helper.createExplicitListConstraint(new String[]{"STAFF","STUDENT"}),new CellRangeAddressList(1,MAX_ROWS,1,1));validation.setEmptyCellAllowed(false);validation.setShowErrorBox(true);validation.createErrorBox("Nhóm không hợp lệ","STAFF: giảng viên/nhân viên; STUDENT: sinh viên ưu tiên.");sheet.addValidationData(validation);
   var guide=book.createSheet("Hướng dẫn");guide.setColumnWidth(0,24*256);guide.setColumnWidth(1,90*256);var wrap=book.createCellStyle();wrap.setWrapText(true);wrap.setVerticalAlignment(VerticalAlignment.CENTER);
   String[][] notes={{"Cột","Quy định"},{"Email","Bắt buộc, mỗi dòng một email. Không phân biệt hoa/thường."},{"Nhóm ưu tiên","Bắt buộc: STAFF = giảng viên/nhân viên; STUDENT = sinh viên ưu tiên."},{"Tệp","Nhập ở sheet Priority từ dòng 2; tối đa 2.000 dòng / 2 MB. Không dùng công thức."},{"Email đã có","Cùng nhóm: bỏ qua. Khác nhóm: cập nhật nhóm. Email lặp khác nhóm trong cùng tệp: báo lỗi."},{"Đăng nhập","Chỉ cấp mức ưu tiên, không cấp quyền đăng nhập hoặc tạo tài khoản. Email chưa có tài khoản vẫn có thể thêm trước."},{"Người dùng còn lại","Email không thuộc danh sách ưu tiên được mở đặt theo mốc còn lại. Vai trò giảng viên không tự động cấp ưu tiên."}};
   for(int r=0;r<notes.length;r++){var line=guide.createRow(r);line.setHeightInPoints(r==0?28:48);for(int c=0;c<2;c++){var cell=line.createCell(c);cell.setCellValue(notes[r][c]);cell.setCellStyle(r==0?style:wrap);}}
   book.write(output);return output.toByteArray();
  }catch(java.io.IOException error){throw new IllegalStateException("Cannot generate priority template",error);}
 }
 private String normal(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");}
 private boolean blank(Cell cell){return cell==null||cell.getCellType()==CellType.BLANK||cell.getCellType()==CellType.STRING&&cell.getStringCellValue().isBlank();}
 private String text(Cell cell){if(blank(cell))return "";require(cell.getCellType()==CellType.STRING,400,"Ô dữ liệu phải là văn bản, không dùng công thức.");return cell.getStringCellValue().trim();}
 public Parsed parse(MultipartFile file){
  require(!file.isEmpty()&&file.getSize()<=2L*1024*1024,400,"Tệp Excel phải từ 1 byte đến 2 MB.");String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);require(name.endsWith(".xlsx")||name.endsWith(".xls"),400,"Chọn tệp .xlsx hoặc .xls.");
  try(var stream=file.getInputStream();var book=WorkbookFactory.create(stream)){
   require(book.getNumberOfSheets()>0,400,"Tệp không có dữ liệu.");var sheet=book.getSheet("Priority");if(sheet==null)sheet=book.getSheetAt(0);
   require(sheet.getLastRowNum()<=MAX_ROWS,400,"Tối đa 2.000 dòng từ dòng 2.");var header=sheet.getRow(0);require(header!=null&&header.getLastCellNum()>0&&header.getLastCellNum()<=10,400,"Dòng 1 phải chứa tên cột. Hãy dùng file mẫu.");
   Integer emailColumn=null,groupColumn=null;for(int i=0;i<header.getLastCellNum();i++){
    String label=text(header.getCell(i));if(label.isEmpty())continue;String key=normal(label);
    if(key.equals("email")){require(emailColumn==null,400,"Cột Email bị trùng.");emailColumn=i;}
    else if(List.of("nhomuutien","prioritygroup","group").contains(key)){require(groupColumn==null,400,"Cột Nhóm ưu tiên bị trùng.");groupColumn=i;}
    else throw new ApiException(400,"Cột không hợp lệ: "+label+". Hãy dùng file mẫu.");
   }
   require(emailColumn!=null&&groupColumn!=null,400,"Thiếu cột Email hoặc Nhóm ưu tiên.");
   var entries=new ArrayList<RowEntry>();var errors=new ArrayList<RowError>();int total=0;
   for(int i=1;i<=sheet.getLastRowNum();i++){
    var row=sheet.getRow(i);if(row==null)continue;boolean empty=true;for(var cell:row)if(!blank(cell)){empty=false;break;}if(empty)continue;total++;String email="";
    try{
     email=text(row.getCell(emailColumn)).toLowerCase(Locale.ROOT);String value=normal(text(row.getCell(groupColumn)));
     String group=switch(value){case "staff","giangviennhanvien"->"STAFF";case "student","sinhvienuutien"->"STUDENT";default->"";};
     require(!group.isEmpty(),400,"Nhóm phải là STAFF hoặc STUDENT.");require(validator.validate(new PriorityContracts.Entry(email,group)).isEmpty(),400,"Email không hợp lệ.");
     for(var cell:row){require(cell.getCellType()!=CellType.FORMULA,400,"Không dùng công thức.");require(cell.getColumnIndex()==emailColumn||cell.getColumnIndex()==groupColumn||blank(cell),400,"Có dữ liệu ngoài các cột đã khai báo.");}
     entries.add(new RowEntry(i+1,email,group));
    }catch(ApiException error){errors.add(new RowError(i+1,email,error.getMessage()));}
   }
   require(total>0,400,"Tệp chưa có dữ liệu. Nhập từ dòng 2.");return new Parsed(total,entries,errors);
  }catch(ApiException error){throw error;}catch(Exception error){throw new ApiException(400,"Không đọc được Excel. Chọn tệp hợp lệ, không có mật khẩu và không vượt giới hạn giải nén.");}
 }
}

