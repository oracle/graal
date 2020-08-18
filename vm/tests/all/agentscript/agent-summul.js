insight.on('enter', function(ctx, frame) {
    ctx.returnNow(frame.a * frame.b);
}, {
    roots: true,
    rootNameFilter: 'plus'
});



