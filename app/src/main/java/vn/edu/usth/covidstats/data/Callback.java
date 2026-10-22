package vn.edu.usth.covidstats.data;

/** Ket qua bat dong bo; ca hai ham luon duoc goi tren main thread. */
public interface Callback<T> {
    void onSuccess(T data, boolean fromCache);
    void onError(String message);
}
