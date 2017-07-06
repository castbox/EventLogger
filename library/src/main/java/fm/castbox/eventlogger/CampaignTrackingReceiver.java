package fm.castbox.eventlogger;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import static fm.castbox.eventlogger.EventLogger.PLAY_STORE;
import static fm.castbox.eventlogger.EventLogger.PLAY_STORE_REFERRER_KEY;

/**
 * Created by xiaocong on 16/10/15.
 */

public class CampaignTrackingReceiver extends com.google.android.gms.analytics.CampaignTrackingReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(PLAY_STORE_REFERRER_KEY)) {
            String url = intent.getStringExtra(PLAY_STORE_REFERRER_KEY);
            if (!TextUtils.isEmpty(url)) {
                EventLogger.getInstance().logEvent(PLAY_STORE, PLAY_STORE_REFERRER_KEY, url);
                EventLogger.getInstance().setCampaignParams(url);
                Log.d("CampaignTracking", "=====> referrer=" + url);
            }
        }

        super.onReceive(context, intent);
    }
}
