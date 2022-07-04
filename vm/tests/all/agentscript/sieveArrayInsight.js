/* global insight */
insight.on("return", (ctx, frame) => {
   if (frame.filter.length >= 50000) {
        heap.dump({
            format: '1.0', depth: 50, events: [
                {
                    stack: [
                        {
                            at: ctx, 
                            frame: frame
                        }
                    ]
                }
            ]});
        throw `50000th prime number is ${frame.n}!`;
    } 
}, {
    roots: true,
    rootNameFilter: 'acceptAndAdd'
});
