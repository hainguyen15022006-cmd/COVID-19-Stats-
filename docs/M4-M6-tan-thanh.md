# M4–M6 — phần Lê Tấn Thành

## Trạng thái và phạm vi

Bản chuẩn bị ngày 24/10/2026, trên nhánh `feat/networknew`. Không đổi Callback,
CovidRepository, model hoặc nguồn dữ liệu. Nhận thêm bằng chứng F12 từ `feat/storage`
commit `ac07ef8`, giữ nguyên tác giả Công Dương. PR #5 vẫn cần Hải Anh review.

Đối chiếu đặc tả trang 7–10 và phân công trang 5–12: Thành giữ main, phần mạng/dữ liệu,
chạy T02/T04/T12, trình bày kiến trúc ở phút 1:30–3:30. Hoàng Anh tổng hợp/nộp;
Minh Sơn ghép slide và ảnh overdraw; Công Dương trình bày networking/cache.
Watchlist thuộc Công Dương theo bảng phân công dù bảng màn hình của đặc tả có một dòng
ghi Hải Anh; không tự đổi chủ file vì dòng không khớp đó.

Hạn nhóm: M4 30/10 21:30, M5 31/10, M6 01/11 21:00. Hôm nay chưa khóa tính năng
và không gọi snapshot chưa đủ màn hình là bản nộp cuối.

## M4 — kiểm tra tích hợp và chuẩn bị khóa tính năng

- Thêm 4 `M4OfflineFileCacheTest`: file cache thật quá hạn vẫn dùng offline, không đổi
  timestamp khi đọc; đổi cửa sổ lịch sử không ghi đè bản đầy đủ; payload hỏng bị xóa;
  lỗi ghi ổ đĩa không làm mất response mạng hợp lệ. Test dùng thư mục riêng và constructor
  production, không dùng cache RAM giả để kết luận cache file.
- Đọc bằng chứng F12 của Công Dương trong `docs/evidence/f12-2026-10-24/`:
  dữ liệu API thật qua tiến trình khác, đọc offline 432 ms và ảnh Watchlist có nhãn offline.
  Đây là bằng chứng của người chạy được ghi trong thư mục đó; không cộng số lượt này vào
  các bài kiểm tra mới của Thành.
- Thử ghép chi tiết `07d93d1` với network/storage `ac07ef8` trên clone tạm, không sửa main.
  Có xung đột CovidApp và hai strings.xml; sau khi hợp nhất riêng các phần đó để thăm dò,
  build lỗi tại `ui/WatchlistFragment.java:79`:
  `RequestQueue cannot be converted to ImageLoader`.
- Nguyên nhân: CountryAdapter trong nhánh chi tiết nhận ImageLoader; Watchlist vẫn đưa
  RequestQueue. Bản sửa đề xuất sau khi đã ghép đủ Application/Adapter là truyền
  `app.getImageLoader()` ở dòng đó. Thử bản đề xuất trên clone tạm đã build thành công.
  Chủ file Công Dương/Hải Anh/Công Thành cần phối hợp review trong bản ghép; không áp dụng
  một dòng đó đơn lẻ vào nhánh chưa có getter. Repo thật chưa sửa thay UI của họ.
- Chi tiết hiện chưa có PR; phần của Công Thành cần Công Dương review. Không lấy commit
  có trên remote làm bằng chứng đã được duyệt.
- `feat/storage` mới có cả commit M3 của Thành. Khi review/merge PR #4 cần giữ điều kiện
  Hải Anh review phần network của Thành, không dùng đường merge phụ để bỏ qua vòng đó.

### Các điểm chốt theo lịch

1. Mỗi phiên 13:00/22:00: đọc PR, file sở hữu, hợp đồng, build/test và reviewer đúng người.
2. Mốc tính theo main lúc 21:30; nhánh riêng chưa tính đạt.
3. 29/10 20:00: F09/F10/F13 chưa chạy thì cùng nhóm xử lý theo quy định; không cắt sớm
   chỉ vì đang chuẩn bị bản kiểm tra ngày 24/10. Nếu trễ M1/M2, dùng quy tắc cắt trong phân công.
4. 30/10 21:30: khóa tính năng. Sau đó chỉ nhận sửa lỗi đã ghi trong ma trận, ưu tiên P0 rồi P1.
5. Quyết định cắt F11/F12 nếu trễ M3 cần Hải Anh và Thành thống nhất; chưa xảy ra thì không
   tự xóa tab/nhánh của đồng đội.

## M5 — kiểm thử thuộc Thành

| Mã | Kiểm tra đã có | Giới hạn cần giữ khi đánh dấu |
|---|---|---|
| T02 | Repository báo lỗi khi offline không cache; timeout/server/malformed JSON có kiểm soát | CountryList vẫn Toast, chưa có nút Retry: T02 toàn UI chưa đạt |
| T04 | M5ParserRobustnessTest: null/số lớn/rate không hữu hạn, thiếu điểm lịch sử, ngày trùng/sai, snapshot bất biến | Không tự biến điểm thiếu thành 0 hoặc đổi số nguồn |
| T12 | M5ApiThreadTest: giữ parser chờ nhưng main vẫn trả lời; success/error về main | Đây là kiểm thread trực tiếp, không suy từ thread mạng |
| T12 UI | M5CachedListUiTest: cache fixture 231 dòng, mở và cuộn màn thật, StrictMode/main heartbeat | Fixture để lặp lại workload; không dùng nó làm bằng chứng tải API/offline thật |

