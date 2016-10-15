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
        EventLogger.getInstance().logEvent("store", PLAY_STORE_REFERRER_KEY, intent.getStringExtra(PLAY_STORE_REFERRER_KEY));

        super.onReceive(context, intent);
    }
}
