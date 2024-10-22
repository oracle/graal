/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.layout;

import static org.graalvm.visualizer.settings.TestUtils.checkNotNulls;
import static org.junit.Assert.assertEquals;

/**
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public class LayoutTestUtil {
    public static void assertPortEquals(Port a, Port b) {
        if (checkNotNulls(a, b)) {
            assertVertexEquals(a.getVertex(), b.getVertex());
            assertEquals(a.getRelativePosition(), b.getRelativePosition());
        }
    }

    public static void assertVertexEquals(Vertex a, Vertex b) {
        if (checkNotNulls(a, b)) {
            assertClusterEquals(a.getCluster(), b.getCluster());
            assertEquals(a.getPosition(), b.getPosition());
            assertEquals(a.getSize(), b.getSize());
        }
    }

    public static void assertClusterEquals(Cluster a, Cluster b) {
        if (checkNotNulls(a, b)) {
            assertClusterEquals(a.getOuter(), b.getOuter());
            assertEquals(a.toString(), b.toString());
        }
    }
}
