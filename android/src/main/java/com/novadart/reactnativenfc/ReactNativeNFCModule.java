package com.novadart.reactnativenfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.novadart.reactnativenfc.parser.NdefParser;
import com.novadart.reactnativenfc.parser.TagParser;

public class ReactNativeNFCModule extends ReactContextBaseJavaModule implements ActivityEventListener,LifecycleEventListener {
    private ReactApplicationContext reactContext;
    private static final String EVENT_NFC_DISCOVERED = "__NFC_DISCOVERED";

    // caches the last message received, to pass it to the listeners when it reconnects
    private WritableMap startupNfcData;
    private boolean startupNfcDataRetrieved = false;

    private boolean startupIntentProcessed = false;

    private static final String TAG = ReactApplicationContext.class.getName();

    private NfcAdapter nfcAdapter;
    
    public ReactNativeNFCModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
        return "ReactNativeNFC";
    }


    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {}

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent,false);
    }

    private void handleIntent(Intent intent, boolean startupIntent) {
        if (intent != null && intent.getAction() != null) {

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            switch (intent.getAction()){

                case NfcAdapter.ACTION_NDEF_DISCOVERED:
                    Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

                    if (rawMessages != null) {
                        NdefMessage[] messages = new NdefMessage[rawMessages.length];
                        for (int i = 0; i < rawMessages.length; i++) {
                            messages[i] = (NdefMessage) rawMessages[i];
                        }
                        processNdefMessages(messages,startupIntent, tag);
                    }
                    break;

                // ACTION_TAG_DISCOVERED is an unlikely case, according to https://developer.android.com/guide/topics/connectivity/nfc/nfc.html
                case NfcAdapter.ACTION_TAG_DISCOVERED:
                case NfcAdapter.ACTION_TECH_DISCOVERED:
                    processTag(tag,startupIntent);
                    break;

            }
        }
    }

    /**
     * This method is used to retrieve the NFC data was acquired before the React Native App was loaded.
     * It should be called only once, when the first listener is attached.
     * Subsequent calls will return null;
     *
     * @param callback callback passed by javascript to retrieve the nfc data
     */
    @ReactMethod
    public void getStartUpNfcData(Callback callback){
        if(!startupNfcDataRetrieved){
            callback.invoke(DataUtils.cloneWritableMap(startupNfcData));
            startupNfcData = null;
            startupNfcDataRetrieved = true;
        } else {
            callback.invoke();
        }
    }


    private void sendEvent(@Nullable WritableMap payload) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(EVENT_NFC_DISCOVERED, payload); }


    private void processNdefMessages(NdefMessage[] messages, boolean startupIntent, Tag tag){
        NdefProcessingTask task = new NdefProcessingTask(startupIntent);
        Object[] params = {
                messages,
                tag
        };
        task.execute(params);
    }

    private void processTag(Tag tag, boolean startupIntent){
        TagProcessingTask task = new TagProcessingTask(startupIntent);
        task.execute(tag);
    }

//    @Override
//    public void onHostResume() {
//        if(!startupIntentProcessed){
//            if(getReactApplicationContext().getCurrentActivity() != null){ // it shouldn't be null but you never know
//                // necessary because NFC might cause the activity to start and we need to catch that data too
//                handleIntent(getReactApplicationContext().getCurrentActivity().getIntent(),true);
//            }
//            startupIntentProcessed = true;
//        }
//    }

    @Override
    public void onHostResume() {
        if(!startupIntentProcessed){
            if(getReactApplicationContext().getCurrentActivity() != null){ // it shouldn't be null but you never know
                // necessary because NFC might cause the activity to start and we need to catch that data too
                handleIntent(getReactApplicationContext().getCurrentActivity().getIntent(),true);
            }
            startupIntentProcessed = true;
        }
    }

    @Override
    public void onHostPause() {}

    @Override
    public void onHostDestroy() {}

    private static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);
        adapter.enableForegroundDispatch(activity, pendingIntent, null, null);
    }

    private static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    private class NdefProcessingTask extends AsyncTask<Object,Void,WritableMap> {

        private final boolean startupIntent;

        NdefProcessingTask(boolean startupIntent) {
            this.startupIntent = startupIntent;
        }

        @Override
        protected WritableMap doInBackground(Object ... params) {
            NdefMessage[] messages = (NdefMessage[]) params[0];
            Tag tag = (Tag) params[1];
            WritableMap tagResult = TagParser.parse(tag);
            return NdefParser.parse(messages, tagResult);
        }

        @Override
        protected void onPostExecute(WritableMap ndefData) {
            if(startupIntent) {
                startupNfcData = ndefData;
            }
            sendEvent(ndefData);
        }
    }


    private class TagProcessingTask extends AsyncTask<Tag,Void,WritableMap> {

        private final boolean startupIntent;

        TagProcessingTask(boolean startupIntent) {
            this.startupIntent = startupIntent;
        }

        @Override
        protected WritableMap doInBackground(Tag... params) {
            Tag tag = params[0];
            return TagParser.parse(tag);
        }

        @Override
        protected void onPostExecute(WritableMap tagData) {
            if(startupIntent) {
                startupNfcData = tagData;
            }
            sendEvent(tagData);
        }
    }


}
