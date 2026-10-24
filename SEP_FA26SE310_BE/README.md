# Fbus Backend

Spring Boot 3.5.16, Java 21, Spring Security, JDBC và Flyway. H2 lưu file khi chạy local; PostgreSQL khi chạy Docker. Backend này độc lập với API Render trước đây.

Frontend nằm tại `E:\SEP_FE`; xem [hướng dẫn chung](../../SEP_FE/README.md). Bộ công cụ portable nằm tại `E:\Temp_BE\.tools`.

## Chạy local

Trong thư mục `E:\Temp_BE\SEP_FA26SE310_BE`:

```powershell
.\run.ps1 -Build
```

Script dùng Java/Maven đã cài hoặc bộ công cụ portable trong `../.tools` nếu có. Có thể dùng trực tiếp `mvn -DskipTests package` và `java -jar target/fbus-api-1.0.0.jar` với JDK 21 và Maven 3.9+.

- API: `http://localhost:8081/api`
- Database: `./data/fbus.mv.db`, tự tạo và giữ dữ liệu khi khởi động lại.
- Múi giờ vận hành: `Asia/Ho_Chi_Minh`.
- Đổi cổng bằng biến môi trường `PORT`.
- Dừng bằng Ctrl+C. Không chạy hai tiến trình cùng mở một file H2.

Profile mặc định **dev** tạo các tài khoản sau nếu chưa có:

| Role | Username / Email | Password |
| --- | --- | --- |
| ADMIN | `admin` / `admin@example.com` | `Admin@12345` |
| STUDENT | `student` / `student@example.com` | `Student@12345` |
| DRIVER | `driver` / `driver@example.com` | `Driver@12345` |
| BUS_ATTENDANT | `attendant` / `attendant@example.com` | `Attendant@12345` |
| LECTURER | `lecturer` / `lecturer@example.com` | `Lecturer@12345` |

Đây là tài khoản phát triển. Không bật profile `dev` khi triển khai công khai. Profile khác yêu cầu `JWT_SECRET` tối thiểu 32 byte và không tự tạo tài khoản; phải cấp tài khoản quản trị với mật khẩu BCrypt qua quy trình quản trị database trước khi sử dụng.

## PostgreSQL / Docker

```powershell
Copy-Item .env.example .env
# Sửa DB_PASSWORD và JWT_SECRET trong .env
docker compose up --build
```

Docker Compose dùng PostgreSQL 17 và volume riêng. Dữ liệu H2 local không tự chuyển vào PostgreSQL. File `.env` chỉ được Docker Compose đọc; chạy Java trực tiếp cần đặt biến môi trường trong shell.

Image backend: `fbus-api:local`. API được mở tại `http://localhost:8081/api`, PostgreSQL chỉ truy cập trong mạng Docker.

```powershell
docker compose up -d --build    # Build và chạy nền
docker compose ps             # Trạng thái container
docker compose logs -f api     # Log backend
docker compose stop           # Dừng, giữ dữ liệu
docker compose start          # Chạy lại
```

