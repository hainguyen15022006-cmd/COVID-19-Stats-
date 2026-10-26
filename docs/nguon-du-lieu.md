# Nguồn dữ liệu — câu trả lời chuẩn bị sẵn

Dành cho lúc trình bày. Thầy nhiều khả năng sẽ hỏi hai câu trong đây.

---

## Câu 1 — "Sao số liệu cũ thế, sao không lấy số mới hơn?"

**Nói thế này:**

> Nguồn disease.sh nhóm em dùng lấy lịch sử từ Johns Hopkins, mà JHU ngừng thu
> thập từ 10/3/2023 nên biểu đồ dừng ở đó. Hiện WHO vẫn còn cập nhật hàng tuần,
> mới nhất là đầu tháng 10 năm nay, nhưng họ chỉ phát hành CSV theo tuần, không
> có API JSON, và không kèm mã ISO hay ảnh cờ. Đổi sang nguồn đó phải viết lại
> tầng parse và đổi hợp đồng dữ liệu của cả nhóm, nên em chọn giữ disease.sh và
> ghi rõ giới hạn này trong README.

**Số liệu để chứng minh nếu bị hỏi tiếp:**

- Lịch sử có **1143 điểm**, từ `22/1/2020` đến `9/3/2023`. Sau đó không có điểm nào.
- Johns Hopkins CSSE đóng dự án ngày **10/3/2023**, không ai nối tiếp.
- `todayCases` và `todayDeaths` bằng **0** ở gần như mọi quốc gia — đó là giới hạn
  của nguồn, không phải lỗi app.

### Lý do giữ disease.sh thay vì chuyển sang WHO

| | disease.sh | WHO |
|---|---|---|
| Định dạng | JSON qua HTTP | CSV, tải cả file |
| Gọi theo từng nước | Có — `/countries/{iso2}` | Không, tải hết rồi tự lọc |
| Mã ISO2 / ISO3 | Có sẵn | Không, phải tự dựng bảng |
| Ảnh cờ | Có sẵn URL | Không có |
| Độ mịn lịch sử | Theo ngày, 1143 điểm | Theo tuần, khoảng 160 điểm |
| Mỗi lần gọi | ~76 KB một nước | Vài MB cả thế giới |

Bốn dòng giữa là lý do chính:

- Không có ISO2 thì mất cách tìm `vn`, `usa`, `uk` — đúng điều kiện đạt của T05.
- Không có cờ thì mất phần `ImageLoader` và `LruCache` của Volley.
- Dữ liệu theo tuần làm bộ lọc 30/90/365 ngày mất ý nghĩa.
- Tải một file CSV vài MB rồi lọc trên máy rất dễ chặn main thread — đúng cái T12 cấm.

WHO vẫn là nguồn chính thống và còn sống (mới nhất 4/10/2026). Nếu đây là app
thật cho người dùng thật thì chọn WHO. Với bài tập môn lập trình di động, nơi
phần được chấm là tầng mạng, thì disease.sh hợp hơn.

---

## Câu 2 — "Dữ liệu đứng im từ 2023 thì sao app còn cần mạng?"

**Nói thế này:**

> Dữ liệu ngừng cập nhật từ 3/2023 nên về nội dung thì đóng gói sẵn cũng được.
> Nhóm em vẫn gọi API vì ba lý do: đề bài yêu cầu tầng mạng thật với parse nền
> và cache, đóng gói lịch sử cả 231 nước sẽ làm app nặng thêm 17 MB trong khi
> người dùng chỉ xem vài nước, và 231 lá cờ vốn là ảnh trên mạng. Em có
> `FakeRepository` đọc từ `res/raw` dùng để phát triển và kiểm thử khi chưa có
> mạng — hai đường đi qua cùng một giao diện `CovidRepository` nên đổi qua lại
> chỉ sửa một dòng trong `CovidApp`.

**Số liệu để chứng minh:**

- Lịch sử một nước: **75,9 KB**. Nhân 231 nước: **17,1 MB** nếu đóng gói hết.
- 231 lá cờ đều là ảnh trên mạng, ví dụ `https://disease.sh/assets/img/flags/us.png`.
- `FakeRepository` và `NetworkRepository` cùng cài `CovidRepository`, đổi nguồn
  chỉ sửa một dòng trong `CovidApp.onCreate()`.

---

## Ba cái bẫy của nguồn này

Ghi lại để không ai "sửa nhầm" thành lỗi thật.

### 1. `/all` và `/historical` là hai nguồn khác nhau

| Chỗ hiển thị | Nguồn | Việt Nam |
|---|---|---|
| Tab Quốc gia, màn chi tiết | Worldometers | 11.625.195 |
| Điểm cuối biểu đồ lịch sử | Johns Hopkins | 11.526.994 |

Hai nơi thu thập khác nhau, chốt sổ ở hai thời điểm khác nhau nên số lệch là
bình thường. **Không ai được sửa cho khớp** — làm thế là bịa số.

### 2. `updated` không phải ngày của dữ liệu

Trường `updated` trong phản hồi là **thời điểm máy chủ trả lời**, lúc nào gọi
cũng ra "vài giây trước". Nó không liên quan gì đến ngày số liệu thay đổi lần cuối.

App lấy ngày thật từ **khoá cuối trong `timeline`** của `/historical`, hiển thị
thành "Số liệu tính đến 9 tháng 3, 2023". Đây chính là điều kiện đạt của **T01**:
*không có chữ "Cập nhật"*.

### 3. Số 0 thường nghĩa là "không báo cáo"

Nguồn ngừng cập nhật nên nhiều nước có `recovered = 0` hoặc `active = 0` — không
phải vì thật sự bằng không, mà vì họ ngừng đếm. Ví dụ Ấn Độ, Nhật, Thổ Nhĩ Kỳ đều
có `recovered = 0`.

Màn chi tiết vì thế hiện dấu `–` cho trường bằng 0, kèm dòng giải thích ngay trên
màn: *"– nghĩa là nguồn báo cáo giá trị bằng không hoặc không cung cấp số liệu."*
Đây là điều kiện đạt của **T07**, không phải lỗi.

---

## Tên nước vẫn là tiếng Anh

Ở chế độ tiếng Việt, tên nước vẫn hiện `USA`, `India`, `France`. Đây không phải
thiếu sót: tên nước đến từ dữ liệu API chứ không phải chuỗi giao diện, và có 231
nước nên không dịch thủ công được.

Tên châu lục thì ngược lại — chỉ có 6 nên nhóm dịch sẵn trong `strings.xml`, đồng
thời giữ riêng giá trị gốc tiếng Anh để lọc vẫn đúng.

---

## Nguồn tham khảo

- WHO COVID-19 Dashboard — https://data.who.int/dashboards/covid19/
- WHO, trang tải dữ liệu — https://data.who.int/dashboards/covid19/data
- disease.sh — https://disease.sh/docs/
