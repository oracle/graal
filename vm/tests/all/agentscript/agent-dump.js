/* global insight, heap */

insight.on('return', (ctx, frame) => {
    if (frame.n !== 2) {
        return;
    }
    heap.dump({
        format: "1.0",
        depth: 10,
        events: [
          {
            stack: [{
                at: ctx,
                frame: frame
            }]
          }
        ]
    });
    throw 'Heap dump written!';
}, {
    roots: true,
    rootNameFilter: 'minusTwo'
});

