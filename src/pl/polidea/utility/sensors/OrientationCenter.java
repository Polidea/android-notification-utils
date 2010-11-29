package pl.polidea.utility.sensors;

import pl.polidea.utility.notificationcenter.Notification;
import pl.polidea.utility.notificationcenter.NotificationCenter;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Single place where orientation is collected and distributed to any listeners.
 * It uses notification service to distribute the notifications to the
 * registered listeners. It implements filtering on orientation received so that
 * we only get notification when the change is permanent.
 * 
 */
public class OrientationCenter {

    private static final String TAG = OrientationCenter.class.getSimpleName();

    private static SensorManager sensorManager; // NOPMD by potiuk on 11/29/10
                                                // 3:40 AM
    private boolean sensorRegistered = false;

    private final ValueProcessor azimuth = new ValueProcessor(0.6f, 0.25f);
    private final ValueProcessor pitch = new ValueProcessor(0.08f, 0.5f);
    private final ValueProcessor roll = new ValueProcessor(0.08f, 0.5f);

    private final float[] accelValues = new float[3];
    private final float[] geomagValues = new float[3];
    private final float[] devR = new float[16];
    private final float[] camR = new float[16];
    private final float[] inclinationMatrix = new float[16];
    private final float[] lastOrientation = new float[3];
    private boolean loopReady = false; // NOPMD
    private boolean calibrationRequestEmited = false;

    private float declination = 0;

    private final SensorEventListener orientationSensorlistener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    if (!calibrationRequestEmited) {
                        calibrationRequestEmited = true;
                        notificationCenter.emitNotification(
                                OrientationAccuracyNotification.class,
                                new OrientationAccuracyNotification(false));
                    }
                } else {
                    if (calibrationRequestEmited) {
                        calibrationRequestEmited = false;
                        notificationCenter.emitNotification(
                                OrientationAccuracyNotification.class,
                                new OrientationAccuracyNotification(true));
                    }
                }
            }
        }

        @Override
        public void onSensorChanged(final SensorEvent event) {
            switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelValues, 0, 3);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, geomagValues, 0, 3);
                loopReady = true;
                break;
            default:
                break;
            }

            if (accelValues != null && geomagValues != null && loopReady) {
                loopReady = false;

                SensorManager.getRotationMatrix(devR, inclinationMatrix,
                        accelValues, geomagValues);
                SensorManager.remapCoordinateSystem(devR, SensorManager.AXIS_Z,
                        SensorManager.AXIS_MINUS_X, camR);
                SensorManager.getOrientation(camR, lastOrientation);

                // azimuth = discriminate(azimuth, lastOrientation[0]);
                // pitch = discriminate(pitch, lastOrientation[1]);
                // roll = discriminate(roll, lastOrientation[2]);
                boolean change = false;
                change = azimuth.push(lastOrientation[0]) || change;
                change = pitch.push(lastOrientation[1]) || change;
                change = roll.push(lastOrientation[2]) || change;
                if (change) {
                    notificationCenter.emitNotification(
                            OrientationUpdateNotification.class,
                            new OrientationUpdateNotification(getAzimuth(),
                                    getPitch(), getRoll()));
                }
            }
        }
    };

    private final NotificationCenter notificationCenter;

    /**
     * Creates the center.
     * 
     * @param context
     *            context for the center.
     * @param notificationCenter
     *            notification center used.
     */
    public OrientationCenter(final Context context,
            final NotificationCenter notificationCenter) {
        sensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE); // NOPMD
        this.notificationCenter = notificationCenter;
    }

    /**
     * Starts collecting orientation. Typically this should be run in onResume
     * of the activity using it.
     */
    public void startCollecting() {
        Log.d(TAG, "startCollecting");
        if (!sensorRegistered) {
            sensorManager.registerListener(orientationSensorlistener,
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                    SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(orientationSensorlistener,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_GAME);
            sensorRegistered = true;
            calibrationRequestEmited = false;
        }
    }

    /**
     * Stops collecting orientation. Typically this should be run in onPause of
     * the activity using it.
     */
    public void stopCollecting() {
        Log.d(TAG, "stopCollecting");
        if (sensorRegistered) {
            sensorManager.unregisterListener(orientationSensorlistener);
            sensorRegistered = false;
        }
    }

    public float getDeclination() {
        return (float) Math.toDegrees(declination);
    }

    public void setDeclination(final float declination) {
        this.declination = (float) Math.toRadians(declination);
    }

    public float getAzimuth() {
        return azimuth.get() - declination;
    }

    public float getPitch() {
        return pitch.get();
    }

    public float getRoll() {
        return roll.get();
    }

    /**
     * Notified when orientation changes.
     * 
     */
    public static class OrientationUpdateNotification implements Notification {
        private final float azimuth;
        private final float pitch;
        private final float roll;

        public OrientationUpdateNotification(final float azimuth,
                final float pitch, final float roll) {
            this.azimuth = azimuth;
            this.pitch = pitch;
            this.roll = roll;
        }

        public float getAzimuth() {
            return azimuth;
        }

        public float getPitch() {
            return pitch;
        }

        public float getRoll() {
            return roll;
        }
    }

    /**
     * Notified when orientation accuracy changed.
     * 
     */
    public static class OrientationAccuracyNotification implements Notification {
        private final boolean sufficient;

        public OrientationAccuracyNotification(final boolean sufficient) {
            this.sufficient = sufficient;
        }

        public boolean isSufficient() {
            return sufficient;
        }
    }

    /**
     * Filtering class. Applies cap on the changes received (so we wont allow
     * single change bigger than highCap) including decay and the fact that we
     * can go round.
     * 
     * @author potiuk
     * 
     */
    protected static class ValueProcessor {
        private final float highCap;
        private final float decay;

        private float oldValue = 0;

        /**
         * Parameters of the filter.
         * 
         * @param highCap
         *            maximum change allowed - this determines how big jumps we
         *            take into account at all, it is more like high frequency
         *            filter set
         * @param decay
         *            how fast we go to the new value - the lower it is, the
         *            slower we move.
         */
        public ValueProcessor(final float highCap, final float decay) {
            this.highCap = highCap;
            this.decay = decay;
        }

        public boolean push(final float val) {
            float newValue = val;
            if (oldValue - newValue >= Math.PI) {
                newValue += 2 * Math.PI;
            } else if (oldValue - newValue <= -Math.PI) {
                newValue -= 2 * Math.PI;
            }
            final float diff = newValue - oldValue;
            oldValue += function(diff);
            if (oldValue >= Math.PI) {
                oldValue -= 2 * Math.PI;
            } else if (oldValue <= -Math.PI) {
                oldValue += 2 * Math.PI;
            }
            return true;
        }

        public float get() {
            return Math.round(oldValue * 400.0f) / 400.0f;
        }

        private float function(final float arg) {
            return arg
                    * (float) (1 - Math.cos(clamp(arg, -highCap, highCap)
                            / highCap * Math.PI)) / 2.0f * decay;
        }

        private float clamp(final float a, final float min, final float max) {
            return a > max ? max : a < min ? min : a;
        }
    }
}
