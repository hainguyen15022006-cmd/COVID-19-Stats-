package vn.edu.usth.covidstats.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /**
     * F13 - cu chi. Tach rieng khoi OnCountryClickListener de man Theo doi
     * cua Cong Duong dung adapter nay ma khong phai cai dat them ham nao.
     */
    public interface OnCountryLongClickListener {
        void onCountryLongClick(CountryStats country);
    }

    private final List<CountryStats> items = new ArrayList<>();
    private final OnCountryClickListener listener;
    private final ImageLoader imageLoader;
    /** Ma iso2 cua cac nuoc dang theo doi; dung de hien ngoi sao tren dong. */
    private Set<String> starredCodes = Collections.emptySet();
    private OnCountryLongClickListener longClickListener;

    public CountryAdapter(ImageLoader imageLoader, OnCountryClickListener listener) {
        this.listener = listener;
        this.imageLoader = imageLoader;
    }

    public void setOnCountryLongClickListener(OnCountryLongClickListener listener) {
        this.longClickListener = listener;
    }

    /** Cap nhat ngoi sao sau khi WatchlistStore bao danh sach doi. */
    public void setStarredCodes(Set<String> codes) {
        this.starredCodes = codes == null ? Collections.emptySet() : new HashSet<>(codes);
        notifyDataSetChanged();
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
        CountryStats country = items.get(position);
        holder.bind(country, imageLoader, listener, longClickListener,
                starredCodes.contains(country.iso2));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Giu san cac View cua mot dong.
     * Nho vay findViewById chi chay khi tao dong moi, khong chay lai moi lan cuon.
     */
    static class CountryViewHolder extends RecyclerView.ViewHolder {
        private final NetworkImageView flag;
        private final ImageView star;
        private final TextView name;
        private final TextView numbers;

        CountryViewHolder(@NonNull View row) {
            super(row);
            flag = row.findViewById(R.id.country_flag);
            star = row.findViewById(R.id.country_star);
            name = row.findViewById(R.id.country_name);
            numbers = row.findViewById(R.id.country_numbers);
        }

        void bind(CountryStats country, ImageLoader imageLoader,
                  OnCountryClickListener listener,
                  OnCountryLongClickListener longClickListener,
                  boolean starred) {
            NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
            name.setText(country.country);
            numbers.setText(itemView.getContext().getString(R.string.country_row_numbers,
                    format.format(country.cases), format.format(country.deaths)));
            flag.setImageUrl(country.flag, imageLoader);
            star.setVisibility(starred ? View.VISIBLE : View.GONE);
            itemView.setOnClickListener(v -> listener.onCountryClick(country));
            // Tra ve true de Android biet cu chi da duoc xu ly, khong goi tiep onClick.
            itemView.setOnLongClickListener(v -> {
                if (longClickListener == null) {
                    return false;
                }
                longClickListener.onCountryLongClick(country);
                return true;
            });
        }
    }
}
