/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graph.test.graphio.parsing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.net.URI;

import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.LocationCache;
import jdk.graal.compiler.graphio.parsing.LocationStackFrame;
import jdk.graal.compiler.graphio.parsing.LocationStratum;

public class StackTraceTest {
    @Test
    public void testFormatWithFileName() {
        BinaryReader.Method m = LocationCache.createMethod("aMethod", "my.Test", null);
        LocationStackFrame st = LocationCache.createFrame(m, 56,
                        LocationCache.fileLineStratum("test.java", 22), null);
        String trace = st.toString();
        assertEquals("my.Test.aMethod(test.java:22) [bci:56]", trace);
    }

    @Test
    public void testStratumInterned() throws Exception {
        LocationStratum l1 = LocationCache.createStratum(new URI("u").toString(), new File(".").getAbsolutePath(), "l", 1, 2, 3);
        LocationStratum l2 = LocationCache.createStratum(new URI("u").toString(), new File(".").getAbsolutePath(), "l", 1, 2, 3);
        assertSame(l1, l2);
    }
}
