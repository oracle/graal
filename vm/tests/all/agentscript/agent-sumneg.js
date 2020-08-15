insight.on('return', function(ctx, frame) {
    let positive = ctx.returnValue(frame);
    try {
        ctx.returnNow(-positive);
    } finally {
        ctx.returnNow('Never reached!');
    }
}, {
    roots: true,
    rootNameFilter: 'sumRange'
});
