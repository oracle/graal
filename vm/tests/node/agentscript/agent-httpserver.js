/* 
* Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
*
* This code is free software; you can redistribute it and/or modify it
* under the terms of the GNU General Public License version 2 only, as
* published by the Free Software Foundation.  Oracle designates this
* particular file as subject to the "Classpath" exception as provided
* by Oracle in the LICENSE file that accompanied this code.
*
* This code is distributed in the hope that it will be useful, but WITHOUT
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
* FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
* version 2 for more details (a copy is included in the LICENSE file that
* accompanied this code).
*
* You should have received a copy of the GNU General Public License version
* 2 along with this work; if not, write to the Free Software Foundation,
* Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
*
* Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
* or visit www.oracle.com if you need additional information or have any
* questions.
*/

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


