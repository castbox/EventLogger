package fm.castbox.eventlogger;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.appevents.AppEventsConstants;
import com.facebook.appevents.AppEventsLogger;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

/**
 * Created by xiaocong on 16/10/8.
 */

public class EventLogger {

    private static EventLogger instance;

    public static EventLogger getInstance() {
        if (instance == null) {
            instance = new EventLogger();
        }
        return instance;
    }

    private final static String KEY_CAMPAIGN_UTM_SOURCE = "utm_source";
    private final static String KEY_CAMPAIGN_UTM_MEDIUM = "utm_medium";
    private final static String KEY_CAMPAIGN_UTM_CAMPAIGN = "utm_campaign";

    static final String PLAY_STORE_REFERRER_KEY = "referrer";
    public static final String PLAY_STORE = "store";
    private static final String PLAY_STORE_ATTRIBUTION_KEY = "attribution";

    private static final String KEY_FIRST_LAUNCH_DATE = "firstLaunchDate";
    private static final String KEY_RETENTION_D2 = "rd2";
    private static final String KEY_RETENTION_W2 = "rw2";
    private static final String KEY_RETENTION_M2 = "rm2";

    public final static String EVENT_NAME_SCREEN = "screen";
    private final static String EVENT_CATEGORY_SCREEN = "screen";
    private final static String EVENT_CATEGORY_SCREEN_LIFE = "screen_life";

    public final static String EVENT_NAME_USER_ACTION = "user_action";

    // enable or disable event logger
    private boolean enabled = true;
    // sharePreferences
    private SharedPreferences sharedPreferences;

    private boolean enableFirebaseAnalytics = false;
    private boolean enableFacebookAnalytics = false;
    // instances
    private FirebaseAnalytics firebaseAnalytics; // Google firebase event logger
    private AppEventsLogger facebookEventsLogger; // Facebook event logger

    // event name filter
    private Set<String> facebookEventNameFilters;

    // screen time
    private String screenName;
    private String shortScreenName;
    private long lastScreenLogTime = 0L;
    private long firstLaunchTime = 0L;

    private Boolean retentionD2 = null;
    private Boolean retentionW2 = null;
    private Boolean retentionM2 = null;

    private EventLoggerCallback eventLoggerCallback;

    private EventLogger() {
    }

    public EventLogger init(@NonNull Application application, EventLoggerCallback callback) {
        eventLoggerCallback = callback;

        sharedPreferences = application.getSharedPreferences("EventLogger", Context.MODE_PRIVATE);

        if (enabled) {
            // firebase
            if (enableFirebaseAnalytics) {
                firebaseAnalytics = FirebaseAnalytics.getInstance(application);
                firebaseAnalytics.setAnalyticsCollectionEnabled(true);
            }
            // fan
            if (enableFacebookAnalytics) {
                // disabled since v4.19.0
                //if (!FacebookSdk.isInitialized())
                //    FacebookSdk.sdkInitialize(application);
                AppEventsLogger.activateApp(application);
                facebookEventsLogger = AppEventsLogger.newLogger(application);
            }
        }
        // to set the install time in case of not exist.
        getInstallTime();

        return this;
    }

