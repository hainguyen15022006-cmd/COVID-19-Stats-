# Phần của Minh Sơn: tài nguyên, hai ngôn ngữ, overdraw

Ba câu trả lời để sẵn khi thầy hỏi.

## 1. Phần này làm gì?

- **Hai ngôn ngữ (F08):** `res/values/strings.xml` (tiếng Anh, khoảng 90 chuỗi) và `res/values-vi/strings.xml` (tiếng Việt), đủ cặp, không thiếu chuỗi nào. Máy để tiếng Việt thì app tự đổi, không cần nút chuyển.
- **Màu và giao diện:** giao diện tối kiểu "phòng điều hành". `colors.xml` (nền xanh đêm, màu nhấn cam, mỗi chỉ số một màu: ca nhiễm, tử vong, hồi phục...), `styles.xml` (kiểu thẻ, kiểu chữ số), `themes.xml` dựa trên Material 3 Dark, `dimens.xml` cho khoảng cách và cỡ chữ.
- **Mảng cho spinner:** `arrays.xml` (các lựa chọn sắp xếp) có bản tiếng Việt riêng.
- **Icon:** icon app (launcher), icon ngôi sao, icon thanh tab dưới đáy, nút quay lại, làm mới, tìm kiếm, tất cả dạng vector.
- **Tối ưu overdraw (T13):** kiểm tra bằng *Debug GPU overdraw*.

## 2. Dữ liệu đi vào, đi ra thế nào?

Phần này không gọi mạng. Code giao diện của mọi người đọc tài nguyên qua mã `R`:

- Chữ: `getString(R.string.global_loading)` hoặc `android:text="@string/..."` trong layout.
- Màu: `ContextCompat.getColor(context, R.color.chart_deaths)`.

Android tự chọn thư mục theo cấu hình máy: tiếng Việt thì lấy `values-vi`, không có thì quay về `values`. App luôn dùng một giao diện tối nên không cần thư mục `values-night`. Số và ngày đổi định dạng (dấu chấm, "9 thg 3, 2023") nhờ `Formatters` dùng `Locale` của máy.

## 3. Vì sao chọn cách làm này?

- **Không viết chữ cứng trong code** để dịch được và để thầy chấm đúng yêu cầu. Thêm ngôn ngữ mới chỉ cần thêm một thư mục `values-xx`.
- **Đặt tên màu theo ý nghĩa** (`chart_deaths`, `offline_container`) chứ không theo màu (`red`), nên khi nhóm đổi sang giao diện tối chỉ cần đổi giá trị màu, code Java gần như không phải sửa.
- **Icon dạng vector** nét căng ở mọi độ phân giải, nhẹ hơn nhiều file PNG.
- **Overdraw:** theme đặt `windowBackground` đúng màu nền và các layout gốc không đặt thêm nền trùng; chỉ các thẻ số liệu có nền riêng. Kết quả cả 3 màn chính chủ yếu **xanh dương nhạt** (vẽ 1 lần), không có vùng đỏ (vẽ 4 lần trở lên). Màn càng ít vẽ chồng thì càng nhẹ GPU, cuộn càng mượt.
