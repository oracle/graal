/* global agent */

var counter = 0;

agent.on('enter', function(ctx, frame) {
    counter++;
}, {
    roots: true,
    rootNameFilter: (n) => n === 'nextNatural'
});

agent.on('close', function() {
   print(`T-Trace: nextNatural called ${counter} times`);
});