    public EventLogger enable(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public EventLogger enableFirebaseAnalytics() {
        // firebase analytics
        enableFirebaseAnalytics = true;
        return this;
    }

    public EventLogger enableFacebookAnalytics() {
        return enableFacebookAnalytics(null);
    }

    public EventLogger enableFacebookAnalytics(Set<String> filter) {
        // facebook
        enableFacebookAnalytics = true;
        facebookEventNameFilters = filter;
        return this;
    }

    public FirebaseAnalytics getFirebaseAnalytics() {
        return firebaseAnalytics;
    }

    public AppEventsLogger getFacebookEventsLogger() {
        return facebookEventsLogger;
    }

    private boolean facebookEventLoggable(@NonNull String eventName) {
        return eventLoggable(eventName, facebookEventNameFilters);
    }

    private boolean eventLoggable(@NonNull String eventName, Set<String> filter) {
        return filter == null || filter.contains(eventName);
    }

    public void setCampaignParams(@NonNull String url) {
        try {
            if (getInstallTime() < 24 * 3600L) {  // allow to set utm in 1 day since installation.
                Map<String, String> queries = getQueryParameters(url);
                setUtmProperties(queries);
                logUtm(queries);
            }
        } catch (Exception ignored) {
        }
    }

    public void setUtmProperties(@NonNull Map<String, String> queries) {
        if (!enabled)
            return;
        try {
            final String utmSource = queries.get(KEY_CAMPAIGN_UTM_SOURCE);
            if (isValidUtm(utmSource))
                setUserProperty(KEY_CAMPAIGN_UTM_SOURCE, utmSource);

            final String utmMedium = queries.get(KEY_CAMPAIGN_UTM_MEDIUM);
            if (isValidUtm(utmMedium))
                setUserProperty(KEY_CAMPAIGN_UTM_MEDIUM, utmMedium);

            final String utmCampaign = queries.get(KEY_CAMPAIGN_UTM_CAMPAIGN);
            if (isValidUtm(utmCampaign))
                setUserProperty(KEY_CAMPAIGN_UTM_CAMPAIGN, utmCampaign);
        } catch (Exception ignored) {
        }
    }

    private void logUtm(@NonNull Map<String, String> queries) {
        if (!enabled) {
            return;
        }

        try {
            final String utmSource = queries.get(KEY_CAMPAIGN_UTM_SOURCE);
            final String utmMedium = queries.get(KEY_CAMPAIGN_UTM_MEDIUM);
            final String utmCampaign = queries.get(KEY_CAMPAIGN_UTM_CAMPAIGN);

            Timber.d("utm_source=%s, utm_campaign=%s, utm_medium=%s", utmSource, utmCampaign, utmMedium);
            final String utmTerm = queries.get("utm_term");
            final String keyword = queries.get("keyword");
            final String campaignId = queries.get("campaignid");
            if (TextUtils.isEmpty(utmSource) && TextUtils.isEmpty(utmMedium) && TextUtils.isEmpty(utmCampaign)) {
                if (!TextUtils.isEmpty(campaignId)) {
                    logEvent(PLAY_STORE, PLAY_STORE_ATTRIBUTION_KEY, "google.cpc");
                    setUserProperty(KEY_CAMPAIGN_UTM_SOURCE, "google");
                    setUserProperty(KEY_CAMPAIGN_UTM_MEDIUM, "cpc");
                }
                if (!TextUtils.isEmpty(keyword)) {
                    logEvent(PLAY_STORE, "keyword", keyword);
                }
            } else if (!TextUtils.isEmpty(utmSource)) {
                if (!TextUtils.isEmpty(utmMedium))
                    logEvent(PLAY_STORE, PLAY_STORE_ATTRIBUTION_KEY, utmSource + "." + utmMedium);
                else
                    logEvent(PLAY_STORE, PLAY_STORE_ATTRIBUTION_KEY, utmSource);
                if (!TextUtils.isEmpty(utmTerm))
                    logEvent(PLAY_STORE, "term", utmTerm);
            }
        } catch (Exception ignore) {
        }
    }

    private boolean isValidUtm(String utm) {
        return !TextUtils.isEmpty(utm) && !TextUtils.equals(utm, "(not%20set)") && !TextUtils.equals(utm, "(not set)");
    }

    private static Map<String, String> getQueryParameters(@NonNull String query) {
        if (query.startsWith("http://") || query.startsWith("https://")) {
            String[] pairs = query.split("\\?");
            if (pairs.length > 1)
                query = pairs[1];
            else
                query = "";
        }

        try {
            query = URLDecoder.decode(query, "UTF-8");
        } catch (Throwable ignored) {
        }

        final Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String pair[] = param.split("=");
            try {
                String key = URLDecoder.decode(pair[0], "UTF-8");
                String value = pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "";
                params.put(key, value);
            } catch (Throwable ignored) {
            }
        }
        return params;
    }

    /**
     * get the time since installation.
     *
     * @return seconds
     */
    public long getInstallTime() {
        long now = System.currentTimeMillis();
        if (firstLaunchTime == 0L)
            firstLaunchTime = sharedPreferences.getLong(KEY_FIRST_LAUNCH_DATE, now);
        if (firstLaunchTime == now)
            sharedPreferences.edit().putLong(KEY_FIRST_LAUNCH_DATE, now).apply();

        return Math.abs((long) ((now - firstLaunchTime) / 1000.));
    }

