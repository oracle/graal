insight.on('return', function(ctx, frame) {
    let positive = ctx.returnValue(frame);
    ctx.returnNow(-positive);
}, {
    roots: true,
    rootNameFilter: 'sumRange'
});
