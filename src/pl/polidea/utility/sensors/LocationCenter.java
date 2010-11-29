package pl.polidea.utility.sensors;

import java.util.List;

import pl.polidea.utility.notificationcenter.Notification;
import pl.polidea.utility.notificationcenter.NotificationCenter;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Single place where location is taken care of. It emits notifications through
 * notification center. Normally there should be singleton attached to
 * Application object serving location notification. Activities using
 * notification center should register notification listeners and start
 * collecting location in onResume and they should unregister listeners and stop
 * collecting location in on Pause. This way clashes between
 * registering/unregistering are avoided. Note! location notification might not
 * come immediately after starting collecting it, so in onResume one should
 * either read the last notification or force next update to be emitted by
 * forceNextLocationChange.
 * 
 * @author potiuk
 * 
 */
public class LocationCenter {

    /**
     * Notified when provider is disabled.
     * 
     */
    public static class LocationProviderDisabledNotification implements
            Notification {
    }

    /**
     * Notified when provider is enabled.
     * 
     */
    public static class LocationProviderEnabledNotification implements
            Notification {
    }

    /**
     * Notified when timeout occurs on receiving notification.
     * 
     */
    public static class LocationTimeoutNotification implements Notification {
    }

    /**
     * Notified when location changed.
     * 
     */
    public static class LocationChangedNotification implements Notification {
        private final Location location;

        public LocationChangedNotification(final Location location) {
            this.location = location;
        }

        public Location getLocation() {
            return location;
        }
    }

    private static final String TAG = LocationCenter.class.getSimpleName();

    /**
     * Network provider.
     */
    public static final int SOURCETYPE_NET = 0;
    /**
     * GPS provider.
     */
    public static final int SOURCETYPE_GPS = 1;
    /**
     * Any provider.
     */
    public static final int SOURCETYPE_ANY = 2;

    private static final int LOCATION_UPDATE_TIMEOUT = 1125;

    private final Context context;

    private Location lastLocationGPS = null;
    private Location lastLocationNet = null;
    private Location bestLocationYet = null;
    private boolean gpsProviderEnabled;
    private boolean networkProviderEnabled;
    private boolean forceNextChange = false;

    private final NotificationCenter notificationCenter;

