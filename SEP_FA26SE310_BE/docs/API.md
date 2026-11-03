# API contract

Base URL local: `http://localhost:8081/api`. Ngoại trừ login, refresh và health, các API yêu cầu `Authorization: Bearer <accessToken>`. Body JSON dùng `Content-Type: application/json`.

## Xác thực

`POST /login`: `{ "username": "admin", "password": "Admin@12345" }` (tài khoản dev).

Response data: `{ token, accessToken, refreshToken, fullName, email, roles }`. JWT chứa `sub` là user ID, `sid`, `roles`, `email`, `fullName`, `iat`, `exp`, `iss: "fbus"`.

`POST /refresh-token`: `{ "refreshToken": "..." }`. Dùng refresh token mới trong phản hồi cho lần tiếp theo.

`POST /session/logout`: body rỗng. `GET /health` trả `data: { "status": "UP" }`.

Ngoại trừ ADMIN, tài khoản phải có email và vai trò tương ứng trong `email_access`. Kiểm tra áp dụng khi đăng nhập, refresh và dùng access token. Migration giữ quyền cho tài khoản hiện có; admin tạo tài khoản mới sẽ đồng thời cấp quyền email. Nhập email qua Excel chỉ cấp quyền, không tạo mật khẩu hoặc xác minh danh tính. Google OAuth chưa được tích hợp; không có endpoint đăng nhập chỉ bằng email.

## Điểm dừng, tuyến, chuyến (ADMIN)

`POST /admin/stops`:

```json
{ "stopCode": "FU-HCM", "name": "FU HCM", "address": "Địa chỉ cơ sở FU HCM" }
```

`GET /admin/stops` trả mảng `{ stopId, stopCode, name, address, offsetMinutes: null }`. Lưu lại ID do server cấp.

`POST /admin/routes`:

```json
{
  "routeCode": "HCM-TU",
  "name": "FU HCM → FU Tân Uyên",
  "stops": [
    { "stopId": 1, "offsetMinutes": 0 },
    { "stopId": 2, "offsetMinutes": 60 }
  ]
}
```

ID ví dụ phải thay bằng ID thật. Ít nhất 2, tối đa 30 điểm dừng không trùng nhau; offset đầu bằng 0, tăng dần, tối đa 1440 phút. Tuyến có hướng; chiều ngược tạo tuyến riêng. `GET /admin/routes` trả mảng `{ routeId, routeCode, name, stops: [{ stopId, stopCode, name, address, offsetMinutes }] }`.

`POST /admin/trips`: `{ routeId, vehicleId: null, vehicle: "51B-123.45", capacity: 45, driver: "Nguyễn Văn A", driverId: null, busAttendant: "", busAttendantId: null, date: "YYYY-MM-DD", time: "HH:mm", slot: 1 }`.

`POST /admin/trips/batch`: cùng thông tin xe/nhân sự như tạo một chuyến, thay `date` bằng `dates: ["YYYY-MM-DD", "YYYY-MM-DD"]`. Từ 1 đến 90 ngày khác nhau. Một transaction cho toàn bộ danh sách: ngày không hợp lệ hoặc trùng lịch sẽ rollback cả lần tạo. Response là mảng Trip đã tạo. Nếu mất kết nối, tải lại lịch để đối chiếu trước khi tạo lại.

`vehicleId` tùy chọn. Web cho nhập thủ công; chọn xe có sẵn sẽ điền biển số, số ghế, tài xế và bus attendant vào các ô có thể sửa. Các giá trị gửi lên được lưu riêng cho chuyến, không thay đổi mặc định của xe. Chỉ gửi `vehicleId` vẫn dùng được các mặc định. Nếu đổi biển số, bỏ `vehicleId`; biển số trùng xe đã đăng ký sẽ tự liên kết lại và kiểm tra trạng thái ACTIVE.

