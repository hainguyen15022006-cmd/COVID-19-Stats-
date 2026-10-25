package vn.edu.usth.covidstats.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import vn.edu.usth.covidstats.CovidApp;
import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.WatchlistStore;
import vn.edu.usth.covidstats.model.CountryStats;

/**
 * Tab Quoc gia.
 * F02 danh sach, F03 tim kiem, F09 sap xep, F10 loc theo chau luc.
 *
 * Danh sach duoc tai mot lan qua CovidRepository roi giu trong allCountries.
 * Moi thao tac tim/sap/loc chi lam tren bo nho, khong goi lai mang.
 */
public class CountryListFragment extends Fragment
        implements CountryAdapter.OnCountryClickListener {

    private static final int SORT_CASES = 0;
    private static final int SORT_DEATHS = 1;
    private static final int SORT_PER_MILLION = 2;

    /** Ten extra khi mo man chi tiet. Phai trung voi ban dac ta. */
    public static final String EXTRA_COUNTRY_ISO2 = "country_iso2";

    private final List<CountryStats> allCountries = new ArrayList<>();

    /**
     * Gia tri chau luc goc tu API, cung thu tu voi cac muc trong spinner.
     * Vi tri 0 la null, nghia la "tat ca chau luc".
     * Phai giu rieng vi chu tren spinner da dich sang tieng Viet, khong con khop du lieu.
     */
    private final List<String> continentValues = new ArrayList<>();

    private CountryAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private RecyclerView listView;
    private TextView emptyView;
    private View emptyBox;
    private Button retryButton;
    /** Loi cua lan tai gan nhat; null nghia la lan tai truoc thanh cong. */
    private String loadError;
    private WatchlistStore watchlistStore;

    /** WatchlistStore bao moi khi danh sach sao doi, ke ca khi doi tu man chi tiet. */
    private final WatchlistStore.Listener watchlistListener = codes -> {
        if (isAdded()) {
            adapter.setStarredCodes(codes);
        }
    };
    private EditText searchInput;
    private Spinner sortSpinner;
    private Spinner continentSpinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Fragment nap layout o day va phai tra ve View, khac Activity dung setContentView.
        View view = inflater.inflate(R.layout.fragment_country_list, container, false);

        searchInput = view.findViewById(R.id.country_search);
        sortSpinner = view.findViewById(R.id.country_sort);
        continentSpinner = view.findViewById(R.id.country_continent);
        refreshLayout = view.findViewById(R.id.country_refresh);
        listView = view.findViewById(R.id.country_list);
        emptyView = view.findViewById(R.id.country_empty);
        emptyBox = view.findViewById(R.id.country_empty_box);
        retryButton = view.findViewById(R.id.country_retry);

        CovidApp app = (CovidApp) requireActivity().getApplication();
        adapter = new CountryAdapter(app.getImageLoader(), this);
        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        listView.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        listView.setAdapter(adapter);

        adapter.setOnCountryLongClickListener(this::toggleWatchlist);
        watchlistStore = WatchlistStore.getInstance(requireContext());
        retryButton.setOnClickListener(v -> load(true));

        setUpSortSpinner();
        setUpContinentSpinner();
        setUpSearch();
        refreshLayout.setOnRefreshListener(() -> load(true));

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        watchlistStore.addListener(watchlistListener);
        watchlistStore.getCodes(new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> codes, boolean fromCache) {
                if (isAdded()) {
                    adapter.setStarredCodes(codes);
                }
            }

            @Override
            public void onError(String message) {
                // Khong doc duoc danh sach sao thi chi la khong co ngoi sao nao hien.
            }
        });
        load(false);
    }

    @Override
    public void onPause() {
        // ViewPager giu ca ba tab song cung luc, nen o tim van giu con tro khi
        // nguoi dung vuot sang tab khac va ban phim di theo, che mat man hinh do.
        // Roi tab nay thi nha con tro va dong ban phim lai.
        if (searchInput != null && searchInput.hasFocus()) {
            InputMethodManager keyboard = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
            searchInput.clearFocus();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        watchlistStore.removeListener(watchlistListener);
        super.onDestroyView();
    }

    // ----- Tai du lieu -----

    private void load(boolean forceRefresh) {
        refreshLayout.setRefreshing(true);
        CovidApp app = (CovidApp) requireActivity().getApplication();
        app.getRepository().getCountries(forceRefresh, new Callback<List<CountryStats>>() {
            @Override
            public void onSuccess(List<CountryStats> data, boolean fromCache) {
                // Callback ve main thread, nhung nguoi dung co the da roi man hinh.
                if (!isAdded()) {
                    return;
                }
                refreshLayout.setRefreshing(false);
                loadError = null;
                allCountries.clear();
                allCountries.addAll(data);
                setUpContinentSpinner();
                applyFilters();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                refreshLayout.setRefreshing(false);
                loadError = message;
                // Da co du lieu cu tren man thi bao nhanh; chua co gi thi bao trong khung rong.
                if (!allCountries.isEmpty()) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                }
                applyFilters();
            }
        });
    }

    // ----- Cai dat cac o dieu khien -----

    private void setUpSortSpinner() {
        ArrayAdapter<CharSequence> options = ArrayAdapter.createFromResource(requireContext(),
                R.array.country_sort_options, android.R.layout.simple_spinner_item);
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(options);
        sortSpinner.setOnItemSelectedListener(new SimpleSelectionListener());
    }

    /** Danh sach chau luc lay tu chinh du lieu da tai, khong goi them endpoint nao. */
    private void setUpContinentSpinner() {
        TreeSet<String> continents = new TreeSet<>();
        for (CountryStats country : allCountries) {
            if (!country.continent.isEmpty()) {
                continents.add(country.continent);
            }
        }

        continentValues.clear();
        continentValues.add(null);
        continentValues.addAll(continents);

        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.country_continent_all));
        for (String raw : continents) {
            labels.add(continentLabel(raw));
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        continentSpinner.setAdapter(spinnerAdapter);
        continentSpinner.setOnItemSelectedListener(new SimpleSelectionListener());
    }

    private void setUpSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // ----- Loc va sap xep -----

    private void applyFilters() {
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String continent = selectedContinent();

        List<CountryStats> shown = new ArrayList<>();
        for (CountryStats country : allCountries) {
            if (continent != null && !continent.equals(country.continent)) {
                continue;
            }
            if (matches(country, query)) {
                shown.add(country);
            }
        }
        sort(shown, sortSpinner.getSelectedItemPosition());
        adapter.setItems(shown);
        showEmptyViewIfNeeded(shown.isEmpty());
    }

    /** Tra ve gia tri goc cua API, hoac null neu dang chon "tat ca chau luc". */
    private String selectedContinent() {
        int position = continentSpinner.getSelectedItemPosition();
        if (position < 0 || position >= continentValues.size()) {
            return null;
        }
        return continentValues.get(position);
    }

    /**
     * Doi ten chau luc tu API sang chu hien thi.
     * API chi tra tieng Anh va chi co 6 gia tri nen dich duoc het.
     * Gap gia tri la thi hien nguyen van, khong bo mat muc do.
     */
    private String continentLabel(String raw) {
        switch (raw) {
            case "Africa":
                return getString(R.string.continent_africa);
            case "Asia":
                return getString(R.string.continent_asia);
            case "Australia-Oceania":
                return getString(R.string.continent_australia_oceania);
            case "Europe":
                return getString(R.string.continent_europe);
            case "North America":
                return getString(R.string.continent_north_america);
            case "South America":
                return getString(R.string.continent_south_america);
            default:
                return raw;
        }
    }

    /**
     * Tim theo ten, ma iso2 va iso3.
     * Can thiet vi API tra ve ten viet tat: USA, UK, S. Korea.
     * Go "usa", "us" hay "united" deu phai ra ket qua.
     */
    private boolean matches(CountryStats country, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return country.country.toLowerCase(Locale.ROOT).contains(query)
                || country.iso2.toLowerCase(Locale.ROOT).contains(query)
                || country.iso3.toLowerCase(Locale.ROOT).contains(query);
    }

    private void sort(List<CountryStats> countries, int option) {
        switch (option) {
            case SORT_CASES:
                Collections.sort(countries, (a, b) -> Long.compare(b.cases, a.cases));
                break;
            case SORT_DEATHS:
                Collections.sort(countries, (a, b) -> Long.compare(b.deaths, a.deaths));
                break;
            case SORT_PER_MILLION:
                Collections.sort(countries,
                        (a, b) -> Double.compare(b.casesPerOneMillion, a.casesPerOneMillion));
                break;
            default:
                Collections.sort(countries,
                        (a, b) -> a.country.compareToIgnoreCase(b.country));
                break;
        }
    }

    private void showEmptyViewIfNeeded(boolean empty) {
        // Dang tai thi de vong cho noi, khong hien chu "chua co du lieu".
        if (!empty || refreshLayout.isRefreshing()) {
            emptyBox.setVisibility(View.GONE);
            return;
        }
        boolean failed = loadError != null && allCountries.isEmpty();
        if (failed) {
            emptyView.setText(loadError);
        } else {
            emptyView.setText(allCountries.isEmpty()
                    ? R.string.country_empty_no_data
                    : R.string.country_empty_no_result);
        }
        // T02: chi moi nguoi dung bam lai khi that su tai hong.
        retryButton.setVisibility(failed ? View.VISIBLE : View.GONE);
        emptyBox.setVisibility(View.VISIBLE);
    }

    // ----- F13: cham giu de theo doi / bo theo doi -----

    private void toggleWatchlist(CountryStats country) {
        watchlistStore.toggle(country.iso2, new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> codes, boolean fromCache) {
                if (!isAdded()) {
                    return;
                }
                adapter.setStarredCodes(codes);
                boolean added = codes.contains(country.iso2);
                Toast.makeText(requireContext(),
                        getString(added ? R.string.country_watchlist_added
                                        : R.string.country_watchlist_removed,
                                country.country),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // ----- Bam vao mot dong -----

    @Override
    public void onCountryClick(CountryStats country) {
        Intent intent = new Intent(requireContext(), CountryDetailActivity.class);
        intent.putExtra(CountryDetailActivity.EXTRA_COUNTRY_ISO2, country.iso2);
        startActivity(intent);
    }

    /** Dung chung cho hai spinner: chon gi cung chi loc lai danh sach. */
    private class SimpleSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            applyFilters();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
