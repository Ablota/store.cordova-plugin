package com.ablota.store.plugin;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DevicePlugin extends CordovaPlugin {
	private static final int DEVICE_INFO_OPENGL_REQUEST = 1;

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		this.cordova.getThreadPool().execute(() -> {
			try {
				if("info".equals(action)) {
					this.info(callbackContext);
				} else if("admin".equals(action)) {
					this.admin(callbackContext);
				}
			} catch(JSONException e) {
				callbackContext.error(e.getMessage());
			}
		});

		return true;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Bundle extras = intent.getExtras();

		if(extras != null) {
			String callbackId = extras.getString(Helpers.EXTRA_CALLBACK_ID);

			try {
				if(requestCode == DEVICE_INFO_OPENGL_REQUEST) {
					if(resultCode == Activity.RESULT_OK) {
						StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());

						JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
						data.put("sdk", Build.VERSION.SDK_INT);
						data.put("abis", new JSONArray(Build.SUPPORTED_ABIS));
						data.put("gpuRenderer", extras.getString("renderer"));
						data.put("bytesAvailable", stat.getAvailableBytes());
						data.put("bytesTotal", stat.getTotalBytes());

						this.webView.sendPluginResult(new PluginResult(PluginResult.Status.OK, data), callbackId);
					} else {
						this.webView.sendPluginResult(new PluginResult(PluginResult.Status.OK, Helpers.callbackData(Helpers.STATUS_FAILURE)), callbackId);
					}
				}
			} catch(JSONException e) {
				this.webView.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()), callbackId);
			}
		}
	}

	private void info(CallbackContext callbackContext) {
		Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), OpenGLActivity.class);
		intent.putExtra(Helpers.EXTRA_CALLBACK_ID, callbackContext.getCallbackId());

		this.cordova.startActivityForResult(this, intent, DEVICE_INFO_OPENGL_REQUEST);
	}

	private void admin(CallbackContext callbackContext) throws JSONException {
		DevicePolicyManager devicePolicyManager = (DevicePolicyManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
		ComponentName deviceAdminReceiver = new ComponentName(this.cordova.getActivity().getApplicationContext(), HandleDeviceAdminReceiver.class);

		JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
		data.put("active", devicePolicyManager.isAdminActive(deviceAdminReceiver));
		data.put("profileOwner", devicePolicyManager.isProfileOwnerApp(this.cordova.getActivity().getApplicationContext().getPackageName()));
		data.put("deviceOwner", devicePolicyManager.isDeviceOwnerApp(this.cordova.getActivity().getApplicationContext().getPackageName()));

		callbackContext.success(data);
	}
}
