/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/* global insight */

var sum = 0;
var max = 0;
var stop = false;

insight.on('enter', (ctx, frame) => {
    if (!stop) {
        ctx.iterateFrames((at, vars) => {
            if (at.name == 'Primes.next') {
                if (vars["this"].natural.x > 100) {
                    stop = true;
                }
            }
        });
        if (stop) {
            return;
        }
        let n = frame.number;
        sum += n;
        if (n > max) {
            max = n;
        }
    }
}, {
  roots: true,
  rootNameFilter: (name) => name === 'Filter'
});

insight.on('return', (ctx, frame) => {
    log(`Hundred thousand prime numbers from 2 to ${max} has sum ${sum}`);
    sum = 0;
    max = 0;
    stop = false;
}, {
    roots: true,
    rootNameFilter: (name) => name === 'measure'
});
