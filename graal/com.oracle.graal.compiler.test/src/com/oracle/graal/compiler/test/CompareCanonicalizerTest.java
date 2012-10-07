/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import org.junit.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.common.*;

public class CompareCanonicalizerTest extends GraalCompilerTest {

    @Test
    public void testCanonicalComparison() {
        StructuredGraph referenceGraph = parse("referenceCanonicalComparison");
        for (int i = 1; i < 4; i++) {
            StructuredGraph graph = parse("canonicalCompare" + i);
            assertEquals(referenceGraph, graph);
        }
        new CanonicalizerPhase(null, runtime(), null).apply(referenceGraph);
        for (int i = 1; i < 4; i++) {
            StructuredGraph graph = parse("canonicalCompare" + i);
            new CanonicalizerPhase(null, runtime(), null).apply(graph);
            assertEquals(referenceGraph, graph);
        }
    }

    public static int referenceCanonicalComparison(int a, int b) {
        if (a < b) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int canonicalCompare1(int a, int b) {
        if (a >= b) {
            return 2;
        } else {
            return 1;
        }
    }

    public static int canonicalCompare2(int a, int b) {
        if (b > a) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int canonicalCompare3(int a, int b) {
        if (b <= a) {
            return 2;
        } else {
            return 1;
        }
    }
}
