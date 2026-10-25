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
import com.google.android.material.bottomnavigation.BottomNavigationView;

import vn.edu.usth.covidstats.R;

/** Host cua toolbar va cac tab chinh. */
public class MainActivity extends AppCompatActivity {
    private ViewPager pager;
    private MainPagerAdapter adapter;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Dark design: light status and navigation bar icons.
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // No bottom padding: the bottom bar adds the navigation bar height itself.
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        pager = findViewById(R.id.main_pager);
        adapter = new MainPagerAdapter(getSupportFragmentManager(), getResources());
        pager.setAdapter(adapter);
        bottomNav = findViewById(R.id.main_bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            pager.setCurrentItem(pageForItem(item.getItemId()));
            return true;
        });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                syncChrome(position);
                invalidateOptionsMenu();
            }
        });
    }

    /** Keeps the bottom bar and the title in step with the page, also after a swipe. */
    private void syncChrome(int position) {
        int itemId = itemForPage(position);
        if (bottomNav.getSelectedItemId() != itemId) {
            bottomNav.setSelectedItemId(itemId);
        }
        setTitle(adapter.getPageTitle(position));
    }

    private static int pageForItem(int itemId) {
        if (itemId == R.id.nav_countries) {
            return MainPagerAdapter.PAGE_COUNTRIES;
        }
        if (itemId == R.id.nav_watchlist) {
            return MainPagerAdapter.PAGE_WATCHLIST;
        }
        return MainPagerAdapter.PAGE_GLOBAL;
    }

    private static int itemForPage(int page) {
        if (page == MainPagerAdapter.PAGE_COUNTRIES) {
            return R.id.nav_countries;
        }
        if (page == MainPagerAdapter.PAGE_WATCHLIST) {
            return R.id.nav_watchlist;
        }
        return R.id.nav_global;
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Restore ViewPager's saved fragments before populating the offscreen tabs.
        // Populating during onCreate can leave duplicate Watchlist views after rotation.
        pager.setOffscreenPageLimit(2);
        // The pager restores its page after rotation, so sync title and bottom bar here.
        syncChrome(pager.getCurrentItem());
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
