# Phần của Minh Sơn: tài nguyên, hai ngôn ngữ, overdraw

Ba câu trả lời để sẵn khi thầy hỏi.

## 1. Phần này làm gì?

- **Hai ngôn ngữ (F08):** `res/values/strings.xml` (tiếng Anh, khoảng 90 chuỗi) và `res/values-vi/strings.xml` (tiếng Việt), đủ cặp, không thiếu chuỗi nào. Máy để tiếng Việt thì app tự đổi, không cần nút chuyển.
- **Màu và giao diện:** `colors.xml` (màu chính, màu biểu đồ ca nhiễm/tử vong, màu báo offline), `values-night/` cho chế độ tối, `themes.xml` dựa trên Material 3 DayNight, `dimens.xml` cho khoảng cách và cỡ chữ.
- **Mảng cho spinner:** `arrays.xml` (các lựa chọn sắp xếp) có bản tiếng Việt riêng.
- **Icon:** icon app (launcher) và 2 icon ngôi sao (rỗng/đầy) dạng vector.
- **Tối ưu overdraw (T13):** kiểm tra bằng *Debug GPU overdraw*, ảnh trong `docs/evidence/kiem-thu-2026-10-25/t13-*.png`.

## 2. Dữ liệu đi vào, đi ra thế nào?

Phần này không gọi mạng. Code giao diện của mọi người đọc tài nguyên qua mã `R`:

- Chữ: `getString(R.string.global_loading)` hoặc `android:text="@string/..."` trong layout.
- Màu: `ContextCompat.getColor(context, R.color.chart_deaths)`.

Android tự chọn thư mục theo cấu hình máy: tiếng Việt thì lấy `values-vi`, chế độ tối thì lấy `values-night`, không có thì quay về `values`. Số và ngày đổi định dạng (dấu chấm, "9 thg 3, 2023") nhờ `Formatters` dùng `Locale` của máy.

## 3. Vì sao chọn cách làm này?

- **Không viết chữ cứng trong code** để dịch được và để thầy chấm đúng yêu cầu. Thêm ngôn ngữ mới chỉ cần thêm một thư mục `values-xx`.
- **Đặt tên màu theo ý nghĩa** (`chart_deaths`, `offline_container`) chứ không theo màu (`red`), nên chế độ tối chỉ cần đổi giá trị, không phải sửa code.
- **Icon dạng vector** nét căng ở mọi độ phân giải, nhẹ hơn nhiều file PNG.
- **Overdraw:** theme đặt `windowBackground` đúng màu nền và các layout không đặt thêm nền trùng. Kết quả cả 3 màn chính chủ yếu **xanh dương nhạt** (vẽ 1 lần), không có vùng đỏ (vẽ 4 lần trở lên). Màn càng ít vẽ chồng thì càng nhẹ GPU, cuộn càng mượt.
