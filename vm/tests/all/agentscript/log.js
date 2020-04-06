function log(msg) {
    if (msg === 'bad') {
        throw 'bad is not allowed';
    }
    console.warn(msg);
}

function howAreYou() {
    log('Hello GraalVM Insight!');
    log('How');
    log('are');
    log('You?');
}

function howDoYouDo() {
    log('Hello GraalVM Insight!');
    log('How');
    log('do');
    log('you');
    log('do?');
}

function areYouBad() {
    log('Hello GraalVM Insight!');
    log('How');
    log('bad');
    log('are');
    log('you?');
}
