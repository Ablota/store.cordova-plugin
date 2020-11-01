var exec = require('cordova/exec');

module.exports = {
	downloaderCodes: {
		PAUSED_WAITING_TO_RETRY: 1,
		PAUSED_WAITING_FOR_NETWORK: 2,
		PAUSED_QUEUED_FOR_WIFI: 3,
		PAUSED_UNKNOWN: 4,
		ERROR_UNKNOWN: 1000,
		ERROR_FILE_ERROR: 1001,
		ERROR_UNHANDLED_HTTP_CODE: 1002,
		ERROR_HTTP_DATA_ERROR: 1004,
		ERROR_TOO_MANY_REDIRECTS: 1005,
		ERROR_INSUFFICIENT_SPACE: 1006,
		ERROR_DEVICE_NOT_FOUND: 1007,
		ERROR_CANNOT_RESUME: 1008,
		ERROR_FILE_ALREADY_EXISTS: 1009,
	},
	readJarFailures: {
		SIGNATURES: 1,
		CERTIFICATES: 2,
	},
	download: function(url, file, headers, downloadManagerConfig, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreFile', 'download', [url, file, headers, downloadManagerConfig]);
	},
	unzip: function(zip, directory, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreFile', 'unzip', [zip, directory]);
	},
	jarInfo: function(jar, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreFile', 'jarInfo', [jar]);
	},
	hash: function(file, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreFile', 'hash', [file]);
	},
	hashName: function(name, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreFile', 'hashName', [name]);
	},
};
