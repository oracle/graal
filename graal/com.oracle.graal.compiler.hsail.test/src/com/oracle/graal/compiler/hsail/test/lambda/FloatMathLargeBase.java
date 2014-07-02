/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.compiler.hsail.test.lambda;

public abstract class FloatMathLargeBase extends FloatMathBase {

    @Override
    void setupArrays() {
        for (int i = 0; i < size / 2; i++) {
            // Include positive and negative values as well as corner cases.
            float val = (float) (i == 0 ? 0 : Math.pow(1.99, (i % 100)));
            inArray[i] = val;
            inArray[i + size / 2] = -val;
        }
        // special values filled at end
        inArray[size - 1] = Float.NaN;
        inArray[size - 2] = Float.NEGATIVE_INFINITY;
        inArray[size - 3] = Float.POSITIVE_INFINITY;
    }
}
