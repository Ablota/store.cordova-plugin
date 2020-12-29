var exec = require('cordova/exec');

module.exports = {
	installerCodes: {
		STATUS_PENDING_USER_ACTION: -1,
		STATUS_SUCCESS: 0,
		STATUS_FAILURE: 1,
		STATUS_FAILURE_BLOCKED: 2,
		STATUS_FAILURE_ABORTED: 3,
		STATUS_FAILURE_INVALID: 4,
		STATUS_FAILURE_CONFLICT: 5,
		STATUS_FAILURE_STORAGE: 6,
		STATUS_FAILURE_INCOMPATIBLE: 7,
	},
	infoFailures: {
		NOT_FOUND: 1,
	},
	list: function(successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStorePackage', 'list', []);
	},
	info: function(packageName, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStorePackage', 'info', [packageName]);
	},
	install: function(apks, automated, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStorePackage', 'install', [apks, automated]);
	},
	uninstall: function(packageName, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStorePackage', 'uninstall', [packageName]);
	},
	launch: function(packageName, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStorePackage', 'launch', [packageName]);
	},
	permissionsInfo: function(permissions, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStorePackage', 'permissionsInfo', [permissions]);
	},
};
