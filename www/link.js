var exec = require('cordova/exec');

module.exports = {
	handler: function(successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AblotaStoreLink', 'handler', []);
	},
};
