/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.util.Objects;

import org.hamcrest.MatcherAssert;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordCastNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests for derived oops in reference maps.
 */
public class DerivedOopTest extends ReplacementsTest implements Snippets {

    private static final class Pointers {
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

        Result() {
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
        return Word.objectToTrackedPointer(obj).rawValue();
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

    private static final String UNKNOWN_REFERENCE_AT_SAFEPOINT_MSG = "should not reach here: unknown reference alive across safepoint";

    @Test
    public void testFieldOffsetMergeNonLiveBasePointer() {
        GraalError error = Assert.assertThrows(GraalError.class, () -> {
            DebugContext debug = getDebugContext();
            try (Scope _ = debug.disable()) {
                // Run a couple times to encourage objects to move
                for (int i = 0; i < 4; i++) {
                    Result r = new Result();
                    test("fieldOffsetMergeSnippet01", r, 8L, 16L);
                    Assert.assertEquals(r.beforeGC.delta(), r.afterGC.delta());
                }
            }
        });
        MatcherAssert.assertThat(error.getMessage(), StringContains.containsString(UNKNOWN_REFERENCE_AT_SAFEPOINT_MSG));
    }

    @Test
    public void testFieldOffsetMergeNonLiveBasePointerNotAccrossSafepoint() {
        // Run a couple times to encourage objects to move
        for (int i = 0; i < 4; i++) {
            Result r = new Result();
            test("fieldOffsetMergeSnippet02", r, 8L, 16L);
        }
    }

    @Test
    public void testFieldOffsetMergeLiveBasePointer() {
        GraalError error = Assert.assertThrows(GraalError.class, () -> {
            DebugContext debug = getDebugContext();
            try (Scope _ = debug.disable()) {
                // Run a couple times to encourage objects to move
                for (int i = 0; i < 4; i++) {
                    Result r = new Result();
                    test("fieldOffsetMergeSnippet03", r, new Result(), new Result(), 8L, 16L);
                    Assert.assertEquals(r.beforeGC.delta(), r.afterGC.delta());
                }
            }
        });
        MatcherAssert.assertThat(error.getMessage(), StringContains.containsString(UNKNOWN_REFERENCE_AT_SAFEPOINT_MSG));
    }

    public static boolean SideEffectB;
    public static long SideEffect1 = 16;
    public static long SideEffect2 = 16;
    public static Object o1 = new Result();
    public static Object o2 = o1;

    public static Result fieldOffsetMergeSnippet01(Result objResult, long offsetA, long offsetB) {
        long internalPointer;
        if (SideEffectB) {
            internalPointer = getRawPointer(o1) + offsetA;
            SideEffect1 = internalPointer;
        } else {
            internalPointer = getRawPointer(o2) + offsetB;
            SideEffect2 = internalPointer;
        }
        GraalDirectives.controlFlowAnchor();
        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);
        objResult.beforeGC.basePointer = getRawPointer(objResult);
        objResult.beforeGC.internalPointer = internalPointer;
        System.gc();
        objResult.afterGC.basePointer = getRawPointer(objResult);
        objResult.afterGC.internalPointer = internalPointer;
        return objResult;
    }

    public static Result fieldOffsetMergeSnippet02(Result objResult, long offsetA, long offsetB) {
        long internalPointer;
        if (SideEffectB) {
            internalPointer = getRawPointer(o1) + offsetA;
            SideEffect1 = internalPointer;
        } else {
            internalPointer = getRawPointer(o2) + offsetB;
            SideEffect2 = internalPointer;
        }
        GraalDirectives.controlFlowAnchor();
        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);
        objResult.beforeGC.basePointer = getRawPointer(objResult);
        objResult.beforeGC.internalPointer = internalPointer;
        objResult.afterGC.basePointer = getRawPointer(objResult);
        objResult.afterGC.internalPointer = internalPointer;
        return objResult;
    }

    public static Result fieldOffsetMergeSnippet03(Result objResult, Result a, Result b, long offsetA, long offsetB) {
        long internalPointer;
        if (SideEffectB) {
            internalPointer = getRawPointer(a) + offsetA;
            SideEffect1 = internalPointer;
        } else {
            internalPointer = getRawPointer(b) + offsetB;
            SideEffect2 = internalPointer;
        }
        GraalDirectives.controlFlowAnchor();
        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);
        objResult.beforeGC.basePointer = getRawPointer(objResult);
        objResult.beforeGC.internalPointer = internalPointer;
        System.gc();
        objResult.afterGC.basePointer = getRawPointer(objResult);
        objResult.afterGC.internalPointer = internalPointer;
        return objResult;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, DerivedOopTest.class);
        r.register(new InvocationPlugin("getRawPointer", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                WordCastNode objectToTracked = b.add(WordCastNode.objectToTrackedPointer(arg, getReplacements().getWordKind()));
                b.addPush(JavaKind.Long, objectToTracked);
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        assert graph.getNodes().filter(WordCastNode.class).count() > 0 : "DerivedOopTest.toLong should be intrinsified";
        super.checkHighTierGraph(graph);
    }
}
