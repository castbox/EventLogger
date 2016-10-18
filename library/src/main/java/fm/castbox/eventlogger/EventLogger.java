package fm.castbox.eventlogger;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
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

    // log tag
    private final static String TAG = "EventLogger";

    private final static String KEY_CAMPAIGN_URI = "campaignUrl";
    private final static String KEY_USER_ID = "userID";
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
    // instances
    private Tracker gaTracker;  // Google Analytics Tracker
    private FirebaseAnalytics firebaseAnalytics; // Google firebase event logger
    private AppEventsLogger facebookEventsLogger; // Facebook event logger

    // campaign params
    private String campaignUrl;

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
            campaignUrl = sharedPreferences.getString(KEY_CAMPAIGN_URI, null);

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
                AppEventsLogger.activateApp(application);
                facebookEventsLogger = AppEventsLogger.newLogger(application);
            }

            String uid = sharedPreferences.getString(KEY_USER_ID, null);
            if (uid != null)
                setUserId(uid);

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

    public Tracker getGaTracker() {
        return gaTracker;
    }

    public FirebaseAnalytics getFirebaseAnalytics() {
        return firebaseAnalytics;
    }

    public AppEventsLogger getFacebookEventsLogger() {
        return facebookEventsLogger;
    }

    public void setCampaignParams(String url) {
        try {
            setCampaignParams(Uri.parse(url));
        } catch (Exception ignored) {
        }
    }

    public void setCampaignParams(Uri uri) {
        if (!enabled || gaTracker == null || !TextUtils.isEmpty(campaignUrl)) return;

        try {
            if (uri.getQueryParameter("utm_source") != null) {
                campaignUrl = uri.toString();
            } else if (uri.getQueryParameter("target_url") != null) {
                Uri targetUrl = Uri.parse(Uri.decode(uri.getQueryParameter("target_url")));
                if (targetUrl.getQueryParameter("utm_source") != null) {
                    campaignUrl = targetUrl.toString();
                }
            } else if (uri.getQueryParameter("referrer") != null) {
                String referrer = Uri.decode(uri.getQueryParameter("referrer"));
                if (referrer.contains("utm_source=")) {
                    campaignUrl = referrer;
                }
            }

            // save for going forward launch
            if (!TextUtils.isEmpty(campaignUrl)) {
                sharedPreferences.edit().putString(KEY_CAMPAIGN_URI, campaignUrl).apply();
            }

            logEvent("launch", "uri", uri.toString());
        } catch (Exception ignored) {
        }
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

        return (long)((now - firstLaunchTime)/1000.);
    }

    /**
     * Enter a screen, mostly we use it to log entering fragment.
     *
     * @param screenName screen name, i.e. fragment class name.
     */
    public synchronized void logScreen(String screenName) {
        Timber.d("Log screen view, screen=%s.", screenName);
        this.screenName = screenName;
        lastScreenLogTime = System.currentTimeMillis();

        if (!enabled) return;

        try {
            if (gaTracker != null) {
                gaTracker.setScreenName(screenName);
                if (TextUtils.isEmpty(campaignUrl))
                    gaTracker.send(new HitBuilders.ScreenViewBuilder().build());
                else
                    gaTracker.send(new HitBuilders.ScreenViewBuilder().setCampaignParamsFromUrl(campaignUrl).build());
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
        }
        this.screenName = null;
        this.lastScreenLogTime = 0L;
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
                if (!TextUtils.isEmpty(campaignUrl))
                    builder.setCampaignParamsFromUrl(campaignUrl);
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
                if (!TextUtils.isEmpty(campaignUrl))
                    builder.setCampaignParamsFromUrl(campaignUrl);
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
        if (!enabled) return;

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

            sharedPreferences.edit().putString(KEY_USER_ID, userId).apply();
        } catch (Exception ignored) {
        }
    }
}
