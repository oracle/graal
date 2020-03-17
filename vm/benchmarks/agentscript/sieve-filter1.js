/* global agent */

var sum = 0;
var max = 0;

agent.on('enter', (ctx, frame) => {
    sum += frame.number;
    if (frame.number > max) {
        max = frame.number;
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
