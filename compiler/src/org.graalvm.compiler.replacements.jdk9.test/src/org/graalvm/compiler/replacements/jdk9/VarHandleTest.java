/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.jdk9;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.junit.Test;

public class VarHandleTest extends GraalCompilerTest {

    static class Holder {
        /* Field is declared volatile, but accessed with non-volatile semantics in the test case. */
        volatile int field = 42;

        static final VarHandle FIELD;

        static {
            try {
                FIELD = MethodHandles.lookup().findVarHandle(Holder.class, "field", int.class);
            } catch (ReflectiveOperationException ex) {
                throw GraalError.shouldNotReachHere(ex);
            }
        }
    }

    public static int test1Snippet(Holder h) {
        /* Explicitly access the volatile field with non-volatile access semantics. */
        return (int) Holder.FIELD.get(h);
    }

    @Test
    public void test1() {
        test("test1Snippet", new Holder());
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        assert graph.getNodes().filter(ReadNode.class).count() == 1 : "Must have one memory read for the field";
        assert graph.getNodes().filter(MembarNode.class).isEmpty() : "Memory access is with non-volatile semantics, so must not have a memory barrier";

        return true;
    }
}
