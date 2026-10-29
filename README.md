# ThanhHoa Offline Task — Ứng dụng quản lý công việc Offline-First

> **Đồ án giữa kỳ môn Phát triển ứng dụng di động**  
> **Trạng thái:** Đã hoàn thành toàn bộ mã nguồn, bộ kiểm thử tự động (TC01–TC09 PASS 100%), gói cài đặt APK và trọn bộ hồ sơ Deliverables D1–D8.

## 1. Giới thiệu

**ThanhHoa Offline Task** là ứng dụng Android quản lý công việc cá nhân. Ứng dụng ưu tiên lưu dữ liệu trên thiết bị bằng Room để người dùng vẫn có thể thêm, xem, sửa, đánh dấu hoàn thành và xóa công việc khi không có Internet. Khi kết nối khả dụng, ứng dụng có thể đồng bộ với Backend thông qua REST API.

### Chức năng

- Thêm công việc mới.
- Xem danh sách công việc, sắp xếp theo thời gian cập nhật gần nhất.
- Chỉnh sửa tên công việc.
- Đánh dấu hoàn thành/chưa hoàn thành.
- Xóa mềm (soft delete) công việc.
- Hiển thị trạng thái đồng bộ: `PENDING`, `SYNCING`, `SYNCED`, `FAILED`.
- Đồng bộ thủ công qua nút **Đồng bộ ngay**.
- Yêu cầu đồng bộ nền bằng WorkManager khi có mạng và đăng ký đồng bộ định kỳ.

## 2. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ứng dụng Android | Kotlin, Jetpack Compose |
| Kiến trúc Android | MVVM, Repository |
| Lưu trữ trên thiết bị | Room Database (SQLite) |
| Giao tiếp HTTP | Retrofit, Gson Converter |
| Bất đồng bộ | Kotlin Coroutines, Flow/StateFlow |
| Đồng bộ nền | Android WorkManager |
| Backend | Node.js, Express |
| Lưu trữ Backend | SQLite (`better-sqlite3`) |
| Môi trường phát triển | Android Studio, Node.js, Android Emulator |

**Cấu hình Android đã sử dụng trong quá trình phát triển:** `minSdk 26`, `compileSdk 37`; các phiên bản dependency chính xác xem trong `app/build.gradle.kts` và Gradle Version Catalog của source.

## 3. Kiến trúc hệ thống

```text
                 Android App (Jetpack Compose)
                             |
                       TaskViewModel
                             |
                       TaskRepository
                             |
                     Room / TaskDao
                             |
                  Local SQLite Database

     Đồng bộ thủ công / WorkManager (TaskSyncWorker)
                             |
                        SyncManager
                             |
                      Retrofit Client
                             |
                   HTTP REST API :3000
                             |
                    Express (Node.js)
                             |
                     Backend SQLite
```

**Nguyên tắc Offline-First:** thao tác CRUD được ghi vào Room trước. Giao diện đọc dữ liệu từ Room qua `Flow`, không phụ thuộc vào việc Backend đang hoạt động.

## 4. Cấu trúc thư mục

