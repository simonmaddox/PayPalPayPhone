package com.simonmaddox.android.ota11.paypalphone;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.paypal.android.MEP.PayPal;
import com.paypal.android.MEP.PayPalPreapproval;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;

public class PayPalPhoneActivity extends Activity {
	
	private static final int server = PayPal.ENV_SANDBOX;
	private static final String appID = "APP-80W284485P519543T";
	
	private Handler handler;
	
	public static PayPalPhoneActivity context;
	
	private String PAY_KEY = null;
	
	Button startButton;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        context = this;
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.main);
        
        handler = new Handler();
        
        startButton = (Button) findViewById(R.id.start);
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
}