/**
 * App update
 * version.js
 * [{'verCode':2,'verName':'1.2.1','apkPath':'http://****.com/your.apk'}]
 * verCode Versienummer
 * verName Versienaam
 * apkPath APK download url
 * @author 
 */
var update = {
	updateEvent: function(url,successCallback,errorCallback){
		cordova.exec(
			successCallback,
			errorCallback,
			'UpdateApp',
			'checkAndUpdate',
			[url]
		);
	}
}
module.exports = update;
