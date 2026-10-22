# M1 — Lê Tấn Thành: bộ khung và hợp đồng dữ liệu

## Phần này làm gì?

M0 lưu JSON mẫu. M1 biến các file đó thành một nguồn dữ liệu Java mà các bạn làm giao
diện gọi được. `CovidApp` khởi tạo một `CovidRepository` và một Volley `RequestQueue`
dùng chung. `FakeRepository` đọc JSON mẫu; nó chưa gửi request đến disease.sh.

Ứng dụng vẫn mở màn hình khung. Màn Toàn cầu, danh sách và chi tiết sẽ do người sở
hữu giao diện xây dựng bằng hợp đồng bên dưới.

## Dữ liệu đi vào và đi ra thế nào?

```text
Activity / Fragment gọi một hàm của CovidRepository
  → FakeRepository đưa công việc vào worker "covid-samples"
  → worker đọc res/raw và gọi JsonParser
  → parser tạo GlobalStats, List<CountryStats>, CountryStats hoặc HistoricalData
  → Handler đưa onSuccess/onError về main thread
  → màn hình dùng model để hiển thị
```

Màn hình chỉ gọi Repository. Nó không mở file, parse JSON hoặc gọi mạng trực tiếp.

## Vì sao chọn cách này?

- JSON mẫu giúp người làm giao diện tiến hành ngay mà không chờ mạng thật.
- Interface giữ nguyên cách gọi khi chuyển sang Repository thật ở M2/M3.
- Worker giúp đọc file và parse 231 nước không chặn giao diện.
- Callback trên main thread giúp người gọi cập nhật View đúng luồng.
- Model bất biến và danh sách/map chỉ đọc giúp các màn hình không sửa dữ liệu của nhau.
- Số đếm dùng `long` vì tests và population có thể vượt giới hạn `int`.

## Hợp đồng nhóm dùng

```java
interface Callback<T> {
    void onSuccess(T data, boolean fromCache);
    void onError(String message);
}

interface CovidRepository {
    int ALL_DAYS = -1;
    void getGlobal(boolean forceRefresh, Callback<GlobalStats> cb);
    void getCountries(boolean forceRefresh, Callback<List<CountryStats>> cb);
    void getCountry(String iso2, Callback<CountryStats> cb);
    void getHistory(String iso2OrAll, int days, Callback<HistoricalData> cb);
}
```

`CovidRepository` là **interface** theo bản phân công. Chữ `class` trong đoạn mã giả
của đặc tả/README không có thân hàm; không thể dán nguyên thành Java chạy được.
Các tên hàm, kiểu trả qua callback và tham số đã được giữ nguyên.

Lấy Repository trong Activity:

```java
CovidRepository repository = ((CovidApp) getApplication()).getRepository();
repository.getGlobal(false, new Callback<GlobalStats>() {
    @Override public void onSuccess(GlobalStats data, boolean fromCache) {
        // Đã ở main thread: định dạng data.cases rồi gán vào TextView.
    }
    @Override public void onError(String message) {
        // Hiển thị message và nút Thử lại.
    }
});
```

Trong Fragment, lấy Application từ `requireActivity().getApplication()`. Chỉ cập nhật
View khi view của Fragment vẫn còn. Các ví dụ này là hướng dẫn tích hợp; chưa sửa
màn hình do Sỹ Chúc hoặc Hải Anh sở hữu.

## Các trường công khai của model

- `GlobalStats`: `cases`, `deaths`, `recovered`, `active`, `critical`, `tests`,
  `population`, `affectedCountries`, `updated` (đều `long`).
- `CountryStats`: `country`, `iso2`, `iso3`, `flag`, `continent` (String); `cases`,
  `deaths`, `recovered`, `active`, `critical`, `tests`, `population`, `updated` (`long`);
  `casesPerOneMillion`, `deathsPerOneMillion` (`double`).
- `HistoricalData`: `iso2OrAll`, `country`, `dates` (`List<String>`), `cases`,
  `deaths`, `recovered` (`Map<String, Long>`), `lastDataDate`.

`dates` và các map được sắp theo ngày thực, không sắp từ điển. Khóa vẫn giữ dạng API
`M/D/YY`. Các số lịch sử là **tổng tích lũy**, không phải ca mới trong ngày.

## Quy ước quan trọng

- `getCountry("VN", cb)` dùng ISO2. Chấp nhận chữ thường và bỏ khoảng trắng đầu/cuối.
  Danh sách vẫn giữ các dòng có ISO2 thiếu; UI cần kiểm tra mã rỗng trước khi mở chi tiết.
- `getHistory("all", CovidRepository.ALL_DAYS, cb)` đọc toàn bộ lịch sử toàn cầu.
- `getHistory("VN", 30, cb)` lấy 30 ngày lịch tính ngược từ ngày dữ liệu cuối cùng,
  không tính từ ngày hiện tại năm 2026. Tương tự cho 90 và 365; mặc định UI dùng ALL_DAYS.