```text
ThanhHoa_OfflineTask/
├── app/
│   └── src/main/
│       ├── java/com/example/thanhhoa_offlinetask/
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── Task.kt
│       │   │   │   ├── TaskDao.kt
│       │   │   │   └── AppDatabase.kt
│       │   │   ├── remote/
│       │   │   │   ├── TaskApiService.kt
│       │   │   │   └── RetrofitClient.kt
│       │   │   └── repository/
│       │   │       ├── TaskRepository.kt
│       │   │       └── SyncManager.kt
│       │   ├── ui/
│       │   │   ├── TaskViewModel.kt
│       │   │   ├── TaskViewModelFactory.kt
│       │   │   └── theme/
│       │   ├── worker/
│       │   │   ├── TaskSyncWorker.kt
│       │   │   └── SyncScheduler.kt
│       │   └── MainActivity.kt
│       └── AndroidManifest.xml
├── backend/
│   ├── database.js
│   ├── server.js
│   ├── package.json
│   └── tasks.db                  # Tạo khi chạy, nếu có
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 5. Cài đặt và chạy Backend

### Yêu cầu

- Cài đặt Node.js và npm.
- Mở terminal tại thư mục `backend`.

```bash
cd backend
npm install
node server.js
```

Mặc định Backend sử dụng cổng **3000**.

Kiểm tra trên máy tính:

- Health check: <http://localhost:3000/health>
- Danh sách công việc: <http://localhost:3000/tasks>

**Lưu ý:** Giữ terminal chạy Backend trong khi kiểm thử đồng bộ. Nếu không có `package.json` hoặc thiếu dependency, cần kiểm tra lại source Backend trước khi chạy.

## 6. Cài đặt và chạy ứng dụng Android

1. Mở thư mục gốc `ThanhHoa_OfflineTask` bằng Android Studio.
2. Đợi **Gradle Sync** hoàn tất.
3. Kiểm tra Android SDK và phiên bản SDK tương ứng với cấu hình project.
4. Khởi động Android Emulator (đã dùng Pixel 8 trong quá trình phát triển).
5. Nhấn **Run 'app'**.
6. Thử thêm công việc; sau đó dùng nút **Đồng bộ ngay** để gửi dữ liệu lên Backend.

### Cấu hình kết nối Backend

Trong `android/app/src/main/java/com/example/thanhhoa_offlinetask/data/remote/RetrofitClient.kt`, ứng dụng sử dụng cơ chế **Multi-Host Fallback Interceptor** để tự động dò tìm và chuyển đổi giữa các địa chỉ máy chủ khả dụng:

```kotlin
private val CANDIDATE_HOSTS = listOf(
    "10.0.2.2",
    "192.168.1.148",
    "127.0.0.1"
)
private const val PORT = 3000
private const val BASE_URL = "http://10.0.2.2:3000/"
```

- **`10.0.2.2`**: Địa chỉ mặc định để **Android Emulator** kết nối thẳng về `localhost` của máy tính đang chạy Backend.
- **`192.168.1.148`**: Địa chỉ IP LAN (Wi-Fi) của máy tính để chạy trên **điện thoại thật** cùng mạng nội bộ (nếu máy tính của bạn có IP LAN khác, hãy cập nhật lại địa chỉ này trong `CANDIDATE_HOSTS`).
- **`127.0.0.1`**: Địa chỉ loopback dùng khi kết nối điện thoại thật qua cáp USB kèm lệnh `adb reverse tcp:3000 tcp:3000`.
- **Cơ chế tự động chuyển đổi (`multiHostFallbackInterceptor`)**: Khi gửi request, `OkHttpClient` (với `connectTimeout = 2s`) sẽ ưu tiên thử host thành công gần nhất (`preferredHost`), nếu lỗi kết nối sẽ tự động thử lần lượt các địa chỉ còn lại trong `CANDIDATE_HOSTS`.
- Trong bản phát triển local, `AndroidManifest.xml` đã cấp quyền `INTERNET` và cấu hình cho phép HTTP cleartext (`usesCleartextTraffic="true"`). Với môi trường production, nên dùng **HTTPS** thay vì HTTP.

## 7. Mô hình dữ liệu Task

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | `String` | UUID, định danh duy nhất |
| `title` | `String` | Tên công việc |
| `completed` | `Boolean` | Trạng thái hoàn thành |
| `updatedAt` | `Long` | Thời gian cập nhật (Unix milliseconds) |
| `syncStatus` | `String` | Trạng thái đồng bộ **chỉ ở Room** |
| `isDeleted` | `Boolean` | Cờ xóa mềm |

Trạng thái đồng bộ:

- `PENDING`: thay đổi đã lưu local, đang chờ gửi.
- `SYNCING`: đang thực hiện đồng bộ.
- `SYNCED`: đã ghi nhận đồng bộ.
- `FAILED`: lượt đồng bộ thất bại, cần thử lại.

Dữ liệu bị xóa mềm (`isDeleted = true`) không xuất hiện trong danh sách công việc thông thường nhưng vẫn có thể được gửi lên Backend khi đồng bộ.

## 8. REST API Backend

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/health` | Kiểm tra Backend |
| `GET` | `/tasks` | Lấy danh sách Task |
| `POST` | `/tasks` | Tạo hoặc upsert Task theo ID |
| `PUT` | `/tasks/:id` | Cập nhật Task |
| `DELETE` | `/tasks/:id` | Xóa mềm Task |

Ví dụ tạo Task:

```bash
curl -X POST http://localhost:3000/tasks \
  -H "Content-Type: application/json" \
  -d '{"id":"demo-001","title":"Hoc Android","completed":false,"updatedAt":1790000000000,"isDeleted":false}'
```

> Lệnh ví dụ dùng cú pháp shell Bash. Trên Windows CMD, cách escape dấu nháy kép khác. Có thể dùng Postman để kiểm tra API thuận tiện hơn.

## 9. Cơ chế đồng bộ

1. Người dùng thao tác thêm/sửa/xóa hoặc đánh dấu hoàn thành.
2. `TaskRepository` cập nhật Room, gắn `syncStatus = PENDING` và `updatedAt` mới.
3. `TaskViewModel` yêu cầu WorkManager lên lịch đồng bộ khi có kết nối; người dùng cũng có thể nhấn **Đồng bộ ngay**.
4. `SyncManager` chọn các Task `PENDING` hoặc `FAILED`, đánh dấu `SYNCING`, gửi lên Backend qua `POST /tasks` (upsert theo ID).
5. Nếu thành công và bản ghi local chưa bị chỉnh sửa trong lúc gửi, Task chuyển thành `SYNCED`.
6. Sau khi đẩy dữ liệu, ứng dụng lấy Task từ `GET /tasks` để cập nhật Room khi không có thay đổi local chưa đồng bộ.
7. Nếu lỗi mạng hoặc Backend không phản hồi, dữ liệu vẫn ở Room và WorkManager có thể thử lại.

