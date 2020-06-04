/* global agent */

var counter = 0;

insight.on('enter', function(ctx, frame) {
    if (++counter === 550) {
        throw `GraalVM Insight: ${ctx.name} method called ${counter} times. enough!`;
    }
}, {
    roots: true,
    rootNameFilter: 'n...Natural'
});


insight.on('enter', function(ctx, frame) {
    print(`found new prime number ${frame.n}`);
}, {
    roots: true,
    rootNameFilter: (n) => n === 'newFilter'
});

