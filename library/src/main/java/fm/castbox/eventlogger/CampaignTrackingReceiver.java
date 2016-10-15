package fm.castbox.eventlogger;

import android.content.Context;
import android.content.Intent;

/**
 * Created by xiaocong on 16/10/15.
 */

public class CampaignTrackingReceiver extends com.google.android.gms.analytics.CampaignTrackingReceiver {

    private static final String PLAY_STORE_REFERRER_KEY = "referrer";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(PLAY_STORE_REFERRER_KEY)) {
            String url = intent.getStringExtra(PLAY_STORE_REFERRER_KEY);
            EventLogger.getInstance().logEvent("store", PLAY_STORE_REFERRER_KEY, url);
            EventLogger.getInstance().setCampaignParams(url);
        }

        super.onReceive(context, intent);
    }
}
