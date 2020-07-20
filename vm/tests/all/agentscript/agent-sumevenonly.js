insight.on('enter', function zeroNonEvenNumbers(ctx, frame) {
    if (frame.b % 2 === 1) {
        frame.b = 0;
    }
}, {
    roots: true,
    rootNameFilter: 'plus'
});