Các biến cấu hình: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ALLOWED_ORIGINS` (danh sách origin cách nhau dấu phẩy), `SPRING_PROFILES_ACTIVE`, `PORT`. Triển khai công khai cần HTTPS và secret riêng. Không đưa file database hay secret lên Git.

## Luồng sử dụng

1. Đăng nhập web bằng tài khoản admin.
2. **Tuyến & điểm dừng → Thêm điểm dừng**: tạo FU HCM, FU Tân Uyên và các điểm trung gian nếu cần.
3. **Tạo tuyến**: chọn các điểm theo thứ tự, nhập số phút tính từ điểm đầu. Điểm đầu là 0, các điểm sau tăng dần. Chiều ngược lại được tạo thành một tuyến riêng.
4. **Người dùng → Tạo tài khoản**: tạo tài xế, bus attendant hoặc giảng viên. **Quản lý xe → Thêm xe**: nhập biển số, số ghế, tài xế mặc định và bus attendant (tùy chọn).
5. **Quản lý chuyến xe → Tạo chuyến**: chọn tuyến, nhập biển số/số ghế/tài xế/bus attendant, giờ, slot 1–6 và một hoặc nhiều ngày trên lịch (tối đa 90). Có thể chọn xe đã đăng ký để điền thông tin vào các ô rồi chỉnh lại. Nếu một ngày bị trùng lịch xe/tài xế/bus attendant hoặc giờ đã qua, toàn bộ lần tạo bị từ chối. Trang quản lý hiển thị lịch tuần T2–CN, mỗi ô là số chuyến của một slot; bấm ô để xem chi tiết và tạm dừng/mở chuyến.
6. Student hoặc lecturer đăng nhập mobile để tìm chuyến, chọn ghế, đặt/hủy chỗ và xem lịch sử.
7. Driver/bus attendant đăng nhập mobile để xem các chuyến được phân công từ hôm nay, theo ID tài khoản được gắn trên chuyến.
8. **Người dùng → Email được phép**: tải mẫu Excel, điền email từ dòng 2 rồi chọn **Nhập Excel**. Chỉ Email bắt buộc; Họ tên và Vai trò tùy chọn (mặc định STUDENT). Nhận .xlsx/.xls tối đa 2 MB / 2.000 dòng; trả số dòng thêm, trùng và lỗi. Nhập nối thêm, không sửa quyền hay xóa dữ liệu cũ. Danh sách cấp quyền không tạo tài khoản/mật khẩu; Google OAuth chưa tích hợp.

Mỗi chuyến lưu snapshot biển số, số ghế, nhân sự và ID tương ứng tại thời điểm tạo. Chỉnh thông tin trong form chuyến không sửa mặc định của xe. Sửa xe/nhân sự mặc định không sửa các chuyến đã tạo hoặc sơ đồ ghế đã có đặt chỗ. Ngừng sử dụng xe chặn tạo chuyến mới; muốn ngừng nhận đặt chỗ ở chuyến cũ cần tạm dừng chuyến đó. Nhân sự nhập thủ công được liên kết tài khoản khi tên trùng duy nhất; chọn từ gợi ý nếu trùng tên. Nhân sự chưa có tài khoản chỉ lưu tên trên chuyến.

## API

Các phản hồi dùng envelope `{ "statusCode": 200, "message": "Success", "data": ... }`. Lỗi trả HTTP tương ứng và `data: null`. Xem hợp đồng đầy đủ trong [docs/API.md](docs/API.md).

| Nhóm | Endpoint |
| --- | --- |
| Auth | `POST /api/login`, `POST /api/refresh-token`, `POST /api/session/logout` |
| Admin | `GET/POST /api/admin/stops`, `/api/admin/routes`, `/api/admin/trips` |
| Admin | `PUT /api/admin/trips/{id}/status`, `GET /api/admin/users?page=0&size=20` |
| Admin | `POST /api/admin/users`, `GET /api/admin/staff`, `GET/POST /api/admin/vehicles`, `PUT /api/admin/vehicles/{id}`, `POST /api/admin/trips/batch` |
| Crew | `GET /api/staff/trips` |
| Student | `GET /api/student/routes`, `/api/student/stops?routeId=...`, `/api/student/trips?date=...` |
| Student | `GET /api/student/trips/{id}/stops`, `/api/student/trips/{id}/seats?boardingTripStopId=...&alightingTripStopId=...` |
| Student | `POST /api/student/bookings`, `GET /api/student/bookings/my`, `PUT /api/student/bookings/{id}/cancel` |
| Student | `PUT /api/student/profile/preferred-stop` |

JWT có hạn 30 phút; refresh token 7 ngày, được xoay sau khi refresh và chỉ lưu hash trong database. Đăng xuất thu hồi session, bao gồm access token đang dùng. API kiểm tra role ở server; student không gọi được admin endpoint.

Danh sách email nằm ở bảng `email_access` (migration V4). Login/refresh/access token kiểm tra email và role trong danh sách, ngoại trừ ADMIN. Tài khoản cũ được giữ quyền bằng migration; tạo tài khoản bằng admin tự cấp quyền email. Email nhập qua Excel chưa có tài khoản sẽ không đăng nhập mật khẩu được. Chưa triển khai Google OAuth; khi bổ sung phải xác minh Google ID token/email đã xác minh trước khi đối chiếu danh sách, không tin email do client tự khai.

Đặt chỗ chạy trong transaction, khóa user rồi chuyến trước khi kiểm tra ghế. Ghế có thể dùng lại ở các chặng không giao nhau; chặng giao nhau bị từ chối bằng `409`. Cùng user và `idempotencyKey` với cùng payload trả về đặt chỗ cũ; đổi payload với key cũ bị từ chối. Hủy cũng giữ cùng thứ tự khóa và có thể gọi lại an toàn.

## Cấu trúc

```text
src/main/java/vn/fbus/
  auth/         # Login, JWT/session, refresh và logout
  booking/      # Tạo, hủy, lịch sử đặt chỗ
  fleet/        # Xe và nhân sự mặc định
  common/       # Envelope, validation DTO, lỗi, tạo khóa database
  config/       # Security, CORS, tài khoản dev
  transport/    # Điểm dừng, tuyến, chuyến, ghế
src/main/resources/db/migration/
  V1__initial_schema.sql
```

Phạm vi hiện tại chưa bao gồm Google OAuth, check-in/điểm danh của nhân sự, danh sách chờ tự động và báo cáo vận hành. Driver demo được giữ riêng; đăng nhập tài khoản nhân sự thật dùng danh sách phân công từ API. Các trang admin dùng dữ liệu thật. Import email lưu quyền trên backend, có file mẫu và báo lỗi theo dòng; không tạo mật khẩu. Chưa chạy kiểm thử chức năng đồng thời hoặc trên thiết bị thật.
