package com.symbiotic.support;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;

import org.apache.http.client.methods.HttpPut;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

public final class S3Connection
{
	private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss +0000";
	private static final String TAG = "S3Connection";

	private static final String URL = "http://%s.s3.amazonaws.com/%s";
	private static final String URL_SECURE = "https://%s.s3.amazonaws.com/%s";

	private static final String ERROR_BADCONNECTION = "Could not establish connection.";
	private static final String ERROR_MISSINGPARAMS = "Missing parameters required for this operation.";
	private static final String ERROR_BADPATH = "Could not open or read file.";
	private static final String ERROR_HTTPERROR = "There was a problem with the request.";

	public interface RequestListener
	{
		void uploadedData(S3Connection connection, String key);
		void uploadedFile(S3Connection connection, String key);
		void requestFailed(S3Connection connection, String key, String errorMessage);
	}
	
	// Base class to make implementing anonymous classes easier
	public static class BasicRequestListener implements RequestListener
	{
		public void uploadedData(S3Connection connection, String key)
		{
			Log.i(TAG, String.format("S3 connection uploaded data with key %s.", key));
		}

		public void uploadedFile(S3Connection connection, String key)
		{
			Log.i(TAG, String.format("S3 connection uploaded file with key %s.", key));
		}

		public void requestFailed(S3Connection connection, String key, String errorMessage)
		{
			Log.e(TAG, String.format("S3 connection for key %s failed with error: %s", key, errorMessage));
		}
	}
	
	private RequestListener listener;
	private String accessKeyId;
	private String secretAccessKey;
	private String error;
	public String bucket;

	public S3Connection(String accessKeyId, String secretAccessKey, RequestListener requestListener)
	{
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.listener = requestListener;
	}

	public void cancel()
	{
		// TODO:
	}
	
	public RequestListener getListener()
	{
		return this.listener;
	}
	
	public String getError()
	{
		return this.error;
	}

	// http://docs.aws.amazon.com/AmazonS3/latest/dev/RESTAuthentication.html
	private String getAuthorizationHeader(String verb, String md5, String contentType, String date, String resource)
	{
		String signature;
		String canonicalizedResource;
		String stringToSign;
		SecretKey key;
		Mac mac;
		String hmac;
	
		if(md5 == null)
			md5 = "";
		if(contentType == null)
			contentType = "";
	
		// Create the string to sign
		if(resource.startsWith("/"))
			canonicalizedResource = String.format("/%s%s", this.bucket, resource);
		else
			canonicalizedResource = String.format("/%s/%s", this.bucket, resource);
		stringToSign = String.format("%s\n%s\n%s\n%s\n%s", verb, md5, contentType, date, canonicalizedResource);

		// Create the signature
		try
		{
			key = new SecretKeySpec(this.secretAccessKey.getBytes(), "HmacSHA1");
			mac = Mac.getInstance("HmacSHA1");
			mac.init(key);
			mac.update(stringToSign.getBytes());
			signature = Base64.encode(mac.doFinal());
		} catch(Exception e) { Log.e(TAG, "HMAC failed with error: " + e.getMessage()); return ""; }

		return String.format("AWS %s:%s", this.accessKeyId, signature);
	}

	private static String getDateHeader()
	{
		SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		return formatter.format(new Date());
	}

	private static String getMimeType(String path)
	{
		String[] components = path.split("\\.");
		return MimeTypeMap.getSingleton().getMimeTypeFromExtension(components[components.length - 1]);
	}
	
	private static String convertStreamToString(InputStream is)
	{
		try {
			return new Scanner(is).useDelimiter("\\A").next();
		} catch (java.util.NoSuchElementException e) {
			return "";
		}
	}

	private static void runOnUiThread(Runnable action)
	{
		Handler mainHandler = new Handler(Looper.getMainLooper());
		if(mainHandler.getLooper() == Looper.myLooper())
			action.run();
		else
			mainHandler.post(action);
	}
	
	private static byte[] convertStreamToMD5(InputStream is)
	{
		try
		{
			byte[] bytes = new byte[512];
			MessageDigest md = MessageDigest.getInstance("MD5");
			int read = 0;

			while(read >= 0)
			{
				read = is.read(bytes);
				if(read > 0)
					md.update(bytes, 0, read);
			}
			return md.digest();
		} catch(Exception e) { return null; }
	}