    private boolean checkRetention(String key) {
        if (TextUtils.equals(key, KEY_RETENTION_D2)) {
            if (retentionD2 == null) {
                retentionD2 = sharedPreferences.getBoolean(KEY_RETENTION_D2, false);
            }
            return retentionD2;
        } else if (TextUtils.equals(key, KEY_RETENTION_W2)) {
            if (retentionW2 == null) {
                retentionW2 = sharedPreferences.getBoolean(KEY_RETENTION_D2, false);
            }
            return retentionW2;
        } else if (TextUtils.equals(key, KEY_RETENTION_M2)) {
            if (retentionM2 == null) {
                retentionM2 = sharedPreferences.getBoolean(KEY_RETENTION_M2, false);
            }
            return retentionM2;
        }

        return false;
    }

    private void logRetentionEvent() {
        // log retention event
        try {
            long installTime = getInstallTime();
            if (installTime > 60 * 60 * 24 && installTime <= 60 * 60 * 24 * 2) {
                if (!checkRetention(KEY_RETENTION_D2)) {
                    logEvent("retention_d2", null, null);
                    retentionD2 = true;
                    sharedPreferences.edit().putBoolean(KEY_RETENTION_D2, true).apply();
                }
            } else if (installTime > 60 * 60 * 24 * 7 && installTime <= 60 * 60 * 24 * 14) {
                if (!checkRetention(KEY_RETENTION_W2)) {
                    logEvent("retention_w2", null, null);
                    retentionW2 = true;
                    sharedPreferences.edit().putBoolean(KEY_RETENTION_W2, true).apply();
                }
            } else if (installTime > 60 * 60 * 24 * 30 && installTime <= 60 * 60 * 24 * 60) {
                if (!checkRetention(KEY_RETENTION_M2)) {
                    logEvent("retention_m2", null, null);
                    retentionM2 = true;
                    sharedPreferences.edit().putBoolean(KEY_RETENTION_M2, true).apply();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Enter a screen, mostly we use it to log entering fragment.
     *
     * @param screenName screen name, i.e. fragment class name.
     */
    public void logScreen(@NonNull Activity activity,  @NonNull String screenName) {
        Timber.d("Log screen view, screen=%s.", screenName);
        this.screenName = screenName;
        lastScreenLogTime = System.currentTimeMillis();

        if (!enabled) return;

        logRetentionEvent();

        try {
            if (firebaseAnalytics != null) {
                // screen_view event
                firebaseAnalytics.setCurrentScreen(activity, screenName, null);

                // screen event
                Bundle bundle = new Bundle();
                String[] names = screenName.split("\\.");
                shortScreenName = names[names.length - 1];
                if (shortScreenName.length() > 36)
                    shortScreenName = shortScreenName.substring(0, 36);
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, shortScreenName);
                bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, EVENT_CATEGORY_SCREEN);
                firebaseAnalytics.logEvent(EVENT_NAME_SCREEN, bundle);
            }
        } catch (Exception ignored) {
        }

        try {
            if (facebookEventsLogger != null && facebookEventLoggable(EVENT_NAME_SCREEN)) {
                Bundle parameters = new Bundle();
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, EVENT_CATEGORY_SCREEN);
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, screenName);
                facebookEventsLogger.logEvent(EVENT_NAME_SCREEN, parameters);
            }
        } catch (Exception ignored) {
        }
    }

    public void logScreenPause(final String screenName) {
        if (TextUtils.equals(this.screenName, screenName)) {
            long duration = System.currentTimeMillis() - lastScreenLogTime;
            if (duration > 0 && duration <= 120 * 60 * 1000) // 0 < duration <= 120 minutes
                logScreenLife(screenName, duration);
            this.screenName = null;
            this.lastScreenLogTime = 0L;
        }
    }

