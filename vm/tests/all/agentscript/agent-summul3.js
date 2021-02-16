for (let i = 1; i <= 3; i++) {
    insight.on('enter', function(ctx, frame) {
        let r = frame.a * frame.b * i;
        print(`enter of ${ctx.name} with ${i} * ${frame.a} * ${frame.b} = ${r}`);
        ctx.returnNow(r);
    }, {
        roots: true,
        rootNameFilter: 'plus'
    });
    insight.on('return', function(ctx, frame) {
        let real = ctx.returnValue(frame);
        if (real != null) {
            throw 'unexpected value ' + real;
        }
        ctx.returnNow('ignored');
    }, {
        roots: true,
        rootNameFilter: 'plus'
    });
}

