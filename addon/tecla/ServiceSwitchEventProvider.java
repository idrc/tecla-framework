package com.android.tecla;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import ca.idi.tecla.sdk.SwitchEvent;

public class ServiceSwitchEventProvider extends Service {

	private static final String CLASS_TAG = "SwitchEventProvider";
	private static final int REQUEST_IME_DELAY = 60000;

	private Intent mSwitchEventIntent;
	private boolean mPhoneRinging;

	private Handler handler;
	private Context context;

	@Override
	public void onCreate() {
		super.onCreate();

		init();
	}

	private void init() {
		context = this;
		handler = new Handler();
		mPhoneRinging = false;

		//Intents & Intent Filters
		registerReceiver(mReceiver, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));

		mSwitchEventIntent = new Intent(SwitchEvent.ACTION_SWITCH_EVENT_RECEIVED);

		handler.postDelayed(requestIME, REQUEST_IME_DELAY);

//		if (TeclaApp.persistence.shouldConnectToShield()) {
//			TeclaStatic.logD(CLASS_TAG, "Starting Shield Service...");
//			TeclaShieldManager.connect(this);
//		}
		TeclaStatic.logD(CLASS_TAG, "Switch Event Provider created");
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		TeclaStatic.logD(CLASS_TAG, "TeclaService onStart called");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
		handler.removeCallbacks(requestIME);
		TeclaStatic.logW(CLASS_TAG, "TeclaService onDestroy called!");
	}

	private Runnable requestIME = new Runnable()
	{
		@Override
		public void run()
		{
			handler.removeCallbacks(requestIME);
			if (TeclaStatic.isDefaultIMESupported(getApplicationContext())) {
				//TODO: Check if soft IME view is created
				if (TeclaApp.getInstance().isSupportedIMERunning()) {
					TeclaStatic.logD(CLASS_TAG, "IME is running!");
				} else {
					TeclaStatic.logD(CLASS_TAG, "IME is NOT running!");
				}
				//TODO: If soft IME not running/created, spawn activity to force it open
				//TeclaStatic.logD("TeclaIME is the default!");
			}
			handler.postDelayed(requestIME, REQUEST_IME_DELAY);
		}

	};

	/** INPUT HANDLING METHODS AND VARIABLES **/
	public void injectSwitchEvent(SwitchEvent event) {

		int switchChanges = event.getSwitchChanges();
		int switchStates = event.getSwitchStates();

		TeclaStatic.logD(CLASS_TAG, "Handling switch event.");
		if (mPhoneRinging) {
			//Screen should be on
			//Answering should also unlock
			TeclaApp.getInstance().answerCall();
			// Assume phone is not ringing any more
			mPhoneRinging = false;
		} else if (!TeclaApp.getInstance().isScreenOn()) {
			// Screen is off, so just wake it
			TeclaApp.getInstance().wakeUnlockScreen();
			TeclaStatic.logD(CLASS_TAG, "Waking and unlocking screen.");
		} else {
			// In all other instances acquire wake lock,
			// WARNING: just poking user activity timer DOES NOT WORK on gingerbread
			TeclaApp.getInstance().wakeUnlockScreen();
			TeclaStatic.logD(CLASS_TAG, "Broadcasting switch event: " +
					TeclaApp.getInstance().byte2Hex(switchChanges) + ":" +
					TeclaApp.getInstance().byte2Hex(switchStates));

			// Reset intent
			mSwitchEventIntent.removeExtra(SwitchEvent.EXTRA_SWITCH_ACTIONS);
			mSwitchEventIntent.removeExtra(SwitchEvent.EXTRA_SWITCH_CHANGES);
			mSwitchEventIntent.removeExtra(SwitchEvent.EXTRA_SWITCH_STATES);
			//Collect the mapped actions of the current switch
			String[] switchActions = TeclaApp.persistence.getSwitchMap().get(event.toString());
			mSwitchEventIntent.putExtra(SwitchEvent.EXTRA_SWITCH_ACTIONS, switchActions);
			mSwitchEventIntent.putExtra(SwitchEvent.EXTRA_SWITCH_CHANGES, switchChanges);
			mSwitchEventIntent.putExtra(SwitchEvent.EXTRA_SWITCH_STATES, switchStates);

			// Broadcast event
			sendBroadcast(mSwitchEventIntent);
		}
	}

	public void injectSwitchEvent(int switchChanges, int switchStates) {
		injectSwitchEvent(new SwitchEvent(switchChanges, switchStates));
	}

	// All intents will be processed here
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
				TeclaStatic.logD(CLASS_TAG, "Phone state changed");
				mPhoneRinging = false;
				TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
				int phoneState =  tm.getCallState();
				switch(phoneState){
					case TelephonyManager.CALL_STATE_RINGING:
						TeclaStatic.logD(CLASS_TAG, "Phone ringing");
						mPhoneRinging = true;
						break;
					case TelephonyManager.CALL_STATE_OFFHOOK:
						//if enabled in prefs, activate speaker phone  whenever the phone is off the hook
						TeclaStatic.logD(CLASS_TAG, "Phone off the hook");
						if(TeclaApp.persistence.isSpeakerphoneSelected()){
								/* Turn the speaker on after a short delay,
								 *  so that Jellybean's auto-turnoff-speaker is completed 
								 *  by PhoneUtils.java .
								 *  Increase the delay if the problem still occurs
								 */
								int delay = 200;// in ms
								new Timer().schedule(new TimerTask() {          
										@Override
										public void run() {
										TeclaStatic.logD(CLASS_TAG, "Enabling Speaker");
											TeclaApp.getInstance().useSpeakerphone();
										}
								}, delay);
              

						}



				}
			}
		}

	};

	/** BINDING METHODS AND VARIABLES **/
	// Binder given to clients
    private final IBinder mBinder = new SwitchEventProviderBinder();
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class SwitchEventProviderBinder extends Binder {
    	ServiceSwitchEventProvider getService() {
            // Return this instance of LocalService so clients can call public methods
            return ServiceSwitchEventProvider.this;
        }
    }
    	
}