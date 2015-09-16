package com.peanutswifi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.List;

/**
 * Created by Jac on 2015/4/7.
 */
public class WifiConnecter{

    // Combo scans can take 5-6s to complete
    private static final int WIFI_RESCAN_INTERVAL_MS = 5 * 1000;

    private static final String TAG = WifiConnecter.class.getSimpleName();
    public static final int MAX_TRY_COUNT = 3;

    private Context mContext;
    private WifiManager mWifiManager;

    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;
    private final ScannerHandler mScanner;
    private ActionListener mListener;
    private String mSsid;
    private String mPasswd;
    private String mEncryp;

    private boolean isRegistered;
    private boolean isActiveScan;

    public WifiConnecter(Context context){
        this.mContext = context;
        mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);

        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };

        context.registerReceiver(mReceiver, mFilter);
        isRegistered = true;
        mScanner = new ScannerHandler();

    }

    public void connect(String ssid, String encryption, String password, ActionListener listener){
        this.mListener = listener;
        this.mSsid = ssid;
        this.mPasswd = password;
        this.mEncryp = encryption;

        if(listener != null){
            listener.onStarted(ssid);
        }

        mScanner.forceScan();
    }

    public void clearConnect(ActionListener listener){
        if (listener != null){
            this.mListener = listener;

            onResume();

            listener.onClearConfig();
            final List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();
            if (configurations != null) {
                for (final WifiConfiguration configTmp : configurations) {
                    mWifiManager.removeNetwork(configTmp.networkId);
                }
                mWifiManager.saveConfiguration();
            }
            listener.onShutDownWifi();
            if (mWifiManager.isWifiEnabled()){
                mWifiManager.setWifiEnabled(false);
            }

        }
    }

    public void clearConnect2(){
//   clear without toast text
        onResume();

        final List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();
        if (configurations != null) {
            for (final WifiConfiguration configTmp : configurations) {
                mWifiManager.removeNetwork(configTmp.networkId);
            }
            mWifiManager.saveConfiguration();
        }

        if (mWifiManager.isWifiEnabled()){
            mWifiManager.setWifiEnabled(false);
        }

    }



    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action) && isActiveScan){
            boolean flag = false;
            List<ScanResult> results = mWifiManager.getScanResults();
            for (ScanResult result: results){
                boolean ssidEquals = mSsid.equals(result.SSID);
                if (ssidEquals){
                    flag = true;
                    String mBssid = result.BSSID;
                    mScanner.pause();
                    if(!Wifi.connectToNewNetwork(mWifiManager, mSsid, mEncryp, mPasswd, mBssid,true)){
                        if(mListener != null){
                            mListener.onFailure("Connect to AP failed!");
                            mListener.onFinished(false);
                        }
                        onPause();
                    }
                    break;
                }
            }
            if(mListener != null && flag == false) {
                mListener.onFailure("Cannot find specified SSID, Scan later!");
            }

        }else if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo mInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (mInfo.isConnected()){
                WifiInfo mWifiInfo = mWifiManager.getConnectionInfo();
                String getSSID = mWifiInfo.getSSID();
                if (mWifiInfo != null && mInfo.isConnected() && getSSID != null) {
                    String quotedString = StringUtils.convertToQuotedString(mSsid);
                    boolean ssidEquals = quotedString.equals(getSSID);
                    if (ssidEquals) {
                        if (mListener != null) {
                            mListener.onSuccess(mWifiInfo);
                            mListener.onFinished(true);
                        }
                        onPause();
                    }
                }
            }
        }
    }

    public void onPause(){
        if(isRegistered){
            mContext.unregisterReceiver(mReceiver);
            isRegistered = false;
        }
        mScanner.pause();
    }

    public void onResume(){
        if(!isRegistered){
            mContext.registerReceiver(mReceiver, mFilter);
            isRegistered = true;
        }
//        mScanner.resume();
    }

    @SuppressLint("HandlerLeak")
    private class ScannerHandler extends Handler {
        private int mRetry = 0;

        void resume(){
            if(!hasMessages(0)){
                sendEmptyMessage(0);
            }
        }

        void forceScan(){
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause(){
            mRetry = 0;
            isActiveScan = false;
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message message){
            if(mRetry < MAX_TRY_COUNT){
                mRetry++;
                isActiveScan = true;
                if (!mWifiManager.isWifiEnabled()){
                    mWifiManager.setWifiEnabled(true);
                }

                boolean startScan = mWifiManager.startScan();
                Log.d(TAG, "StarScan:" + startScan);

                if (!startScan) {
                    if(mListener != null) {
                        mListener.onFailure("Scan failed, try later!");
                        mListener.onFinished(false);
                    }
                    onPause();
                    return;
                }
            }else{
                mRetry = 0;
                isActiveScan = false;
                if(mListener != null){
                    mListener.onFailure("Specified SSID isnot exist!");
                    mListener.onFinished(false);
                }
                onPause();
                return;
            }
            sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS);
        }
    }

    public interface ActionListener {

        /**
         * The operation started
         *
         * @param ssid
         */
        public void onStarted(String ssid);

        /**
         * The operation succeeded
         *
         * @param info
         */
        public void onSuccess(WifiInfo info);

        /**
         * The operation failed
         */
        public void onFailure(String reason);

        /**
         * The operation finished
         *
         * @param isSuccessed
         */
        public void onFinished(boolean isSuccessed);

        public void onClearConfig();

        public void onShutDownWifi();
    }
}
