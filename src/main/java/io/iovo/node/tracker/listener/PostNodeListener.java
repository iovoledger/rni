package io.iovo.node.tracker.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostNodeListener implements Callback<Void> {
    private static final Logger logger = LogManager.getLogger();

    @Override
    public void onResponse(Call<Void> call, Response<Void> response) {
        if (response.isSuccessful()) {
            logger.info("IP sent to tracker");
        }
    }

    @Override
    public void onFailure(Call<Void> call, Throwable throwable) {

    }
}
