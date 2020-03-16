/* global agent */

var sum = 0;
var max = 0;

agent.on('enter', (ctx, frame) => {
    let n = frame.number;
    sum += n;
    if (n > max) {
        max = n;
    }
}, {
  roots: true,
  rootNameFilter: (name) => name === 'Filter'
});

agent.on('return', (ctx, frame) => {
    log(`Hundred thousand prime numbers from 2 to ${max} is sum ${sum}`);
    sum = 0;
    max = 0;
}, {
    roots: true,
    rootNameFilter: (name) => name === 'measure'
});
