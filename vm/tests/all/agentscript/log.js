function log(msg) {
    if (msg === 'bad') {
        throw 'bad is not allowed';
    }
    console.warn(msg);
}

function howAreYou() {
    log('Hello T-Trace!');
    log('How');
    log('are');
    log('You?');
}

function howDoYouDo() {
    log('Hello T-Trace!');
    log('How');
    log('do');
    log('you');
    log('do?');
}

function areYouBad() {
    log('Hello T-Trace!');
    log('How');
    log('bad');
    log('are');
    log('you?');
}