Biển số bắt buộc, chuẩn hóa chữ hoa và bỏ khoảng trắng, 5–30 ký tự chữ/số/dấu chấm/gạch ngang. Số ghế 1–100, tài xế bắt buộc, bus attendant tùy chọn. Có ID nhân sự thì kiểm tra đúng role/ACTIVE và dùng tên tài khoản; chỉ nhập tên trùng duy nhất một tài khoản đúng role thì tự liên kết ID. Tên trùng nhiều tài khoản phải chọn từ gợi ý. Tên nhập mới chưa có tài khoản được lưu với ID null; chuyến đó không xuất hiện trong phân công của một tài khoản nhân sự. Gửi `busAttendant: ""` và `busAttendantId: null` để bỏ attendant mặc định.

Giờ khởi hành phải ở tương lai theo giờ Việt Nam. Slot 1–6. Không trùng khoảng thời gian của xe, tài xế hoặc bus attendant theo ID; khi chưa có ID kiểm tra biển số/tên. Điểm dừng, nhân sự và sức chứa là snapshot tại thời điểm tạo; ghế tự tạo (`1A`, `1B`, ...). Mỗi hàng thường có 4 ghế (2–2, có lối đi); khi sức chứa chia 4 dư 1 và có ít nhất 5 ghế, hàng cuối có 5 ghế liền nhau. Ví dụ 45 ghế = 10 hàng × 4 + hàng cuối `11A`–`11E`; 49 ghế = 11 hàng × 4 + 5. Các sức chứa khác giữ đủ số ghế đã nhập, hàng cuối dùng phần còn lại (1–4). Mã và ID ghế của chuyến đã tạo được giữ nguyên.

`GET /admin/trips` trả mảng Trip (bên dưới). `booked` là số chỗ đang giữ cao nhất trên các chặng của chuyến, không phải tổng lịch sử booking. `PUT /admin/trips/{id}/status`: `{ "status": "SCHEDULED" }` hoặc `PAUSED`. Tạm dừng chặn đặt mới, không tự hủy các chỗ đang giữ. Xe và tài xế vẫn được giữ lịch khi chuyến tạm dừng.

## Tìm chuyến (STUDENT, LECTURER)

`GET /student/routes` và `GET /student/stops?routeId=...` dùng cùng dữ liệu tuyến/điểm dừng của admin.

`GET /student/trips?date=YYYY-MM-DD&routeId=...&boardingStopId=...&alightingStopId=...&slot=...`

Chỉ `date` bắt buộc. Các bộ lọc khác tùy chọn. Điểm đón/trả trong tìm kiếm dùng **stopId của tuyến**. Không có bộ lọc điểm thì dùng hai đầu tuyến. Ngày theo ngày khởi hành đầu tuyến. Chỉ trả chuyến đang mở, đúng thứ tự điểm dừng và còn trước giờ đón; vẫn trả chuyến hết ghế để UI hiển thị trạng thái.

Trip response:

```text
{ tripId, tripCode, routeId, routeName, date, time, arrivalTime, slot,
  vehicle, driver, capacity, booked, status, availableSeats,
  vehicleId, driverId, busAttendantId, busAttendant,
  stops: [{ tripStopId, stopId, name, position, arrivalAt }] }
```

`availableSeats` là số ghế trống đúng chặng đang tìm. `arrivalAt` và timestamp booking dạng `YYYY-MM-DDTHH:mm:ss`, giờ Việt Nam, không hậu tố UTC. `position` bắt đầu từ 0. Kết quả tìm kiếm sắp theo giờ đầu tuyến. `GET /student/trips/{id}/stops` trả mảng `stops` của chuyến.

`GET /student/trips/{id}/seats?boardingTripStopId=...&alightingTripStopId=...` trả `[{ seatId, seatCode, status: "AVAILABLE" | "BOOKED" }]`. Hai query bắt buộc và phải thuộc cùng chuyến, đúng chiều. Ghế trống ở lần đọc có thể được người khác đặt trước khi xác nhận.

