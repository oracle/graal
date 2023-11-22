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

var map = new Map();

function dumpHistogram() {
    print("==== Histogram ====");
    var digits = 3;
    Array.from(map.entries()).sort((one, two) => two[1] - one[1]).forEach(function (entry) {
        var number = entry[1].toString();
        if (number.length >= digits) {
            digits = number.length;
        } else {
            number = Array(digits - number.length + 1).join(' ') + number;
        }
        print(`${number} calls to ${entry[0]}`);
    });
    print("===================");
}

insight.on('enter', function(ev) {
    var cnt = map.get(ev.name);
    if (cnt) {
        cnt = cnt + 1;
    } else {
        cnt = 1;
    }
    map.set(ev.name, cnt);
}, {
    roots: true
});

insight.on('close', dumpHistogram);
