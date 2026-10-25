# Bàn giao M2/M3 — Lê Tấn Thành

## Phạm vi

Nhánh làm việc `feat/networknew`, nền `main` tại `d40ae7a`. Đã ghép nhánh của Công Dương
`feat/storage` tại `5cbb78e`, giữ nguyên mã và tác giả của phần lưu trữ.
M2 là ngày 25/10/2026; M3 nhóm là 28/10/2026 lúc 21:30. Hạn cá nhân của Thành là 27/10.

Java, AndroidX, minSdk 24, Volley, org.json. Không thêm thư viện mới.
Giữ nguyên `Callback`, bốn hàm của `CovidRepository` và các model M1.

## Các lớp mới và cách chạy

- `CovidApi`: custom Volley Request. `parseNetworkResponse` giải mã UTF-8 và gọi JsonParser
  ở worker; `deliverResponse` về main. Timeout 10 giây, không tự retry nhiều lần.
- `NetworkRepository`: dùng cache RAM/file, TTL 10 phút, forceRefresh, fallback khi lỗi.
- `CovidApp`: một Queue và một NetworkRepository dùng chung; dùng CacheManager thật.
  Queue dùng NoCache; cache JSON do CacheManager quản lý, cờ vẫn dùng ImageLoader/LruCache của UI.
- Chuỗi lỗi có trong cả `values/strings.xml` và `values-vi/strings.xml`.

```text
Activity/Fragment -> CovidRepository -> NetworkRepository
                                         | cache worker
                                         | CovidApi -> disease.sh
                                         | JsonParser (Volley worker)
                                         v
                               callback trên main -> UI
```

| Hàm | Endpoint | Cache key |
|---|---|---|
| getGlobal | /all | /all |
| getCountries | /countries?sort=cases | /countries?sort=cases |
| getCountry("VN") | /countries/VN | /countries/VN |
| getHistory("VN", days) | /historical/VN?lastdays=all | /historical/VN?lastdays=all |
| getHistory("all", days) | /historical/all?lastdays=all | /historical/all?lastdays=all |

ISO được trim/uppercase và kiểm tra hai chữ A–Z. `days` nhận số dương hoặc ALL_DAYS=-1.
Lịch sử cache toàn bộ, không lấy bản 30 ngày đè lên bản all. Chọn khoảng tính lùi từ ngày
cuối của nguồn, không tính từ hôm nay. Global history đọc root, country history đọc timeline.

## Chính sách cache

CacheManager của Công Dương tự ghi `savedAtMillis` khi write; Repository đọc lại timestamp
đó, không làm mới khi chỉ đọc. Toàn bộ read/write/remove chạy trên worker tuần tự.
Payload cache parse hỏng sẽ bị xóa. Chỉ ghi response đã parse thành công.

- Cache mới, không force: success fromCache=true, không HTTP.
- Force global/countries: thử mạng khi có Internet; lỗi vẫn dùng cache đọc được.
- Cache cũ và Android xác nhận offline: trả ngay, không đợi timeout mạng.
- Cache cũ nhưng có kết nối: thử lấy mới; thất bại fallback.
- Không cache và mạng lỗi: onError có chuỗi dễ hiểu, không dùng res/raw thay thế.
- Ghi cache lỗi: vẫn trả dữ liệu mạng, ghi log CACHE_WRITE_FAILED.

`fromCache` không phải cờ offline. UI phải dùng NetworkUtil kiểm tra trạng thái kết nối.
`savedAtMillis` là giờ tải để tính TTL; `updated` chỉ dùng nhãn “Tải lúc”. Ngày số liệu
lấy từ `HistoricalData.lastDataDate`; không gắn ngày tải 2026 lên số liệu nguồn 2023.

Các yêu cầu cùng kiểu và cùng cửa sổ đang chạy được gom callback. Hai cửa sổ history khác
nhau khởi động đồng thời khi chưa cache có thể gửi riêng; khi cache đầy đủ đã có thì tái dùng.
Callback về main; chủ UI vẫn phải bảo vệ lifecycle View và giữ trạng thái khi xoay.

## Kiểm thử

Dùng PowerShell ở gốc repo và JBR của Android Studio:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M1ContractTest'
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M3RepositoryTest'
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M3FileCacheTest'
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.StorageContractTest'
```

Chạy từng class để đọc đủ số lượng test trong báo cáo. M1 test tự tạo FakeRepository để
kiểm fixture; Application assertion riêng xác nhận NetworkRepository.
M3RepositoryTest dùng phản hồi kiểm soát, không cần disease.sh; không dùng nó làm bằng
chứng Live. M3FileCacheTest ghép CacheManager thật trong thư mục test riêng.

Live cần Internet và chạy riêng:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M3LiveApiTest'
```

Kiểm qua đóng tiến trình bằng ADB, hai lần chạy riêng. Toolchain hiện tại gỡ APK sau
Gradle connected test, nên KHÔNG dùng hai lần Gradle để kiểm tra giữ nguyên data:

```powershell
$adb='C:\Users\DELL\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 install -r '.\app\build\outputs\apk\debug\app-debug.apk'
& $adb -s emulator-5554 install -r '.\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
& $adb -s emulator-5554 shell am instrument -w -r -e class vn.edu.usth.covidstats.M3PersistentCacheTest -e m3CachePhase seed vn.edu.usth.covidstats.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 shell am force-stop vn.edu.usth.covidstats
# Ghi nhận trạng thái Wi-Fi/data ban đầu trước khi tắt để khôi phục sau test.
& $adb -s emulator-5554 shell svc wifi disable
& $adb -s emulator-5554 shell svc data disable
& $adb -s emulator-5554 shell am instrument -w -r -e class vn.edu.usth.covidstats.M3PersistentCacheTest -e m3CachePhase read vn.edu.usth.covidstats.test/androidx.test.runner.AndroidJUnitRunner
# Bật lại những kết nối đã bật trước test; KHÔNG clear storage giữa hai phase.
```

`M3CacheProof` ghi phase, PID, elapsedMs và số nước. Phase read phải có PID mới, fromCache=true,
không có HTTP mới và phần đọc số liệu dưới 2 giây. Chỉ đo phần Repository trả số, chưa
đo thời gian từ bấm icon đến render toàn bộ giao diện. Phase read còn assert NetworkUtil
đang offline. Giữ nguyên hai APK đã cài và data giữa hai phase.

Logcat: `package:vn.edu.usth.covidstats tag:M3Data`; xem REQUEST, PARSE/thread, NETWORK,
CACHE và ERROR. T12 dùng thêm StrictMode và thử UI; StrictMode không bắt mọi CPU task nặng.

## Trạng thái bằng chứng

- 24/10/2026, Medium Phone API 37: assembleDebug + assembleDebugAndroidTest thành công.
- Lint: 0 error, 21 warning; không có HardcodedText.
- M1ContractTest 15/15; M3RepositoryTest 18/18; M3FileCacheTest 2/2;
  StorageContractTest 9/9; M3LiveApiTest 1/1 (5 endpoint thật); WatchlistPreviewTest 1/1.
- Hai lượt M3PersistentCacheTest: seed online PID 6535; force stop + tắt mạng;
  read offline PID 6617, đọc global + 231 nước từ file trong 414 ms. Cả hai lượt đạt.
- Tổng 48 lượt kiểm thử đạt; không lấy các lần retry UI thất bại để tính thêm số test đạt.
- UI test ban đầu bị System UI ANR/dialog và thao tác chuyển cửa sổ; sau khi đóng dialog
  và tắt animation trong lúc chạy, nguyên test của Công Dương đạt, không sửa mã UI/test đó.
- Network/animation emulator được khôi phục về trạng thái ban đầu sau kiểm thử.
- T02 tầng dữ liệu và T04/T12 Repository đã có test; T02 trọn UI còn thiếu nút Retry ở
  CountryListFragment. Toàn bộ F01–F08 của nhóm vẫn cần các chủ UI bàn giao.
- Main giữ quy trình review; không tự coi một nhánh cá nhân là M3 toàn nhóm đã xong.


## Điều kiện tích hợp còn thuộc các chủ màn hình

Main được kiểm tra lúc bắt đầu mới mở CountryListFragment. Chưa có GlobalFragment,
CountryDetailActivity hay ChartHelper trên các nhánh remote được lấy về.

- Hải Anh: onError hiện mới Toast, cần nút Retry để T02/F06 đạt đủ; chạm dòng hiện mới Toast,
  cần Intent extra country_iso2 để F04 đạt. Giữ/bảo vệ View qua callback/rotation.
- Sỹ Chúc: ba tab, global numbers/date, App Bar Refresh đúng tab và global chart integration.
- Công Thành: detail/ChartHelper, 30/90/365/all mặc định all, portrait/landscape, sao.
- Minh Sơn: toàn bộ chuỗi và định dạng locale ở mọi màn hình.
- Công Dương: nối WatchlistFragment vào pager do Sỹ Chúc; offline notice dùng NetworkUtil.

getCountry/getHistory không có force trong hợp đồng. Nếu nhóm muốn ép tải cả lịch sử/chi tiết
trong TTL, cần một PR thống nhất với người gọi; không tự đổi chữ ký M1.
F09/F10 là nên có, không thêm getContinents trong PR tầng dữ liệu này.

Không đánh dấu toàn bộ F01–F08 hoặc UI T02/T12 đạt khi mới test Repository.
PR của Thành do Hải Anh review. Việc đã ở feat/networknew chưa tính là mốc nhóm trên main.
Các file của Công Dương đi qua merge dependency, không phải Thành viết lại.
File bàn giao này đáp ứng yêu cầu mỗi người giải thích ba ý trong docs; Hoàng Anh tổng hợp.

## Ba câu trả lời để học và trình bày

**Phần này làm gì?** Cung cấp dữ liệu mạng thật, cache và lỗi qua một hợp đồng chung để
các màn hình dùng cùng cách gọi; thay FakeRepository tại Application.

**Dữ liệu vào/ra?** UI đưa tham số và callback. Repository tìm cache trên worker hoặc gọi
CovidApi; Volley worker parse JSON thành model. Repository ghi cache trên worker rồi trả
callback main để UI cập nhật. Lỗi không cache đi qua onError.

**Vì sao chọn cách này?** Volley phù hợp yêu cầu môn; parse nặng không chặn UI. Một Queue,
một Repository giúp chia sẻ cache và gom request. CacheManager của Công Dương giữ trách
nhiệm file, adapter của Repository nối đúng API thật và không tạo tầng cache file thứ hai.
