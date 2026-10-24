package vn.edu.usth.covidstats.ui;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import android.content.res.Resources;

import vn.edu.usth.covidstats.R;

/** Pager de 3 tab dung cung mot he AndroidX Fragment. */
public final class MainPagerAdapter extends FragmentStatePagerAdapter {
    public static final int PAGE_GLOBAL = 0;
    public static final int PAGE_COUNTRIES = 1;
    public static final int PAGE_WATCHLIST = 2;
    private static final int PAGE_COUNT = 3;
    private final Resources titleResources;

    public MainPagerAdapter(@NonNull FragmentManager fragmentManager,
                            @NonNull Resources titleResources) {
        super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.titleResources = titleResources;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case PAGE_GLOBAL:
                return new GlobalFragment();
            case PAGE_COUNTRIES:
                return new CountryListFragment();
            case PAGE_WATCHLIST:
                return new WatchlistFragment();
            default:
                throw new IllegalArgumentException("Unknown page index: " + position);
        }
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case PAGE_GLOBAL:
                return resourceTitle(R.string.main_tab_global);
            case PAGE_COUNTRIES:
                return resourceTitle(R.string.main_tab_countries);
            case PAGE_WATCHLIST:
                return resourceTitle(R.string.main_tab_watchlist);
            default:
                return null;
        }
    }

    private CharSequence resourceTitle(int resourceId) {
        return titleResources.getString(resourceId);
    }

}
