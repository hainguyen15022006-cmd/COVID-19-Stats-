# Phần của Công Thành: màn chi tiết và biểu đồ

Ba câu trả lời để sẵn khi thầy hỏi.

## 1. Phần này làm gì?

- **`CountryDetailActivity`** (F04, F07) là màn chi tiết một nước:
  - Cờ, tên, mũi tên Quay lại, nút ☆ gắn sao theo dõi.
  - Vòng tròn tỷ lệ hồi phục (số ca hồi phục / tổng ca), tính ngay trong app từ dữ liệu đã tải; nguồn không có số hồi phục thì hiện "–".
  - 8 chỉ số: ca mắc, tử vong, đã khỏi, đang điều trị, xét nghiệm, dân số, ca và tử vong trên một triệu dân.
  - Trường bằng 0 hoặc thiếu hiện dấu **"–"**, kèm chú thích phía dưới (T07).
  - Biểu đồ lịch sử với 4 lựa chọn **30N / 90N / 365N / TẤT CẢ** (một thanh bấm liền, vẫn là `RadioGroup`), mặc định TẤT CẢ (T08).
  - Có layout riêng cho màn ngang (`res/layout-land/activity_country_detail.xml`): số bên trái, biểu đồ bên phải.
- **`ChartHelper`** là lớp dùng chung để vẽ biểu đồ đường, dùng cho cả màn chi tiết và tab Toàn cầu.

## 2. Dữ liệu đi vào, đi ra thế nào?

1. Màn danh sách mở màn này bằng `Intent` có extra `country_iso2` (ví dụ `"VN"`).
2. Gọi `repository.getCountry(iso2, callback)` → `/v3/covid-19/countries/{iso2}`, nhận `CountryStats`.
3. Gọi `repository.getHistory(iso2, days, callback)` → `/v3/covid-19/historical/{iso2}?lastdays=…`, nhận `HistoricalData`.
4. `ChartHelper.drawLine(chart, data, days)` biến các mốc ngày thành 2 đường: ca mắc (trục trái, xanh) và tử vong (trục phải, đỏ). Nhãn ngày định dạng theo ngôn ngữ máy.

## 3. Vì sao chọn cách làm này?

- **Chỉ truyền mã `iso2` qua Intent**, không truyền cả đối tượng. Gọn, đúng hợp đồng chung của nhóm, và màn này tự tải được nếu mở lại.
- **Giữ trạng thái khi xoay màn hình** bằng `onRetainCustomNonConfigurationInstance` và `onSaveInstanceState`. Xoay không mất dữ liệu, không mất lựa chọn 30/90/365, không gọi lại API (T09).
- **Hai trục Y** vì tử vong nhỏ hơn số ca khoảng 100 lần. Chung một trục thì đường tử vong nằm bẹt.
- **Dấu "–" thay cho số 0**, vì nguồn disease.sh ngừng cập nhật nhiều nước từ 3/2023. Số 0 thường nghĩa là "không báo cáo" chứ không phải "không có ca".
- **Gom code vẽ vào `ChartHelper`** để tab Toàn cầu và màn chi tiết vẽ giống nhau, sửa một chỗ là đúng cả hai.
- **Bật pinch-zoom** trên biểu đồ (F13) để xem kỹ một giai đoạn.
