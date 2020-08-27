insight.on('enter', function(ctx, frame) {
    if ("a + b" === ctx.characters) {
        ctx.returnNow(frame.a * frame.b);
    }
}, {
    expressions: true,
    rootNameFilter: 'plus'
});

insight.on('return', function(ctx, frame) {
    if ("return 0;" === ctx.characters) {
        ctx.returnNow(10);
    }
    if ("return 1;" === ctx.characters) {
        ctx.returnNow(20);
    }
}, {
    statements: true,
    rootNameFilter: 'fib'
});

