package vn.edu.usth.covidstats;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NoCache;

import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.data.FakeRepository;
import vn.edu.usth.covidstats.util.BitmapLruCache;

/** Khoi tao som va giu dung mot Repository, mot RequestQueue cho ca ung dung. */
public final class CovidApp extends Application {
    private RequestQueue requestQueue;
    private ImageLoader imageLoader;
    private CovidRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
        }

        // Cache file cua app se do CacheManager so huu. Queue khong tao cache thu hai.
        requestQueue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
        requestQueue.start();
        imageLoader = new ImageLoader(requestQueue, new BitmapLruCache());
        repository = new FakeRepository(this);
    }

    public CovidRepository getRepository() {
        return repository;
    }

    public RequestQueue getRequestQueue() {
        return requestQueue;
    }

    public ImageLoader getImageLoader() {
        return imageLoader;
    }
}
