# Bàn giao phần Công Dương — feat/storage

## Trạng thái ngày 25/10/2026

Phần Công Dương (F11/F12) đã tích hợp và kiểm chứng trên màn chính ba tab thật,
với màn Chi tiết và NetworkRepository của nhóm. Nhánh local `feat/storage` đã có
`main` tại `205edff` và bản Refresh của Sỹ Chúc (`b2d9226`, merge `c8649e3`).

Đã sửa hai lỗi chỉ lộ ra sau khi ghép:

- Trong `WatchlistFragment`, giữ cử chỉ ngang trên dòng nước cho vuốt bỏ theo dõi;
  nhả cử chỉ dọc cho kéo xuống làm mới. Trước đó ViewPager chặn vuốt xóa.
- Trong `ui/MainActivity`, chuyển `setOffscreenPageLimit(2)` sang `onPostCreate`.
  Pager phải khôi phục Fragment trước khi tạo tab ngoài màn hình, tránh view Theo dõi
  trùng sau khi tạo lại Activity. Đây là thay đổi tích hợp ngoài 5 file sở hữu ban đầu.

Build debug + APK test đạt; lint **0 lỗi, 14 cảnh báo** của toàn app.
**37 lượt kiểm thử đạt** trên Android 17/API 37: 9 storage, 18 repository,
4 file-cache và 6 lượt tích hợp/đọc cache/khôi phục. Có bằng chứng:

- Gắn sao VN/US qua nút thật ở Chi tiết; mở tab Theo dõi có đủ hai nước.
- Force-stop, tắt Wi-Fi/data, mở ở tiến trình mới: sao và số liệu còn, có nhãn offline.
- Vuốt VN: dòng biến mất và store chỉ còn US; Refresh vẫn giữ US.
- Tạo lại Activity giữ tab/dữ liệu; Refresh trên toolbar và kéo xuống hoạt động.
- Bật mạng lại: Refresh thành công, nhãn offline biến mất; tab tiếng Việt hiển thị đúng.
- Đọc global và 231 nước từ file trong **214 ms**; tab offline hiện hai dòng sau **1830 ms**
  trong lần đo này. Đây là số đo emulator một lần, không phải bảo đảm trên mọi thiết bị.

Ảnh, log, giới hạn và lệnh chạy lại: [bằng chứng 25/10](evidence/storage-2026-10-25/README.md).
Mạng, ngôn ngữ và danh sách sao ban đầu đã khôi phục. Người dùng tự push GitHub;
nhóm vẫn cần review/merge và kiểm thử chung theo lịch M3/M5.

## Màn thử debug

`app/src/debug/` có StoragePreviewActivity, manifest và tài nguyên riêng. Chỉ bản debug
có thêm biểu tượng launcher Storage Test/Thử lưu trữ, không đổi MainActivity của nhóm.
Gắn sao VN và US bằng các nút phía trên, sau đó vuốt trực tiếp trong WatchlistFragment thật.
Màn thử hiện dùng NetworkRepository từ CovidApp, lấy dữ liệu API thật và cache file.

Mở lại màn thử bằng Terminal:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n vn.edu.usth.covidstats/.debug.StoragePreviewActivity
```

Chạy riêng bài kiểm thử UI (đã tránh API Espresso cũ không tương thích Android 17):

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache "-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.WatchlistPreviewTest"
```

Host debug được giữ để thử riêng storage. Bằng chứng mới ngày 25/10 dùng MainActivity
và CountryDetailActivity thật, không phụ thuộc các nút thêm VN/US của host debug.

## API mới cho nhóm dùng

### CacheManager — Tấn Thành

```java
CacheManager cache = new CacheManager(context);
// Ba hàm sau chỉ gọi trong Executor/worker; gọi main thread sẽ bị từ chối.
CacheManager.Entry entry = cache.read("/countries?sort=cases");
cache.write("/countries?sort=cases", responseJson);
cache.remove("/countries?sort=cases");
// Entry gồm json, savedAtMillis và isFresh().
```

JSON được bọc cùng thời điểm lưu, ghi bằng AtomicFile trong `getFilesDir()/covid_cache`.
Tên file là SHA-256 của toàn bộ key; không dùng tên nước hoặc đường dẫn người dùng trực tiếp.
Không có/hỏng định dạng cache trả null; lỗi I/O khác ném IOException để Repository xử lý.
Cache hết 10 phút vẫn đọc được để fallback offline; `isFresh()` trả false tại đúng phút thứ 10.
`savedAtMillis` là lúc tải/lưu, không phải ngày số liệu COVID.

Quy trình Repository cần dùng:

