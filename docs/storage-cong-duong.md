# Bàn giao phần Công Dương — feat/storage

## Trạng thái trung thực

Viết dựa trên `main` commit `d40ae7a` của repo `hainguyen15022006-cmd/COVID-19-Stats-`.
Đã triển khai 5 file được phân công, thêm chuỗi Anh/Việt, 9 storage tests và 1 UI test.
Đã build debug, build APK kiểm thử và chạy lint: 0 lỗi, 21 cảnh báo (có cả code sẵn của repo).
Đã chạy 9 StorageContractTest đạt và 1 WatchlistPreviewTest đạt trên Android 17/API 37.
Kiểm thử UI xác nhận rỗng, thêm VN/US, tạo lại Activity, vuốt bỏ VN và refresh giữ US.
Người dùng đã xác nhận thử tay thành công: thêm VN/US, vuốt bỏ VN, Refresh vẫn giữ US;
force stop/mở lại vẫn giữ US; ngắt mạng/Refresh vẫn có US và nhãn offline.
Các xác nhận thử tay hiện nằm trong hội thoại, chưa có ảnh/log đính kèm cho nhóm.
Ngày 24/10/2026: đã ghép `origin/feat/networknew` tại `55ece8d` vào nhánh local feat/storage.
Repository thật đã dùng CacheManager. Kiểm chứng tải API thật, tắt tiến trình, đọc offline
được global và 231 quốc gia trong 432 ms; Watchlist hiển thị số liệu và nhãn offline.
Sau khi bật mạng và Refresh, request thật thành công và nhãn offline biến mất.
Ảnh/log nằm trong `docs/evidence/f12-2026-10-24/`. Đây là bằng chứng tầng dữ liệu và
Watchlist chạy trong host debug; màn chính ba tab/chi tiết của nhóm vẫn chờ tích hợp.
Người dùng sẽ tự push GitHub.

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

Nhánh local hiện có Repository mạng của Tấn Thành. MainActivity vẫn chỉ mở CountryListFragment;
Watchlist được kiểm tra trong host debug riêng. Phần Công Thành và Sỹ Chúc chưa được ghép
vào nhánh này theo yêu cầu người dùng. Các hướng dẫn CacheManager dưới đây mô tả hợp đồng
đã được NetworkRepository sử dụng; hướng dẫn tab/nút sao vẫn dành cho bước tích hợp sau.

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

Khi click, Fragment mở `vn.edu.usth.covidstats.ui.CountryDetailActivity` bằng extra
`country_iso2`. Công Thành/Tấn Thành cần có đúng Activity và khai báo Manifest. Nếu chưa
có Activity, hiện thông báo thay vì crash. Không thêm Activity giả trong nhánh storage.

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
- [ ] Gắn sao VN ở chi tiết, Back sang tab Theo dõi: có Vietnam.
- [x] Trong màn thử debug: gắn US; bỏ VN không mất US; vuốt bỏ dòng cập nhật ngay.
- [ ] Force stop rồi mở lại: sao còn đúng; chụp ảnh trước/sau (T10).
- [ ] Xoay màn hình lúc đang tải và sau khi có dữ liệu: không crash, không request lặp vì rotation.
- [x] Repository thật: tải thành công, force stop, tắt Wi-Fi/data, đọc lại ở tiến trình mới;
      dữ liệu file và nhãn offline có trên Watchlist debug (24/10, ảnh + log trong evidence).
- [ ] Cache cũ hơn 10 phút vẫn fallback offline; cache mới online không gọi API nếu không Refresh.
- [ ] Mất mạng/chưa cache: lỗi dễ hiểu + Thử lại; cache hỏng không crash.
- [x] Có mạng trở lại, Refresh: log NETWORK countries và nhãn offline biến mất (24/10).
- [ ] Đổi Anh/Việt; chạy StrictMode để xác nhận không disk I/O trên main.

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

## Nội dung PR gợi ý (chỉ gửi sau khi chạy thử)

Tiêu đề: `feat(storage): add file cache and persistent watchlist`

Mã chức năng: F11, F12; hỗ trợ vuốt bỏ theo dõi thuộc F13/T10.
Thêm cache JSON atomic với TTL 10 phút, store ISO2 bất đồng bộ, kiểm tra Internet và
WatchlistFragment có refresh/rỗng/lỗi/offline. Thêm strings Anh/Việt và StorageContractTest.
Các file ngoài 5 file sản phẩm: strings theo ngoại lệ tài liệu, test riêng và ghi chú bàn giao này.
Không đổi CovidRepository/model, Manifest, Gradle hay màn của thành viên khác.

Đã chạy thử: **điền kết quả thật và ảnh/log trước khi gửi**.
Repository mạng đã ghép và kiểm chứng F12 trong host debug. Còn phụ thuộc tab/pager
(Sỹ Chúc) và nút sao/chi tiết (Công Thành) để kiểm tra luồng app chung.
Người review theo tài liệu: Tấn Thành; nhóm trưởng phối hợp duyệt.
