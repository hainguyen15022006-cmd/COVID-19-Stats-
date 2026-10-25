# Phần của Hoàng Anh: Formatters và CountryNameMatcher

Ba câu trả lời để sẵn khi thầy hỏi.

## 1. Phần này làm gì?

Hai lớp tiện ích nằm trong `util/`, dùng chung cho mọi màn hình.

- **`Formatters`** biến số và ngày thành chữ dễ đọc theo ngôn ngữ của máy.
  Ví dụ `1234567` hiện thành `1,234,567` khi máy để tiếng Anh, `1.234.567` khi để tiếng Việt.
  Ngày `3/9/23` trong dữ liệu hiện thành `Mar 9, 2023` hoặc `09/03/2023`.
  Số 0 hoặc dữ liệu thiếu hiện dấu `–`. Hàm `latestTimelineKey()` tìm ngày mới nhất cho dòng "Số liệu tính đến…".
- **`CountryNameMatcher`** quyết định một nước có khớp với chữ người dùng gõ vào ô tìm kiếm hay không.
  Gõ `viet`, `vn`, `Việt Nam` đều ra Vietnam; `usa`, `us`, `Mỹ` ra USA; `uk`, `Anh` ra UK.
  Không phân biệt hoa thường, không phân biệt dấu.

## 2. Dữ liệu đi vào, đi ra thế nào?

| Hàm | Vào | Ra |
|---|---|---|
| `Formatters.number(value, locale)` | số `long`, ngôn ngữ | chuỗi có dấu phân cách |
| `Formatters.numberOrDash(value, locale)` | số (có thể `null`) | chuỗi, hoặc `–` nếu 0 / null |
| `Formatters.timelineDate(key, locale)` | khóa ngày `"M/d/yy"` từ JSON timeline | ngày theo ngôn ngữ, hoặc `–` nếu sai |
| `Formatters.latestTimelineKey(keys)` | danh sách khóa ngày | khóa mới nhất, hoặc `null` |
| `CountryNameMatcher.score(query, name, iso2, iso3)` | chữ gõ + tên và mã nước | `0..5` (nhỏ = khớp tốt hơn), `-1` = không khớp |
| `CountryNameMatcher.matches(...)` | như trên | `true` / `false` |

Màn danh sách quốc gia gọi `score()` cho từng nước, bỏ nước có điểm `-1`, rồi xếp theo điểm tăng dần.
Nhờ vậy gõ `us` thì USA (trùng mã, điểm 0) đứng trên Australia (chỉ chứa chữ "us", điểm 5).

## 3. Vì sao chọn cách làm này?

- **Hàm thuần, không đụng giao diện hay mạng.** Không cần `Context`, không chạy luồng nền, nên test được bằng JUnit thường (12 test trong `app/src/test`), không cần emulator, và không làm hỏng phần của ai.
- **Dùng `NumberFormat` và `SimpleDateFormat` của Java** thay vì tự chèn dấu phẩy, để tự đúng theo ngôn ngữ. Không dùng `java.time` vì nó cần API 26, mà app hỗ trợ từ API 24.
- **So sánh ngày bằng giá trị ngày, không so chuỗi.** So chuỗi thì `"9/1/22"` lớn hơn `"10/1/22"`, sai.
- **Bỏ dấu bằng `Normalizer` (NFD)** rồi xóa dấu thanh, đổi `đ` thành `d`. Người Việt hay gõ không dấu, nên "Việt Nam" và "viet nam" phải ra cùng kết quả.
- **Thêm bảng tên gọi khác** (`my` → US, `anh` → GB, `trung quoc` → China) vì disease.sh chỉ có tên tiếng Anh.
- **Không ném lỗi khi dữ liệu xấu.** Chuỗi rỗng, `null`, ngày sai định dạng đều trả về giá trị an toàn, để app không crash (hỗ trợ T04).
