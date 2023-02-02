insight.on('return', function(ctx, frame) {
    let positive = ctx.returnValue(frame);
    try {
        ctx.returnNow(-positive);
    } finally {
        print(`Original value was ${positive}, but returning ${-positive}`);
    }
}, {
    roots: true,
    rootNameFilter: 'sumRange'
});
