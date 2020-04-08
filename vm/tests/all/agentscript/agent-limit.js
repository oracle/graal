/* global agent */

var counter = 0;

insight.on('enter', function(ctx, frame) {
    if (++counter === 1000) {
        throw `GraalVM Insight: ${ctx.name} method called ${counter} times. enough!`;
    }
}, {
    roots: true,
    rootNameFilter: (n) => n === 'nextNatural'
});

