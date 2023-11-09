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