- Chỉ có mẫu lịch sử toàn cầu và VN. Nước khác trả `onError` có thông báo; không gán
  lịch sử VN cho nước khác. Các nước khác vẫn đọc được chỉ số từ mẫu danh sách.
- `forceRefresh` vẫn có trong hợp đồng; bản Fake đọc lại cùng bộ mẫu khi nhận tham số này.
- `fromCache=false` ở Fake: dữ liệu đóng gói trong APK không phải cache runtime.
  Khi làm Repository thật, `true` dành cho dữ liệu lấy từ CacheManager. Không dùng cờ
  này để suy ra rằng mẫu M1 là dữ liệu mới trên Internet.
- `updated` chỉ dùng cho nhãn "Tải lúc". Ngày dữ liệu lấy từ `HistoricalData.lastDataDate`.
- Trường tổng hợp thiếu/null dùng 0; theo đặc tả, UI hiển thị dấu gạch cùng chú thích
  khi nguồn không báo cáo. Điều này không xác nhận giá trị thật bằng 0. ISO thiếu dùng
  chuỗi rỗng. Điểm lịch sử null bị bỏ qua để tránh tự vẽ thêm điểm 0.
- Nếu một ngày chỉ có trong một chuỗi, chuỗi kia có thể không chứa khóa đó. ChartHelper
  cần bỏ qua điểm thiếu và xử lý giá trị không còn báo cáo theo quy ước hiển thị.
- Thông báo hiển thị có cả `values/strings.xml` và `values-vi/strings.xml`.
- Volley dùng `NoCache`; cache file offline sẽ do `CacheManager` của Công Dương quản
  lý. Queue được khởi tạo sớm nhưng Fake chưa thêm request nào vào queue.
- Bản debug bật StrictMode để ghi log nếu đọc/ghi đĩa hoặc gọi mạng trên main thread.

## Kiểm tra M1

Từ thư mục repo trong PowerShell (Android Studio đã cấu hình SDK/JDK):

```powershell
.\gradlew.bat assembleDebug lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

`M1ContractTest` chạy trên Android thật/emulator: mẫu 231 nước, ISO null, số lớn,
JSON thiếu/null, hai dạng lịch sử, ngày qua năm mới, khoảng 30/90/365, mã không hợp lệ,
callback bất đồng bộ trên main thread, và StrictMode khi đọc mẫu. Kết quả XML/HTML
do Gradle tạo trong các thư mục `app/build/` (được Git bỏ qua).

Để thử app: mở repo trong Android Studio, chọn `app`, chọn thiết bị và bấm Run.
M1 chưa hoàn thành kiểm thử T02 mất mạng của Repository thật; việc đó đi cùng M2/M3.

## Bàn giao

PR của Tấn Thành cần Hải Anh review theo phân công. Bằng chứng build/test phải đi
cùng PR. Chỉ coi mốc "trên main" hoàn thành sau khi PR được review và merge.
Không tự nhận đã merge khi mới có thay đổi trên nhánh `feat/network`.

## Kết quả thực tế ngày 22/10/2026

- `assembleDebug`: đạt.
- `lintDebug`: 0 lỗi; 8 cảnh báo về phiên bản thư viện sẵn có trong scaffold và hai
  màu chưa dùng. Không có HardcodedText hoặc MissingTranslation.
- `connectedDebugAndroidTest`: 16 kiểm thử đạt, 0 lỗi, 0 thất bại, 0 bị bỏ qua
  (15 kiểm thử M1 và 1 kiểm thử mặc định của Android Studio).
- Emulator: Medium Phone, Android 17 / API 37, x86_64.
- Cài APK debug và mở `vn.edu.usth.covidstats/.MainActivity`: `Status: ok`; Activity
  được xác nhận ở foreground. APK sau instrumentation có cờ test-only; Android
  Studio Run tự xử lý, còn khi cài bằng adb dùng `adb install -r -t <apk>`.

Môi trường thực tế của bộ khung bạn đã tạo: Android Studio 2026.2.1, Android Gradle
Plugin 9.4.1, Gradle 9.6.0, compile/target SDK 37, min SDK 24; lệnh Gradle đã chạy bằng
JBR 25.0.3 đi kèm Android Studio. Mã Java của module vẫn biên dịch theo Java 11 như
scaffold. Thành viên khác nên dùng cùng Android Studio và chọn Gradle JDK đi kèm.
Thông tin Giraffe/JDK 17 trong README ban đầu chưa mô tả bộ khung mới này; cần đồng
bộ môi trường khi bàn giao, không hạ phiên bản ngẫu nhiên nếu Gradle Sync báo lỗi.

Báo cáo để mở trên máy:

- `app/build/reports/lint-results-debug.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- XML: `app/build/outputs/androidTest-results/connected/debug/`
