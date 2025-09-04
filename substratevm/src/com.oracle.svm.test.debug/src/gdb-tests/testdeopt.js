/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

function add(a, b, test) {
    if (test) {
        a += b;
    }
    return a + b;
}

// trigger compilation add for ints and test = true
for (let i = 0; i < 1000 * 1000; i++) {
    add(i, i, true);
}

// deoptimize with failed assumption in compiled method
// then trigger compilation again
console.log("deopt1")
for (let i = 0; i < 1000 * 1000; i++) {
    add(i, i, false);
}

// deoptimize with different parameter types
console.log("deopt2");
add({f1: "test1", f2: 2}, {x: "x", y: {test: 42}}, false);
