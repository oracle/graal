/* global agent */

var counter = 0;

agent.on('enter', function(ctx, frame) {
    if (++counter === 1000) {
        throw `T-Trace: ${ctx.name} method called ${counter} times. enough!`;
    }
}, {
    roots: true,
    rootNameFilter: (n) => n === 'nextNatural'
});