	public void uploadData(final byte[] data, final String contentType, final String key, final boolean securely)
	{
		if(data == null || key == null || data.length == 0 || key.length() == 0)
		{
			this.error = ERROR_MISSINGPARAMS;
			if(this.listener != null)
				this.listener.requestFailed(this, key, this.error);
			return;
		}
		
		this.cancel();
		
		Thread thread = new Thread(new Runnable() {
			public void run()
			{
				try
				{
					FileInputStream fileInputStream;
					String dataContentType, md5, authorization, date;
					HttpPut request;
					DefaultHttpClient client;
					HttpResponse response;
					HttpEntity entity;
					
					// Calculate the MD5 and other headers
					if(contentType != null && contentType.length() > 0)
						dataContentType = contentType;
					else
						dataContentType = getMimeType(key);
					md5 = Base64.encode(convertStreamToMD5(new ByteArrayInputStream(data)));
					date = getDateHeader();
					authorization = S3Connection.this.getAuthorizationHeader("PUT", md5, dataContentType, date, key);

					if(securely)					
						request = new HttpPut(String.format(URL_SECURE, S3Connection.this.bucket, key));
					else
						request = new HttpPut(String.format(URL, S3Connection.this.bucket, key));

					// Note: Content-Length is set automatically by the entity. Adding a Content-Length will cause an exception
					if(dataContentType != null && dataContentType.length() > 0)
						request.addHeader("Content-Type", dataContentType);
					request.addHeader("Content-MD5", md5);
					request.addHeader("Date", date);
					request.addHeader("Authorization", authorization);
					request.setEntity(new ByteArrayEntity(data));
					
					client = new DefaultHttpClient();
					response = client.execute(request);
					
					if(response.getStatusLine().getStatusCode() != 200)
					{
						int start, end;
						String responseString = S3Connection.this.convertStreamToString(response.getEntity().getContent());
						start = responseString.indexOf("<Message>");
						end = responseString.indexOf("</Message>");
						if(start >= 0 && end >= 0)
							S3Connection.this.error = responseString.substring(start, end - 1);
						else
							S3Connection.this.error = ERROR_HTTPERROR;
						if(S3Connection.this.listener != null)
							S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.requestFailed(S3Connection.this, key, S3Connection.this.error); } });
					}
					else
					{
						if(S3Connection.this.listener != null)
							S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.uploadedData(S3Connection.this, key); } });
					}
				}
				catch(Exception e)
				{
					S3Connection.this.error = e.getMessage();
					if(S3Connection.this.listener != null)
						S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.requestFailed(S3Connection.this, key, S3Connection.this.error); } });
				}
			}
		});
		thread.start();
	}

	public void uploadFile(final File file, final String key, final boolean securely)
	{
		if(key == null || key.length() == 0)
		{
			this.error = ERROR_MISSINGPARAMS;
			if(this.listener != null)
				this.listener.requestFailed(this, key, this.error);
			return;
		}

		if(!file.canRead() || !file.exists())
		{
			this.error = ERROR_BADPATH;
			if(this.listener != null)
				this.listener.requestFailed(this, key, this.error);
			return;
		}
		
		this.cancel();
		
		Thread thread = new Thread(new Runnable() {
			public void run()
			{
				try
				{
					FileInputStream fileInputStream;
					String contentType, md5, authorization, date;
					HttpPut request;
					DefaultHttpClient client;
					HttpResponse response;
					HttpEntity entity;
					
					// Calculate the MD5 and other headers
					contentType = getMimeType(file.getName());
					fileInputStream = new FileInputStream(file);
					md5 = Base64.encode(convertStreamToMD5(fileInputStream));
					fileInputStream.close();
					date = getDateHeader();
					authorization = S3Connection.this.getAuthorizationHeader("PUT", md5, contentType, date, key);

					if(securely)					
						request = new HttpPut(String.format(URL_SECURE, S3Connection.this.bucket, key));
					else
						request = new HttpPut(String.format(URL, S3Connection.this.bucket, key));

					// Note: Content-Length is set automatically by the entity. Adding a Content-Length will cause an exception
					if(contentType != null && contentType.length() > 0)
						request.addHeader("Content-Type", contentType);
					request.addHeader("Content-MD5", md5);
					request.addHeader("Date", date);
					request.addHeader("Authorization", authorization);
					request.setEntity(new FileEntity(file, contentType));
					
					client = new DefaultHttpClient();
					response = client.execute(request);
					
					if(response.getStatusLine().getStatusCode() != 200)
					{
						int start, end;
						String responseString = S3Connection.this.convertStreamToString(response.getEntity().getContent());
						start = responseString.indexOf("<Message>");
						end = responseString.indexOf("</Message>");
						if(start >= 0 && end >= 0)
							S3Connection.this.error = responseString.substring(start, end - 1);
						else
							S3Connection.this.error = ERROR_HTTPERROR;
						if(S3Connection.this.listener != null)
							S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.requestFailed(S3Connection.this, key, S3Connection.this.error); } });
					}
					else
					{
						if(S3Connection.this.listener != null)
							S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.uploadedFile(S3Connection.this, key); } });
					}
				}
				catch(Exception e)
				{
					S3Connection.this.error = e.getMessage();
					if(S3Connection.this.listener != null)
						S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.requestFailed(S3Connection.this, key, S3Connection.this.error); } });
				}
			}
		});
		thread.start();
	}

	public static void uploadData(byte[] data, String bucket, String key, String contentType, boolean securely, String accessKeyId, String secretAccessKey, RequestListener requestListener)
	{
		S3Connection connection = new S3Connection(accessKeyId, secretAccessKey, requestListener);
		connection.bucket = bucket;
		connection.uploadData(data, contentType, key, securely);
	}

	public static void uploadFile(File file, String bucket, String key, boolean securely, String accessKeyId, String secretAccessKey, RequestListener requestListener)
	{
		S3Connection connection = new S3Connection(accessKeyId, secretAccessKey, requestListener);
		connection.bucket = bucket;
		connection.uploadFile(file, key, securely);
	}
}
