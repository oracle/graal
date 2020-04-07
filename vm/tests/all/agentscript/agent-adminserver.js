/* global agent */

function initializeAgent(agent, require) {
    const http = require("http");
    const srv = http.createServer((req, res) => {
        let method = req.method;
        if (method === 'POST') {
            var data = '';
            req.on('data', (chunk) => {
                data += chunk.toString();
            });
            req.on('end', () => {
                const fn = new Function('agent', data);
                try {
                    fn(agent);
                    res.write('T-Trace hook activated\n');
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
    agent.off('source', waitForRequire);
    initializeAgent(agent, process.mainModule.require.bind(process.mainModule));
  }
};

agent.on('source', waitForRequire, { roots: true });

