# Ma trận kiểm thử T01–T14

Giữ bởi Hải Anh (cổng chất lượng). Cập nhật lần cuối: 25/10/2026.

**Quy tắc:** một mã chỉ được đánh dấu **Đạt** khi có **ảnh màn hình hoặc đoạn log**
kèm theo. "Em test rồi, chạy tốt" không tính. Ảnh trong bảng này cũng chính là kho
ảnh để làm slide, nên không phải việc làm thêm.

Mỗi người tự chạy phần mình rồi đăng bằng chứng vào issue `Kiem thu T01-T14`.
Hải Anh chỉ giữ bảng và nhắc cái còn thiếu.

Mốc: chạy lần đầu **M3 — 28/10**, chạy lại đủ bộ **M5 — 31/10**.

---

## Bảng

| Mã | Kiểm tra | Người chạy | Điều kiện đạt | Trạng thái | Bằng chứng |
|---|---|---|---|---|---|
| T01 | Số liệu toàn cầu | Sỹ Chúc | Đủ trường, số có dấu phân cách; dòng "Số liệu tính đến…" lấy từ `timeline`, không có chữ "Cập nhật" | ⬜ Chưa chạy | — |
| T02 | Mất mạng, chưa có cache | Tấn Thành | Thông báo dễ hiểu + nút Thử lại; không crash | 🟡 Một phần | Tầng dữ liệu: `M3RepositoryTest` 18 đạt. Phần giao diện chưa chạy |
| T03 | Mất mạng, đã có cache | Công Dương | Hiện dữ liệu lần trước + dòng "Đang offline" | ✅ Đạt | `docs/evidence/f12-2026-10-24/offline-watchlist.png`, `offline-read.txt` — 231 nước đọc từ file trong 432 ms |
| T04 | Parser chịu dữ liệu thiếu | Tấn Thành | Trường thiếu hoặc null không làm crash | ✅ Đạt | `M5ParserRobustnessTest` 4 đạt — null, số cực lớn, thiếu điểm, ngày trùng/sai |
| T05 | Tìm kiếm | Hải Anh | `viet`, `vn`, `usa`, `us`, `uk` đều ra kết quả; không phân biệt hoa thường | ⬜ Chưa ghi | — |
| T06 | Sắp xếp, lọc châu lục | Hải Anh | Thứ tự đúng, kết quả lọc đúng | ⬜ Chưa ghi | — |
| T07 | Chi tiết quốc gia | Công Thành | Mở đúng nước theo `iso2`; trường bằng 0 hiện dấu `–` | ⬜ Chưa chạy | — |
| T08 | Biểu đồ | Công Thành | Đổi 30/90/365/tất cả thì biểu đồ đổi; mặc định là tất cả | ⬜ Chưa chạy | — |
| T09 | Xoay màn hình | Công Thành + Sỹ Chúc | Không mất dữ liệu, không gọi lại API | ⬜ Chưa chạy | — |
| T10 | Theo dõi | Công Dương | Còn sau khi tắt app; vuốt xóa cập nhật ngay | 🟡 Một phần | `WatchlistPreviewTest` đạt, nhưng chạy trong host debug. Chưa chạy trong tab thật |
| T11 | Tiếng Việt | Minh Sơn | Toàn bộ chữ đổi; số và ngày đúng định dạng | 🟡 Một phần | `slide/evidence/t11-country-list-*.png` — mới có màn danh sách, máy API 30 |
| T12 | Không chặn main thread | Tấn Thành | StrictMode không báo; không ANR; danh sách 231 nước cuộn không giật | ✅ Đạt | `M5ApiThreadTest` 2 đạt; `M5CachedListUiTest`: 231 dòng trong 1891 ms, main thread nghẽn tối đa 1 ms, StrictMode 0 vi phạm |
| T13 | Overdraw | Minh Sơn | Ảnh trước/sau thật cho 3 màn chính | ⬜ Chưa chạy | Cần Toàn cầu + Chi tiết có trên main — nay đã có |
| T14 | Bàn giao | Hoàng Anh | Clone sạch build được; APK cài và chạy | ⬜ Chưa chạy | — |

**Hiện tại: 3 đạt · 3 một phần · 8 chưa chạy.**

---

## Ai còn nợ gì

| Người | Nợ |
|---|---|
| Sỹ Chúc | T01, và T09 phần tab |
| Công Thành | T07, T08, T09 phần màn chi tiết |
| Minh Sơn | T11 chạy lại đủ màn trên API 37, T13 ba cặp ảnh trước/sau |
| Hải Anh | T05, T06 |
| Công Dương | T10 chạy lại trong tab thật, không phải host debug |
| Tấn Thành | T02 phần giao diện |
| Hoàng Anh | T14, và mở issue `Kiem thu T01-T14` |

## Việc chặn cả bảng

Tính đến 01:00 ngày 25/10, **toàn bộ 6 phần việc vừa gộp vào `main` lần đầu
và chưa ai biên dịch bản gộp đó**. Trước khi chạy bất kỳ mã nào ở trên, phải
build được `main` đã. Nếu `main` không build thì mọi ô trong bảng đều vô nghĩa.

## Phân loại lỗi từ 31/10

- **P0** — crash, không build được, hoặc sai số liệu
- **P1** — hỏng một chức năng bắt buộc
- **P2** — còn lại
