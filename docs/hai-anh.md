# Phần của Hải Anh: danh sách quốc gia và cổng chất lượng

Ba câu trả lời để sẵn khi thầy hỏi.

## 1. Phần này làm gì?

- **`CountryListFragment`** là tab Quốc gia:
  - F02: danh sách khoảng 231 nước, có cờ, cuộn mượt.
  - F03: ô tìm kiếm lọc ngay khi gõ, khớp theo tên, mã ISO2 (`vn`, `us`) và ISO3 (`vnm`, `usa`).
  - F09: sắp xếp theo tổng ca, tử vong, ca trên một triệu dân, hoặc tên.
  - F10: lọc theo châu lục.
  - F13: giữ lâu một nước để gắn/bỏ sao theo dõi.
  - Kéo xuống để làm mới, nút Thử lại khi lỗi, dòng "Đang offline" khi xem dữ liệu cũ.
- Chạm vào một nước thì mở màn chi tiết, gửi kèm mã `country_iso2`.
- **`CountryAdapter`** là adapter của `RecyclerView`: mỗi dòng có cờ, tên, số ca và số tử vong.
- **Cổng chất lượng:** giữ ma trận kiểm thử T01–T14 (`docs/kiem-thu.md`, issue *Kiem thu T01-T14*), chỉ tính một mục là đạt khi có ảnh hoặc log.

## 2. Dữ liệu đi vào, đi ra thế nào?

1. Vào tab: gọi `repository.getCountries(forceRefresh, callback)` → `/v3/covid-19/countries`, nhận `List<CountryStats>`.
2. Danh sách gốc giữ trong `allCountries`. **Mọi thao tác tìm, sắp, lọc chỉ làm trên bộ nhớ**, không gọi lại mạng.
3. `applyFilters()`: bỏ nước khác châu lục đang chọn → bỏ nước không khớp chữ gõ → sắp xếp → `adapter.setItems(...)`.
4. Danh sách châu lục lấy từ chính dữ liệu đã tải, rồi dịch ra chữ hiển thị bằng `strings.xml`. Giá trị gốc tiếng Anh được giữ riêng để so sánh cho đúng.

## 3. Vì sao chọn cách làm này?

- **`RecyclerView` + `ViewHolder`** chỉ tạo khoảng hơn chục dòng đủ màn hình rồi tái sử dụng khi cuộn, nên 231 nước vẫn mượt. `ListView` hay tạo hết từng dòng sẽ giật.
- **Cờ tải bằng `ImageLoader` của Volley**, có cache trong bộ nhớ (`LruCache`), cuộn lên xuống không tải lại ảnh.
- **Tìm/sắp/lọc trên bộ nhớ** vì 231 phần tử là rất nhỏ. Gọi mạng mỗi lần gõ phím vừa chậm vừa tốn.
- **Spinner dùng mảng trong `arrays.xml`**, không viết chữ cứng trong code, nên đổi ngôn ngữ là tự đổi.
- **Kiểm tra `isAdded()` trong callback**, vì người dùng có thể đã rời tab trước khi mạng trả về. Không kiểm tra thì app crash.
