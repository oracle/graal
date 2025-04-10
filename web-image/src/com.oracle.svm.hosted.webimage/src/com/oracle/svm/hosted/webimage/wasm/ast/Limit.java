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

package com.oracle.svm.hosted.webimage.wasm.ast;

/**
 * A WASM limit with optional maximum value.
 * <p>
 * Ref: https://webassembly.github.io/spec/core/text/types.html#text-limits
 */
public final class Limit {
    private final int min;
    private final int max;
    private final boolean hasMax;

    private Limit(int min, int max, boolean hasMax) {
        this.min = min;
        this.max = max;
        this.hasMax = hasMax;
    }

    /**
     * Constructor for limit without maximum.
     */
    public static Limit withoutMax(int min) {
        return new Limit(min, 0, false);
    }

    /**
     * Constructor for limit with maximum.
     */
    public static Limit withMax(int min, int max) {
        assert max >= min : max + " < " + min;
        return new Limit(min, max, true);
    }

    /**
     * Constructor for limit with a single element (min = max).
     */
    public static Limit fixed(int num) {
        return new Limit(num, num, true);
    }

    public boolean hasMax() {
        return hasMax;
    }

    /**
     * Lower bound of limit, has to be interpreted as an unsigned integer.
     */
    public int getMin() {
        return min;
    }

    /**
     * Upper bound of limit, has to be interpreted as an unsigned integer.
     */
    public int getMax() {
        assert hasMax() : "This limit does not have an upper bound";
        return max;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Limit{");
        sb.append(min);
        sb.append(", ");

        if (hasMax()) {
            sb.append("<no max>");
        } else {
            sb.append(max);
        }

        sb.append('}');
        return sb.toString();
    }
}
