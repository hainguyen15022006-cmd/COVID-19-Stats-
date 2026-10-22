package vn.edu.usth.covidstats.ui;

import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.model.CountryStats;

/**
 * Do du lieu tung nuoc vao mot dong cua RecyclerView.
 * La co tai bang ImageLoader cua Volley va duoc giu lai trong bo nho,
 * nen cuon len cuon xuong khong phai tai lai anh.
 */
public class CountryAdapter extends RecyclerView.Adapter<CountryAdapter.CountryViewHolder> {

    /** Fragment cai dat de biet nguoi dung bam vao nuoc nao. */
    public interface OnCountryClickListener {
        void onCountryClick(CountryStats country);
    }

    private final List<CountryStats> items = new ArrayList<>();
    private final OnCountryClickListener listener;
    private final ImageLoader imageLoader;

    public CountryAdapter(RequestQueue queue, OnCountryClickListener listener) {
        this.listener = listener;
        this.imageLoader = new ImageLoader(queue, new FlagCache());
    }

    /** Thay toan bo danh sach dang hien (sau khi loc hoac sap xep lai). */
    public void setItems(List<CountryStats> countries) {
        items.clear();
        items.addAll(countries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CountryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_country, parent, false);
        return new CountryViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull CountryViewHolder holder, int position) {
        holder.bind(items.get(position), imageLoader, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** Bo nho dem anh co, toi da 1/8 bo nho ma he thong cap cho app. */
    private static class FlagCache implements ImageLoader.ImageCache {
        private final LruCache<String, Bitmap> cache;

        FlagCache() {
            int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
            cache = new LruCache<String, Bitmap>(maxKb / 8) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }

        @Override
        public Bitmap getBitmap(String url) {
            return cache.get(url);
        }

        @Override
        public void putBitmap(String url, Bitmap bitmap) {
            cache.put(url, bitmap);
        }
    }

    /**
     * Giu san cac View cua mot dong.
     * Nho vay findViewById chi chay khi tao dong moi, khong chay lai moi lan cuon.
     */
    static class CountryViewHolder extends RecyclerView.ViewHolder {
        private final NetworkImageView flag;
        private final TextView name;
        private final TextView numbers;

        CountryViewHolder(@NonNull View row) {
            super(row);
            flag = row.findViewById(R.id.country_flag);
            name = row.findViewById(R.id.country_name);
            numbers = row.findViewById(R.id.country_numbers);
        }

        void bind(CountryStats country, ImageLoader imageLoader, OnCountryClickListener listener) {
            NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
            name.setText(country.country);
            numbers.setText(itemView.getContext().getString(R.string.country_row_numbers,
                    format.format(country.cases), format.format(country.deaths)));
            flag.setImageUrl(country.flag, imageLoader);
            itemView.setOnClickListener(v -> listener.onCountryClick(country));
        }
    }
}
