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

const Long64 = {
    Zero: () => $t["com.oracle.svm.webimage.longemulation.Long64"].$f["LongZero"],

    fromInt: (n) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["fromInt"](n);
    },

    fromDouble: (number) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["fromDouble"](number);
    },

    add: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["add"](left, right);
    },

    sub: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["sub"](left, right);
    },

    mul: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["mul"](left, right);
    },

    div: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["div"](left, right);
    },

    and: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["and"](left, right);
    },

    or: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["or"](left, right);
    },

    not: (left) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["not"](left);
    },

    xor: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["xor"](left, right);
    },

    mod: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["mod"](left, right);
    },

    sl: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["sl"](left, right);
    },

    sr: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["sr"](left, right);
    },

    usr: (left, right) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["usr"](left, right);
    },

    slFromNum: (left, num) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["slFromNum"](left, num);
    },

    srFromNum: (left, num) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["srFromNum"](left, num);
    },

    usrFromNum: (left, num) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["usrFromNum"](left, num);
    },

    negate: (left) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["negate"](left);
    },

    equal: (l, r) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["equal"](l, r);
    },

    lessThan: (l, r) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["lessThan"](l, r);
    },

    belowThan: (l, r) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["belowThan"](l, r);
    },

    test: (l, r) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["test"](l, r);
    },

    fromTwoInt: (low, high) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["fromTwoInt"](low, high);
    },

    fromZeroExtend: (a) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["fromZeroExtend"](a);
    },

    lowBits: (a) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["lowBits"](a);
    },

    highBits: (a) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["highBits"](a);
    },

    compare: (a, b) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["compare"](a, b);
    },

    compareUnsigned: (a, b) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["compareUnsigned"](a, b);
    },

    toNumber: (a) => {
        return $t["com.oracle.svm.webimage.longemulation.Long64"].$m["toNumber"](a);
    },

    instanceof: (other) => {
        return other instanceof $t["com.oracle.svm.webimage.longemulation.Long64"];
    },
};
