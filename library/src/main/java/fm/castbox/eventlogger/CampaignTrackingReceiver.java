package fm.castbox.eventlogger;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import timber.log.Timber;

/**
 * Created by xiaocong on 16/10/15.
 */

public class CampaignTrackingReceiver extends com.google.android.gms.analytics.CampaignTrackingReceiver {

    private static final String PLAY_STORE_REFERRER_KEY = "referrer";
    private static final String PLAY_STORE_ATTRIBUTION_KEY = "attribution";
    private static final String PLAY_STORE = "store";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(PLAY_STORE_REFERRER_KEY)) {
            String url = intent.getStringExtra(PLAY_STORE_REFERRER_KEY);
            if (!TextUtils.isEmpty(url)) {
                EventLogger.getInstance().logEvent(PLAY_STORE, PLAY_STORE_REFERRER_KEY, url);
                try {  // report attribution and keywords
                    final Uri uri = Uri.parse(url);
                    final String utmSource = uri.getQueryParameter("utm_source");
                    final String utmMedium = uri.getQueryParameter("utm_medium");
                    final String utmTerm = uri.getQueryParameter("utm_term");
                    final String keyword = uri.getQueryParameter("keyword");
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
                } catch (Exception e) {
                    Timber.e(e, "Invalid referrer.");
                }
                EventLogger.getInstance().setCampaignParams(url);
                Log.d("CampaignTracking", "=====> referrer=" + url);
            }
        }

        super.onReceive(context, intent);
    }
}
