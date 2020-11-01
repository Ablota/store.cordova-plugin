package com.ablota.store.plugin;

import android.content.Intent;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class LinkPlugin extends CordovaPlugin {
	private final ArrayList<CallbackContext> handlers = new ArrayList<>();
	private JSONObject event;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		this.onNewIntent(cordova.getActivity().getIntent());
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		this.cordova.getThreadPool().execute(() -> {
			try {
				if(action.equals("handler")) {
					this.handler(callbackContext);
				}
			} catch(JSONException e) {
				callbackContext.error(e.getMessage());
			}
		});

		return true;
	}

	@Override
	public void onNewIntent(Intent intent) {
		String action = intent.getAction();
		Uri url = intent.getData();

		if(!Intent.ACTION_VIEW.equals(action) || url == null) {
			return;
		}

		try {
			this.event = new JSONObject();
			this.event.put("url", url.toString());
			this.event.put("scheme", url.getScheme());
			this.event.put("host", url.getHost());
			this.event.put("path", url.getPath());
			this.event.put("query", url.getQuery());
			this.event.put("fragment", url.getFragment());

			this.informHandlers();
		} catch(JSONException e) {
			for(CallbackContext callbackContext : this.handlers) {
				callbackContext.error(e.getMessage());
			}
		}
	}

	private void handler(CallbackContext callbackContext) throws JSONException {
		this.handlers.add(callbackContext);

		this.informHandlers();
	}

	private void informHandlers() throws JSONException {
		if(this.handlers.size() == 0 || this.event == null) {
			return;
		}

		for(CallbackContext callbackContext : this.handlers) {
			JSONObject data = Helpers.callbackData(Helpers.STATUS_UPDATE);
			data.put("event", this.event);

			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
			pluginResult.setKeepCallback(true);

			callbackContext.sendPluginResult(pluginResult);
		}

		this.event = null;
	}
}
