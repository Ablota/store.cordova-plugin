package com.ablota.store.plugin;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.PowerManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class FilePlugin extends CordovaPlugin {
	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		this.cordova.getThreadPool().execute(() -> {
			try {
				if("download".equals(action)) {
					this.download(args.getString(0), args.getString(1), args.getJSONObject(2), args.getJSONObject(3), callbackContext);
				} else if("unzip".equals(action)) {
					this.unzip(args.getString(0), args.getString(1), callbackContext);
				} else if("jarInfo".equals(action)) {
					this.jarInfo(args.getString(0), callbackContext);
				} else if("hash".equals(action)) {
					this.hash(args.getString(0), callbackContext);
				} else if("hashName".equals(action)) {
					this.hashName(args.getString(0), callbackContext);
				}
			} catch(JSONException e) {
				callbackContext.error(e.getMessage());
			}
		});

		return true;
	}

	private void download(String url, String file, JSONObject headers, JSONObject downloadManagerConfig, CallbackContext callbackContext) throws JSONException {
		PowerManager powerManager = (PowerManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AblotaStorePlugin::FilePluginDownload");

		wakeLock.acquire(30 * 60 * 1000L);

		try {
			if(downloadManagerConfig.length() >= 1) {
				DownloadManager downloadManager = (DownloadManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
				DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

				if(headers.length() >= 1) {
					for(int i = 0; i < headers.names().length(); i++) {
						String key = headers.names().getString(i);
						String value = headers.getString(key);

						request.addRequestHeader(key, value);
					}
				}
				request.setTitle(downloadManagerConfig.getString("title"));
				request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
				request.setDestinationUri(Uri.parse(file));
				request.setVisibleInDownloadsUi(false);

				long requestId = downloadManager.enqueue(request);
				boolean downloading = true;
				int progress = -1;
				int status = 0;

				while(downloading) {
					DownloadManager.Query query = new DownloadManager.Query();
					query.setFilterById(requestId);

					Cursor cursor = downloadManager.query(query);
					cursor.moveToFirst();

					int statusNew = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));

					if(statusNew == DownloadManager.STATUS_SUCCESSFUL) {
						downloading = false;

						JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
						data.put("localUri", cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
						data.put("contentUri", downloadManager.getUriForDownloadedFile(requestId).toString());

						callbackContext.success(data);
					} else if(statusNew == DownloadManager.STATUS_RUNNING) {
						int bytesCurrent = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
						int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

						if(bytesCurrent > 0 && bytesTotal > 0) {
							int progressNew = (int) ((bytesCurrent * 100L) / bytesTotal);

							if(progress != progressNew) {
								progress = progressNew;

								JSONObject data = Helpers.callbackData(Helpers.STATUS_UPDATE);
								data.put("progress", progress);
								data.put("bytesCurrent", bytesCurrent);
								data.put("bytesTotal", bytesTotal);

								PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
								pluginResult.setKeepCallback(true);

								callbackContext.sendPluginResult(pluginResult);
							}
						}
					} else if(statusNew == DownloadManager.STATUS_PAUSED && status != statusNew) {
						int code = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));

						PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, Helpers.callbackData(Helpers.STATUS_UPDATE, code, null));
						pluginResult.setKeepCallback(true);

						callbackContext.sendPluginResult(pluginResult);
					} else if(statusNew == DownloadManager.STATUS_FAILED) {
						downloading = false;
						int code = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));

						callbackContext.success(Helpers.callbackData(Helpers.STATUS_FAILURE, code, null));
					}

					status = statusNew;
				}
			} else {
				URL connectionUrl = new URL(url);

				HttpURLConnection httpConnection = (HttpURLConnection) connectionUrl.openConnection();
				httpConnection.setUseCaches(false);

				if(headers.length() >= 1) {
					for(int i = 0; i < headers.names().length(); i++) {
						String key = headers.names().getString(i);
						String value = headers.getString(key);

						httpConnection.setRequestProperty(key, value);
					}
				}

				httpConnection.connect();

				if(httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
					File destination = new File(new URI(Uri.parse(file).toString()));
					int bytesCurrent = 0;
					int bytesTotal = httpConnection.getContentLength();
					int progress = -1;

					try(BufferedInputStream in = new BufferedInputStream(httpConnection.getInputStream()); FileOutputStream out = new FileOutputStream(destination)) {
						int n;
						byte[] buffer = new byte[16384];

						while((n = in.read(buffer)) >= 0) {
							bytesCurrent += n;

							out.write(buffer, 0, n);

							if(bytesCurrent > 0 && bytesTotal > 0) {
								int progressNew = (int) ((bytesCurrent * 100L) / bytesTotal);

								if(progress != progressNew) {
									progress = progressNew;

									JSONObject data = Helpers.callbackData(Helpers.STATUS_UPDATE);
									data.put("progress", progress);
									data.put("bytesCurrent", bytesCurrent);
									data.put("bytesTotal", bytesTotal);

									PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
									pluginResult.setKeepCallback(true);

									callbackContext.sendPluginResult(pluginResult);
								}
							}
						}
					}

					JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
					data.put("localUri", Uri.fromFile(destination));

					callbackContext.success(data);
				} else {
					callbackContext.success(Helpers.callbackData(Helpers.STATUS_FAILURE, httpConnection.getResponseCode(), httpConnection.getResponseMessage()));
				}
			}
		} catch(IOException | URISyntaxException e) {
			callbackContext.error(e.getMessage());
		}

		wakeLock.release();
	}

	private void unzip(String zip, String directory, CallbackContext callbackContext) throws JSONException {
		PowerManager powerManager = (PowerManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AblotaStorePlugin::FilePluginUnzip");

		wakeLock.acquire(10 * 60 * 1000L);

		try {
			File zipFile = new File(new URI(Uri.parse(zip).toString()));
			long bytesCurrent = 0;
			long bytesTotal = 0;
			Enumeration<? extends ZipEntry> zipFileEntries = new ZipFile(zipFile).entries();

			while(zipFileEntries.hasMoreElements()) {
				ZipEntry zipFileEntry = (ZipEntry) zipFileEntries.nextElement();
				bytesTotal += zipFileEntry.getSize();
			}

			try(ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
				ZipEntry zipEntry;
				int n;
				int progress = -1;
				byte[] buffer = new byte[16384];

				while((zipEntry = zipInputStream.getNextEntry()) != null) {
					File file = new File(new URI(directory + zipEntry.getName()));
					File dir = zipEntry.isDirectory() ? file : file.getParentFile();

					if(!dir.isDirectory() && !dir.mkdirs()) throw new FileNotFoundException("Failed to ensure directory: " + dir.getAbsolutePath());
					if(zipEntry.isDirectory()) continue;

					try(FileOutputStream fileOutputStream = new FileOutputStream(file)) {
						while((n = zipInputStream.read(buffer)) >= 0) {
							bytesCurrent += n;

							fileOutputStream.write(buffer, 0, n);

							if(bytesCurrent > 0 && bytesTotal > 0) {
								int progressNew = (int) ((bytesCurrent * 100L) / bytesTotal);

								if(progress != progressNew) {
									progress = progressNew;

									JSONObject data = Helpers.callbackData(Helpers.STATUS_UPDATE);
									data.put("progress", progress);
									data.put("bytesCurrent", bytesCurrent);
									data.put("bytesTotal", bytesTotal);

									PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
									pluginResult.setKeepCallback(true);

									callbackContext.sendPluginResult(pluginResult);
								}
							}
						}
					}
				}
			}

			callbackContext.success(Helpers.callbackData(Helpers.STATUS_SUCCESS));
		} catch(IOException | URISyntaxException e) {
			callbackContext.error(e.getMessage());
		}

		wakeLock.release();
	}

	private void jarInfo(String jar, CallbackContext callbackContext) throws JSONException {
		try {
			JarFile jarFile = new JarFile(new File(new URI(Uri.parse(jar).toString())), true);
			JarEntry indexEntry = (JarEntry) jarFile.getEntry("index-v1.json");

			StringBuilder indexContent = new StringBuilder();
			try(InputStream indexInputStream = jarFile.getInputStream(indexEntry); BufferedReader reader = new BufferedReader(new InputStreamReader(indexInputStream))) {
				String line;

				while((line = reader.readLine()) != null) {
					indexContent.append(line).append("\n");
				}
			}

			final CodeSigner[] codeSigners = indexEntry.getCodeSigners();

			if(codeSigners != null && codeSigners.length == 1) {
				List<? extends Certificate> certs = codeSigners[0].getSignerCertPath().getCertificates();

				if(certs.size() == 1) {
					X509Certificate certificate = (X509Certificate) certs.get(0);
					String fingerprint = Helpers.byte2Hex(Helpers.sha256(certificate.getEncoded()));

					JSONObject data = new JSONObject(indexContent.toString());
					data.put(Helpers.STATUS, Helpers.STATUS_SUCCESS);
					data.put("fingerprint", fingerprint);

					callbackContext.success(data);
				} else {
					callbackContext.success(Helpers.callbackData(Helpers.STATUS_FAILURE, 2, "No or multiple code signer certificates found in jar file."));
				}
			} else {
				callbackContext.success(Helpers.callbackData(Helpers.STATUS_FAILURE, 1, "No or multiple signatures found in index entry."));
			}
		} catch(IOException | URISyntaxException | CertificateEncodingException | NoSuchAlgorithmException e) {
			callbackContext.error(e.getMessage());
		}
	}

	private void hash(String file, CallbackContext callbackContext) throws JSONException {
		try {
			File content = new File(new URI(Uri.parse(file).toString()));

			try(InputStream in = new FileInputStream(content)) {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				byte[] buffer = new byte[16384];
				int n;

				while((n = in.read(buffer)) >= 0) {
					md.update(buffer, 0, n);
				}

				JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
				data.put("hash", Helpers.byte2Hex(md.digest()));

				callbackContext.success(data);
			}
		} catch(URISyntaxException | IOException | NoSuchAlgorithmException e) {
			callbackContext.error(e.getMessage());
		}
	}

	private void hashName(String name, CallbackContext callbackContext) throws JSONException {
		try {
			JSONObject data = Helpers.callbackData(Helpers.STATUS_SUCCESS);
			data.put("hash", Helpers.byte2Hex(Helpers.sha256(name.getBytes())));

			callbackContext.success(data);
		} catch(NoSuchAlgorithmException e) {
			callbackContext.error(e.getMessage());
		}
	}
}
