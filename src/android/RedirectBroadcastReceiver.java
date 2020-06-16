package com.ablota.store.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RedirectBroadcastReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent local = new Intent();
		local.setAction(intent.getAction());
		local.replaceExtras(intent);

		context.sendBroadcast(local);
	}
}
