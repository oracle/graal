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
function long64ToDouble(longval) {
    var arraybuffer = new ArrayBuffer(8);
    var dataview = new DataView(arraybuffer);
    dataview.setInt32(4, Long64.lowBits(longval));
    dataview.setInt32(0, Long64.highBits(longval));
    return dataview.getFloat64(0);
}

function doubleToLong64(doubleval) {
    var arraybuffer = new ArrayBuffer(8);
    var dataview = new DataView(arraybuffer);
    dataview.setFloat64(0, doubleval);
    var low = dataview.getInt32(4);
    var high = dataview.getInt32(0);
    var longval = Long64.fromTwoInt(low, high);
    return longval;
}

function floatToRawInt(float32val) {
    var arraybuffer = new ArrayBuffer(4);
    var dv = new DataView(arraybuffer);
    dv.setFloat32(0, float32val);
    return dv.getInt32(0);
}

function intToRawFloat(int32val) {
    var arraybuffer = new ArrayBuffer(4);
    var dv = new DataView(arraybuffer);
    dv.setInt32(0, int32val);
    return dv.getFloat32(0);
}