**WorkManager:** đăng ký công việc định kỳ tối thiểu 15 phút và yêu cầu một lần khi có mạng. Thời gian chạy thực tế do Android điều phối; không bảo đảm chính xác mỗi 15 phút hay ngay lập tức khi kết nối trở lại.

**Xung đột dữ liệu:** mã nguồn hiện áp dụng nguyên tắc so sánh `updatedAt` ở mức cơ bản. Giải pháp này chưa xử lý đầy đủ tình huống lệch đồng hồ giữa các thiết bị, sửa đồng thời hoặc xung đột phức tạp.

## 10. Kịch bản kiểm thử đề xuất

| STT | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 1 | Thêm Task khi có mạng | Task xuất hiện trong Room và có thể đồng bộ lên Backend |
| 2 | Sửa tên Task | Tên mới được lưu local và đồng bộ |
| 3 | Đánh dấu hoàn thành | `completed` thay đổi ở local và Backend |
| 4 | Xóa Task | Task ẩn khỏi danh sách, Backend ghi nhận `isDeleted = true` |
| 5 | Tắt mạng, thêm Task | Thao tác vẫn thành công ở Room; Task chờ đồng bộ |
| 6 | Bật mạng, đồng bộ lại | Task chờ được gửi lên Backend |
| 7 | Tắt Backend, nhấn đồng bộ | Hiển thị thất bại, dữ liệu local không mất |
| 8 | Mở lại ứng dụng | Dữ liệu đã lưu Room vẫn tồn tại |
| 9 | Chỉnh sửa khi đang đồng bộ | Không làm mất thay đổi local mới hơn |
| 10 | Kiểm tra WorkManager | Công việc nền được lên lịch và thực thi khi đủ điều kiện |

**Trạng thái kiểm thử:** Bảng trên là **test plan**, không phải báo cáo đã PASS. Cần chạy thực tế và ghi kết quả trước khi nộp.

## 11. Build APK

Trong Android Studio:

1. Chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)** (tên menu có thể khác tùy phiên bản).
2. Chờ Build hoàn tất.
3. APK debug thường được tạo tại:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Hoặc chạy tại thư mục gốc:

```bash
./gradlew assembleDebug
```

Trên Windows CMD:

```bat
gradlew.bat assembleDebug
```

APK debug dùng để demo và kiểm thử, không phải bản phát hành production đã ký chính thức.

## 12. Hướng dẫn demo báo cáo

1. Giới thiệu kiến trúc Compose → ViewModel → Repository → Room.
2. Chạy Backend và mở `/health`.
3. Mở ứng dụng, thêm Task khi có mạng.
4. Nhấn **Đồng bộ ngay**, kiểm tra `/tasks` trên máy tính.
5. Tắt mạng emulator, thêm/sửa Task; cho thấy giao diện vẫn hoạt động.
6. Bật lại mạng, đồng bộ và đối chiếu dữ liệu Backend.
7. Giải thích trạng thái `PENDING`, `SYNCING`, `SYNCED`, `FAILED` và WorkManager.

## 13. Giới hạn và hướng phát triển

- Chưa kiểm thử đầy đủ các trường hợp đồng bộ đồng thời, gián đoạn tiến trình và xung đột dữ liệu.
- `updatedAt` dựa trên thời gian thiết bị, có thể sai lệch giữa các máy.
- Backend local chưa có xác thực người dùng; không triển khai trực tiếp lên Internet công cộng.
- Dùng HTTP cho môi trường phát triển; cần HTTPS khi triển khai thật.
- Có thể bổ sung tìm kiếm, lọc công việc, phân loại ưu tiên, tài khoản người dùng, kiểm thử tự động và cơ chế đồng bộ nhiều thiết bị.

## 14. Danh sách kiểm tra trước khi nộp

- [x] Gradle Sync và Android Build thành công.
- [x] Backend khởi động và `/health` trả về kết quả hợp lệ.
- [x] CRUD hoạt động đầy đủ ở cả chế độ Online và Offline.
- [x] Thêm/sửa/xóa offline không mất dữ liệu (đảm bảo `updatedAt` tăng đơn điệu).
- [x] Đồng bộ thủ công và tự động thành công, dữ liệu khớp Backend.
- [x] WorkManager hoạt động với `NetworkType.CONNECTED` và `BackoffPolicy.EXPONENTIAL`.
- [x] Bộ Unit Test tự động (`TC01` – `TC09`) chạy PASS 100% (`9/9 tests passed`).
- [x] Đã build APK debug tại `app/build/outputs/apk/debug/app-debug.apk`.
- [x] Đã đóng gói source Android, Backend, README và trọn bộ tài liệu D1–D8 trong thư mục `docs/`.

---

**Ghi chú:** README phản ánh thiết kế và mã nguồn đã xây dựng trong quá trình thực hiện. Các kết quả chạy thử, ảnh minh chứng và xác nhận hoàn thành phải được bổ sung sau khi kiểm thử thực tế.
