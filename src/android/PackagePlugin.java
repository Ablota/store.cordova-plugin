package com.ablota.store.plugin;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class PackagePlugin extends CordovaPlugin {
	private static final String PACKAGE_INSTALLED_ACTION = "com.ablota.store.plugin.PACKAGE_INSTALLED";
	private static final String PACKAGE_UNINSTALLED_ACTION = "com.ablota.store.plugin.PACKAGE_UNINSTALLED";

	private CallbackContext callbackContext = null;

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				Bundle extras = intent.getExtras();

				if(extras != null) {
					int code = extras.getInt(PackageInstaller.EXTRA_STATUS);
					String message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);
					PluginResult pluginResult;

					if(code == PackageInstaller.STATUS_PENDING_USER_ACTION) {
						pluginResult = new PluginResult(PluginResult.Status.OK, Helpers.callbackData(Helpers.STATUS_UPDATE, code, message));
						pluginResult.setKeepCallback(true);

						Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
						cordova.getActivity().startActivity(confirmIntent);
					} else {
						pluginResult = new PluginResult(PluginResult.Status.OK, Helpers.callbackData(Helpers.STATUS_SUCCESS, code, message));
					}

					webView.sendPluginResult(pluginResult, callbackContext.getCallbackId());
				}
			} catch(JSONException e) {
				callbackContext.error(e.getMessage());
			}
		}
	};

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(PACKAGE_INSTALLED_ACTION);
		intentFilter.addAction(PACKAGE_UNINSTALLED_ACTION);

		this.cordova.getActivity().registerReceiver(this.broadcastReceiver, intentFilter);
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		this.cordova.getThreadPool().execute(() -> {
			try {
				if("list".equals(action)) {
					this.list(callbackContext);
				} else if("info".equals(action)) {
					this.info(args.getString(0), callbackContext);
				} else if("install".equals(action)) {
					this.install(args.getJSONArray(0), args.getBoolean(1), callbackContext);
				} else if("uninstall".equals(action)) {
					this.uninstall(args.getString(0), callbackContext);
				} else if("launch".equals(action)) {
					this.launch(args.getString(0), callbackContext);
				}
			} catch(JSONException e) {
				callbackContext.error(e.getMessage());
			}
		});

		return true;
	}

	@Override
	public void onDestroy() {
		this.cordova.getActivity().unregisterReceiver(this.broadcastReceiver);
	}

	private void list(CallbackContext callbackContext) throws JSONException {
		JSONArray packages = new JSONArray();
		List<PackageInfo> packageInfos;

		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
			packageInfos = this.cordova.getActivity().getApplicationContext().getPackageManager().getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES);
		} else {
			packageInfos = this.cordova.getActivity().getApplicationContext().getPackageManager().getInstalledPackages(PackageManager.GET_SIGNATURES);
		}

		for(PackageInfo packageInfo : packageInfos) {
			try {
				packages.put(getPackageDetails(packageInfo));
			} catch(JSONException | NoSuchAlgorithmException e) {
				callbackContext.error(e.getMessage());
			}
		}

		JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
		data.put("packages", packages);

		callbackContext.success(packages);
	}

	@SuppressLint("PackageManagerGetSignatures")
	private void info(String packageName, CallbackContext callbackContext) throws JSONException {
		try {
			PackageInfo packageInfo;

			if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
				packageInfo = this.cordova.getActivity().getApplicationContext().getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
			} else {
				packageInfo = this.cordova.getActivity().getApplicationContext().getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
			}

			try {
				JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
				data.put("package", getPackageDetails(packageInfo));

				callbackContext.success(data);
			} catch(NoSuchAlgorithmException e) {
				callbackContext.error(e.getMessage());
			}
		} catch(PackageManager.NameNotFoundException e) {
			callbackContext.success(Helpers.callbackData(Helpers.STATUS_FAILURE, 1, e.getMessage()));
		}
	}

	private void install(JSONArray apks, boolean automated, CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;

		PackageInstaller.Session session = null;

		try {
			PackageInstaller packageInstaller = this.cordova.getActivity().getApplicationContext().getPackageManager().getPackageInstaller();
			PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				if(automated) {
					sessionParams.setInstallReason(PackageManager.INSTALL_REASON_POLICY);
				} else {
					sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER);
				}
			}

			int sessionId = packageInstaller.createSession(sessionParams);
			session = packageInstaller.openSession(sessionId);

			for(int i = 0; i < apks.length(); i++) {
				final JSONObject apk = apks.getJSONObject(i);

				addApkToInstallSession(apk.getString("file"), apk.getString("hash"), session);
			}

			Context context = this.cordova.getActivity().getApplicationContext();
			Intent intent = new Intent(context, RedirectBroadcastReceiver.class);
			intent.setAction(PACKAGE_INSTALLED_ACTION);

			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

			session.commit(pendingIntent.getIntentSender());
		} catch(IOException | URISyntaxException | NoSuchAlgorithmException e) {
			throw new RuntimeException("Exception while installing package.", e);
		} catch(RuntimeException e) {
			if(session != null) {
				session.abandon();
			}

			callbackContext.error(e.getMessage());
		}
	}

	private void uninstall(String packageName, CallbackContext callbackContext) {
		this.callbackContext = callbackContext;

		PackageInstaller packageInstaller = this.cordova.getActivity().getApplicationContext().getPackageManager().getPackageInstaller();

		Context context = this.cordova.getActivity().getApplicationContext();
		Intent intent = new Intent(context, RedirectBroadcastReceiver.class);
		intent.setAction(PACKAGE_UNINSTALLED_ACTION);

		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

		packageInstaller.uninstall(packageName, pendingIntent.getIntentSender());
	}

	private void launch(String packageName, CallbackContext callbackContext) throws JSONException {
		Intent intent = this.cordova.getActivity().getApplicationContext().getPackageManager().getLaunchIntentForPackage(packageName);

		if(intent != null) {
			this.cordova.getActivity().startActivity(intent);
		}

		callbackContext.success(Helpers.callbackData(Helpers.STATUS_SUCCESS));
	}

	private JSONObject getPackageDetails(PackageInfo packageInfo) throws JSONException, NoSuchAlgorithmException {
		JSONObject packageDetails = new JSONObject();

		packageDetails.put("packageName", packageInfo.packageName);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			packageDetails.put("versionCode", packageInfo.getLongVersionCode());
		} else {
			packageDetails.put("versionCode", packageInfo.versionCode);
		}
		packageDetails.put("versionName", packageInfo.versionName);
		packageDetails.put("installerPackageName", this.cordova.getActivity().getApplicationContext().getPackageManager().getInstallerPackageName(packageInfo.packageName));
		packageDetails.put("applicationSystem", (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1);

		JSONArray packageSignatures = new JSONArray();

		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
			for(Signature signature : packageInfo.signingInfo.getApkContentsSigners()) {
				packageSignatures.put(Helpers.byte2Hex(Helpers.sha256(signature.toByteArray())));
			}
		} else {
			for(Signature signature : packageInfo.signatures) {
				packageSignatures.put(Helpers.byte2Hex(Helpers.sha256(signature.toByteArray())));
			}
		}
		packageDetails.put("signatures", packageSignatures);

		return packageDetails;
	}

	private void addApkToInstallSession(String file, String hash, PackageInstaller.Session session) throws IOException, URISyntaxException, NoSuchAlgorithmException, RuntimeException {
		File apk = new File(new URI(Uri.parse(file).toString()));

		try(OutputStream out = session.openWrite(apk.getName(), 0, apk.length()); InputStream in = new FileInputStream(apk)) {
			byte[] buffer = new byte[16384];
			int n;

			while((n = in.read(buffer)) >= 0) {
				out.write(buffer, 0, n);
			}

			session.fsync(out);
		}

		try(InputStream in = session.openRead(apk.getName()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[16384];
			int n;

			while((n = in.read(buffer)) >= 0) {
				out.write(buffer, 0, n);
			}

			if(!hash.toLowerCase().equals(Helpers.byte2Hex(Helpers.sha256(out.toByteArray())).toLowerCase())) {
				throw new RuntimeException("Exception while validating package.");
			}
		}
	}
}
