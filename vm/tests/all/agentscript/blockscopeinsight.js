/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

function dumpVars(vars) {
  return Object.entries(vars).filter(([name]) => name != "this").map(([name, value]) => `${name}=${value}`).join(", ");
}

for (let [eventId, eventName = eventId] of [["enter"], ["return", "leave"]]) {
  insight.on(eventId, function(ctx, frame) {
    console.log(`=== on ${eventName} at ${ctx.name} (${ctx.source.name}:${ctx.line}) : {${dumpVars(frame)}}`);
    ctx.iterateFrames((at, frameVars) => {
      if (at.name.endsWith("sumEvenNumbers")) {
        console.log(`       frame at ${at.name} (${at.source.name}:${at.line}) : {${dumpVars(frameVars)}}`);
      }
    });
  }, {
    statements: true,
    at: {
      sourcePath: ".*blockscope.*",
      line: 32,
    }
  });
}

insight.on("return", function(ctx, frame) {
  console.log(`=== on leave at ${ctx.name} (${ctx.source.name}:${ctx.line}) : {${dumpVars(frame)}}`);
}, {
  roots: true,
  rootNameFilter: "sumEvenNumbers",
});
