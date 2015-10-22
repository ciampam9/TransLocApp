package mc_sg.translocapp.network;

import java.util.List;

import mc_sg.translocapp.model.Agency;
import mc_sg.translocapp.model.ArrivalEstimate;
import mc_sg.translocapp.model.Response;
import retrofit.http.GET;
import retrofit.http.Query;

/**
 * Created by steven on 10/21/15.
 */
public interface TransLocApi {

    @GET("/agencies.json")
    void getAgencies(@Query("agencies") String agencies, @Query("geo_area") String geo_area,
                     NetworkUtil.RetroCallback<Response<List<Agency>>> callback);

    @GET("/arrival-estimates.json")
    void getArrivalEstimates(@Query("agencies") String agencies,
                             @Query("routes") String routes,
                             @Query("stops") String stops,
                             NetworkUtil.RetroCallback<Response<List<ArrivalEstimate>>> callback);
}