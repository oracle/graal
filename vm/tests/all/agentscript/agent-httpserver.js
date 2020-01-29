var initTracer = require('jaeger-client').initTracer;
console.log('server: Jaeger tracer obtained');

// See schema https://github.com/jaegertracing/jaeger-client-node/blob/master/src/configuration.js#L37
var config = {
  serviceName: 't-trace-demo',
  reporter: {
    // Provide the traces endpoint; this forces the client to connect directly to the Collector and send
    // spans over HTTP
    collectorEndpoint: 'http://localhost:14268/api/traces',
    // Provide username and password if authentication is enabled in the Collector
    // username: '',
    // password: '',
  },
  sampler: {
      type : 'const',
      param : 1
  }
};
var options = {
  tags: {
    't-trace-demo.version': '1.1.2',
  },
//  metrics: metrics,
  logger: console,
  sampler: {
      type : 'const',
      param : 1
  }
};

function tracerIsReady(tracer) {
    return tracer;
}

var tracer = tracerIsReady(initTracer(config, options));

const http = require("http");
const srv = http.createServer(function handler(req, res) {
    setTimeout(() => {
        res.write('OK');
        res.end();
    }, 5);
});

const testCount = 10;

srv.listen(function nowPerformTheTesting() {
    let port = srv.address().port;
    console.log(`server: ready on port ${port}`);
    for (let i = 0; i < testCount; i++) {
        let url = `http://localhost:${port}/test/${i}`;
        console.log(`client: Connecting to ${url}`);
        http.get(url, (resp) => {
            let data = '';
            resp.on('data', (chunk) => {
                data += chunk;
            });
            resp.on('end', () => {
                console.log(`client: reply for ${url} request: ${data}`);
                if (i >= testCount / 2) {
                    console.log('client: Testing OK. Exiting.');
                    process.exit(0);
                }
            });

        }).on('error', (err) => {
            console.warn('client: [error]: ' + err.message);
            process.exit(1);
        });
    }
});


