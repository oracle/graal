/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.jtt.bytecode;

import com.oracle.graal.jtt.JTTTest;

public abstract class BC_fdiv_frem_base extends JTTTest {

    /** Some interesting values. */
    private static final float[] values = {
                    0.0f,
                    -0.0f,
                    1.0f,
                    -1.0f,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    Float.NaN,
                    10.0f,
                    -10.0f,
                    311.0f,
                    -311.0f,
    };

    public void run() {
        for (int i = 0; i < values.length; i++) {
            float x = values[i];
            for (int j = 0; j < values.length; j++) {
                float y = values[j];
                runTest("test", x, y);
            }
        }
    }

}
