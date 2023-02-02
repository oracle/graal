insight.on('enter', function(ctx, frame) {
    print("Squares: " + frame.x2 + ", " + frame.y2);
}, {
    statements: true,
    at: {
        sourcePath: ".*distance.js",
        line: 4
    }
});

insight.on('enter', function(ctx, frame) {
    print("Loop var i = " + frame.i);
}, {
    expressions: true,
    at: {
        sourcePath: ".*distance.js",
        line: 5,
        column: 21
    }
});
