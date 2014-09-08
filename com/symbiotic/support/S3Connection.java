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
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TimeZone;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * A simple class to upload files to S3.
 * Optionally, gzip the data or files first using java.util.zip.GZIPOutputStream and then setting the DETECT_GZIP option.
 */
public final class S3Connection
{
	public static final int DETECT_GZIP = (1<<0);
	public static final int NO_CACHE = (1<<1);
	public static final int PERMANENT_CACHE = (1<<2);
	public static final int REDUCED_REDUNDANCY = (1<<3);
	public static final int HTTPS = (1<<4);

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
	
	private HttpRequestBase request;
	private RequestListener listener;
	private String accessKeyId;
	private String secretAccessKey;
	private String error;
	private HashMap<String, String> extraHeaders = HashMap<String, String>();
	public String bucket;

	public S3Connection(String accessKeyId, String secretAccessKey, RequestListener requestListener)
	{
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.listener = requestListener;
	}

	public void cancel()
	{
		if(this.request != null)
			this.request.abort();
	}

	public RequestListener getListener()
	{
		return this.listener;
	}

	public String getError()
	{
		return this.error;
	}

	public void setExtraHeader(String header, String value)
	{
		this.extraHeaders.put(header, value);
	}

	public void clearExtraHeaders()
	{
		this.extraHeaders.clear();
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

	public void uploadData(final byte[] data, final String contentType, final String key, final int options)
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

					if((options & HTTPS) != 0)
						request = new HttpPut(String.format(URL_SECURE, S3Connection.this.bucket, key));
					else
						request = new HttpPut(String.format(URL, S3Connection.this.bucket, key));
					S3Connection.this.request = request;

					// Check for the gzip magic number and add the Content-Encoding header
					if((options & DETECT_GZIP) != 0 && data[0] == 0x1f && data[1] == 0x8b)
						request.setHeader("Content-Encoding", "gzip");
					if((options & NO_CACHE) != 0)
						request.setHeader("Cache-Control", "no-cache");
					if((options & PERMANENT_CACHE) != 0)
						request.setHeader("Cache-Control", "max-age=315360000");
					if((options & REDUCED_REDUNDANCY) != 0)
						request.setHeader("x-amz-storage-class", "REDUCED_REDUNDANCY");
					// Note: Content-Length is set automatically by the entity. Adding a Content-Length will cause an exception
					if(dataContentType != null && dataContentType.length() > 0)
						request.setHeader("Content-Type", dataContentType);
					request.setHeader("Content-MD5", md5);
					request.setHeader("Date", date);
					request.setHeader("Authorization", authorization);
					for(Entry<String, String> extraHeader : S3Connection.this.extraHeaders.entrySet())
						request.addHeader(extraHeader.getKey(), extraHeader.getValue());
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
					if(!request.isAborted())
					{
						S3Connection.this.error = e.getMessage();
						if(S3Connection.this.listener != null)
							S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.requestFailed(S3Connection.this, key, S3Connection.this.error); } });
					}
				}
			}
		});
		thread.start();
	}

	public void uploadFile(final File file, final String key, final int options)
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

					if((options & HTTPS) != 0)
						request = new HttpPut(String.format(URL_SECURE, S3Connection.this.bucket, key));
					else
						request = new HttpPut(String.format(URL, S3Connection.this.bucket, key));
					S3Connection.this.request = request;

					if((options & DETECT_GZIP) != 0)
					{
						fileInputStream = new FileInputStream(file);
						if(fileInputStream.read() == 0x1f && fileInputStream.read() == 0x8b)
							request.setHeader("Content-Encoding", "gzip");
						fileInputStream.close();
					}
					if((options & NO_CACHE) != 0)
						request.setHeader("Cache-Control", "no-cache");
					if((options & PERMANENT_CACHE) != 0)
						request.setHeader("Cache-Control", "max-age=315360000");
					if((options & REDUCED_REDUNDANCY) != 0)
						request.setHeader("x-amz-storage-class", "REDUCED_REDUNDANCY");
					// Note: Content-Length is set automatically by the entity. Adding a Content-Length will cause an exception
					if(contentType != null && contentType.length() > 0)
						request.setHeader("Content-Type", contentType);
					request.setHeader("Content-MD5", md5);
					request.setHeader("Date", date);
					request.setHeader("Authorization", authorization);
					for(Entry<String, String> extraHeader : S3Connection.this.extraHeaders.entrySet())
						request.addHeader(extraHeader.getKey(), extraHeader.getValue());
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
					if(!request.isAborted())
					{
						S3Connection.this.error = e.getMessage();
						if(S3Connection.this.listener != null)
							S3Connection.runOnUiThread(new Runnable() { public void run() { S3Connection.this.listener.requestFailed(S3Connection.this, key, S3Connection.this.error); } });
					}
				}
			}
		});
		thread.start();
	}

	public static void uploadData(byte[] data, String bucket, String key, String contentType, int options, String accessKeyId, String secretAccessKey, RequestListener requestListener)
	{
		S3Connection connection = new S3Connection(accessKeyId, secretAccessKey, requestListener);
		connection.bucket = bucket;
		connection.uploadData(data, contentType, key, options);
	}

	public static void uploadFile(File file, String bucket, String key, int options, String accessKeyId, String secretAccessKey, RequestListener requestListener)
	{
		S3Connection connection = new S3Connection(accessKeyId, secretAccessKey, requestListener);
		connection.bucket = bucket;
		connection.uploadFile(file, key, options);
	}
}