Đo lần chạy UI: có 231 dòng trong **1891 ms** tính từ ActivityScenario.launch; main
heartbeat lớn nhất **1 ms**, StrictMode **0 vi phạm**. Đây là lượt thử trên emulator
API 37 và cuộn bằng RecyclerView; không suy thành “60 fps”, không thay cho đo cold start
từ icon của toàn app và không xác nhận mọi màn hình chưa được ghép.

Lượt này 32 test executions đạt:

- 4 M4OfflineFileCacheTest.
- 4 M5ParserRobustnessTest.
- 2 data.M5ApiThreadTest.
- 1 M5CachedListUiTest.
- 2 M6ManifestContractTest.
- 18 M3RepositoryTest hồi quy.
- 1 WatchlistPreviewTest hồi quy, vẫn mở host debug bằng component tường minh.

Chạy từng class, lưu báo cáo riêng và log M5UiProof. Animation emulator được tắt tạm
cho test cử chỉ rồi khôi phục. Các test API 28+ có SdkSuppress; không nói đã chạy chúng
trên Android 7/API 24. Production vẫn minSdk 24 và được Lint kiểm tra.

Ví dụ PowerShell từ gốc repo:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M4OfflineFileCacheTest'
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M5ParserRobustnessTest'
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.data.M5ApiThreadTest'
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M5CachedListUiTest'
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache '-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.usth.covidstats.M6ManifestContractTest'
```

Chạy lại ma trận thật ngày 31/10 trên main sau khi các chủ màn hình ghép xong.
Không đánh T01/T07/T08/T09/T11/T13/T14 đạt thay cho người phụ trách khi chưa có bằng chứng.

## M6 — APK, bản nguồn và phần trình bày

- Debug Manifest bỏ intent-filter launcher của StoragePreviewActivity: chỉ còn một icon
  CovidStats. Host thử vẫn giữ, mở được bằng adb hoặc ActivityScenario. Không đổi layout UI.
- M6ManifestContractTest xác nhận một launcher MainActivity, host debug vẫn tồn tại,
  có hai quyền mạng và không xin quyền dangerous. Quyền signature AndroidX tự sinh
  không bị nhầm với quyền runtime nguy hiểm.
- Bản bàn giao cá nhân có APK candidate, source ZIP tại cùng commit, SHA-256, báo cáo test,
  bằng chứng lỗi tích hợp và script kiểm tra điều kiện xuất bản.
- Hai slide kiến trúc có PowerPoint chỉnh sửa được, PDF để xem và lời nói khoảng 2 phút;
  để Minh Sơn ghép vào mẫu môn. Không coi đây là toàn bộ slide final của 7 người.
- Script đóng gói từ clone sạch; chế độ final từ chối khi chưa ở main/khác GitHub,
  source bẩn, thiếu các màn bắt buộc, thiếu bằng chứng T01–T14 hoặc slide/video/overdraw/
  hai lượt tập của nhóm. Candidate luôn ghi rõ chưa phải final.
- Script, slide và log nằm trong gói bàn giao cá nhân ngoài Git; không tự sửa README,
  ma trận của Hải Anh hay bộ slide cuối do Minh Sơn phụ trách.
- Không đưa APK, build/, local.properties, khóa hoặc log máy cá nhân vào Git.

Điều kiện hoàn tất M6 thật còn cần cả nhóm: main đã review và chạy đầy đủ, slide đúng mẫu,
ảnh overdraw trước/sau của ba màn, video 2–3 phút và hai lượt tập thực tế.
Không thể tự xác nhận thay người thuyết trình rằng đã tập xong.

## Ba ý để giải thích phần Thành

**Làm gì?** Giữ đường lấy số liệu từ mạng/file đến màn hình hoạt động đúng, kiểm tra lỗi
trước khi ghép vào bản chung và chuẩn bị bản app có thể kiểm lại theo đúng source.

**Dữ liệu vào/ra?** UI hỏi Repository; Repository đọc cache trên worker hoặc gọi CovidApi.
Volley worker chạy JsonParser, model và callback về main để UI hiển thị. Cache dùng thời điểm
tải; ngày số liệu lấy từ timeline. Thiếu cache/mạng lỗi đi qua onError để UI có Retry.

**Vì sao cách này?** Mỗi lớp làm một việc, giữ hợp đồng để nhóm ghép được độc lập. Kiểm tra
thread trực tiếp giúp phát hiện xử lý nặng ở main. Kiểm tra trên clone và gói cùng commit
giúp tránh nộp source một bản nhưng APK một bản khác. Có lỗi/phụ thuộc thì ghi đúng bằng
chứng, không tuyên bố bản cuối khi điều kiện nhóm chưa đủ.
