package vn.edu.usth.covidstats.data;

import static org.junit.Assert.*;

import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.NoCache;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** T12: do TRUC TIEP thread cua parser, khong suy ra tu thread cua mang. */
@RunWith(AndroidJUnit4.class)
public class M5ApiThreadTest {
    private RequestQueue queue;
    private CovidApi api;
    @Before public void setUp() {
        queue = new RequestQueue(new NoCache(), request -> new NetworkResponse(200,
                "{\"country\":\"Việt Nam\",\"cases\":42}".getBytes(StandardCharsets.UTF_8),
                false, 0, Collections.emptyList()), 1);
        queue.start(); api = new CovidApi(queue);
    }
    @After public void tearDown() { queue.stop(); }

    @Test public void blockedParserDoesNotBlockMainAndDeliveryReturnsOnMain() throws Exception {
        CountDownLatch parsing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch heartbeat = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Thread> parserThread = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicInteger value = new AtomicInteger();
        AtomicReference<Thread> deliveryThread = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> api.get("all", json -> {
            parserThread.set(Thread.currentThread()); parsing.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new JSONException("Parser release timeout");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new JSONException("Interrupted parser");
            }
            JSONObject object = new JSONObject(json);
            if (!"Việt Nam".equals(object.optString("country"))) throw new JSONException("UTF-8 mismatch");
            return object.getInt("cases");
        }, result -> {
            value.set(result.data); deliveryThread.set(Thread.currentThread()); done.countDown();
        }, failure -> { error.set(failure.toString()); done.countDown(); }));
        boolean mainResponded;
        try {
            assertTrue(parsing.await(5, TimeUnit.SECONDS));
            new Handler(Looper.getMainLooper()).post(heartbeat::countDown);
            mainResponded = heartbeat.await(2, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertTrue("Main must respond while parser is waiting", mainResponded);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertEquals(42, value.get());
        assertNotEquals(Looper.getMainLooper().getThread(), parserThread.get());
        assertEquals(Looper.getMainLooper().getThread(), deliveryThread.get());
    }

    @Test public void parserFailureIsDeliveredOnceAsErrorOnMain() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicReference<Thread> delivery = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        api.get("all", json -> { throw new JSONException("Controlled invalid payload"); },
                result -> { success.incrementAndGet(); done.countDown(); }, error -> {
                    errors.incrementAndGet(); failure.set(error);
                    delivery.set(Thread.currentThread()); done.countDown();
                });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(0, success.get()); assertEquals(1, errors.get());
        assertTrue(failure.get() instanceof ParseError);
        assertEquals(Looper.getMainLooper().getThread(), delivery.get());
    }
}
