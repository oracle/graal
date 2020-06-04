
const http = require("http");
const srv = http.createServer((_, res) => {
    setTimeout(() => {
        res.write('OK#' + res.id);
        res.end();
    }, 5);
});

const testCount = 10;

srv.listen(function nowPerformTheTesting() {
    let port = srv.address().port;
    console.log(`server: ready on port ${port}`);
    for (let i = 0; i < testCount; i++) {
        let url = `http://localhost:${port}/test/${String.fromCharCode(65 + i)}`;
        console.log(`client: Connecting to ${url}`);
        http.get(url, (resp) => {
            let data = '';
            resp.on('data', (chunk) => {
                data += chunk;
            });
            resp.on('end', () => {
                console.log(`client: reply for ${url} request: ${data}`);
                let prefix = data.startsWith('OK#');
                let cnt = Number.parseInt(data.substring(3));
                if (!prefix || Number.isNaN(cnt)) {
                    console.log(`client: unexpected reply: ${data}`);
                    process.exit(2);
                }
                if (cnt >= testCount / 2) {
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