1. Trong worker, đọc cache; nếu không forceRefresh và cache còn mới, parse và trả về main với `fromCache=true`.
2. Nếu cần tải mới, gọi Volley. Parse ở worker; chỉ ghi JSON đã parse thành công. Sau đó trả kết quả với `fromCache=false`.
3. Nếu request thất bại, đọc cả cache cũ; parse được thì trả `fromCache=true`. Không có/hỏng cache thì `onError` có thông báo phù hợp.
4. Nếu ghi cache lỗi nhưng request đã thành công, vẫn trả dữ liệu mới cho UI và log lỗi ghi; không biến thành lỗi tải mạng.
5. Nếu payload cache không parse được, xóa entry hỏng và xử lý như cache miss. CacheManager chỉ kiểm tra envelope, không kiểm tra schema của endpoint.

Thống nhất key gợi ý: `/all`, `/countries?sort=cases`, `/countries/VN`,
`/historical/VN?lastdays=all`, `/historical/all?lastdays=all`. Với lịch sử nên lưu toàn bộ
JSON rồi dùng JsonParser lấy 30/90/365 ngày; không ghi response rút gọn đè lên key `all`.
Không dùng `fromCache` làm cờ mất mạng: cache mới có thể được dùng khi có Internet.

### WatchlistStore — Công Thành và Hải Anh

```java
WatchlistStore store = WatchlistStore.getInstance(context);
store.getCodes(callback);                    // Set<String> ISO2
store.setStarred("VN", true, callback);       // thêm sao
store.setStarred("VN", false, callback);      // bỏ sao
store.toggle("VN", callback);                // đảo trạng thái
store.addListener(listener);                 // đăng ký trong onStart
store.removeListener(listener);              // gỡ trong onStop
```

Callback là `Callback<Set<String>>` hiện có. Chuỗi được trim/uppercase theo Locale.ROOT;
mã không đúng hai chữ A–Z trả onError, không lưu. Các nước không có ISO2 không thể gắn sao.
Set trả về bất biến, không sửa trực tiếp. Dùng getCodes để hiển thị trạng thái sao ban đầu;
sau toggle, cập nhật icon theo `codes.contains(iso2)` trong onSuccess. Khi lỗi, giữ icon cũ.

SharedPreferences được mở/đọc/commit trên một worker tuần tự. Callback và listener về
main thread sau khi commit thành công; callback không có nghĩa là response từ cache mạng.
Không cần tự khởi tạo store trong CovidApp. Dùng singleton để mọi màn cùng nhận thay đổi.
Tự bảo vệ callback theo vòng đời View khi tích hợp vào màn chi tiết.

### WatchlistFragment — Sỹ Chúc

Thêm `new WatchlistFragment()` ở vị trí tab 3 trong pager; tiêu đề `R.string.watchlist_title`.
Nút Refresh trên App Bar gọi `fragment.refresh()` khi tab Theo dõi đang hiển thị.
Fragment tự đọc sao khi onStart và lắng nghe thay đổi; kéo xuống ép refresh Repository.
ViewModel giữ dữ liệu/request qua rotation, không giữ Activity hoặc View.
RecyclerView dùng lại CountryAdapter của Hải Anh. Vuốt trái/phải bỏ sao và cập nhật danh sách
sau commit; ghi lỗi sẽ khôi phục dòng và có nút Thử lại.

Khi click, Fragment mở `CountryDetailActivity` thật bằng extra `country_iso2`.
Activity và Manifest đã được ghép; nút sao dùng chung singleton WatchlistStore.

Nhãn offline xuất hiện khi có dòng dữ liệu và NetworkUtil không thấy Internet đã được
Android xác thực. Kiểm tra lại khi render/Refresh/quay lại màn hình. Dữ liệu mẫu cũng có
thể hiển thị offline nhưng không được coi là bằng chứng cache mạng T03.

## Build và kiểm thử cần chạy

