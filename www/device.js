var exec = require('cordova/exec');

module.exports = {
	info: function(successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreDevice', 'info', []);
	},
	admin: function(successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreDevice', 'admin', []);
	},
};
