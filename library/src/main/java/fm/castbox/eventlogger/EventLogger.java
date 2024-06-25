package fm.castbox.eventlogger;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.appevents.AppEventsConstants;
import com.facebook.appevents.AppEventsLogger;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
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

    private AppEventsLogger facebookEventsLogger; //

    private Set<String> facebookEventNameFilters;

    // screen time
    private String screenName;
    private String shortScreenName;
    private long lastScreenLogTime = 0L;
    private long firstLaunchTime = 0L;

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
            if (enableFacebookAnalytics) {
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

    public FirebaseAnalytics getFirebaseAnalytics() {
        return firebaseAnalytics;
    }

    private boolean eventLoggable(@NonNull String eventName, Set<String> filter) {
        return filter == null || filter.contains(eventName);
    }

    public EventLogger enableFacebookAnalytics() {
        Set<String> set = new HashSet<>();
        set.add("tch_ad_rev_roas_001");
        return enableFacebookAnalytics(set);
    }

    public EventLogger enableFacebookAnalytics(Set<String> filter) {
        // facebook
        enableFacebookAnalytics = true;
        facebookEventNameFilters = filter;
        return this;
    }

    public AppEventsLogger getFacebookEventsLogger() {
        return facebookEventsLogger;
    }

    private boolean facebookEventLoggable(@NonNull String eventName) {
        return eventLoggable(eventName, facebookEventNameFilters);
    }

    public void logEventFacebook(final @NonNull String eventName, final @Nullable String category, final @Nullable String itemName, final Map<String, Object> extra) {
        Timber.d("Log event facebook: event name=%s, category=%s, itemName=%s, extra=%s, facebookEventsLogger:" + facebookEventsLogger, eventName, category, itemName, extra == null ? "" : extra.toString());
        if (!enabled || facebookEventsLogger == null) return;
        try {
            Bundle parameters = createBundle(extra);

            if (!TextUtils.isEmpty(category))
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, category);
            if (!TextUtils.isEmpty(itemName))
                parameters.putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, itemName);
            facebookEventsLogger.logEvent(eventName, parameters);
        } catch (Exception ignored) {
        }
    }

    public void logFacebookPurchase(double amount, String currencyCode, HashMap<String, Object> params) {
        if (!enabled || facebookEventsLogger == null) return;
        BigDecimal purchaseAmount = new BigDecimal(amount);
        Currency currency = Currency.getInstance(currencyCode);
        Bundle bundle = createBundle(params);
        facebookEventsLogger.logPurchase(purchaseAmount, currency, bundle);
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

    /**
     * Enter a screen, mostly we use it to log entering fragment.
     *
     * @param screenName screen name, i.e. fragment class name.
     */
    public synchronized void logScreen(@NonNull Activity activity,  @NonNull String screenName) {
        Timber.d("Log screen view, screen=%s.", screenName);
        // Last event hasn't called logScreenPause, do it first
        if (lastScreenLogTime != 0) {
            logScreenPause(this.screenName);
        }
        this.screenName = screenName;
        lastScreenLogTime = System.currentTimeMillis();

        if (!enabled) return;

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
    }

    public synchronized void logScreenPause(final String screenName) {
        if (TextUtils.equals(this.screenName, screenName)) {
            long duration = System.currentTimeMillis() - lastScreenLogTime;
            if (duration > 0 && duration <= 30 * 60 * 1000) // 0 < duration <= 30 minutes
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
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.PURCHASE, bundle);
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
