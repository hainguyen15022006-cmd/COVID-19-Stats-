package vn.edu.usth.covidstats.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
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
    private MainPagerAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Dark design: light status and navigation bar icons.
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_main);
        TabLayout tabs = findViewById(R.id.main_tabs);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            // The tab bar sits at the bottom, so its colour runs under the navigation bar.
            tabs.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        pager = findViewById(R.id.main_pager);
        adapter = new MainPagerAdapter(getSupportFragmentManager(), getResources());
        pager.setAdapter(adapter);
        tabs.setupWithViewPager(pager);
        setTabIcon(tabs, MainPagerAdapter.PAGE_GLOBAL, R.drawable.ic_nav_globe_24);
        setTabIcon(tabs, MainPagerAdapter.PAGE_COUNTRIES, R.drawable.ic_nav_list_24);
        setTabIcon(tabs, MainPagerAdapter.PAGE_WATCHLIST, R.drawable.ic_nav_star_24);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                setTitle(adapter.getPageTitle(position));
                invalidateOptionsMenu();
            }
        });
    }

    private static void setTabIcon(TabLayout tabs, int position, int iconRes) {
        TabLayout.Tab tab = tabs.getTabAt(position);
        if (tab != null) {
            tab.setIcon(iconRes);
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Restore ViewPager's saved fragments before populating the offscreen tabs.
        // Populating during onCreate can leave duplicate Watchlist views after rotation.
        pager.setOffscreenPageLimit(2);
        // The pager restores its page after rotation, so set the title here.
        setTitle(adapter.getPageTitle(pager.getCurrentItem()));
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
            int page = pager.getCurrentItem();
            refresh.setVisible(page == MainPagerAdapter.PAGE_GLOBAL
                    || page == MainPagerAdapter.PAGE_WATCHLIST);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            int page = pager.getCurrentItem();
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (page == MainPagerAdapter.PAGE_GLOBAL && fragment instanceof GlobalFragment) {
                    ((GlobalFragment) fragment).refresh();
                    break;
                }
                if (page == MainPagerAdapter.PAGE_WATCHLIST && fragment instanceof WatchlistFragment) {
                    ((WatchlistFragment) fragment).refresh();
                    break;
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
