package fm.castbox.eventlogger;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import timber.log.Timber;

/**
 * Created by xiaocong on 16/10/15.
 */

public class CampaignTrackingReceiver extends com.google.android.gms.analytics.CampaignTrackingReceiver {

    private static final String PLAY_STORE_REFERRER_KEY = "referrer";
    private static final String PLAY_STORE = "store";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(PLAY_STORE_REFERRER_KEY)) {
            String url = intent.getStringExtra(PLAY_STORE_REFERRER_KEY);
            if (!TextUtils.isEmpty(url)) {
                EventLogger.getInstance().logEvent(PLAY_STORE, PLAY_STORE_REFERRER_KEY, url);
                try {  // report ADS keyword of Google adWords
                    String[] kvs = url.split("&");
                    for (String kv: kvs) {
                        String[] pair = kv.split("=");
                        if (pair.length == 2 && TextUtils.equals(pair[0], "keyword")) {
                            EventLogger.getInstance().logEvent(PLAY_STORE, "ad_keyword", pair[1]);
                            break;
                        }
                    }
                } catch (Exception e) {
                    Timber.e(e, "Invalid referrer.");
                }
                EventLogger.getInstance().setCampaignParams(url);

                Log.e("CampaignTracking", "=====> referrer=" + url);
            }
        }

        super.onReceive(context, intent);
    }
}
