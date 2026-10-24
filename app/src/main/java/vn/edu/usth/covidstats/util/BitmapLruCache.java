package vn.edu.usth.covidstats.util;

import android.graphics.Bitmap;
import android.util.LruCache;

import com.android.volley.toolbox.ImageLoader;

/** In-memory bitmap cache used by the application's shared Volley image loader. */
public final class BitmapLruCache implements ImageLoader.ImageCache {
    private final LruCache<String, Bitmap> cache;

    public BitmapLruCache() {
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        cache = new LruCache<String, Bitmap>(maxKb / 8) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
    }

    @Override
    public Bitmap getBitmap(String url) {
        return cache.get(url);
    }

    @Override
    public void putBitmap(String url, Bitmap bitmap) {
        cache.put(url, bitmap);
    }
}
