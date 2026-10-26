# Phần của Sỹ Chúc: màn chính và tab Toàn cầu

Ba câu trả lời để sẵn khi thầy hỏi.

## 1. Phần này làm gì?

- **`MainActivity`** là màn hình đầu tiên. Có App Bar (tiêu đề đổi theo trang đang mở) với nút Refresh, `ViewPager` để vuốt qua lại, và `TabLayout` 3 tab có icon đặt ở đáy màn hình như thanh điều hướng: **Toàn cầu**, **Quốc gia**, **Theo dõi**.
- **`MainPagerAdapter`** quyết định trang nào là Fragment nào (`GlobalFragment`, `CountryListFragment`, `WatchlistFragment`) và lấy tên tab từ `strings.xml`, nên tự đổi tiếng Việt.
- **`GlobalFragment`** (F01, F07) hiện 6 con số toàn cầu (ca nhiễm trong thẻ lớn có đường biểu đồ nhỏ; tử vong, hồi phục, đang điều trị, nguy kịch, xét nghiệm trong các thẻ nhỏ), dòng "Số liệu tính đến…", và biểu đồ ca nhiễm/tử vong theo thời gian. Có kéo xuống để làm mới, nút Thử lại khi lỗi, và dòng "Đang offline" khi đang xem dữ liệu cũ.
- Nút **Refresh** chỉ hiện ở tab Toàn cầu và Theo dõi, bấm thì gọi `refresh()` của đúng tab đang mở.

## 2. Dữ liệu đi vào, đi ra thế nào?

| Bước | Chi tiết |
|---|---|
| Gọi | `repository.getGlobal(forceRefresh, callback)` → `/v3/covid-19/all` |
| | `repository.getHistory("all", ALL_DAYS, callback)` → `/v3/covid-19/historical/all?lastdays=all` |
| Nhận | `GlobalStats` (6 số) và `HistoricalData` (các mốc ngày → số ca, số tử vong) |
| Giữ | Đặt vào `GlobalModel` (một `ViewModel`, dùng `MutableLiveData`) |
| Hiện | Fragment `observe` LiveData: có dữ liệu thì điền số, có lỗi thì hiện chữ lỗi + nút Thử lại |

Ngày "Số liệu tính đến…" lấy từ **ngày cuối cùng trong timeline**, không lấy trường `updated`, vì `updated` chỉ là giờ server trả lời.

## 3. Vì sao chọn cách làm này?

- **`ViewPager` + `TabLayout`** là cách đơn giản nhất để có 3 tab vuốt được, đúng như môn học.
- **Dùng `ViewModel` để giữ dữ liệu.** Khi xoay màn hình, Activity và Fragment bị tạo lại, nhưng `ViewModel` thì không. Nhờ vậy xoay màn hình không mất số và **không gọi lại API** (T09).
- **`setOffscreenPageLimit(2)`** giữ cả 3 tab trong bộ nhớ, vuốt qua lại không phải tải lại.
- **Kiểm tra `isOnline()` trước khi báo offline.** Repository trả `fromCache = true` cả khi cache còn mới lúc có mạng, nên phải kiểm tra thêm mạng thì mới hiện "Đang offline" đúng lúc.
- **Biểu đồ 2 trục** (ca nhiễm trục trái, tử vong trục phải) vì số tử vong nhỏ hơn số ca khoảng 100 lần. Chung một trục thì đường tử vong nằm bẹt dưới đáy.
