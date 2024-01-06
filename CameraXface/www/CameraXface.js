var exec = require('cordova/exec');


var CameraXface ={
    startCameraX : function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'CameraXface', 'startCamera', []);
    },
    readImageFrame : function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'CameraXface', 'getFrameData', []);
    }
}

module.exports = CameraXface;