package vn.edu.usth.covidstats.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.fragment.app.Fragment;
import com.google.android.material.tabs.TabLayout;

import vn.edu.usth.covidstats.R;

/** Host cua toolbar va cac tab chinh. */
public class MainActivity extends AppCompatActivity {
    private ViewPager pager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        pager = findViewById(R.id.main_pager);
        pager.setAdapter(new MainPagerAdapter(getSupportFragmentManager(), getResources()));
        pager.setOffscreenPageLimit(2);
        TabLayout tabs = findViewById(R.id.main_tabs);
        tabs.setupWithViewPager(pager);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                invalidateOptionsMenu();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem refresh = menu.findItem(R.id.action_refresh);
        if (refresh != null) {
            refresh.setVisible(pager.getCurrentItem() == MainPagerAdapter.PAGE_GLOBAL);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_refresh) {
        if (pager.getCurrentItem() == MainPagerAdapter.PAGE_GLOBAL) {
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof GlobalFragment) {
                    ((GlobalFragment) fragment).refresh();
                    break;
                }
            }
        }
        return true;
    }
    return super.onOptionsItemSelected(item);
}
}