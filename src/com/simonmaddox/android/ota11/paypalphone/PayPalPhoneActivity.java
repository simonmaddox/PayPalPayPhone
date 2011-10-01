package com.simonmaddox.android.ota11.paypalphone;

import java.util.HashMap;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.paypal.android.MEP.PayPal;
import com.paypal.android.MEP.PayPalPreapproval;

import android.app.Activity;
import android.content.Intent;
import android.net.ParseException;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class PayPalPhoneActivity extends Activity implements View.OnTouchListener, View.OnKeyListener {
	
	private static final int server = PayPal.ENV_SANDBOX;
	private static final String appID = "APP-80W284485P519543T";
	
	private Handler handler;
	
	public static PayPalPhoneActivity context;
	
	private String PAY_KEY = null;
	
	private Button startButton;
	private ButtonGridLayout dialpad;
	private ImageButton dialButton;
	private ImageButton deleteButton;
	
	private SipManager sipManager = null;
    private SipProfile me = null;
    
    private TextView mDialpadDigits;
    private DTMFKeyListener mDialerKeyListener;
    
    private static final HashMap<Integer, Character> mDisplayMap =
            new HashMap<Integer, Character>();
    
    static {
        // Map the buttons to the display characters
        mDisplayMap.put(R.id.one, '1');
        mDisplayMap.put(R.id.two, '2');
        mDisplayMap.put(R.id.three, '3');
        mDisplayMap.put(R.id.four, '4');
        mDisplayMap.put(R.id.five, '5');
        mDisplayMap.put(R.id.six, '6');
        mDisplayMap.put(R.id.seven, '7');
        mDisplayMap.put(R.id.eight, '8');
        mDisplayMap.put(R.id.nine, '9');
        mDisplayMap.put(R.id.zero, '0');
        mDisplayMap.put(R.id.pound, '#');
        mDisplayMap.put(R.id.star, '*');
    }
    	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        context = this;
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.main);
        
        handler = new Handler();
        
        dialpad = (ButtonGridLayout) findViewById(R.id.dialpad);
        //dialpad.setVisibility(View.GONE);
        
        mDialpadDigits = (TextView) findViewById(R.id.enteredNumber);
        
        if (mDialpadDigits != null) {
            mDialerKeyListener = new DTMFKeyListener();
            mDialpadDigits.setKeyListener(mDialerKeyListener);

            // remove the long-press context menus that support
            // the edit (copy / paste / select) functions.
            mDialpadDigits.setLongClickable(false);

            // TODO: may also want this at some point:
            // mDialpadDigits.setMovementMethod(new DTMFDisplayMovementMethod());
        }
        
        setupKeypad(dialpad);
        
        startButton = (Button) findViewById(R.id.start);
        startButton.setVisibility(View.GONE);
        startButton.setEnabled(false);
        startButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View arg0) {
				handler.post(new Runnable(){

					@Override
					public void run() {
						startButton.setEnabled(false);
						startPreApproval();
					}
					
				});
			}
        	
        });
        
        dialButton = (ImageButton) findViewById(R.id.dialButton);
        dialButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View arg0) {
				// TODO: call				
			}
        	
        });
        
        deleteButton = (ImageButton) findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View arg0) {
				String n = mDialpadDigits.getText().toString();
				if (n.length() > 0){
					mDialpadDigits.setText(n.substring(0, n.length() - 1));
				}
			}
        	
        });
        
        Thread libraryInitializationThread = new Thread() {
			public void run() {
				initLibrary();
				
				// The library is initialized so let's create our CheckoutButton and update the UI.
				if (PayPal.getInstance().isLibraryInitialized()) {
					handler.post(new Runnable(){

						@Override
						public void run() {
							startButton.setEnabled(true);
						}
						
					});
				}
			}
		};
		libraryInitializationThread.start();
        
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
    	return true;
    }
    
    
    public void startPreApproval(){
    	SMHttpClient.getPreApproval(new AsyncHttpResponseHandler(){
    		@Override
    		public void onSuccess(final String response) {
    			handler.post(new Runnable(){

    				@Override
    				public void run() {

    					PayPalPreapproval preapproval = new PayPalPreapproval();
    					preapproval.setCurrencyType("GBP");
    					preapproval.setMerchantName("PayPalPayPhone");

    					PayPal.getInstance().setPreapprovalKey(response);
    					Intent preapproveIntent = PayPal.getInstance().preapprove(preapproval, PayPalPhoneActivity.this, new ResultDelegate());
    					startActivityForResult(preapproveIntent, 1); 
    				}

    			});
    		}

    		@Override
    		public void onFailure(Throwable e) {
    			Log.e("FAIL", e.toString());
    		}
    	});
    }

    // Paypal
    
    private void initLibrary() {
		PayPal pp = PayPal.getInstance();
		if(pp == null) {
			pp = PayPal.initWithAppID(this, appID, server);
			pp.setLanguage("en_US"); // Sets the language for the library.
        	pp.setFeesPayer(PayPal.FEEPAYER_EACHRECEIVER); 
        	pp.setShippingEnabled(false);
        	pp.setDynamicAmountCalculationEnabled(false);
		}
	}
    
    public void onPaymentSucceeded(String payKey, String paymentStatus) {
		Log.e("SUCCESS","" + payKey);
		PAY_KEY = payKey;
		handler.post(new Runnable(){
			@Override
			public void run() {
				startButton.setEnabled(true);
				loginToSIP();
				startButton.setVisibility(View.GONE);
			}
		});
	}
    
    public void onPaymentFailed(String paymentStatus, String correlationID,
    		String payKey, String errorID, String errorMessage) {
    	Log.e("FAIL","" + errorMessage);
    	Log.e("PAYKEY", "" + payKey);
    	handler.post(new Runnable(){
    		@Override
    		public void run() {
    			startButton.setEnabled(true);
    		}
    	});
    }
    
    public void onPaymentCanceled(String paymentStatus) {
    	Log.e("CANCEL","" + paymentStatus);
    	handler.post(new Runnable(){
			@Override
			public void run() {
				startButton.setEnabled(true);
			}
		});
    }
    
    public void loginToSIP(){
    	if (SipManager.isVoipSupported(this) && SipManager.isApiSupported(this)){
            // SIP is supported, let's go!
            Log.e("SIP", "SUPPORTED");
            if (sipManager == null){
            	sipManager = SipManager.newInstance(this);
            }

            String username = "447801104782";
            String password = "4S5NVnBTA23M";
            String domain = "178.22.137.51";

            try {
            	SipProfile.Builder builder = new SipProfile.Builder(username, domain);
				builder.setPassword(password);
				builder.setDisplayName(username);
				builder.setProfileName(username + "@" + domain);
				builder.setProtocol("TCP");
				builder.setAutoRegistration(false);
				me = builder.build();
				
                sipManager.open(me);

                sipManager.register(me, 30, new SipRegistrationListener() {
                        public void onRegistering(String localProfileUri) {
                            Log.e("SIP","Registering with SIP Server...");
                        }

                        public void onRegistrationDone(String localProfileUri, long expiryTime) {
                            Log.e("SIP","Ready!");
                        }

                        public void onRegistrationFailed(String localProfileUri, int errorCode,
                                String errorMessage) {
                            Log.e("SIP","Registration failed. " + errorMessage + " ("+errorCode+") - " + localProfileUri);
                        }
                    });
            } catch (ParseException pe) {
                Log.e("SIP","Connection Error.");
            } catch (SipException se) {
                Log.e("SIP","Connection error.");
            } catch (java.text.ParseException e) {
				
			}

        }
    }
    
    
    
    private final void processDtmf(char c) {
    	// if it is a valid key, then update the display and send the dtmf tone.
    	if (PhoneNumberUtils.is12Key(c)) {
    		mDialpadDigits.setText(mDialpadDigits.getText().toString() + c);
    	}
    }

    private void setupKeypad(ButtonGridLayout dialerView) {
    	// for each view id listed in the displaymap
    	View button;
    	for (int viewId : mDisplayMap.keySet()) {
    		// locate the view
    		button = dialerView.findViewById(viewId);
    		// Setup the listeners for the buttons
    		button.setOnTouchListener(this);
    		button.setClickable(true);
    		button.setOnKeyListener(this);
    	}
    }
    
    public boolean onTouch(View v, MotionEvent event) {
        int viewId = v.getId();

        // if the button is recognized
        if (mDisplayMap.containsKey(viewId)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Append the character mapped to this button, to the display.
                    // start the tone
                    processDtmf(mDisplayMap.get(viewId));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // stop the tone on ANY other event, except for MOVE.
                    break;
            }
            // do not return true [handled] here, since we want the
            // press / click animation to be handled by the framework.
        }
        return false;
    }

    /**
     * Implements View.OnKeyListener for the DTMF buttons.  Enables dialing with trackball/dpad.
     */
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // if (DBG) log("onKey:  keyCode " + keyCode + ", view " + v);

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            int viewId = v.getId();
            if (mDisplayMap.containsKey(viewId)) {
                switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    if (event.getRepeatCount() == 0) {
                        processDtmf(mDisplayMap.get(viewId));
                    }
                    break;
                case KeyEvent.ACTION_UP:
                    break;
                }
                // do not return true [handled] here, since we want the
                // press / click animation to be handled by the framework.
            }
        }
        return false;
    }

    private class DTMFKeyListener extends DialerKeyListener {

        private DTMFKeyListener() {
            super();
        }

        /**
         * Overriden to return correct DTMF-dialable characters.
         */
        @Override
        protected char[] getAcceptedChars(){
            return DTMF_CHARACTERS;
        }

        /** special key listener ignores backspace. */
        @Override
        public boolean backspace(View view, Editable content, int keyCode,
                KeyEvent event) {
            return false;
        }

        /**
         * Return true if the keyCode is an accepted modifier key for the
         * dialer (ALT or SHIFT).
         */
        private boolean isAcceptableModifierKey(int keyCode) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_ALT_LEFT:
                case KeyEvent.KEYCODE_ALT_RIGHT:
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                case KeyEvent.KEYCODE_SHIFT_RIGHT:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Overriden so that with each valid button press, we start sending
         * a dtmf code and play a local dtmf tone.
         */
        @Override
        public boolean onKeyDown(View view, Editable content,
                                 int keyCode, KeyEvent event) {
            // if (DBG) log("DTMFKeyListener.onKeyDown, keyCode " + keyCode + ", view " + view);

            // find the character
            char c = (char) lookup(event, content);

            // if not a long press, and parent onKeyDown accepts the input
            if (event.getRepeatCount() == 0 && super.onKeyDown(view, content, keyCode, event)) {

                boolean keyOK = ok(getAcceptedChars(), c);

                // if the character is a valid dtmf code, start playing the tone and send the
                // code.
                if (keyOK) {
                    processDtmf(c);
                }
                return true;
            }
            return false;
        }

        /**
         * Overriden so that with each valid button up, we stop sending
         * a dtmf code and the dtmf tone.
         */
        @Override
        public boolean onKeyUp(View view, Editable content,
                                 int keyCode, KeyEvent event) {
            // if (DBG) log("DTMFKeyListener.onKeyUp, keyCode " + keyCode + ", view " + view);

            super.onKeyUp(view, content, keyCode, event);

            // find the character
            char c = (char) lookup(event, content);

            boolean keyOK = ok(getAcceptedChars(), c);

            if (keyOK) {
                return true;
            }

            return false;
        }

        /**
         * Handle individual keydown events when we DO NOT have an Editable handy.
         */
        public boolean onKeyDown(KeyEvent event) {
            char c = lookup(event);

            // if not a long press, and parent onKeyDown accepts the input
            if (event.getRepeatCount() == 0 && c != 0) {
                // if the character is a valid dtmf code, start playing the tone and send the
                // code.
                if (ok(getAcceptedChars(), c)) {
                    processDtmf(c);
                    return true;
                }
            }
            return false;
        }

        /**
         * Handle individual keyup events.
         *
         * @param event is the event we are trying to stop.  If this is null,
         * then we just force-stop the last tone without checking if the event
         * is an acceptable dialer event.
         */
        public boolean onKeyUp(KeyEvent event) {
            if (event == null) {
                //the below piece of code sends stopDTMF event unnecessarily even when a null event
                //is received, hence commenting it.
                /*if (DBG) log("Stopping the last played tone.");
                stopTone();*/
                return true;
            }

            char c = lookup(event);

            // TODO: stopTone does not take in character input, we may want to
            // consider checking for this ourselves.
            if (ok(getAcceptedChars(), c)) {
                //stopTone();
                return true;
            }

            return false;
        }

        /**
         * Find the Dialer Key mapped to this event.
         *
         * @return The char value of the input event, otherwise
         * 0 if no matching character was found.
         */
        private char lookup(KeyEvent event) {
            // This code is similar to {@link DialerKeyListener#lookup(KeyEvent, Spannable) lookup}
            int meta = event.getMetaState();
            int number = event.getNumber();

            if (!((meta & (KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON)) == 0) || (number == 0)) {
                int match = event.getMatch(getAcceptedChars(), meta);
                number = (match != 0) ? match : number;
            }

            return (char) number;
        }

        /**
         * Check to see if the keyEvent is dialable.
         */
        boolean isKeyEventAcceptable (KeyEvent event) {
            return (ok(getAcceptedChars(), lookup(event)));
        }

        /**
         * Overrides the characters used in {@link DialerKeyListener#CHARACTERS}
         * These are the valid dtmf characters.
         */
        public final char[] DTMF_CHARACTERS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*'
        };
    }
}
	