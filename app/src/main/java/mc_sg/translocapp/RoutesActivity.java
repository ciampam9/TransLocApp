package mc_sg.translocapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import mc_sg.translocapp.model.AgencyRouteMap;
import mc_sg.translocapp.model.Response;
import mc_sg.translocapp.model.SegmentMap;
import mc_sg.translocapp.network.ApiUtil;
import mc_sg.translocapp.view.RouteListItem;
import retrofit.RetrofitError;

public class RoutesActivity extends AppCompatActivity {

    private int segmentsReceived = 0;
    private Map<String, SegmentMap> activeSegments = new HashMap<>();

    private ListView listView;
    private View listProgress;
    private String agencyId;
    private Context context;

    private List<AgencyRouteMap.Route> activeRoutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_routes);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("Routes");
        setSupportActionBar(toolbar);

        // get agency id from bundle or shared prefs
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            agencyId = extras.getString(LauncherActivity.KEY_AGENCY_ID);
        } else {
            SharedPreferences prefs = getSharedPreferences(HomeAgencyActivity.PREFS_HOME_AGENCY, MODE_PRIVATE);
            agencyId = prefs.getString(HomeAgencyActivity.KEY_PREFS_AGENCY_ID, null);
        }

        ApiUtil.getTransLocApi().getRoutes(agencyId, null, new RoutesCallback(this));

        // R aggregates xml data to interface with.
        listView = (ListView) findViewById(R.id.routes_listview);
        listView.setOnItemClickListener(new OnRouteClick());
        listProgress = findViewById(R.id.routes_list_progress_card);
    }

    private class OnRouteClick implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            AgencyRouteMap.Route route = activeRoutes.get(position);
            Intent intent = new Intent(context, RouteActivity.class);
            Bundle data = new Bundle();
            data.putString(RouteActivity.KEY_ROUTE_ID, route.routeId);
            data.putString(RouteActivity.KEY_ROUTE_NAME, (route.shortName == null
                    || route.shortName.isEmpty() ? route.longName : route.shortName));
            intent.putExtras(data);
            startActivity(intent);
        }
    }

    private class RoutesCallback extends ApiUtil.RetroCallback<Response<AgencyRouteMap>> {

        private Context context;
        public RoutesCallback(Context context) {
            super(context);
            this.context = context;
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            super.failure(retrofitError);
            Toast.makeText(context, "An error has occurred. Trying again.", Toast.LENGTH_LONG).show();
            // TODO: retry routes request here
        }

        @Override
        public void success(Response<AgencyRouteMap> agencyRouteMapResponse, retrofit.client.Response response) {

            List<AgencyRouteMap.Route> routes = agencyRouteMapResponse.data.getRoutes(agencyId);
            activeRoutes = new ArrayList<>();

            for (AgencyRouteMap.Route route : routes) {
                if (route.isActive) {
                    activeRoutes.add(route);
                    ApiUtil.getTransLocApi().getSegments(agencyId, null, route.routeId,
                            new SegmentsCallback(context, route.routeId));
                }
            }
        }
    }

    private class SegmentsCallback extends ApiUtil.RetroCallback<Response<SegmentMap>> {

        String routeId;

        public SegmentsCallback(Context context, String routeId) {
            // maybe change put route id here to retry with
            super(context);
            this.routeId = routeId;
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            super.failure(retrofitError);
            Toast.makeText(context, "An error has occurred. Trying again.", Toast.LENGTH_LONG).show();
            // TODO: retry specific route segment request here with "routeId"
        }

        @Override
        public void success(Response<SegmentMap> segmentMapResponse, retrofit.client.Response response) {
            segmentsReceived += 1;
            activeSegments.put(routeId, segmentMapResponse.data);

            if (segmentsReceived == activeRoutes.size()) {
                listProgress.setVisibility(View.INVISIBLE);
                listView.setAdapter(new RouteAdapter(context, activeRoutes, activeSegments));
            }
        }
    }

    private class RouteAdapter extends BaseAdapter {

        private final List<AgencyRouteMap.Route> routes;
        private final Context context;

        Map<Integer, Integer> colorMap = new HashMap<>();

        public RouteAdapter(Context context, List<AgencyRouteMap.Route> routes, Map<String, SegmentMap> segments) {
            this.routes = routes;
            this.context = context;
        }

        protected List<AgencyRouteMap.Route> getRoutes() {
            return routes;
        }

        @Override
        public int getCount() {
            return getRoutes().size();
        }

        @Override
        public Object getItem(int position) {
            return getRoutes().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new RouteListItem(context, position);
            }
            RouteListItem routeView = (RouteListItem) convertView;
            AgencyRouteMap.Route currentRoute = routes.get(position);

            if (currentRoute.shortName == null || currentRoute.shortName.isEmpty()) {
                routeView.setTitle(currentRoute.longName);
            } else {
                routeView.setTitle(currentRoute.shortName);
            }
            routeView.setDesc(currentRoute.stops.size() + " stops");

            Random random = new Random(position); // seed so views keep the same color
            int currentColor = ColorGenerator.MATERIAL.getColor(random.nextInt());

            // make sure we get a dark color for the polyline
            if (colorMap.get(position) == null) {
                while (!isColorDark(currentColor)) {
                    currentColor = ColorGenerator.MATERIAL.getColor(random.nextInt());
                }
                colorMap.put(position, currentColor);
            } else {
                currentColor = colorMap.get(position);
            }


            TextDrawable icon = TextDrawable.builder().buildRound((""+(position+1)), currentColor);
            routeView.setIconImageDrawable(icon);

            routeView.setupMap(activeSegments.get(currentRoute.routeId), currentColor);

            return routeView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return getRoutes().isEmpty();
        }

        public boolean isColorDark(int color){
            double darkness = 1-(0.299* Color.red(color) + 0.587*Color.green(color) + 0.114*Color.blue(color))/255;
            return darkness >= 0.2;
        }
    }

}
