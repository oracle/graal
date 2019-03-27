/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class CodePointRange implements Comparable<CodePointRange>, JsonConvertible {

    public final int lo;
    public final int hi;

    public CodePointRange(int lo, int hi) {
        assert hi >= lo;
        this.lo = lo;
        this.hi = hi;
    }

    public CodePointRange(int c) {
        this(c, c);
    }

    public CodePointRange(char lo, char hi) {
        this((int) lo, (int) hi);
    }

    public CodePointRange(char c) {
        this((int) c);
    }

    public static CodePointRange fromUnordered(int c1, int c2) {
        return new CodePointRange(Math.min(c1, c2), Math.max(c1, c2));
    }

    public CodePointRange expand(CodePointRange o) {
        assert intersects(o) || adjacent(o);
        return new CodePointRange(Math.min(lo, o.lo), Math.max(hi, o.hi));
    }

    public boolean isSingle() {
        return lo == hi;
    }

    public boolean intersects(CodePointRange o) {
        return lo <= o.hi && o.lo <= hi;
    }

    public boolean adjacent(CodePointRange o) {
        return hi + 1 == o.lo || lo - 1 == o.hi;
    }

    @Override
    public int compareTo(CodePointRange o) {
        return lo - o.lo;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CodePointRange && lo == ((CodePointRange) obj).lo && hi == ((CodePointRange) obj).hi;
    }

    @Override
    public int hashCode() {
        return (31 * lo) + (31 * hi);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (isSingle()) {
            return DebugUtil.charToString(lo);
        }
        return DebugUtil.charToString(lo) + "-" + DebugUtil.charToString(hi);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("hi", hi),
                        Json.prop("lo", lo));
    }
}
