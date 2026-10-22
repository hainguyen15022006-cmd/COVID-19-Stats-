package vn.edu.usth.covidstats.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import java.util.TreeSet;

import vn.edu.usth.covidstats.CovidApp;
import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.data.Callback;
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

    private CountryAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private RecyclerView listView;
    private TextView emptyView;
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

        CovidApp app = (CovidApp) requireActivity().getApplication();
        adapter = new CountryAdapter(app.getRequestQueue(), this);
        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        listView.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        listView.setAdapter(adapter);

        setUpSortSpinner();
        setUpContinentSpinner();
        setUpSearch();
        refreshLayout.setOnRefreshListener(() -> load(true));

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        load(false);
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
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
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
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.country_continent_all));
        options.addAll(continents);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, options);
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

    /** Tra ve null neu dang chon "tat ca chau luc". */
    private String selectedContinent() {
        if (continentSpinner.getAdapter() == null
                || continentSpinner.getSelectedItemPosition() <= 0) {
            return null;
        }
        return (String) continentSpinner.getSelectedItem();
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
            emptyView.setVisibility(View.GONE);
            return;
        }
        emptyView.setText(allCountries.isEmpty()
                ? R.string.country_empty_no_data
                : R.string.country_empty_no_result);
        emptyView.setVisibility(View.VISIBLE);
    }

    // ----- Bam vao mot dong -----

    @Override
    public void onCountryClick(CountryStats country) {
        // Man chi tiet do Cong Thanh lam. Khi CountryDetailActivity co tren main thi doi thanh:
        //
        // Intent intent = new Intent(requireContext(), CountryDetailActivity.class);
        // intent.putExtra(EXTRA_COUNTRY_ISO2, country.iso2);
        // startActivity(intent);
        Toast.makeText(requireContext(),
                getString(R.string.country_selected, country.country),
                Toast.LENGTH_SHORT).show();
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
