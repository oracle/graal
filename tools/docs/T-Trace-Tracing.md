# [T-Trace](T-Trace.md): OpenTracing API on top of **T-Trace**

It is possible to use the [T-Trace](T-Trace.md) system to implement smooth, declarative
tracing via standard OpenTracing API. First of all use the `npm` command to install
one of the JavaScript libraries for tracing:

```bash
$ graalvm/bin/npm install jaeger-client@3.17.1
```

Now you can use its API in your instrument `function-tracing.js` via the
`require` function (once it becomes available):

```js
let initializeAgent = function (tracer) {
    var counter = 0;

    agent.on('enter', function(ctx, frame) {
        const args = frame.args;
        if ('request' !== frame.type || args.length !== 2 || typeof args[0] !== 'object' || typeof args[1] !== 'object') {
            return;
        }
        const req = args[0];
        const res = args[1];
        const span = tracer.startSpan(req.url);
        res.id = ++counter;
        res.span = span;
        console.log(`agent: handling #${res.id} request for ${req.url}`);
    }, {
        roots: true,
        rootNameFilter: name => name === 'emit',
        sourceFilter: src => src.name === 'events.js'
    });

    agent.on('return', function(ctx, frame) {
        var res = frame['this'];
        if (res.span) {
            res.span.finish();
            console.log(`agent: finished #${res.id} request`);
        } else {
            // OK, caused for example by Tracer itself connecting to Jaeger server
        }
    }, {
        roots: true,
        rootNameFilter: name => name === 'end',
        sourceFilter: src => src.name === '_http_outgoing.js'
    });
    console.log('agent: ready');
};
```

The system hooks into `emit('request', ...)` and `res.end()` functions
which are used to initialize a response to an HTTP request and finish it.
Because the `res` object is a dynamic JavaScript object, it is possible to
add `id` and `span` attributes to it in the `enter` handler of the `emit` function
from the source `events.js`. Then it is possible to use them in the `return` handler
of the `end` function.

With such instrument, it is just a matter of selecting the right `ttrace`
pointcuts - declaratively, selectively, precisely, accuratelly
(via specifying the right tags and filtering on function names, location in
sources and other characteristics) and the OpenTracing will happen
automatically and only on demand, without modifying the application code
at all.

