package pl.polidea.utility.notificationcenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.os.Handler;
import android.util.Log;

/**
 * Implements generic notifcation service. It has generic-ed processing of the
 * notifications send - you can register for particular class of notifications
 * to receive.
 * 
 * @author potiuk
 * 
 */
public class NotificationCenter {
    private static final String TAG = NotificationCenter.class.getSimpleName();
    private final transient Handler handler;

    // NOTE: Here Object is needed. See Joshua Blosh's typesafe heterogeneous
    // container pattern (Item 29) Effective Java 2nd Edition
    private final transient Map<Class< ? extends Notification>, Object> listenerMap = new HashMap<Class< ? extends Notification>, Object>();

    public NotificationCenter() {
        handler = new Handler();
    }

    /**
     * Get all listeners of the type specified.
     * 
     * @param <T>
     *            generic
     * @param notificationType
     *            type
     * @return list of listeners
     */
    public synchronized <T extends Notification> // NOPMD
    List<NotificationListener<T>> getListeners(final Class<T> notificationType) {
        final List<NotificationListener<T>> list = internalGetListeners(notificationType);
        if (list == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(new LinkedList<NotificationListener<T>>(list));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<NotificationListener<T>> internalGetListeners(final Class<T> notificationType) {
        return (List<NotificationListener<T>>) listenerMap.get(notificationType);
    }

    /**
     * Register the listener to receive objects of type <T>. You typically do it
     * in onResume of activity using it.
     * 
     * @param <T>
     *            type of the object to register
     * 
     * @param notificationType
     *            type of the object
     * @param listener
     *            listener to register
     */
    public synchronized <T extends Notification> // NOPMD
    void registerListener(final Class<T> notificationType, final NotificationListener<T> listener) {
        if (!listenerMap.containsKey(notificationType)) {
            listenerMap.put(notificationType, new ArrayList<NotificationListener<T>>());
        }
        if (!internalGetListeners(notificationType).contains(listener)) {
            internalGetListeners(notificationType).add(listener);
        }
    }

    /**
     * Unregister the listener from receiving objects of type <T>. You typically
     * do it in onPause of activity using it.
     * 
     * @param <T>
     *            type of the object to register
     * 
     * @param notificationType
     *            type of the object
     * @param listener
     *            listener to register
     */
    public synchronized <T extends Notification> // NOPMD
    void unregisterListener(final Class<T> notificationType, final NotificationListener<T> listener) {
        final List<NotificationListener<T>> list = internalGetListeners(notificationType);
        list.remove(listener);
    }

    /**
     * Emits notification of the type specified.
     * 
     * @param <T>
     *            type of notification
     * @param clazz
     *            class of the notification (same as type - workarounding Java
     *            generic limitation)
     * @param notification
     *            notification to send.
     */
    public synchronized <T extends Notification> void emitNotification(// NOPMD
            final Class<T> clazz, final T notification) {
       // Log.d(TAG, "Emiting notification " + notification);
        final Throwable t = new Throwable(); // NOPMD
       // Log.d(TAG, "Stack trace for emitting notification:", t);
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (final NotificationListener<T> listener : getListeners(clazz)) {
                  // Log.d(TAG, "Sending Notification " + notification + " to " + listener);
                    listener.notificationReceived(notification);
                  // Log.d(TAG, "Processe Notification " + notification + " by " + listener);
                }
            }
        });
    }

    /**
     * Listener for given type.
     * 
     * @param <T>
     */
    public interface NotificationListener<T> {
        void notificationReceived(T notification);
    }
}