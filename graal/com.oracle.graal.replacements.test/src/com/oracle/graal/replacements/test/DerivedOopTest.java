/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import org.junit.*;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.nodes.*;

/**
 * Tests for derived oops in reference maps.
 */
public class DerivedOopTest extends GraalCompilerTest implements Snippets {

    private static class Pointers {
        public long basePointer;
        public long internalPointer;

        public long delta() {
            return internalPointer - basePointer;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Pointers)) {
                return false;
            }

            Pointers other = (Pointers) obj;
            return this.delta() == other.delta();
        }

        @Override
        public int hashCode() {
            return (int) delta();
        }
    }

    private static class Result {
        public Pointers beforeGC;
        public Pointers afterGC;

        public Result() {
            beforeGC = new Pointers();
            afterGC = new Pointers();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((afterGC == null) ? 0 : afterGC.hashCode());
            result = prime * result + ((beforeGC == null) ? 0 : beforeGC.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Result)) {
                return false;
            }
            Result other = (Result) obj;
            return Objects.equals(this.beforeGC, other.beforeGC) && Objects.equals(this.afterGC, other.afterGC);
        }
    }

    @Test
    public void testFieldOffset() {
        // Run a couple times to encourage objects to move
        for (int i = 0; i < 4; i++) {
            Result r = new Result();
            test("fieldOffsetSnippet", r, 16L);

            Assert.assertEquals(r.beforeGC.delta(), r.afterGC.delta());
        }
    }

    static long getRawPointer(Object obj) {
        // fake implementation for interpreter
        return obj.hashCode();
    }

    static long getRawPointerIntrinsic(Object obj) {
        return Word.fromObject(obj).rawValue();
    }

    public static Result fieldOffsetSnippet(Result obj, long offset) {
        long internalPointer = getRawPointer(obj) + offset;

        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);

        obj.beforeGC.basePointer = getRawPointer(obj);
        obj.beforeGC.internalPointer = internalPointer;

        System.gc();

        obj.afterGC.basePointer = getRawPointer(obj);
        obj.afterGC.internalPointer = internalPointer;

        return obj;
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(plugins.getInvocationPlugins(), DerivedOopTest.class);

        ResolvedJavaMethod intrinsic = getResolvedJavaMethod("getRawPointerIntrinsic");
        r.register1("getRawPointer", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.intrinsify(targetMethod, intrinsic, new ValueNode[]{arg});
                return true;
            }
        });

        return plugins;
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        assert graph.getNodes().filter(WordCastNode.class).count() > 0 : "DerivedOopTest.toLong should be intrinsified";
        return super.checkHighTierGraph(graph);
    }
}
