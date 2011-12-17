/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.max;

import com.sun.max.ide.*;
import com.sun.max.program.*;

/**
 * Helps finding out whether the VM executing this test can populate object heaps with sizes beyond 4GB.
 * Run HotSpot with -verbose:gc to see heap numbers.
 */
public class MaxHeapTest extends MaxTestCase {

    public MaxHeapTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(MaxHeapTest.class);
    }

    private final int numberOfArrays = 128;
    private final int leafLength = 1024 * 1024;

    public void test_max() {
        final int[][] objects = new int[numberOfArrays][];
        int i = 0;
        try {
            while (i < numberOfArrays) {
                objects[i] = new int[leafLength];
                i++;
            }
        } catch (OutOfMemoryError e) {
            ProgramWarning.message("allocated " + i + " int[" + leafLength + "] arrays");
        }
    }
}
