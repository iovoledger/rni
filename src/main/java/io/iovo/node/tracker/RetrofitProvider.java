package io.iovo.node.tracker;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RetrofitProvider {

    public static Retrofit provide(String address) {
        return new Retrofit.Builder()
                .baseUrl(address)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

}