## Đặt và hủy chỗ (STUDENT, LECTURER)

`POST /student/bookings`:

```text
{ tripId, boardingTripStopId, alightingTripStopId, seatId, idempotencyKey }
```

Lấy `tripStopId` từ `trip.stops`, không dùng `stopId` thay thế. Lấy `seatId` từ API ghế theo đúng chặng. Key gồm 16–100 ký tự chữ/số/`_`/`-`, giữ nguyên khi thử lại cùng yêu cầu. Mỗi học sinh chỉ có một booking đang hoạt động trên một chuyến. Ghế chỉ bị chiếm trong khoảng `[boardingPosition, alightingPosition)`.

Response data booking và `GET /student/bookings/my`:

```text
{ bookingId, bookingCode, tripId, tripCode, boardingStopId, boardingStop,
  alightingStopId, alightingStop, seatId, seatCode, status,
  reservedAt, checkedInAt, cancelledAt, departureAt,
  boardingTripStopId, alightingTripStopId }
```

`departureAt` là giờ đón tại điểm của học sinh. `checkedInAt`, `cancelledAt` có thể null. Booking mới có trạng thái `RESERVED`. Lịch sử gồm cả `CANCELLED`. `PUT /student/bookings/{id}/cancel` (body `{}` hoặc rỗng) chỉ cho chủ booking, trước giờ đón và chưa check-in; trả booking sau khi hủy. Gọi lại đã hủy vẫn trả thành công.

`PUT /student/profile/preferred-stop`: `{ "stopId": ... }` lưu lựa chọn và trả ID điểm dừng.

## Tài khoản, xe và nhân sự

`POST /admin/users`: `{ username, email, fullName, password, role }`. Admin tạo các role `STUDENT`, `LECTURER`, `DRIVER`, `BUS_ATTENDANT`. Username 3–100 ký tự chữ/số/`_.-`; password ít nhất 8 ký tự, tối đa 72 byte UTF-8. Mật khẩu lưu BCrypt. Username/email được chuẩn hóa chữ thường và kiểm tra trùng. Response không chứa password hoặc hash.

`GET /admin/staff` trả danh sách tài khoản đang hoạt động thuộc DRIVER/BUS_ATTENDANT: `[{ userId, fullName, email, role }]`.

`GET /admin/vehicles` trả `[{ vehicleId, licensePlate, capacity, driverId, driverName, busAttendantId, busAttendantName, status }]`.

`POST /admin/vehicles` và `PUT /admin/vehicles/{id}` dùng body `{ licensePlate, capacity, driverId, busAttendantId: null, status: "ACTIVE" }`. Biển số là tên hiển thị mặc định, chuẩn hóa chữ hoa và bỏ khoảng trắng; phải duy nhất. Số ghế 1–100; driver bắt buộc, attendant tùy chọn; kiểm tra đúng role và tài khoản đang hoạt động. Status nhận `ACTIVE` hoặc `INACTIVE`.

Sửa mặc định của xe chỉ ảnh hưởng chuyến được tạo sau đó; các chuyến cũ giữ snapshot nhân sự và ghế. API không xóa xe/tài khoản đang được tham chiếu. Xe ngừng sử dụng không tạo được chuyến mới, các chuyến cũ không tự tạm dừng.

`GET /staff/trips` yêu cầu DRIVER hoặc BUS_ATTENDANT, chỉ trả các chuyến từ hôm nay có `driverId` hoặc `busAttendantId` của người đang đăng nhập. Payload dùng Trip response. Nhân sự không có quyền tạo chuyến, xem toàn bộ đội xe hoặc tạo đặt chỗ qua API student. LECTURER sử dụng các API `/student/**` để đặt chỗ như STUDENT; web quản trị vẫn chỉ dành cho ADMIN.

## Danh sách email được phép đăng nhập (ADMIN)