    private final Handler timeoutHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            if (lastLocationGPS == null && lastLocationNet == null) {
                stopCollecting(SOURCETYPE_ANY);
                notificationCenter.emitNotification(
                        LocationTimeoutNotification.class,
                        new LocationTimeoutNotification());
            }
        }
    };

    private final LocationListener listenerGPS = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            Log.i(TAG, "location update(GPS): " + location);
            lastLocationGPS = location;
            pushLocation(location);
        }

        @Override
        public void onProviderDisabled(final String provider) {
            Log.i(TAG, "provider disabled(GPS)");
            gpsProviderEnabled = false;
            if (!networkProviderEnabled) {
                notificationCenter.emitNotification(
                        LocationProviderDisabledNotification.class,
                        new LocationProviderDisabledNotification());
            }
        }

        @Override
        public void onProviderEnabled(final String provider) {
            Log.i(TAG, "provider enabled(GPS)");
            if (!gpsProviderEnabled && !networkProviderEnabled) {
                notificationCenter.emitNotification(
                        LocationProviderEnabledNotification.class,
                        new LocationProviderEnabledNotification());
            }
            gpsProviderEnabled = true;
        }

        @Override
        public void onStatusChanged(final String provider, final int status,
                final Bundle extras) {
            // do nothing
        }
    };

    private final android.location.LocationListener listenerNet = new android.location.LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            Log.i(TAG, "location update(Net): " + location);
            lastLocationNet = location;
            pushLocation(location);
        }

        @Override
        public void onProviderDisabled(final String provider) {
            Log.i(TAG, "provider disabled(Net)");
            networkProviderEnabled = false;
            if (!gpsProviderEnabled) {
                notificationCenter.emitNotification(
                        LocationProviderDisabledNotification.class,
                        new LocationProviderDisabledNotification());
            }
        }

        @Override
        public void onProviderEnabled(final String provider) {
            Log.i(TAG, "provider enabled(Net)");
            if (!gpsProviderEnabled && !networkProviderEnabled) {
                notificationCenter.emitNotification(
                        LocationProviderEnabledNotification.class,
                        new LocationProviderEnabledNotification());
            }
            networkProviderEnabled = true;
        }

        @Override
        public void onStatusChanged(final String provider, final int status,
                final Bundle extras) {
            // do nothing
        }
    };

    public LocationCenter(final Context context,
            final NotificationCenter notificationCenter) {
        this.context = context;
        this.notificationCenter = notificationCenter;
    }

    /**
     * Retrieves last location - either specified type or best location if ANY
     * used.
     * 
     * @param type
     *            type of notifcation (one of SOURCETYPE_* constants)
     * @return
     */
    public Location getLastLocation(final int type) {
        switch (type) {
        case SOURCETYPE_NET:
            return lastLocationNet;
        case SOURCETYPE_GPS:
            return lastLocationGPS;
        case SOURCETYPE_ANY:
            return getBest();
        default:
            return getBest();
        }
    }

    /**
     * Checks if provider of the type specified is supported.
     * 
     * @param type
     *            one of SOURCETYPE_*
     * @return true if the type supported (or any supported in case ANY used)
     */
    public boolean isProviderSupported(final int type) {
        final LocationManager mgr = (LocationManager) this.context
                .getSystemService(Context.LOCATION_SERVICE);
        final List<String> providers = mgr.getAllProviders();
        if (providers != null) {
            switch (type) {
            case SOURCETYPE_NET:
                return providers.contains(LocationManager.NETWORK_PROVIDER);
            case SOURCETYPE_GPS:
                return providers.contains(LocationManager.GPS_PROVIDER);
            case SOURCETYPE_ANY:
                return providers.contains(LocationManager.NETWORK_PROVIDER)
                        || providers.contains(LocationManager.GPS_PROVIDER);
            default:
                return providers.contains(LocationManager.NETWORK_PROVIDER)
                        || providers.contains(LocationManager.GPS_PROVIDER);
            }
        }
        return false;
    }

    /**
     * Start collecting location information. This should typically be run in
     * onResume of activity using it.
     * 
     * @param type
     *            type of the location information
     * @param minTime
     *            minimum time between updates (hint)
     * @param minDistance
     *            minimum distance between updates (hint)
     */
    public void startCollecting(final int type, final long minTime,
            final float minDistance) {
        final LocationManager mgr = (LocationManager) this.context
                .getSystemService(Context.LOCATION_SERVICE);
        Log.i(TAG, "startCollecting: " + type);
        switch (type) {
        case SOURCETYPE_NET:
            mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    minTime, minDistance, listenerNet);
            break;
        case SOURCETYPE_GPS:
            mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime,
                    minDistance, listenerGPS);
            break;
        case SOURCETYPE_ANY:
            startCollectingAny(minTime, minDistance, mgr);
            break;
        default:
            startCollectingAny(minTime, minDistance, mgr);
        }
    }

    private void startCollectingAny(final long minTime,
            final float minDistance, final LocationManager mgr) {
        if (isProviderSupported(SOURCETYPE_NET)) {
            mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    minTime, minDistance, listenerNet);
        }
        if (isProviderSupported(SOURCETYPE_GPS)) {
            mgr.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER, minTime,
                    minDistance, listenerGPS);
        }
    }

    /**
     * Force next location update to be notified to all listeners EVEN if
     * current location has not changed. This covers the use case where client
     * does not want to use cached location, but wants to get best location but
     * wants to get fresh information as well.
     */
    public void forceNextLocationChange() {
        forceNextChange = true;
    }

    /**
     * Stops collecting information of given type. This should typically be run
     * in onPause of activity using it.
     * 
     * @param type
     *            type of the provider
     */
    public void stopCollecting(final int type) {
        Log.i(TAG, "stopCollecting: " + type);
        final LocationManager mgr = (LocationManager) this.context
                .getSystemService(Context.LOCATION_SERVICE);
        switch (type) {
        case SOURCETYPE_NET:
            mgr.removeUpdates(listenerNet);
            break;
        case SOURCETYPE_GPS:
            mgr.removeUpdates(listenerGPS);
            break;
        case SOURCETYPE_ANY:
            mgr.removeUpdates(listenerNet);
            mgr.removeUpdates(listenerGPS);
            break;
        default:
            mgr.removeUpdates(listenerNet);
            mgr.removeUpdates(listenerGPS);
            break;
        }
    }

    /**
     * Pushes location (checks if it is better than the previous onen and sets
     * it only if it is really better). Note that true value returned does not
     * mean that the new location was actually better but when notification was
     * emitted. Emiting notification might also happen when client forced
     * location update to be emitted on next location update
     * (forceNextLocationChange).
     * 
     * @param location
     *            location to push
     * @return true if location has been emitted.
     */
    private boolean pushLocation(final Location location) {
        boolean better = false;

        if (location == null) {
            return false;
        }

        if (bestLocationYet == null) {
            better = true;
        } else if (bestLocationYet.getTime() < location.getTime() - 20000) {
            better = true;
        } else if (bestLocationYet.hasAccuracy() && location.hasAccuracy()) {
            // decide using accuracy information
            if (bestLocationYet.getAccuracy() > location.getAccuracy()) {
                better = true;
            }
        } else if (location == lastLocationGPS) {
            better = true;
        } else if (bestLocationYet != lastLocationGPS) {
            better = true;
        }

        if (better) {
            bestLocationYet = location;
        }
        if (better || forceNextChange) {
            emitLocationChange();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns best location yet.
     * 
     * @return
     */
    private Location getBest() {
        return bestLocationYet;
    }

    private void emitLocationChange() {
        notificationCenter.emitNotification(LocationChangedNotification.class,
                new LocationChangedNotification(getBest()));
    }

    public void setupTimeout(final int timeoutMs) {
        timeoutHandler.sendEmptyMessageDelayed(LOCATION_UPDATE_TIMEOUT,
                timeoutMs);
    }

}
