package com.simonmaddox.android.ota11.paypalphone;

import com.loopj.android.http.*;

public class SMHttpClient {
	
	private static final String PP_PREAPPROVAL = "http://geektech.co.uk/payphone/preapproval.php?maxPerPay=5&maxPayment=100&maxTotal=100&senderEmail=buyer_1317399244_per@simonmaddox.com";
	private static final String GETRATES = "http://ec2.sammachin.com/payphone/getrate?dest=";
	private static final String PAY = "http://geektech.co.uk/payphone/pay.php?senderEmail=seller_1317399345_biz@simonmaddox.com&key=";
		
	private static AsyncHttpClient client = new AsyncHttpClient();
	
	public static void getPreApproval(AsyncHttpResponseHandler responseHandler){
		SMHttpClient.get(PP_PREAPPROVAL, null, responseHandler);
	}
	
	public static void getRates(String msisdn, AsyncHttpResponseHandler responseHandler){
		SMHttpClient.get(GETRATES + msisdn, null, responseHandler);
	}
	
	public static void pay(String key, double amount, AsyncHttpResponseHandler responseHandler){
		SMHttpClient.get(PAY + key + "&amount=" + amount, null, responseHandler);
	}
	
	// Underlying methods
	
	public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
		client.get(url, params, responseHandler);
	}
}
