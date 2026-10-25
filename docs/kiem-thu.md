# Ma trận kiểm thử T01–T14

Giữ bởi Hải Anh (cổng chất lượng). Cập nhật lần cuối: 25/10/2026 (Hoàng Anh, theo issue #12).

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
| T01 | Số liệu toàn cầu | Sỹ Chúc | Đủ trường, số có dấu phân cách; dòng "Số liệu tính đến…" lấy từ `timeline`, không có chữ "Cập nhật" | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t01-global-en.png`, `t01-global-vi.png` |
| T02 | Mất mạng, chưa có cache | Tấn Thành | Thông báo dễ hiểu + nút Thử lại; không crash | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t02-no-cache-offline.png`; tầng dữ liệu `M3RepositoryTest` 18 đạt |
| T03 | Mất mạng, đã có cache | Công Dương | Hiện dữ liệu lần trước + dòng "Đang offline" | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t03-offline-global.png`, `t03-offline-countries.png`; `docs/evidence/f12-2026-10-24/` |
| T04 | Parser chịu dữ liệu thiếu | Tấn Thành | Trường thiếu hoặc null không làm crash | ✅ Đạt | `M5ParserRobustnessTest` 4 đạt — null, số cực lớn, thiếu điểm, ngày trùng/sai |
| T05 | Tìm kiếm | Hải Anh | `viet`, `vn`, `usa`, `us`, `uk` đều ra kết quả; không phân biệt hoa thường | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t05-search-usa.png`, `t05-search-uk.png`, `t05-search-vn.png` |
| T06 | Sắp xếp, lọc châu lục | Hải Anh | Thứ tự đúng, kết quả lọc đúng | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t06-sort-deaths.png` (USA, Brazil, India dẫn đầu), `t06-filter-asia.png` (chỉ còn nước châu Á) |
| T07 | Chi tiết quốc gia | Công Thành | Mở đúng nước theo `iso2`; trường bằng 0 hiện dấu `–` | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t07-detail-vn.png`, `t07-zero-dash-tanzania.png` |
| T08 | Biểu đồ | Công Thành | Đổi 30/90/365/tất cả thì biểu đồ đổi; mặc định là tất cả | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t08-30d.png`, `t08-90d.png`, `t08-365d.png`, `t08-all.png` |
| T09 | Xoay màn hình | Công Thành + Sỹ Chúc | Không mất dữ liệu, không gọi lại API | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t09-global-landscape.png`, `t09-detail-landscape.png` |
| T10 | Theo dõi | Công Dương | Còn sau khi tắt app; vuốt xóa cập nhật ngay | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t10-after-restart.png`, `t10-after-swipe.png`; `docs/evidence/storage-2026-10-25/` |
| T11 | Tiếng Việt | Minh Sơn | Toàn bộ chữ đổi; số và ngày đúng định dạng | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t01-global-vi.png`, `t11-countries-vi.png`, `t11-watchlist-vi.png`, `t11-offline-vi.png` (API 37) |
| T12 | Không chặn main thread | Tấn Thành | StrictMode không báo; không ANR; danh sách 231 nước cuộn không giật | ✅ Đạt | `M5ApiThreadTest` 2 đạt; `M5CachedListUiTest`: 231 dòng trong 1891 ms, main thread nghẽn tối đa 1 ms, StrictMode 0 vi phạm |
| T13 | Overdraw | Minh Sơn | Ảnh trước/sau thật cho 3 màn chính | ✅ Đạt (ảnh "sau") | `docs/evidence/kiem-thu-2026-10-25/t13-overdraw-global.png`, `t13-overdraw-countries.png`, `t13-overdraw-detail.png` — chủ yếu xanh dương, không có vùng đỏ |
| T14 | Bàn giao | Hoàng Anh | Clone sạch build được; APK cài và chạy | ✅ Đạt | `docs/evidence/kiem-thu-2026-10-25/t14-clean-build-run.png` — clone sạch main f263f5f, BUILD SUCCESSFUL 7s |

**Lần chạy đầu (25/10): 14/14 đạt, đủ bằng chứng.** Chạy lại đủ bộ: 31/10 (M5). Kết quả chi tiết trong issue #12.

---

## Phân loại lỗi từ 31/10

- **P0** — crash, không build được, hoặc sai số liệu
- **P1** — hỏng một chức năng bắt buộc
- **P2** — còn lại
