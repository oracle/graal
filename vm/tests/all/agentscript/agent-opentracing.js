/* global agent */

let initializeTracer = function (tracer) {
    var counter = 0;

    insight.on('enter', function(ctx, frame) {
        const args = frame.args;
        if ('request' !== frame.type || args.length !== 2 || typeof args[0] !== 'object' || typeof args[1] !== 'object') {
            return;
        }
        const req = args[0];
        const res = args[1];
        const span = tracer.startSpan("request");
        span.setTag("span.kind", "server");
        span.setTag("http.url", req.url);
        span.setTag("http.method", req.method);
        res.id = ++counter;
        res.span = span;
        console.log(`agent: handling #${res.id} request for ${req.url}`);
    }, {
        roots: true,
        rootNameFilter: 'emit',
        sourceFilter: src => src.name === 'events.js'
    });

    insight.on('return', function(ctx, frame) {
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
        rootNameFilter: name => name === 'end',
        sourceFilter: src => src.name === '_http_outgoing.js'
    });
    console.log('agent: ready');
};

let initializeAgent = function (require) {
    let http = require("http");
    console.log(`${typeof http.createServer} http.createServer is available to the agent`);

    var initTracer = require('jaeger-client').initTracer;
    console.log('server: Jaeger tracer obtained');

// See schema https://github.com/jaegertracing/jaeger-client-node/blob/master/src/configuration.js#L37
    var config = {
        serviceName: 'insight-demo',
        reporter: {
            // Provide the traces endpoint; this forces the client to connect directly to the Collector and send
            // spans over HTTP
            collectorEndpoint: 'http://localhost:14268/api/traces',
            // Provide username and password if authentication is enabled in the Collector
            // username: '',
            // password: '',
        },
        sampler: {
            type: 'const',
            param: 1
        }
    };
    var options = {
        tags: {
            'insight-demo.version': '1.1.2',
        },
//  metrics: metrics,
        logger: console,
        sampler: {
            type: 'const',
            param: 1
        }
    };

    var tracer = initTracer(config, options);
    initializeTracer(tracer);
};

let waitForRequire = function (event) {
  if (typeof process === 'object' && process.mainModule && process.mainModule.require) {
    insight.off('source', waitForRequire);
    initializeAgent(process.mainModule.require.bind(process.mainModule));
  }
};

insight.on('source', waitForRequire, { roots: true });

