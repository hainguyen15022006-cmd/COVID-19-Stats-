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
    /** Show the 1, 2, 3... rank column. Off by default (watchlist). */
    private boolean showRank;

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

    /** Bat cot thu hang khi danh sach dang xep theo mot con so. */
    public void setShowRank(boolean show) {
        if (showRank != show) {
            showRank = show;
            notifyDataSetChanged();
        }
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
                starredCodes.contains(country.iso2), showRank ? position + 1 : 0);
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
        private final View numbers;
        private final TextView rank;
        private final TextView cases;
        private final TextView deaths;

        CountryViewHolder(@NonNull View row) {
            super(row);
            flag = row.findViewById(R.id.country_flag);
            star = row.findViewById(R.id.country_star);
            name = row.findViewById(R.id.country_name);
            numbers = row.findViewById(R.id.country_numbers);
            rank = row.findViewById(R.id.country_rank);
            cases = row.findViewById(R.id.country_cases);
            deaths = row.findViewById(R.id.country_deaths);
        }

        void bind(CountryStats country, ImageLoader imageLoader,
                  OnCountryClickListener listener,
                  OnCountryLongClickListener longClickListener,
                  boolean starred, int rankNumber) {
            NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
            name.setText(country.country);
            String casesText = format.format(country.cases);
            String deathsText = format.format(country.deaths);
            cases.setText(casesText);
            deaths.setText(itemView.getContext().getString(R.string.country_row_deaths, deathsText));
            // Screen readers read cases and deaths as one sentence, as before.
            numbers.setContentDescription(itemView.getContext().getString(
                    R.string.country_row_numbers, casesText, deathsText));
            if (rankNumber > 0) {
                rank.setText(String.format(Locale.ROOT, "%02d", rankNumber));
                rank.setVisibility(View.VISIBLE);
            } else {
                rank.setVisibility(View.GONE);
            }
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
