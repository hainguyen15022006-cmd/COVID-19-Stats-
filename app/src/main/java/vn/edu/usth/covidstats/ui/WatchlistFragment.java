package vn.edu.usth.covidstats.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import vn.edu.usth.covidstats.CovidApp;
import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.data.WatchlistStore;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.util.NetworkUtil;

/** Tab F11. Du lieu quoc gia qua Repository; danh sach sao qua WatchlistStore. */
public final class WatchlistFragment extends Fragment {
    private WatchlistStore store;
    private WatchlistModel model;
    private CountryAdapter adapter;
    private SwipeRefreshLayout refresh;
    private TextView status;
    private TextView offline;
    private View retry;
    private View root;
    private Set<String> codes = Collections.emptySet();
    private final List<CountryStats> visibleCountries = new ArrayList<>();
    private boolean codesReady;
    private boolean removing;
    private String storageError;
    private int generation;
    private final WatchlistStore.Listener listener = result -> {
        codes = result;
        codesReady = true;
        storageError = null;
        ensureCountries();
        render();
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_watchlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        refresh = view.findViewById(R.id.watchlist_refresh);
        status = view.findViewById(R.id.watchlist_status);
        offline = view.findViewById(R.id.watchlist_offline);
        retry = view.findViewById(R.id.watchlist_retry);
        RecyclerView list = view.findViewById(R.id.watchlist_list);
        CovidApp app = (CovidApp) requireActivity().getApplication();
        store = WatchlistStore.getInstance(requireContext());
        model = new ViewModelProvider(this).get(WatchlistModel.class);
        adapter = new CountryAdapter(app.getImageLoader(), this::openCountry);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recycler, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) { return false; }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recycler, @NonNull RecyclerView.ViewHolder holder) {
                return removing ? 0 : super.getSwipeDirs(recycler, holder);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getBindingAdapterPosition();
                if (position < 0 || position >= visibleCountries.size()) {
                    render();
                    return;
                }
                removeCountry(visibleCountries.get(position).iso2);
            }
        }).attachToRecyclerView(list);
        refresh.setOnRefreshListener(this::refresh);
        retry.setOnClickListener(v -> refresh());
        model.state.observe(getViewLifecycleOwner(), state -> render());
    }

    @Override
    public void onStart() {
        super.onStart();
        store.addListener(listener);
        loadCodes(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        render(); // Cap nhat nhan mang sau khi quay lai tu Settings/che do may bay.
    }

    @Override
    public void onStop() {
        store.removeListener(listener);
        generation++; // Bo callback doc/sua cua lan hien thi cu.
        removing = false;
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        generation++;
        ((RecyclerView) root.findViewById(R.id.watchlist_list)).setAdapter(null);
        root = null;
        adapter = null;
        refresh = null;
        status = null;
        offline = null;
        retry = null;
        super.onDestroyView();
    }

    /** MainActivity co the goi tu nut Refresh tren App Bar khi tab nay dang hien. */
    public void refresh() {
        if (root != null) loadCodes(true);
    }

    private void loadCodes(boolean forceRefresh) {
        int request = ++generation;
        codesReady = false;
        storageError = null;
        render();
        store.getCodes(new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> data, boolean fromCache) {
                if (!current(request)) return;
                codes = data;
                codesReady = true;
                removing = false;
                if (!codes.isEmpty()) model.load(repository(), forceRefresh);
                render();
            }

            @Override
            public void onError(String message) {
                if (!current(request)) return;
                codesReady = true;
                removing = false;
                storageError = message;
                render();
            }
        });
    }

    private void removeCountry(String code) {
        removing = true;
        int request = generation;
        store.setStarred(code, false, new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> data, boolean fromCache) {
                if (!current(request)) return;
                removing = false;
                codes = data;
                storageError = null;
                render();
            }

            @Override
            public void onError(String message) {
                if (!current(request)) return;
                removing = false;
                storageError = message;
                render(); // Khoi phuc dong vuot neu ghi that bai.
            }
        });
    }

    private boolean current(int request) { return root != null && request == generation; }

    private CovidRepository repository() {
        return ((CovidApp) requireActivity().getApplication()).getRepository();
    }

    private void ensureCountries() {
        if (root != null && !codes.isEmpty()) model.load(repository(), false);
    }

    private void render() {
        if (root == null) return;
        State state = model.state.getValue();
        boolean loading = !codesReady || (!codes.isEmpty() && state != null && state.loading);
        refresh.setRefreshing(loading);
        visibleCountries.clear();
        if (state != null && state.data != null) {
            for (CountryStats country : state.data) {
                if (codes.contains(country.iso2)) visibleCountries.add(country);
            }
        }
        adapter.setItems(visibleCountries);
        String error = storageError != null ? storageError
                : (!codes.isEmpty() && state != null ? state.error : null);
        retry.setVisibility(!loading && error != null ? View.VISIBLE : View.GONE);
        offline.setVisibility(!visibleCountries.isEmpty() && !NetworkUtil.isOnline(requireContext())
                ? View.VISIBLE : View.GONE);
        status.setVisibility(View.VISIBLE);
        if (loading && visibleCountries.isEmpty()) status.setText(R.string.watchlist_loading);
        else if (error != null) status.setText(error);
        else if (codes.isEmpty()) status.setText(R.string.watchlist_empty);
        else if (visibleCountries.size() < codes.size()) status.setText(R.string.watchlist_missing_countries);
        else status.setText(R.string.watchlist_swipe_hint);
    }

    private void openCountry(CountryStats country) {
        Intent intent = new Intent(requireContext(), CountryDetailActivity.class);
        intent.putExtra(CountryDetailActivity.EXTRA_COUNTRY_ISO2, country.iso2);
        startActivity(intent);
    }

    private static final class State {
        final List<CountryStats> data;
        final boolean loading;
        final String error;
        State(List<CountryStats> data, boolean loading, String error) {
            this.data = data;
            this.loading = loading;
            this.error = error;
        }
    }

    /** Khong giu Activity/View. Xoay man hinh van dung request va du lieu hien co. */
    public static final class WatchlistModel extends ViewModel {
        private final MutableLiveData<State> state = new MutableLiveData<>();

        void load(CovidRepository repository, boolean forceRefresh) {
            State old = state.getValue();
            if (old != null && (old.loading || (!forceRefresh && old.data != null))) return;
            List<CountryStats> previous = old == null ? null : old.data;
            state.setValue(new State(previous, true, null));
            repository.getCountries(forceRefresh, new Callback<List<CountryStats>>() {
                @Override
                public void onSuccess(List<CountryStats> data, boolean fromCache) {
                    state.setValue(new State(new ArrayList<>(data), false, null));
                }

                @Override
                public void onError(String message) {
                    state.setValue(new State(previous, false, message));
                }
            });
        }
    }
}
