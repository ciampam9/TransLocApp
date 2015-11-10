package mc_sg.translocapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.amulyakhare.textdrawable.util.ColorGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import mc_sg.translocapp.model.AgencyRouteMap;
import mc_sg.translocapp.model.Response;
import mc_sg.translocapp.model.SegmentMap;
import mc_sg.translocapp.network.ApiUtil;
import mc_sg.translocapp.view.RouteListItem;
import retrofit.RetrofitError;

/**
 * Nearly exact copy of ActiveRoutsActivity, if I had more time I would
 * DRY this out. Maybe later. The activity only displays routes that are
 * favorited in sharedprefs.
 */
public class FavoriteRoutesActivity extends AppCompatActivity {

    private int segmentsReceived = 0;
    private Map<String, SegmentMap> activeSegments = new HashMap<>();

    private ListView listView;
    private View listProgress;
    private String agencyId, agencyName;
    private Set<String> favRouteIds;
    private Context context;

    private List<AgencyRouteMap.Route> favoriteRoutes;
    Map<Integer, Integer> colorMap = new HashMap<>();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_favorite_routes, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() ==  R.id.reset_favorites) {

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            // clear user preferences
                            SharedPreferences agencyPrefs = getSharedPreferences(HomeAgencyActivity.PREFS_HOME_AGENCY, MODE_PRIVATE);
                            SharedPreferences.Editor agencyEditor = agencyPrefs.edit();
                            agencyEditor.remove(HomeAgencyActivity.KEY_PREFS_AGENCY_ID);
                            agencyEditor.remove(HomeAgencyActivity.KEY_PREFS_AGENCY_NAME);
                            agencyEditor.apply();

                            SharedPreferences routesPrefs = getSharedPreferences(ActiveRoutesActivity.PREFS_FAVORITES, MODE_PRIVATE);
                            SharedPreferences.Editor routesEditor = routesPrefs.edit();
                            routesEditor.remove(ActiveRoutesActivity.KEY_PREFS_FAV_ROUTES);
                            routesEditor.apply();

                            startActivity(new Intent(context, HomeAgencyActivity.class));
                            finish();
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            // Do nothing
                            break;
                    }
                }
            };

            String message = "Are you sure you would like to clear your favorites? " +
                    "This can't be undone.";
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(message)
                    .setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener)
                    .show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        loadPreferences();

        setContentView(R.layout.activity_routes);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("Favorites" + (!agencyName.isEmpty() ? " - " + agencyName : ""));
        setSupportActionBar(toolbar);

        ApiUtil.getTransLocApi().getRoutes(agencyId, null, new RoutesCallback(this));

        // R aggregates xml data to interface with.
        listView = (ListView) findViewById(R.id.routes_listview);
        listView.setOnItemClickListener(new OnRouteClick());
        listProgress = findViewById(R.id.routes_list_progress_card);
    }

    private void loadPreferences() {
        SharedPreferences agencyPrefs = getSharedPreferences(HomeAgencyActivity.PREFS_HOME_AGENCY, MODE_PRIVATE);
        agencyId = agencyPrefs.getString(HomeAgencyActivity.KEY_PREFS_AGENCY_ID, "");
        agencyName = agencyPrefs.getString(HomeAgencyActivity.KEY_PREFS_AGENCY_NAME, "");

        SharedPreferences routesPrefs = getSharedPreferences(ActiveRoutesActivity.PREFS_FAVORITES, MODE_PRIVATE);
        favRouteIds = routesPrefs.getStringSet(ActiveRoutesActivity.KEY_PREFS_FAV_ROUTES, new HashSet<String>());
    }

    private class OnRouteClick implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            AgencyRouteMap.Route route = favoriteRoutes.get(position);
            Intent intent = new Intent(context, RouteActivity.class);
            Bundle data = new Bundle();
            data.putSerializable(RouteActivity.KEY_ROUTE, route);
            data.putSerializable(RouteActivity.KEY_ROUTE_SEGMENTS, activeSegments.get(route.routeId));
            data.putInt(RouteActivity.KEY_COLOR, colorMap.get(position));
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
            Toast.makeText(context, "An error has occurred, trying again...", Toast.LENGTH_SHORT).show();
            ApiUtil.getTransLocApi().getRoutes(agencyId, null, new RoutesCallback(context));
        }

        @Override
        public void success(Response<AgencyRouteMap> agencyRouteMapResponse, retrofit.client.Response response) {

            List<AgencyRouteMap.Route> routes = agencyRouteMapResponse.data.getRoutes(agencyId);
            favoriteRoutes = new ArrayList<>();

            SharedPreferences prefs = context.getSharedPreferences(ActiveRoutesActivity.PREFS_FAVORITES, Context.MODE_PRIVATE);
            Set<String> routeIds = prefs.getStringSet(ActiveRoutesActivity.KEY_PREFS_FAV_ROUTES, new HashSet<String>());

            if (routes != null) {
                for (AgencyRouteMap.Route route : routes) {
                    if (favRouteIds.contains(route.routeId)) {
                        favoriteRoutes.add(route);
                        ApiUtil.getTransLocApi().getSegments(agencyId, null, route.routeId,
                                new SegmentsCallback(context, route.routeId));
                        route.following = routeIds.contains(route.routeId);
                    }
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
            Toast.makeText(context, "An error has occurred, trying again...", Toast.LENGTH_SHORT).show();
            ApiUtil.getTransLocApi().getSegments(agencyId, null, routeId,
                    new SegmentsCallback(context, routeId));
        }

        @Override
        public void success(Response<SegmentMap> segmentMapResponse, retrofit.client.Response response) {
            segmentsReceived += 1;
            activeSegments.put(routeId, segmentMapResponse.data);

            if (segmentsReceived == favoriteRoutes.size()) {
                listProgress.setVisibility(View.INVISIBLE);
                listView.setAdapter(new RouteAdapter(context, favoriteRoutes, activeSegments));
            }
        }
    }

    private class RouteAdapter extends BaseAdapter {

        private final List<AgencyRouteMap.Route> routes;
        private final Context context;

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


        private class FavoriteBtnListener implements View.OnClickListener {

            @Override
            public void onClick(View view) {
                String routeId = (String) view.getTag();
                SharedPreferences prefs = context.getSharedPreferences(ActiveRoutesActivity.PREFS_FAVORITES, Context.MODE_PRIVATE);

                if (!favRouteIds.isEmpty() && favRouteIds.contains(routeId)) {
                    favRouteIds.remove(routeId);
                    notify("Route removed from favorites!").show();
                    view.setSelected(false);

                } else {
                    favRouteIds.add(routeId);
                    notify("Route added to favorites!").show();
                    view.setSelected(true);
                }

                SharedPreferences.Editor editor = prefs.edit();
                editor.putStringSet(ActiveRoutesActivity.KEY_PREFS_FAV_ROUTES, favRouteIds);
                editor.apply();

                if (favoriteRoutes != null) {
                    for (AgencyRouteMap.Route route : favoriteRoutes) {
                        if (route.routeId.equals(routeId)) {
                            route.following = !route.following;
                        }
                    }
                }
            }

            public Toast notify(String text) {
                return Toast.makeText(context, text, Toast.LENGTH_SHORT);
            }

        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new RouteListItem(context, position);
                convertView
                        .findViewById(R.id.item_route_list_favorite)
                        .setOnClickListener(new FavoriteBtnListener());
            }
            RouteListItem routeView = (RouteListItem) convertView;
            AgencyRouteMap.Route currentRoute = routes.get(position);

            if (currentRoute.shortName == null || currentRoute.shortName.isEmpty()) {
                routeView.setTitle(currentRoute.longName);
            } else {
                routeView.setTitle(currentRoute.shortName);
            }
            routeView.setDesc(currentRoute.stops.size() + " stops");
            routeView.setFavorited(currentRoute.following);

            Random random = new Random(position); // seed so views keep the same color
            int currentColor = ColorGenerator.MATERIAL.getColor(random.nextInt());

            // make sure we get a dark color for the polyline
            if (colorMap.get(position) == null) {
                while (!isColorDark(currentColor)) {
                    currentColor = ColorGenerator.MATERIAL.getColor(random.nextInt()*575787) | 0xFF000000;
                }
                colorMap.put(position, currentColor);
            } else {
                currentColor = colorMap.get(position);
            }

            routeView.setBackgroundColor(currentColor); // remove alpha
            routeView.setupMap(activeSegments.get(currentRoute.routeId), currentColor);
            routeView.setRouteId(favoriteRoutes.get(position).routeId);
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
            return darkness >= 0.3;
        }
    }

}
