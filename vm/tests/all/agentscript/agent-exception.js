agent.on('enter', function checkLogging(ev, frame) {
    if (frame.msg === 'are') {
        throw "great you are!";
    }
}, {
    roots: true,
    rootNameFilter: (n) => n === 'log'
});
