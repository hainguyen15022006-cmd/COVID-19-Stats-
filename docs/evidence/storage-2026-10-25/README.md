# Bằng chứng F11/F12 — Công Dương — 25/10/2026

Máy: emulator Medium Phone, Android 17/API 37. Base local: `c8649e3`, cộng bản sửa
WatchlistFragment và MainActivity trong cùng thay đổi này. Thời gian log UTC+07:00.

## Kết quả

| Kiểm tra | Kết quả | Log/ảnh |
|---|---|---|
| Build app + test, lint | Đạt; 0 lỗi/14 cảnh báo | `build-test.txt`, `build-final.txt`, `lint.txt` |
| Cache/SharedPreferences, callback, worker | 9 đạt | `storage-contract.txt` |
| Repository: TTL, force refresh, cache hỏng/lỗi, main-thread I/O | 18 đạt | `repository.txt` |
| File cache cũ offline, lịch sử, file hỏng, ghi lỗi | 4 đạt | `offline-file-cache.txt` |
| Rỗng → gắn sao từ Chi tiết → tab thật → recreate → toolbar Refresh | 1 đạt | `seed-online.txt`, `01-empty.png`, `02-detail-stars-in-real-tab.png` |
| T03/T10: tiến trình mới offline, recreate, vuốt VN, Refresh giữ US | 1 đạt | `read-offline-swipe.txt`, `03-after-force-stop-offline.png`, `04-swiped-vn-refreshed-offline.png` |
| Global + 231 nước từ file trong 214 ms | 1 đạt | `persistent-cache.txt`, `device-proof.txt` |
| Mạng trở lại, Refresh, nhãn offline ẩn | 1 đạt | `recover-online.txt`, `05-back-online.png` |
| Kéo xuống gửi request countries; tab và định dạng số tiếng Việt | 1 đạt | `pull-refresh-vi.txt`, `06-watchlist-vi.png` |
| Khôi phục danh sách sao ban đầu | 1 đạt | `restore-original.txt` |

Tổng: **37 lượt đạt** (bao gồm lượt khôi phục dữ liệu). Các lớp cũ đã được chạy lại
trên app tích hợp; lớp mới dùng MainActivity/Chi tiết thật, không dùng StoragePreviewActivity.

## Bằng chứng qua tiến trình

Trích `device-proof.txt`, ngày 25/10:

```text
15:14:54 SEED pid=9249 codes=[US, VN]
15:15:01 OFFLINE pid=9424 visibleAfterLaunchMs=1830 remaining=[US]
15:15:03 phase=read pid=9483 elapsedMs=214 countries=231
15:15:12 RECOVERED pid=9557 codes=[US]
15:15:13 RESTORED original=[VN]
```

Script thực hiện `am force-stop`, tắt cả Wi-Fi/data trước pha offline. Test xác nhận
NetworkUtil báo offline, hai sao đã lưu vẫn có, UI có hai dòng và nhãn offline;
sau đó vuốt VN và kiểm tra store/UI chỉ còn US. PID thay đổi xác nhận không dựa vào
singleton/JSON còn trong RAM. Thời gian 1830 ms bao gồm khởi chạy Activity và mở tab,
214 ms là đọc dữ liệu tầng Repository trong tiến trình khác; không cộng hay đánh đồng hai số đo.

## Hai lỗi đã sửa

1. Tạo tab ngoài màn hình trước khi pager restore state để lại view Watchlist trùng
   sau recreate. Test ghi nhận `page=2 rows=2 children=0` ở view trùng dù có dữ liệu.
   Chuyển `setOffscreenPageLimit` đến `onPostCreate`; cùng test sau đó đạt.
2. ViewPager chặn cử chỉ ngang trước ItemTouchHelper nên vuốt không xóa.
   Watchlist giữ cử chỉ ngang trên dòng nước; trả cử chỉ dọc cho parent. Kiểm thử
   vuốt xóa và kéo xuống gửi request thật đều đạt.

`failure.png` là ảnh chẩn đoán trước bản sửa pager, không phải ảnh kết quả cuối.

## Chạy lại

Mở emulator có mạng rồi chạy từ repo:

```powershell
.\docs\verify-storage.ps1
```

Có thể truyền `-Sdk`, `-Jdk`, `-Serial` và `-Evidence` cho máy khác. Script dùng các
dependencies đã cache (`--offline` của Gradle); emulator vẫn cần Internet cho API.
Script cài APK bằng `install -r`, tự khôi phục sao và trạng thái mạng trong `finally`.
Test tiếng Việt khôi phục app locale trong `finally`. Không chạy trên điện thoại cá nhân.

Các pha tích hợp là opt-in: nếu gọi thủ công, thêm `-e storagePhase <tên phương thức>`
cùng `-e class vn.edu.usth.covidstats.WatchlistIntegrationTest#<tên phương thức>`.
Nếu dừng cưỡng bức giữa chừng, chạy pha `restoreOriginal` để khôi phục sao từ file dự phòng.

## Phạm vi kết luận

- Đủ bằng chứng T03/T10 cho phần Công Dương trên bản local này; chưa phải kết quả main
  sau review/merge hoặc lượt M3/M5 của cả nhóm.
- Recreate sau khi có dữ liệu đã kiểm tra; xoay vật lý trong khi request đang tải và
  mọi màn hình/ngôn ngữ của app vẫn thuộc kiểm thử T09/T11 chung.
- Các tests cache lỗi/chưa cache kiểm tra tầng dữ liệu; T02 toàn giao diện chưa được
  kết luận từ bộ này.
- Ảnh cờ không thuộc file JSON cache; offline có thể không hiện cờ, nhưng số liệu và sao vẫn có.
- Test tiếng Việt cuối chạy sau khi khôi phục sao ban đầu, vì vậy ảnh có Vietnam;
  ảnh offline trước đó có US. Đây là hai pha kiểm thử khác nhau.