- `GET /admin/email-access?page=0&size=20`: `{ content: [{ email, fullName, role, createdAt, userId }], page: { number, size, totalElements, totalPages } }`. `userId: null` nghĩa là email chưa có tài khoản.
- `GET /admin/email-access/template`: tải `fbus-email-template.xlsx` (binary, không có envelope), cần Bearer token. Sheet `Emails` có tiêu đề ở dòng 1: `Email`, `Họ tên`, `Vai trò`; sheet `Hướng dẫn` nêu quy định. File trống, không chứa email ví dụ để tránh nhập nhầm; Vai trò có danh sách chọn.
- `POST /admin/email-access/import`: multipart/form-data, trường `file`; không tự đặt boundary trong client. Nhận `.xlsx` và `.xls`, tối đa 2 MB / 2.000 dòng. Đọc sheet `Emails`, hoặc sheet đầu nếu không có tên này. Header bắt buộc ở dòng 1; hỗ trợ tên cột `Email`, `Họ tên`/`fullName`, `Vai trò`/`role`. Không nhận công thức, ô không phải văn bản, cột lạ hoặc dữ liệu ngoài cột khai báo. Giữ cơ chế bảo vệ ZIP và giới hạn kích thước entry giải nén 32 MB của POI.

Email bắt buộc, tối đa 254 ký tự, chuẩn hóa chữ thường và bỏ khoảng trắng hai đầu. Họ tên tùy chọn, tối đa 150 ký tự. Vai trò tùy chọn, mặc định `STUDENT`; nhận `STUDENT`, `LECTURER`, `DRIVER`, `BUS_ATTENDANT`, không cấp ADMIN qua Excel.

Response envelope data: `{ totalRows, imported, skipped, failed, errors: [{ row, email, message }] }`. `row` là số dòng Excel (bắt đầu 2); bỏ qua dòng trống. Các dòng hợp lệ được thêm, dòng lỗi trả trong `errors`, kể cả khi không thêm được dòng nào. Email trùng (trong tệp hoặc database) cùng vai trò bị bỏ qua; khác vai trò bị báo lỗi. Không cập nhật tên/vai trò/trạng thái của tài khoản hoặc email đã có. Import nối thêm, không xóa email vắng mặt trong tệp. Gửi lại sau lỗi mạng không tạo email trùng.

Thao tác lưu được khóa chung với tạo tài khoản; nếu lỗi database, rollback toàn bộ các dòng thêm mới của lần nhập. Email mới lưu `imported_by` (admin hiện tại), `created_at`. Danh sách chỉ dành cho ADMIN. Nhân sự có tên trong Excel chưa tự trở thành tài khoản đăng nhập hoặc nhân sự phân công được.


## Wishlist theo ngày/slot (STUDENT, LECTURER)

- POST /student/wishlists: { routeId, boardingStopId, alightingStopId, date: "YYYY-MM-DD", slot: 1 }.
- GET /student/wishlists: danh sách của chính người đăng nhập.
- PUT /student/wishlists/{id}/cancel: rút wishlist của chính mình, body {}.

Wishlist là nhu cầu chờ cho một tuyến/chặng, ngày khởi hành đầu tuyến và slot 1–6; không giữ ghế. Cho đăng ký khi chưa có chuyến phù hợp hoặc TẤT CẢ chuyến phù hợp trong slot hết ghế cho chặng đó. Nếu còn ghế hoặc đã có đặt chỗ trên một chuyến trong tuyến/ngày/slot thì trả 409. Không có kết quả tìm kiếm vẫn được chọn slot và thêm/rút wishlist ngay tại trang tìm chuyến. Không đăng ký ngày quá khứ. Mỗi user chỉ có một wishlist trên cùng tuyến/ngày/slot; gửi lại cùng chặng trả bản ghi cũ, khác chặng phải rút trước. Rút rồi đăng ký lại vẫn kiểm tra điều kiện và dùng lại bản ghi. Không tự đặt chỗ khi admin bổ sung xe.

