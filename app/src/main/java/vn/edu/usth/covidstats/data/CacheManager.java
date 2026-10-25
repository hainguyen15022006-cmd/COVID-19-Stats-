package vn.edu.usth.covidstats.data;

import android.content.Context;
import android.os.Looper;
import android.util.AtomicFile;

import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/** Cache JSON trong bo nho trong. Repository goi cac ham I/O tren worker thread. */
public final class CacheManager {
    public static final long MAX_AGE_MILLIS = 10 * 60 * 1000L;
    // Dong bo ca nhieu instance: AtomicFile khong tu bao ve truy cap dong thoi.
    private static final Object LOCK = new Object();
    private final Context context;

    public CacheManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static final class Entry {
        public final String json;
        public final long savedAtMillis;

        private Entry(String json, long savedAtMillis) {
            this.json = json;
            this.savedAtMillis = savedAtMillis;
        }

        public boolean isFresh() {
            return isFresh(System.currentTimeMillis());
        }

        public boolean isFresh(long nowMillis) {
            // Dong ho bi chinh lui khong duoc lam cache tuoi mai mai.
            return nowMillis >= savedAtMillis && nowMillis - savedAtMillis < MAX_AGE_MILLIS;
        }
    }

    /** Tra null neu chua co/hong cache. Van tra entry cu de Repository fallback offline. */
    @WorkerThread
    public Entry read(String key) throws IOException {
        requireWorker();
        synchronized (LOCK) {
            AtomicFile file = fileFor(key);
            try {
                JSONObject envelope = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
                if (envelope.getInt("version") != 1) return null;
                Object payload = envelope.get("json");
                long savedAt = envelope.getLong("savedAtMillis");
                if (!(payload instanceof String) || ((String) payload).trim().isEmpty()
                        || savedAt <= 0) return null;
                return new Entry((String) payload, savedAt);
            } catch (FileNotFoundException | JSONException e) {
                return null;
            }
        }
    }

    /** Chi ghi response thanh cong sau khi parser chap nhan, khong ghi response loi. */
    @WorkerThread
    public void write(String key, String json) throws IOException {
        requireWorker();
        Objects.requireNonNull(json, "json");
        if (json.trim().isEmpty()) throw new IllegalArgumentException("Empty JSON");
        synchronized (LOCK) {
            AtomicFile file = fileFor(key);
            File directory = file.getBaseFile().getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create cache directory");
            }
            FileOutputStream output = null;
            try {
                JSONObject envelope = new JSONObject();
                envelope.put("version", 1);
                envelope.put("savedAtMillis", System.currentTimeMillis());
                envelope.put("json", json);
                output = file.startWrite();
                output.write(envelope.toString().getBytes(StandardCharsets.UTF_8));
                file.finishWrite(output);
            } catch (IOException | JSONException e) {
                if (output != null) file.failWrite(output);
                throw new IOException("Cannot save cache", e);
            }
        }
    }

    @WorkerThread
    public void remove(String key) {
        requireWorker();
        synchronized (LOCK) {
            fileFor(key).delete();
        }
    }

    private AtomicFile fileFor(String key) {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Empty cache key");
        // Hash toan bo endpoint (kem query): tranh trung khoa va path traversal.
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : digest) name.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return new AtomicFile(new File(new File(context.getFilesDir(), "covid_cache"), name + ".json"));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void requireWorker() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Cache I/O must run on a worker thread");
        }
    }
}
