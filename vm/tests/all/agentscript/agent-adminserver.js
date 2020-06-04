/* global agent */

function initializeInsight(insight, require) {
    const http = require("http");
    const srv = http.createServer((req, res) => {
        let method = req.method;
        if (method === 'POST') {
            var data = '';
            req.on('data', (chunk) => {
                data += chunk.toString();
            });
            req.on('end', () => {
                const fn = new Function('insight', data);
                try {
                    fn(insight);
                    res.write('GraalVM Insight hook activated\n');
                } finally {
                    res.end();
                }
            });
        }
    });
    srv.listen(9999, () => console.log("Admin ready at 9999"));
}


let waitForRequire = function (event) {
  if (typeof process === 'object' && process.mainModule && process.mainModule.require) {
    insight.off('source', waitForRequire);
    initializeInsight(insight, process.mainModule.require.bind(process.mainModule));
  }
};

insight.on('source', waitForRequire, { roots: true });

