package fm.castbox.eventlogger;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import timber.log.Timber;

import static fm.castbox.eventlogger.EventLogger.PLAY_STORE;
import static fm.castbox.eventlogger.EventLogger.PLAY_STORE_REFERRER_KEY;

/**
 * 监听 Google play 下载安装来源的广播
 */
public class CampaignTrackingReceiver extends BroadcastReceiver {

    private InstallReferrerClient mReferrerClient;

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
//        Timber.d("onReceive intent bundle: " + bundle2string(intent.getExtras()));

        // 支持新版本渠道来源数据
        try {
            mReferrerClient = InstallReferrerClient.newBuilder(context).build();
            mReferrerClient.startConnection(new InstallReferrerStateListener() {
                @Override
                public void onInstallReferrerSetupFinished(int responseCode) {
                    switch (responseCode) {
                        case InstallReferrerClient.InstallReferrerResponse.OK:
                            // Connection established
                            Timber.d("Connection established.");

                            // 上报数据
                            try {
                                ReferrerDetails response = mReferrerClient.getInstallReferrer();
                                String url = response.getInstallReferrer();

                                if (!TextUtils.isEmpty(url)) {
                                    EventLogger.getInstance().logEvent(PLAY_STORE, PLAY_STORE_REFERRER_KEY, url);
                                    EventLogger.getInstance().setCampaignParams(url);
                                    Timber.d("referrer = " + url);
                                }

                                mReferrerClient.endConnection();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }

                            break;
                        case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                            // API not available on the current Play Store app
                            Timber.d("API not available on the current Play Store app.");
                            break;
                        case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                            // Connection could not be established
                            Timber.d("Connection could not be established.");
                            break;
                    }
                }

                @Override
                public void onInstallReferrerServiceDisconnected() {
                    // Try to restart the connection on the next request to
                    // Google Play by calling the startConnection() method.
                    Timber.d("Connection could not be established.");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String bundle2string(Bundle bundle) {
        if (bundle == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Bundle{");

        for (String key : bundle.keySet()) {
            sb.append(" ").append(key).append(" => ").append(bundle.get(key)).append(";");
        }
        sb.append(" }Bundle");
        return sb.toString();
    }

}
