/* global agent */

let initializeAgent = function (tracer) {
    var counter = 0;

    agent.on('enter', function(ctx, frame) {
        const span = tracer.startSpan(frame.req.url);
        frame.res.id = ++counter;
        frame.res.span = span;
        console.log(`agent: handling #${frame.res.id} request for ${frame.req.url}`);
    }, {
        roots: true,
        rootNameFilter: name => name === 'handler'
    });

    agent.on('return', function(ctx, frame) {
        var res = frame['this'];
        if (res.span) {
            res.span.finish();
            console.log(`agent: finished #${res.id} request`);
        } else {
            // OK, caused for example by Tracer itself connecting to Jaeger server
            // console.warn(new Error("end of request with no active span").stack);
        }
    }, {
        roots: true,
        rootNameFilter: name => name === 'end'
    });
    console.log('agent: ready');
};

// register on a call to function tracerIsReady(tracer)
// that has to be defined by the application and called
// to give us a tracer to use in the agent
agent.on('enter', (ctx, frame) => {
    initializeAgent(frame.tracer);
}, {
    roots: true,
    rootNameFilter: (name) => name === 'tracerIsReady'
});

