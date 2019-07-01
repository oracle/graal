/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.charset;

public class RangesAccumulator<T extends RangesBuffer> {

    private T acc;
    private T tmp;

    public RangesAccumulator(T acc) {
        this.acc = acc;
    }

    public T get() {
        return acc;
    }

    public T getTmp() {
        if (tmp == null) {
            tmp = acc.create();
        }
        return tmp;
    }

    public void addRange(int lo, int hi) {
        acc.addRange(lo, hi);
    }

    public void appendRange(int lo, int hi) {
        acc.appendRange(lo, hi);
    }

    public void addSet(SortedListOfRanges set) {
        T t = getTmp();
        tmp = acc;
        acc = t;
        SortedListOfRanges.union(tmp, set, acc);
    }

    public void clear() {
        acc.clear();
    }
}
