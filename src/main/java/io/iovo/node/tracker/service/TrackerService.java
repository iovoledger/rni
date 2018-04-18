package io.iovo.node.tracker.service;

import io.iovo.node.tracker.model.GetNodeResponse;
import io.iovo.node.tracker.model.RegisterNodeRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import java.util.List;

public interface TrackerService {

    @GET("node")
    Call<List<GetNodeResponse>> getNodes();

    @POST("node")
    Call<Void> addNode(@Body RegisterNodeRequest registerNodeRequest);
}