Cài Android Studio tương thích cấu hình Gradle hiện có, đồng bộ project và cài SDK mà
Gradle yêu cầu. Cấu hình repo lúc viết là AGP 9.4.1, Gradle 9.6.0, compile/target SDK 37;
không tự hạ phiên bản trong nhánh storage. Nếu dependency không tải được, gửi lỗi cụ thể
cho Tấn Thành xử lý cấu hình chung.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.StorageContractTest
```

9 storage tests: cache miss/Unicode qua instance mới; TTL 10 phút/đồng hồ lùi; phân biệt query/xóa
đúng key; file hỏng/phục hồi; từ chối I/O main thread; sao chuẩn hóa/lưu/snapshot bất biến;
mã không hợp lệ; 20 toggle tuần tự; listener main thread sau commit. Các tests chạm mã ZZ/ZY
và khôi phục giá trị ban đầu, nên dùng emulator thử nghiệm. Các storage tests chưa chứng minh process
restart hay UI; kiểm tra thủ công bên dưới vẫn bắt buộc.

### Checklist bằng chứng T03/T10 sau tích hợp

- [x] Build, lint và StorageContractTest chạy thành công; báo cáo trong app/build/reports.
- [x] Chưa có sao: tab Theo dõi có thông báo rỗng, không vòng chờ vô hạn (UI test).
- [x] Gắn sao VN ở Chi tiết rồi mở tab Theo dõi thật: có Vietnam.
- [x] Trong màn thử debug: gắn US; bỏ VN không mất US; vuốt bỏ dòng cập nhật ngay.
- [x] Force stop rồi mở lại: sao còn đúng; có ảnh/log trước/sau (T10).
- [x] Tạo lại Activity sau khi có dữ liệu: giữ tab, hai dòng và Refresh hoạt động.
- [ ] Kiểm thử xoay vật lý lúc request đang tải thuộc lượt T09 chung của nhóm.
- [x] Repository thật: tải thành công, force stop, tắt Wi-Fi/data, đọc lại ở tiến trình mới;
      dữ liệu file và nhãn offline có trên Watchlist debug (24/10, ảnh + log trong evidence).
- [x] Cache cũ hơn 10 phút vẫn fallback offline; cache mới online dùng lại trừ Refresh (tests repository/file-cache).
- [x] Cache hỏng hoặc chưa có cache trả lỗi ở tầng dữ liệu, không crash (tests repository/file-cache).
- [ ] T02 giao diện mất mạng/chưa cache của toàn app vẫn do Tấn Thành kiểm thử.
- [x] Có mạng trở lại, Refresh: log NETWORK countries và nhãn offline biến mất (24/10).
- [x] Tab Theo dõi đổi Anh/Việt; kéo xuống thực sự gửi request countries.
- [x] StorageContractTest từ chối file I/O trên main; M3RepositoryTest kiểm tra main-thread I/O đạt.

Chạy lại bộ bằng chứng trên emulator đã mở (cần Internet cho các pha online):

```powershell
.\docs\verify-storage.ps1
```

Script không gỡ app/xóa app data; sao thử VN/US có bản sao lưu và được khôi phục trong
`finally`. Các pha `WatchlistIntegrationTest` chỉ chạy khi chỉ rõ `storagePhase`,
để lần chạy toàn bộ test thông thường không phụ thuộc thao tác tắt/bật mạng.

## Ba ý để giải thích

**Phần em làm gì?** Lưu JSON đã tải để xem lại khi mất mạng; lưu ISO2 các nước theo dõi;
hiển thị danh sách sao và cho bỏ sao bằng vuốt.

**Dữ liệu đi vào/ra?** Repository đưa JSON đã parse hợp lệ vào CacheManager. Khi cần,
Repository đọc JSON rồi parse thành model và callback main thread. Nút sao đưa ISO2 vào
WatchlistStore; tab nhận Set ISO2, lọc model CountryStats lấy từ Repository.

**Vì sao cách này?** File chứa response lớn, SharedPreferences chỉ chứa danh sách mã nhỏ.
AtomicFile tránh file dở dang khi gián đoạn ghi; worker giữ UI không bị chặn. TTL tránh gọi
mạng thừa, nhưng không xóa cache cũ vì vẫn cần xem offline. Singleton/worker tuần tự tránh
mất cập nhật khi nhiều màn đổi sao.

Phần trình bày 6:30–9:00: disease.sh trả JSON qua HTTPS; Volley gọi bất đồng bộ;
onResponse ở main nên parse ở parseNetworkResponse/worker; Repository chọn cache hoặc
mạng. Giải thích bằng log request thật sau tích hợp. Không nói nguồn cập nhật hàng ngày;
ngày dữ liệu lấy từ timeline, không lấy updated.

## Nội dung PR gợi ý (đã có bằng chứng, người dùng tự gửi)

Tiêu đề: `fix(storage): finish watchlist gestures and integrated offline verification`

Sau khi ghép vào pager, vuốt bỏ theo dõi bị nhận thành vuốt tab; tạo lại Activity
có thể để lại view Watchlist trùng. Sửa phân phối cử chỉ trong WatchlistFragment
và trì hoãn tạo tab ngoài màn hình đến sau khi MainActivity khôi phục state.

Đã build/lint (0 lỗi/14 cảnh báo), chạy 37 lượt kiểm thử gồm T03/T10 trên tab thật,
force-stop/offline, vuốt xóa, toolbar/pull Refresh và tiếng Việt. Bằng chứng ở
`docs/evidence/storage-2026-10-25/`; script chạy lại `docs/verify-storage.ps1`.
Thay đổi `MainActivity` là sửa tích hợp với phần của Sỹ Chúc; cần người sở hữu rà lại.
Người review theo tài liệu: Tấn Thành; nhóm trưởng phối hợp duyệt.
