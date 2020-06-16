insight.on('enter', function checkLogging(ev, frame) {
    if (frame.msg === 'are') {
        throw "great you are!";
    }
}, {
    roots: true,
    rootNameFilter: (n) => n === 'log'
});
insight.on('return', function checkLogging(ev, frame) {
    if (frame.msg === 'do') {
        throw "you feel?";
    }
    if (frame.msg === 'bad') {
        throw "good you are?";
    }
}, {
    roots: true,
    rootNameFilter: 'log'
});