    /**
     * Leave a screen, with screen lifetime.
     *
     * @param screenName screen name, i.e. fragment class name.
     * @param duration   screen duration.
     */
    private void logScreenLife(final String screenName, final long duration) {
        if (!enabled) return;

        if (duration <= 0) return;
        try {
            if (firebaseAnalytics != null) {
                Bundle bundle = new Bundle();
                String[] names = screenName.split("\\.");
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, names[names.length - 1]);
                bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, EVENT_CATEGORY_SCREEN_LIFE);
                bundle.putLong(FirebaseAnalytics.Param.VALUE, duration);
                firebaseAnalytics.logEvent(EVENT_NAME_SCREEN, bundle);
            }
        } catch (Exception ignored) {
        }

        try {
            if (facebookEventsLogger != null && facebookEventLoggable(EVENT_NAME_SCREEN)) {
                Bundle parameters = new Bundle();
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, EVENT_CATEGORY_SCREEN_LIFE);
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, screenName);
                facebookEventsLogger.logEvent(EVENT_NAME_SCREEN, duration, parameters);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Log user action
     *
     * @param itemName name of action
     */
    public void logAction(final @Nullable String itemName) {
        logAction(null, itemName);
    }

    /**
     * Log user action.
     *
     * @param category event category
     * @param itemName name of action
     */
    public void logAction(final @Nullable String category, final @Nullable String itemName) {
        logEvent(EVENT_NAME_USER_ACTION, category, itemName);
    }

    /**
     * Log user action.
     *
     * @param category event category
     * @param itemName event itemName
     * @param value    event value
     */
    public void logAction(final @Nullable String category, final @Nullable String itemName, final long value) {
        logEventValue(EVENT_NAME_USER_ACTION, category, itemName, value);
    }

    /**
     * Log user action.
     *
     * @param category event category
     * @param itemName name of action
     */
    public void logAction(final @Nullable String category, final @Nullable String itemName, final Map<String, Object> extra) {
        logEvent(EVENT_NAME_USER_ACTION, category, itemName, extra);
    }

    /**
     * @param category
     * @param itemName
     */
    public void logPurchase(final @Nullable String category, final @NonNull String itemName) {
        Timber.d("Log purchase event, category=%s, name=%s", category, itemName);
        if (!enabled) return;

        try {
            if (firebaseAnalytics != null) {
                Bundle bundle = new Bundle();
                if (!TextUtils.isEmpty(category))
                    bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, category);
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, itemName);
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.ECOMMERCE_PURCHASE, bundle);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Log common event.
     *
     * @param eventName event name
     * @param category event category
     * @param itemName item id.
     */
    public void logEvent(final @NonNull String eventName, final @Nullable String category, final @Nullable String itemName) {
        logEvent(eventName, category, itemName, null, false);
    }

    /**
     * Log common event.
     *
     * @param eventName event name
     * @param category event category
     * @param itemName item id.
     */
    public void logEvent(final @NonNull String eventName, final @Nullable String category, final @Nullable String itemName, final Map<String, Object> extra) {
        logEvent(eventName, category, itemName, extra, false);
    }

    /**
     * Log common event with a value.
     *
     * @param eventName event name
     * @param category event category
     * @param itemName item id.
     */
    public void logEventValue(final @NonNull String eventName, final @Nullable String category, final @Nullable String itemName, final long value) {
        logEventValue(eventName, category, itemName, value, null);
    }

    /**
     * Log common event with a value.
     *
     * @param eventName event name
     * @param category event category
     * @param itemName item id.
     * @param value value to be logged
     * @param extra extra parameters
     */
    public void logEventValue(final @NonNull String eventName, final @Nullable String category, final @Nullable String itemName, final long value, final Map<String, Object> extra) {
        boolean extendSession = eventLoggerCallback != null && eventLoggerCallback.needExtendSession(eventName, category);
        Timber.d("Log event: event name=%s, category=%s, itemName=%s, value=%d, extendSession=%s.", eventName, category, itemName, value, String.valueOf(extendSession));
        if (!enabled) return;

        try {
            if (firebaseAnalytics != null) {
                Bundle bundle = createBundle(extra);

                if (extendSession) {
                    bundle.putLong("extend_session", 1);
                }
                if (!TextUtils.isEmpty(category))
                    bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, category);
                if (!TextUtils.isEmpty(itemName))
                    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, itemName);
                bundle.putLong(FirebaseAnalytics.Param.VALUE, value);
                firebaseAnalytics.logEvent(eventName, bundle);
            }
        } catch (Exception ignored) {
        }

        try {
            if (facebookEventsLogger != null && facebookEventLoggable(eventName)) {
                Bundle parameters = createBundle(extra);

                if (!TextUtils.isEmpty(category))
                    parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, category);
                if (!TextUtils.isEmpty(itemName))
                    parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, itemName);
                facebookEventsLogger.logEvent(eventName, value, parameters);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Log common event.
     *
     * @param category event category
     * @param itemName item id.
     * @param extra extra parameters
     * @param isItem   should use item id or not to send the event.
     */
    private void logEvent(final @NonNull String eventName, final @Nullable String category, final @Nullable String itemName, final Map<String, Object> extra, boolean isItem) {
        boolean extendSession = eventLoggerCallback != null && eventLoggerCallback.needExtendSession(eventName, category);
        Timber.d("Log event: event name=%s, category=%s, %s=%s, extendSession=%s", eventName, category, isItem ? "itemId" : "itemName", itemName, String.valueOf(extendSession));
        if (!enabled) return;

        try {
            if (firebaseAnalytics != null) {
                Bundle bundle = createBundle(extra);

                if (extendSession) {
                    bundle.putLong("extend_session", 1);
                }
                if (!TextUtils.isEmpty(category))
                    bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, category);
                if (TextUtils.equals(eventName, EVENT_NAME_USER_ACTION) && !TextUtils.isEmpty(shortScreenName)) {
                    bundle.putString("screen", shortScreenName);
                }
                if (!TextUtils.isEmpty(itemName))
                    bundle.putString(isItem ? FirebaseAnalytics.Param.ITEM_ID : FirebaseAnalytics.Param.ITEM_NAME, itemName);
                firebaseAnalytics.logEvent(eventName, bundle);
            }
        } catch (Exception ignored) {
        }

        try {
            if (facebookEventsLogger != null && facebookEventLoggable(eventName)) {
                Bundle parameters = createBundle(extra);

                if (!TextUtils.isEmpty(category))
                    parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, category);
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, itemName);
                facebookEventsLogger.logEvent(eventName, parameters);
            }
        } catch (Exception ignored) {
        }
    }

    private Bundle createBundle(final Map<String, Object> extra) {
        Bundle parameters = new Bundle();
        if (extra != null) {
            for (String k: extra.keySet()) {
                Object v = extra.get(k);
                if (v instanceof String)
                    parameters.putString(k, (String)v);
                else if (v instanceof Long)
                    parameters.putLong(k, (Long)v);
                else if (v instanceof Integer)
                    parameters.putInt(k, (Integer)v);
                else if (v instanceof Float)
                    parameters.putFloat(k, (Float)v);
                else if (v instanceof Double)
                    parameters.putDouble(k, (Double)v);
                else
                    Timber.d("FacebookAnalytics: Ignore event property %s", k);
            }
        }
        return parameters;
    }

    /**
     * Set user property.
     *
     * @param key   property name.
     * @param value property value.
     */
    public void setUserProperty(final @NonNull String key, final @Nullable String value) {
        Timber.d("Log event: set user property %s=%s", key, value);
        if (!enabled) return;

        try {
            if (firebaseAnalytics != null) {
                firebaseAnalytics.setUserProperty(key, value);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (facebookEventsLogger != null) {
                Bundle parameters = new Bundle();
                parameters.putString(key, value);
                AppEventsLogger.updateUserProperties(parameters, new GraphRequest.Callback() {
                    @Override
                    public void onCompleted(GraphResponse response) {
                        Timber.d("User properties updated: %s=%s", key, value);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sets the user ID property.
     *
     * @param userId user id. null to remove the user id from event logger.
     */
    public void setUserId(final String userId) {
        Timber.d("Log event: set user id=%s", userId);
        if (!enabled) return;

        try {
            if (firebaseAnalytics != null) {
                firebaseAnalytics.setUserId(userId);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (facebookEventsLogger != null) {
                AppEventsLogger.setUserID(userId);
            }
        } catch (Throwable ignored) {
        }
    }

    public interface EventLoggerCallback {
        boolean needExtendSession(String eventName, String category);
    }
}
