package com.ablota.store.plugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Helpers {
	public static final String STATUS = "status";
	public static final String STATUS_SUCCESS = "success";
	public static final String STATUS_UPDATE = "update";
	public static final String STATUS_FAILURE = "failure";
	public static final String CODE = "code";
	public static final String MESSAGE = "message";
	public static final String EXTRA_CALLBACK_ID = "com.ablota.store.plugin.EXTRA_CALLBACK_ID";

	public static String byte2Hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();

		for(byte b : bytes) {
			sb.append(String.format("%02x", b));
		}

		return sb.toString();
	}

	public static byte[] sha256(byte[] data) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(data);

		return md.digest();
	}

	public static JSONObject callbackData(String status, Integer code, String message) throws JSONException {
		JSONObject data = new JSONObject();
		data.put(STATUS, status);
		if(code != null) data.put(CODE, code);
		if(message != null) data.put(MESSAGE, message);

		return data;
	}

	public static JSONObject callbackData(String status) throws JSONException {
		return callbackData(status, null, null);
	}
}
