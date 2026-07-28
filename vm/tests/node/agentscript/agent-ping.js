/* 
* Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

const portWaitRetryDelay = 200;
const portWaitTimeout = 10_000;

var counter = 0;

function ping() {
    console.log(`Ping ${++counter}`);
    if (counter === 2) {
        applyGraalVMInsightScriptViaCurlAnytimeLater();
    } else {
        setTimeout(ping, 500);
    }
}
setTimeout(ping, 500);

function applyGraalVMInsightScriptViaCurlAnytimeLater(deadline = Date.now() + portWaitTimeout) {
    const port = process.env.GRAALVM_INSIGHT_ADMIN_PORT;
    if (!port) {
        if (deadline < Date.now()) {
            throw new Error('Timed out waiting for the admin server port');
        }
        setTimeout(applyGraalVMInsightScriptViaCurlAnytimeLater, portWaitRetryDelay, deadline);
        return;
    }
    const script = 'insight.on("enter", (ctx, frame) => {' +
            '  console.log("observing ping at " + frame.counter);' +
            '  if (frame.counter >= 5) process.exit(5);' +
            '}';
    const cmd = `curl --data '${script}, { roots: true, rootNameFilter: (n) => n === "ping" });' -X POST http://localhost:${port}/`;
    console.log(`Attaching to ${port}`);
    const { exec } = require('child_process');
    exec(cmd, {}, ping);
}
