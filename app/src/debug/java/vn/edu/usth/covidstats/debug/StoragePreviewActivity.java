package vn.edu.usth.covidstats.debug;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Set;

import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.WatchlistStore;
import vn.edu.usth.covidstats.ui.WatchlistFragment;

/** Host debug rieng: khong thay MainActivity/pager cua Sy Chuc. */
public final class StoragePreviewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_storage_preview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.storage_preview_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.storage_preview_container, new WatchlistFragment())
                    .commit();
        }
        findViewById(R.id.storage_preview_vn).setOnClickListener(v -> star("VN"));
        findViewById(R.id.storage_preview_us).setOnClickListener(v -> star("US"));
        findViewById(R.id.storage_preview_refresh).setOnClickListener(v -> {
            WatchlistFragment fragment = (WatchlistFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.storage_preview_container);
            if (fragment != null) fragment.refresh();
        });
    }

    private void star(String code) {
        WatchlistStore.getInstance(this).setStarred(code, true, new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> data, boolean fromCache) {
                showMessage(getString(R.string.storage_preview_saved, code));
            }

            @Override
            public void onError(String message) { showMessage(message); }
        });
    }

    private void showMessage(String message) {
        if (!isFinishing() && !isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
