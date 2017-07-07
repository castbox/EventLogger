package fm.castbox.eventlogger;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.facebook.appevents.AppEventsConstants;
import com.facebook.appevents.AppEventsLogger;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

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
    static final String PLAY_STORE = "store";
    private static final String PLAY_STORE_ATTRIBUTION_KEY = "attribution";

    private static final String KEY_FIRST_LAUNCH_DATE = "firstLaunchDate";

    private final static String EVENT_NAME_SCREEN = "screen";
    private final static String EVENT_CATEGORY_SCREEN = "screen";
    private final static String EVENT_CATEGORY_SCREEN_LIFE = "screen_life";

    private final static String EVENT_NAME_USER_ACTION = "user_action";

    // enable or disable event logger
    private boolean enabled = true;
    // sharePreferences
    private SharedPreferences sharedPreferences;

    private String googleAnalyticsStringId;
    private int googleAnalyticsResId = 0;
    private boolean enableFirebaseAnalytics = false;
    private boolean enableFacebookAnalytics = false;
    private boolean enableCrashReport = false;
    // instances
    private Tracker gaTracker;  // Google Analytics Tracker
    private FirebaseAnalytics firebaseAnalytics; // Google firebase event logger
    private AppEventsLogger facebookEventsLogger; // Facebook event logger

    // screen time
    private String screenName;
    private String shortScreenName;
    private long lastScreenLogTime = 0L;
    private long firstLaunchTime = 0L;

    private EventLogger() {
    }

    public EventLogger init(@NonNull Application application) {
        sharedPreferences = application.getSharedPreferences("EventLogger", Context.MODE_PRIVATE);

        if (enabled) {
            // ga
            if (googleAnalyticsStringId != null || googleAnalyticsResId > 0) {
                GoogleAnalytics analytics = GoogleAnalytics.getInstance(application);
                // To enable debug logging use: adb shell setprop log.tag.GAv4 DEBUG
                if (googleAnalyticsResId > 0)
                    gaTracker = analytics.newTracker(googleAnalyticsResId);
                else
                    gaTracker = analytics.newTracker(googleAnalyticsStringId);
                gaTracker.enableExceptionReporting(false);
                gaTracker.enableAutoActivityTracking(false);
                gaTracker.enableAdvertisingIdCollection(true);
                gaTracker.setAnonymizeIp(true);
            }
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

    public EventLogger enableGoogleAnalytics(String id) {
        // Google analytics
        googleAnalyticsStringId = id;
        return this;
    }

    public EventLogger enableGoogleAnalytics(int id) {
        // Google analytics
        googleAnalyticsResId = id;
        return this;
    }

    public EventLogger enableFirebaseAnalytics() {
        // firebase analytics
        enableFirebaseAnalytics = true;
        return this;
    }

    public EventLogger enableFacebookAnalytics() {
        // facebook
        enableFacebookAnalytics = true;
        return this;
    }

    public EventLogger enableCrashReport(boolean enabled) {
        enableCrashReport = enabled;
        return this;
    }

    public Tracker getGaTracker() {
        return gaTracker;
    }

    public FirebaseAnalytics getFirebaseAnalytics() {
        return firebaseAnalytics;
    }

    public AppEventsLogger getFacebookEventsLogger() {
        return facebookEventsLogger;
    }

    public void setCampaignParams(@NonNull String url) {
        try {
            logUtm(getQueryParameters(url));
        } catch (Exception ignored) {
        }
    }

    private void logUtm(Map<String, String> queries) {
        if (!enabled) {
            return;
        }

        try {
            final String utmSource = queries.get(KEY_CAMPAIGN_UTM_SOURCE);
            final String utmMedium = queries.get(KEY_CAMPAIGN_UTM_MEDIUM);
            final String utmCampaign = queries.get(KEY_CAMPAIGN_UTM_CAMPAIGN);

            Timber.d("utm_source=%s, utm_campaign=%s, utm_medium=%s", utmSource, utmCampaign, utmMedium);
            if (!TextUtils.isEmpty(utmSource))
                setUserProperty(KEY_CAMPAIGN_UTM_SOURCE, utmSource);
            if (!TextUtils.isEmpty(utmMedium))
                setUserProperty(KEY_CAMPAIGN_UTM_MEDIUM, utmMedium);
            if (!TextUtils.isEmpty(utmCampaign))
                setUserProperty(KEY_CAMPAIGN_UTM_CAMPAIGN, utmCampaign);

            final String utmTerm = queries.get("utm_term");
            final String keyword = queries.get("keyword");
            if (TextUtils.isEmpty(utmSource) && TextUtils.isEmpty(utmMedium)) {
                if (!TextUtils.isEmpty(keyword)) {
                    EventLogger.getInstance().logEvent(PLAY_STORE, "keyword", keyword);
                    EventLogger.getInstance().logEvent(PLAY_STORE, PLAY_STORE_ATTRIBUTION_KEY, "google.adwords");
                }
            } else if (!TextUtils.isEmpty(utmSource)) {
                if (TextUtils.isEmpty(utmMedium))
                    EventLogger.getInstance().logEvent(PLAY_STORE, PLAY_STORE_ATTRIBUTION_KEY, utmSource);
                else
                    EventLogger.getInstance().logEvent(PLAY_STORE, PLAY_STORE_ATTRIBUTION_KEY, utmSource + "." + utmMedium);
                if (!TextUtils.isEmpty(utmTerm))
                    EventLogger.getInstance().logEvent(PLAY_STORE, "term", utmTerm);
            }

        } catch (Exception ignore) {
        }
    }

    private static Map<String, String> getQueryParameters(@NonNull String query) {
        if (query.startsWith("http://") || query.startsWith("https://")) {
            String[] pairs = query.split("\\?");
            if (pairs.length > 1)
                query = pairs[1];
            else
                query = "";
        }

        final Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String pair[] = param.split("=");
            try {
                String key = URLDecoder.decode(pair[0], "UTF-8");
                String value = pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "";
                params.put(key, value);
            } catch (Exception ignored) {
            }
        }
        return params;
    }

    /**
     * get the time since installation.
     * @return seconds
     */
    public long getInstallTime() {
        long now = System.currentTimeMillis();
        if (firstLaunchTime == 0L)
            firstLaunchTime = sharedPreferences.getLong(KEY_FIRST_LAUNCH_DATE, now);
        if (firstLaunchTime == now)
            sharedPreferences.edit().putLong(KEY_FIRST_LAUNCH_DATE, now).apply();

        return Math.abs((long)((now - firstLaunchTime)/1000.));
    }

    /**
     * Enter a screen, mostly we use it to log entering fragment.
     *
     * @param screenName screen name, i.e. fragment class name.
     */
    public synchronized void logScreen(String screenName) {
        Timber.d("Log screen view, screen=%s.", screenName);
        // Last event hasn't called logScreenPause, do it first
        if (lastScreenLogTime != 0) {
            logScreenPause(this.screenName);
        }
        this.screenName = screenName;
        lastScreenLogTime = System.currentTimeMillis();

        if (!enabled) return;

        try {
            if (gaTracker != null) {
                gaTracker.setScreenName(screenName);
                gaTracker.send(new HitBuilders.ScreenViewBuilder().build());
            }
        } catch (Exception ignored) {
        }

        try {
            if (firebaseAnalytics != null) {
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
            if (facebookEventsLogger != null) {
                Bundle parameters = new Bundle();
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, EVENT_CATEGORY_SCREEN);
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, screenName);
                facebookEventsLogger.logEvent(EVENT_NAME_SCREEN, parameters);
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void logScreenPause(final String screenName) {
        if (TextUtils.equals(this.screenName, screenName)) {
            logScreenLife(screenName, System.currentTimeMillis() - lastScreenLogTime);
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
//        Timber.d("Log screen life, screen=%s, duration=%ds.", screenName, duration/1000. );
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
            if (facebookEventsLogger != null) {
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
    public void logAction(final @NonNull String itemName) {
        logAction(null, itemName);
    }

    /**
     * Log user action.
     *
     * @param category   event category
     * @param itemName name of action
     */
    public void logAction(final @Nullable String category, final @NonNull String itemName) {
        logEvent(EVENT_NAME_USER_ACTION, category, itemName);
    }

    /**
     *
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
     * Log item view event
     *
     * @param itemId item id to be viewed.
     */
    public void logItemView(final @NonNull String itemId) {
        logItemView(null, itemId);
    }

    /**
     * Log item view event
     *
     * @param category event category
     * @param itemId   item id to be viewed.
     */
    public void logItemView(final @Nullable String category, final @NonNull String itemId) {
        logItemAction(FirebaseAnalytics.Event.VIEW_ITEM, category, itemId);
    }

    /**
     * Log event performed on specified item.
     *
     * @param itemId item id to be viewed.
     */
    public void logItemAction(final @NonNull String eventName, final @NonNull String itemId) {
        logItemAction(eventName, null, itemId);
    }

    /**
     * Log event performed on specified item.
     *
     * @param category event category
     * @param itemId   item id to be viewed.
     */
    public void logItemAction(final @NonNull String eventName, final @Nullable String category, final @NonNull String itemId) {
        logEvent(eventName, category, itemId, true);
    }

    /**
     * Log common event.
     *
     * @param category event category
     * @param itemName item id.
     */
    public void logEvent(final @NonNull String eventName, final @Nullable String category, final @NonNull String itemName) {
        logEvent(eventName, category, itemName, false);
    }

    /**
     * Log common event.
     *
     * @param category event category
     * @param itemName item id.
     * @param isItem should use item id or not to send the event.
     */
    private void logEvent(final @NonNull String eventName, final @Nullable String category, final @NonNull String itemName, boolean isItem) {
        Timber.d("Log event: event name=%s, category=%s, %s=%s", eventName, category, isItem ? "itemId" : "itemName", itemName);
        if (!enabled) return;

        try {
            if (gaTracker != null) {
                HitBuilders.EventBuilder builder = new HitBuilders.EventBuilder()
                        .setCategory(eventName)
                        .setLabel(itemName);
                if (!TextUtils.isEmpty(category))
                    builder.setAction(category);
                gaTracker.send(builder.build());
            }
        } catch (Exception ignored) {
        }

        try {
            if (firebaseAnalytics != null) {
                Bundle bundle = new Bundle();
                if (!TextUtils.isEmpty(category))
                    bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, category);
                if (TextUtils.equals(eventName, EVENT_NAME_USER_ACTION) && !TextUtils.isEmpty(shortScreenName)) {
                    bundle.putString("screen", shortScreenName);
                }
                bundle.putString(isItem ? FirebaseAnalytics.Param.ITEM_ID : FirebaseAnalytics.Param.ITEM_NAME, itemName);
                firebaseAnalytics.logEvent(eventName, bundle);
            }
        } catch (Exception ignored) {
        }

        try {
            if (facebookEventsLogger != null) {
                Bundle parameters = new Bundle();
                if (!TextUtils.isEmpty(category))
                    parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, category);
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, itemName);
                facebookEventsLogger.logEvent(eventName, parameters);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Log common event with a value.
     *
     * @param category event category
     * @param itemName item id.
     */
    public void logEventValue(final @NonNull String eventName, final @Nullable String category, final @Nullable String itemName, final long value) {
        Timber.d("Log event: event name=%s, category=%s, itemName=%s, value=%d.", eventName, category, itemName, value);
        if (!enabled) return;

        try {
            if (gaTracker != null) {
                HitBuilders.EventBuilder builder = new HitBuilders.EventBuilder()
                        .setCategory(eventName)
                        .setValue(value);
                if (!TextUtils.isEmpty(category))
                    builder.setAction(category);
                if (!TextUtils.isEmpty(itemName))
                    builder.setLabel(itemName);
                gaTracker.send(builder.build());
            }
        } catch (Exception ignored) {
        }

        try {
            if (firebaseAnalytics != null) {
                Bundle bundle = new Bundle();
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
            if (facebookEventsLogger != null) {
                Bundle parameters = new Bundle();
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
     * Report error.
     *
     * @param err     throwable to be report.
     * @param message readable message additional to err.
     */
    public void logError(final Throwable err, @Nullable String message) {
        if (!enableCrashReport) return;

        try {
            if (!TextUtils.isEmpty(message))
                FirebaseCrash.log(message);
            FirebaseCrash.report(err);
        } catch (Exception ignored) {
        }
    }

    /**
     * Set user property.
     * @param key property name.
     * @param value property value.
     */
    public void setUserProperty(final @NonNull String key, final @Nullable String value) {
        Timber.d("Log event: set user property %s=%s", key, value);
        if (!enabled) return;

        try {
            if (firebaseAnalytics != null) {
                firebaseAnalytics.setUserProperty(key, value);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Sets the user ID property.
     * @param userId user id. null to remove the user id from event logger.
     */
    public void setUserId(final String userId) {
        Timber.d("Log event: set user id=%s", userId);
        if (!enabled) return;

        try {
            if (firebaseAnalytics != null) {
                firebaseAnalytics.setUserId(userId);
            }
        } catch (Exception ignored) {
        }
    }
}
