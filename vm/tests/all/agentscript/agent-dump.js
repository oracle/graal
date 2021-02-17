/* global insight, heap */

insight.on('return', (ctx, frame) => {
    if (frame.n !== 2) {
        return;
    }
    heap.record([{
        stack: [{
            at: ctx,
            frame: frame
        }]
    }], 10);
    throw 'Heap dump written!';
}, {
    roots: true,
    rootNameFilter: 'minusTwo'
});

