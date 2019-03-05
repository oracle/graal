/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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
package org.graalvm.compiler.replacements.jdk9.test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class VolatileVirtualizeTest extends GraalCompilerTest {

    static class Holder {
        volatile int volatileField = 42;
        int field = 2018;

        static final VarHandle VOLATILE_HANDLE;
        static final Field VOLATILE_FIELD;
        static final long OFFSET;

        static {
            try {
                VOLATILE_HANDLE = MethodHandles.lookup().findVarHandle(Holder.class, "volatileField", int.class);
                VOLATILE_FIELD = Holder.class.getDeclaredField("volatileField");
                OFFSET = UNSAFE.objectFieldOffset(VOLATILE_FIELD);
            } catch (ReflectiveOperationException ex) {
                throw GraalError.shouldNotReachHere(ex);
            }
        }

        Holder(int i) {
            field = field * i;
        }
    }

    // test compilation of a virtualized unsafe volatile get
    public static int testReadSnippet(int i) {
        Holder h = new Holder(i);
        h.field = UNSAFE.getIntVolatile(h, Holder.OFFSET);
        return h.field;
    }

    // test compilation of a virtualized varhandle volatile set
    public static int testWriteSnippet(int i) {
        Holder h = new Holder(i);
        Holder.VOLATILE_HANDLE.setVolatile(h, i);
        return h.field;
    }

    // test compilation of a virtualized unsafe volatile set
    public static int testWriteSnippet2(int i) {
        Holder h = new Holder(i);
        UNSAFE.putIntVolatile(h, Holder.OFFSET, i);
        return h.field;
    }

    void testAccess(String name) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        StructuredGraph graph = parseForCompile(method);
        compile(method, graph);
    }

    @Test
    public void testRead() {
        testAccess("testReadSnippet");
    }

    @Test
    public void testWrite() {
        testAccess("testWriteSnippet");
    }

    @Test
    public void testWrite2() {
        testAccess("testWriteSnippet2");
    }
}