Payload danh sách: [{ wishlistId, routeId, routeName, boardingStopId, boardingStop, alightingStopId, alightingStop, date, slot, status, createdAt }]. Status ACTIVE / CANCELLED / FULFILLED. Đặt chỗ thành công cùng user/tuyến/ngày/slot/chặng tự chuyển ACTIVE thành FULFILLED. Hủy booking không tự đăng ký lại wishlist. Wishlist ACTIVE của ngày cũ vẫn được giữ trong lịch sử; app hiển thị đã qua ngày.

## Số liệu nhu cầu (ADMIN)

- GET /admin/demand?from=YYYY-MM-DD&to=YYYY-MM-DD: tối đa 93 ngày, trả [{ date, slot, tripCount, passengerCount, bookingCount, wishlistCount }], đủ 6 slot/ngày (kể cả số 0).
- GET /admin/wishlists?date=YYYY-MM-DD&slot=1: wishlist ACTIVE của ngày/slot; thêm userId, fullName và email vào payload wishlist.

tripCount gồm chuyến đang mở và tạm dừng. passengerCount là số user KHÁC NHAU có booking RESERVED/CHECKED_IN trong ngày/slot đó, trên toàn bộ tuyến. bookingCount là số booking tương ứng; không dùng mức chiếm chỗ cao nhất để thay số người. wishlistCount là số yêu cầu ACTIVE (cùng người chờ hai tuyến tính hai yêu cầu); không tính CANCELLED/FULFILLED. Ngày lấy theo departure_at đầu tuyến. Lịch tuần hiển thị số liệu toàn bộ tuyến; ô tìm kiếm chỉ lọc danh sách chuyến trong chi tiết. Tải lại để cập nhật từ mobile, chưa có realtime/push.

## Đặt lịch dài hạn (LECTURER)

