package io.iovo.node.tracker.listener;

import io.iovo.node.tracker.TrackerConnector;
import io.iovo.node.tracker.model.GetNodeResponse;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class GetNodeListener implements Callback<List<GetNodeResponse>> {
    private static final Logger logger = LogManager.getLogger();

    private final TrackerConnector trackerConnector;
    private final String myIp;

    @Override
    public void onResponse(Call<List<GetNodeResponse>> call, Response<List<GetNodeResponse>> response) {
        if (response.isSuccessful()) {
            for (GetNodeResponse nodeResponse : response.body()) {
                if (myIp == null) {
                    Optional.ofNullable(nodeResponse.getIp())
                            .ifPresent(trackerConnector::addNode);
                } else {
                    if (!myIp.equals(nodeResponse.getIp())) {
                        Optional.ofNullable(nodeResponse.getIp())
                                .ifPresent(trackerConnector::addNode);
                    }
                }
            }
        }
    }

    @Override
    public void onFailure(Call<List<GetNodeResponse>> call, Throwable throwable) {

    }
}
