var exec = require('cordova/exec');

var TaskLockingPinning={
    coolMethod : function (arg0, success, error) {
        exec(success, error, 'TaskLockingPinning', 'coolMethod', [arg0]);
    }
}

module.exports = TaskLockingPinning;