Các endpoint /lecturer/** chỉ cho LECTURER, được kiểm tra ở security và service; STUDENT không được dùng dù gửi role giả ở client.

- POST /lecturer/booking-plans/preview: { routeId, boardingStopId, alightingStopId, startDate, endDate, weekdays: [1,2,3,4,5], slot: 1 }. ISO weekday: T2=1,...,CN=7. Khoảng ngày bao gồm hai đầu, tối đa 90 ngày; bắt đầu từ hôm nay theo giờ Việt Nam. weekdays phải có ít nhất một thứ, không trùng.
- Preview data: { days: [{ date, status, tripId, tripCode, time, availableSeats }], totalDates, availableCount }. Không giữ ghế trong bước này. AVAILABLE / NO_TRIP / FULL / ALREADY_BOOKED / UNAVAILABLE. time là giờ đón tại điểm giảng viên.
- POST /lecturer/booking-plans: cùng body, thêm idempotencyKey 16–100 ký tự [A-Za-z0-9_-]. Giữ nguyên key khi thử lại cùng payload sau lỗi mạng; đổi payload với key cũ trả 409.
- GET /lecturer/booking-plans: danh sách lịch của mình, bao gồm các ngày và booking thực tế.
- PUT /lecturer/booking-plans/{id}/retry: body {}; chỉ đặt các ngày còn thiếu, chưa qua, chưa bị hủy. Giảng viên chủ động dùng sau khi admin thêm chuyến/ghế.
- PUT /lecturer/booking-plans/{id}/cancel: body {}; hủy các booking RESERVED thuộc lịch còn trước giờ đón, trả ghế, và hủy các ngày chưa có booking. Giữ lịch sử booking đã đi/check-in; không hủy booking được tạo ngoài lịch. Lịch đã hủy không được đặt bổ sung.

Create data / một phần tử GET: { planId, routeId, routeName, boardingStop, alightingStop, slot, startDate, endDate, weekdays, status: ACTIVE|CANCELLED, days: [{ date, status, bookingId, tripId, tripCode, time, seatCode }] }.

Chỉ đặt trên chuyến admin đã tạo. Mỗi ngày lấy chuyến phù hợp sớm nhất còn ghế trong đúng tuyến/slot/chặng và tự chọn một ghế AVAILABLE; các ngày có thể khác số ghế. Ngày đã có đặt chỗ trên chuyến phù hợp ghi ALREADY_BOOKED, không giữ thêm hoặc hủy booking đó. Ngày chưa có chuyến ghi NO_TRIP, hết ghế FULL; chuyến tạm dừng/đã qua giờ đón ghi UNAVAILABLE. Các ngày đó được lưu để đặt bổ sung, không tự tạo chuyến/ghế và không có tác vụ nền tự đặt chỗ sau này. Khi xác nhận, giảng viên có thể chọn wishlistMissingTrips: true để thêm wishlist cho các ngày NO_TRIP. Kết quả ngày có booking lấy trạng thái thật RESERVED/CHECKED_IN/CANCELLED; PAST chỉ dùng cho ngày chưa đặt đã qua khi xử lý lại.

Một transaction giữ ghế cho các ngày đặt được và lưu trạng thái ngày còn thiếu. Lỗi database rollback cả yêu cầu, không trả thành công một phần do lỗi kỹ thuật. Khóa user trước, sau đó các trip theo ID tăng dần; dùng BookingService hiện có để kiểm tra chặng/ghế và chống đặt trùng. Booking của lịch cũng xuất hiện ở Chỗ của tôi và tính vào thống kê admin. Hủy riêng booking trong Chỗ của tôi không bị retry của lịch tự đặt lại.

## Lỗi

`400`: dữ liệu không hợp lệ, điểm sai chuyến/chiều, ngày quá khứ, Excel không đọc được. `401`: chưa đăng nhập/token hết hạn/session thu hồi. `403`: sai role hoặc email chưa được cấp quyền khi login/refresh. `404`: không có tài nguyên hoặc không phải chủ booking. `409`: ghế vừa được đặt, đã có booking, key bị dùng với payload khác, chuyến không nhận đặt chỗ, lịch xe/tài xế bị trùng hoặc mã bị trùng. `413`: tệp vượt giới hạn upload.

## Khác với API Render cũ

Backend mới trả HTTP 200 cho thao tác thành công (client nhận cả 200/201); search dùng `boardingStopId`/`alightingStopId`, không dùng trip-stop ID để lọc tuyến. API ghế, tạo/hủy booking và tạo tuyến/chuyến đã có hợp đồng rõ ở trên. Các file hợp đồng Render cũ trong mobile được giữ như tài liệu lịch sử.

## Tùy chọn wishlist cho giảng viên

POST /lecturer/booking-plans nhận thêm wishlistMissingTrips (boolean, mặc định false). Mobile hỏi rõ ở bước xem trước khi có ngày NO_TRIP; không tự bật tùy chọn. Khi đồng ý, tạo lịch/booking và wishlist của các ngày NO_TRIP trong cùng transaction. Dựa vào trạng thái thật lúc xác nhận, không tin danh sách ngày từ client. Không thêm wishlist cho ngày có chỗ, FULL hoặc UNAVAILABLE trong luồng này. Các trường hợp FULL vẫn có nút thêm wishlist khi tìm chuyến.

Mỗi phần tử days trong lịch trả thêm wishlistId, wishlistStatus, wishlistNote; metadata lịch có wishlistMissingTrips và boardingStopId/alightingStopId. Nếu cùng wishlist đã tồn tại, dùng lại mà không trùng số đếm. Nếu đã có wishlist khác chặng, hoặc slot vừa có ghế/đặt chỗ, ghi wishlistNote thay vì ghi thành công giả hay làm mất các booking hợp lệ. Lỗi database vẫn rollback transaction. Đổi lựa chọn wishlist với cùng idempotencyKey bị từ chối; client đổi key khi thay lựa chọn. Retry lịch chỉ đặt bổ sung ngày thiếu, không tự đăng ký lại wishlist đã rút. Hủy lịch chỉ rút wishlist ACTIVE được lần tạo lịch đó thêm mới/kích hoạt lại; wishlist đã tồn tại trước đó giữ nguyên.

## Lịch lặp tạo chuyến (ADMIN)

- POST /admin/trips/recurring: cùng thông tin tuyến/xe/nhân sự của tạo chuyến; thay dates bằng startDate, intervalWeeks (1–52), weekdays (ISO T2=1,...,CN=7), endType: NEVER | UNTIL | COUNT, untilDate cho UNTIL, occurrences cho COUNT (1–1000), idempotencyKey (16–100 ký tự chữ/số/_/-). Ngày bắt đầu từ hôm nay; các ngày đã qua giờ khởi hành không được tạo bù.
- Response data: { rule, trips }. trips là tối đa 90 ngày chuyến đã tạo trước. Gửi lại cùng key/payload trả quy tắc cũ, không tạo trùng; đổi payload với key cũ trả 409.
- GET /admin/trips/recurring: danh sách { ruleId, routeName, vehicle, slot, time, startDate, intervalWeeks, weekdays, endType, untilDate, occurrences, generatedCount, generatedThrough, status, lastError }.
- PUT /admin/trips/recurring/{id}/status: { status: ACTIVE | PAUSED }. PAUSED dừng tạo mới, không tạm dừng hoặc hủy các chuyến đã tạo. ACTIVE tiếp tục tạo phần sắp tới còn thiếu. Lịch COMPLETED đã tạo đủ số lần/đến ngày cuối không được tiếp tục.

Tuần được neo vào thứ hai của tuần chứa startDate; mỗi intervalWeeks tuần chỉ lấy các thứ đã chọn, không lấy ngày trước startDate. Số lần xuất hiện là số ngày chạy, không phải số tuần. Trước mắt tạo trong 90 ngày từ hôm nay (hoặc từ startDate nếu bắt đầu ở tương lai). Tác vụ Spring chạy sau khởi động 1 phút và mỗi giờ để duy trì phạm vi 90 ngày phía trước. NEVER lưu quy tắc lâu dài đến khi tạm dừng; không giả lập vô hạn bằng một batch hữu hạn. Tác vụ chỉ chạy khi backend đang chạy; sau thời gian dừng không tạo chuyến quá khứ bù lại.

Khóa chung trip-schedule chống xung đột với tạo chuyến/xe khác. Tạo ban đầu lỗi thì rollback cả quy tắc và các chuyến của lần tạo; khi mở rộng về sau lỗi trùng xe/nhân sự hoặc xe/tài khoản không còn hợp lệ thì rollback phần mở rộng, giữ các chuyến cũ, PAUSED quy tắc và ghi lastError để admin xử lý rồi tiếp tục. Tất cả chuyến dùng snapshot xe/số ghế/nhân sự của quy tắc, vẫn kiểm tra xe/nhân sự ACTIVE khi phát sinh chuyến mới.

Migration V6 bổ sung trip_recurrence, trip_recurrence_day và liên kết wishlist với ngày của booking_plan; không sửa checksum migration cũ.


## Vận hành chuyến (DRIVER / BUS_ATTENDANT)

Migration V7 bổ sung tiến trình trên trip, mốc xuống xe/lý do hủy trên booking, nhật ký crew_event và trip_report. Không thay bookingId/seatId hoặc xóa dữ liệu cũ. Các API yêu cầu nhân sự được phân công bằng driver_id/bus_attendant_id, không xác thực dựa trên tên nhập tự do.

- GET /staff/schedule?from=YYYY-MM-DD&to=YYYY-MM-DD: mặc định hôm nay tới 30 ngày sau; tối đa 93 ngày. Mỗi phần tử {trip,operation,canDrive,canManagePassengers}. Có thể chọn khoảng quá khứ để xem lịch sử.
- GET /staff/trips/{id}: {trip,operation,canDrive,canManagePassengers,passengers}. operation: {status: READY|IN_PROGRESS|COMPLETED,currentStopPosition,startedAt,completedAt}. Chưa bắt đầu có position=-1. passengers gồm bookingId/code, họ tên/email, mã ghế, điểm đón/trả và vị trí, trạng thái RESERVED|CHECKED_IN|ALIGHTED|NO_SHOW|CANCELLED, checkedInAt,alightedAt.
- PUT /staff/trips/{id}/progress: {action:START|ARRIVE|COMPLETE,expectedPosition}. Chỉ tài xế. START chỉ trong ngày khởi hành, từ 30 phút trước giờ chạy, ghi đang ở điểm đầu. ARRIVE đến điểm tiếp theo theo đúng thứ tự, sớm nhất 15 phút trước giờ dự kiến. Phải xử lý hết hành khách cần đón/trả ở điểm hiện tại trước khi chuyển điểm. COMPLETE chỉ ở điểm cuối và không còn hành khách chờ đón/đang trên xe. Gửi lại cùng bước không chuyển thêm lần nữa; tiến trình cũ trả 409.
- PUT /staff/trips/{id}/passengers/{bookingId}: {action:CHECK_IN|CHECK_OUT|NO_SHOW}. Chỉ phụ xe được phân công; tài xế có thể thay nếu chuyến không có phụ xe gắn tài khoản. Chuyến phải IN_PROGRESS và không PAUSED. CHECK_IN/NO_SHOW chỉ ở đúng điểm đón, CHECK_OUT chỉ ở đúng điểm trả và đã CHECKED_IN. NO_SHOW chỉ từ giờ đón dự kiến, chuyển booking thành CANCELLED với cancellationReason=NO_SHOW để trả ghế. Không tự chuyển ghế cho wishlist. Các thao tác khóa trip để đồng bộ với đặt/hủy ghế và lưu actor/action/time vào crew_event.

GET /student/bookings/my trả thêm cancellationReason (NO_SHOW hoặc null) và alightedAt; mobile hiển thị Vắng mặt/Đã xuống xe tương ứng. Chuyến COMPLETED và điểm đón đã đi qua không nhận đặt chỗ mới, kể cả tìm chuyến/lịch dài hạn. Admin không đổi SCHEDULED/PAUSED sau khi chuyến đã bắt đầu.

## Báo cáo chuyến

- GET /staff/trips/{id}/reports: báo cáo do chính nhân sự gửi trong chuyến được phân công.
- POST /staff/trips/{id}/reports: {category:VEHICLE|CAPACITY|DELAY|PASSENGER|OTHER,priority:LOW|NORMAL|URGENT,location:string(max200),description:string(bắt buộc,max1000),idempotencyKey:string(16–100)}. Lưu thật; gửi lại key/payload trả cùng báo cáo, đổi payload cùng key trả 409.
- GET /admin/reports?status=OPEN|IN_REVIEW|RESOLVED&page=0&size=20: có thể bỏ status để xem tất cả, size tối đa 100. Trả mảng trang hiện tại, sắp xếp mới nhất trước.
- PUT /admin/reports/{id}: {status:OPEN|IN_REVIEW|RESOLVED,resolution:string(max500)}. Khi RESOLVED bắt buộc kết quả xử lý. Lưu người xử lý và thời gian cập nhật; nhân sự xem được kết quả trong báo cáo đã gửi.

Report trả {reportId,tripId,tripCode,routeName,vehicle,reporter,reporterRole,category,priority,location,description,status,createdAt,resolution,resolvedBy,updatedAt}. Chưa triển khai gửi thông báo đẩy, GPS tự chuyển điểm, quét QR hoặc tự động nhận khách từ wishlist. Dữ liệu tải khi mở trang, kéo làm mới/bấm Tải lại hoặc trở lại app; không cập nhật thời gian thực.
