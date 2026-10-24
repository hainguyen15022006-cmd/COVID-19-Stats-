package vn.edu.usth.covidstats.data;

import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;

import org.json.JSONException;

import java.nio.charset.StandardCharsets;

/** HTTP + parse tren Volley worker; ket qua duoc giao tren main thread. */
public final class CovidApi {
    private static final String BASE_URL = "https://disease.sh/v3/covid-19/";
    private final RequestQueue queue;

    public interface Parser<T> {
        T parse(String json) throws JSONException;
    }

    public static final class Result<T> {
        public final T data;
        public final String json;
        public final long fetchedAt;

        Result(T data, String json, long fetchedAt) {
            this.data = data;
            this.json = json;
            this.fetchedAt = fetchedAt;
        }
    }

    public CovidApi(RequestQueue queue) {
        this.queue = queue;
    }

    // Chi NetworkRepository trong cung package truyen path da kiem soat.
    <T> void get(String path, Parser<T> parser, Response.Listener<Result<T>> success,
                 Response.ErrorListener failure) {
        Request<Result<T>> request = new Request<Result<T>>(
                Request.Method.GET, BASE_URL + path, failure) {
            @Override
            protected Response<Result<T>> parseNetworkResponse(NetworkResponse response) {
                try {
                    Log.d("M3Data", "PARSE " + path + " thread="
                            + Thread.currentThread().getName());
                    String json = new String(response.data, StandardCharsets.UTF_8);
                    T data = parser.parse(json);
                    return Response.success(new Result<>(data, json,
                            System.currentTimeMillis()), null);
                } catch (JSONException | RuntimeException error) {
                    return Response.error(new ParseError(error));
                }
            }

            @Override
            protected void deliverResponse(Result<T> result) {
                success.onResponse(result);
            }
        };
        request.setShouldCache(false);
        request.setRetryPolicy(new DefaultRetryPolicy(10_000, 0, 1f));
        Log.d("M3Data", "REQUEST " + path);
        queue.add(request);
    }
}
