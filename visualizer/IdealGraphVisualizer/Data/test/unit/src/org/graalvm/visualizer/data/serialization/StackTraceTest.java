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

package org.graalvm.visualizer.data.serialization;

import java.io.File;
import java.net.URI;
import org.graalvm.visualizer.data.impl.DataSrcApiAccessor;
import org.graalvm.visualizer.data.src.LocationStackFrame;
import org.graalvm.visualizer.data.src.LocationStratum;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertSame;
import org.junit.Before;
import org.junit.Test;

public class StackTraceTest {
    @Before
    public void setUp() {
        LocationStackFrame.init();
    }

    @Test
    public void formatWithFileName() {
        BinaryReader.Klass clazz = new BinaryReader.Klass("my.Test");
        BinaryReader.Method m = new BinaryReader.Method("aMethod", null, null, clazz, 0);
        LocationStackFrame st = DataSrcApiAccessor.getInstance().createFrame(m, 56, 
                                    DataSrcApiAccessor.getInstance().fileLineStratum("test.java", 22), null);
        String trace = st.toString();
        assertEquals("my.Test.aMethod(test.java:22) [bci:56]", trace);
    }

    @Test
    public void testNodeToString() {
        Builder.NodeClass nc = new Builder.NodeClass("org.mypkg.test.MockNode", "", null, null);
        Builder.Node node = new Builder.Node(73, nc);
        assertEquals("73 : Mock", node.toString());
    }

    @Test
    public void stratumInterned() throws Exception {
        LocationStratum l1 = DataSrcApiAccessor.getInstance().createStratum(new URI("u").toString(), new File(".").getAbsolutePath(), "l", 1, 2, 3);
        LocationStratum l2 = DataSrcApiAccessor.getInstance().createStratum(new URI("u").toString(), new File(".").getAbsolutePath(), "l", 1, 2, 3);
        assertSame(l1, l2);
    }
}
