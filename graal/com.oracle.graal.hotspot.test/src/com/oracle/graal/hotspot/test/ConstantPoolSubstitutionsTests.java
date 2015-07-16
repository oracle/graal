/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.test;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.*;

import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.debug.Debug.*;
import sun.misc.*;
import sun.reflect.*;

public class ConstantPoolSubstitutionsTests extends GraalCompilerTest {

    protected StructuredGraph test(final String snippet) {
        try (Scope s = Debug.scope("ConstantPoolSubstitutionsTests", getMetaAccess().lookupJavaMethod(getMethod(snippet)))) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            compile(graph.method(), graph);
            assertNotInGraph(graph, Invoke.class);
            Debug.dump(graph, snippet);
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(node.toString());
            }
        }
        return graph;
    }

    @Test
    public void testGetSize() {
        ConstantPool cp = SharedSecrets.getJavaLangAccess().getConstantPool(Object.class);
        test("getSize", cp);
    }

    @Test
    public void testGetIntAt() {
        test("getIntAt");
    }

    @Test
    public void testGetLongAt() {
        test("getLongAt");
    }

    @Test
    public void testGetFloatAt() {
        test("getFloatAt");
    }

    @Test
    public void testGetDoubleAt() {
        test("getDoubleAt");
    }

    // @Test
    public void testGetUTF8At() {
        test("getUTF8At");
    }

    public int getSize(ConstantPool cp) {
        return cp.getSize();
    }

    public int getIntAt(ConstantPool cp) {
        return cp.getIntAt(0);
    }

    public long getLongAt(ConstantPool cp) {
        return cp.getLongAt(0);
    }

    public float getFloatAt(ConstantPool cp) {
        return cp.getFloatAt(0);
    }

    public double getDoubleAt(ConstantPool cp) {
        return cp.getDoubleAt(0);
    }

    public String getUTF8At(ConstantPool cp) {
        return cp.getUTF8At(0);
    }

}
