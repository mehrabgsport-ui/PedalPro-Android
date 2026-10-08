package ir.pedalpro.app;

import java.util.ArrayList;
import java.util.List;

public class RouteRepository {

    public static class RouteItem {
        public long id;
        public String title;
        public String date;
        public double distance;
        public long duration;

        public RouteItem(long id, String title, String date, double distance, long duration) {
            this.id = id;
            this.title = title;
            this.date = date;
            this.distance = distance;
            this.duration = duration;
        }
    }

    public List<RouteItem> search(String dateQuery) {
        return new ArrayList<>();
    }

    public boolean delete(long routeId) {
        return true;
    }
}
