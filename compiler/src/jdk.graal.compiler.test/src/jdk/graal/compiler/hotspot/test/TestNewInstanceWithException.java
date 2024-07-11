/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.java.DynamicNewArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewInstanceWithExceptionNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalUnsafeAccess;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class TestNewInstanceWithException extends SubprocessTest {

    static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    static final boolean TRACE_DEOPT = false;

    static final boolean DEBUG_SUB_PROCESS = false;

    static final boolean LOG_OOME_HAPPENED = false;

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);
        if (midTierChecker != null) {
            midTierChecker.check(graph);
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        if (highTierChecker != null) {
            highTierChecker.check(graph);
        }
    }

    public interface Checker {
        void check(StructuredGraph g);
    }

    protected Checker midTierChecker;

    protected Checker highTierChecker;

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf);
    }

    static Class<?> foldInHighTier(Class<?> c) {
        return c;
    }

    @NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0, allowedUsageTypes = {InputType.Anchor})
    public static class FoldInHighTier extends FixedWithNextNode implements Canonicalizable {

        public static final NodeClass<FoldInHighTier> TYPE = NodeClass.create(FoldInHighTier.class);

        @OptionalInput ValueNode object;

        public FoldInHighTier(ValueNode object) {
            super(TYPE, StampFactory.object());
            this.object = object;
        }

        @Override
        public jdk.graal.compiler.graph.Node canonical(CanonicalizerTool tool) {
            // after trapping nulls
            if (graph().isAfterStage(StageFlag.FINAL_PARTIAL_ESCAPE)) {
                return object;
            }
            return this;
        }
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins p = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(p.getInvocationPlugins(), TestNewInstanceWithException.class);
        r.register(new InvocationPlugin("foldInHighTier", Class.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.addPush(JavaKind.Object, new FoldInHighTier(arg));
                return true;
            }
        });
        return p;
    }

    public static class T {

    }

    static int S;

    static T[] Ts = new T[100];
    static T[][] TsMult = new T[100][100];

    public static int snippet(int limit) {
        try {
            for (int i = 0; i < limit; i++) {
                T[] s = new T[Ts.length * 2];
                Ts = s;
                GraalDirectives.controlFlowAnchor();
            }
            return 1;
        } catch (OutOfMemoryError oome) {
            Ts = null;
            System.gc();
            S = 42;
            if (LOG_OOME_HAPPENED) {
                GraalDirectives.log("[1]Out of memory happend all good..\n");
            }
            if (GraalDirectives.inCompiledCode()) {
                /*
                 * Graal does not support throwing and catching exceptions out of foreign calls into
                 * hotspot, thus this will never be true. We will deopt on the slowpath oome above
                 * in the try block.
                 */
                GraalDirectives.log("[2]... and we are even in compiled code..\n");
            }
            return 2;
        }
    }

    public void oomeAndCompile() throws InvalidInstalledCodeException {
        Ts = new T[1];
        resetCache();
        snippet(10);
        S = 0;
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        try {
            Assert.assertEquals(2, getCode(getResolvedJavaMethod("snippet"), opt).executeVarargs(Integer.MAX_VALUE));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    static class Node {
        Node next;

        Node mem1;
        Node mem2;
        Node mem3;
        Node mem4;
        Node mem5;
        Node mem6;
        Node mem7;
        Node mem8;
        Node mem9;
        Node mem10;
        Node mem12;
        Node mem13;
        Node mem14;
        Node mem15;
        Node mem16;
        Node mem17;
        Node mem18;
        Node mem19;
        Node mem20;
        Node mem21;
        Node mem22;
        Node mem23;
        Node mem24;
        Node mem25;
        Node mem26;
        Node mem27;
        Node mem28;
        Node mem29;
        Node mem30;
        Node mem31;
        Node mem32;
        Node mem33;
    }

    static Node head = new Node();

    public static int snippet2(int limit) {
        int count = 0;
        try {
            while (count++ < limit) {
                Node newHead = new Node();
                newHead.next = head;
                head = newHead;
                GraalDirectives.controlFlowAnchor();
            }
            head = new Node();
            return 1;
        } catch (OutOfMemoryError oome) {
            head = null;
            System.gc();
            if (LOG_OOME_HAPPENED) {
                GraalDirectives.log("[2]Out of memory happend all good..\n");
            }
            if (GraalDirectives.inCompiledCode()) {
                /*
                 * Graal does not support throwing and catching exceptions out of foreign calls into
                 * hotspot, thus this will never be true. We will deopt on the slowpath oome above
                 * in the try block.
                 */
                GraalDirectives.log("[2]... and we are even in compiled code..\n");
            }
            return 3;
        }
    }

    public void oomeAndCompile2() throws InvalidInstalledCodeException {
        resetCache();
        snippet2(10);
        S = 0;
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        try {
            Assert.assertEquals(3, getCode(getResolvedJavaMethod("snippet2"), opt).executeVarargs(Integer.MAX_VALUE));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    @BytecodeParserNeverInline
    static Class<? extends INode> getTestClass() {
        return IINode.class;
    }

    interface INode {
        void setNext(INode n);
    }

    static class IINode implements INode {
        INode next;

        @Override
        public void setNext(INode n) {
            this.next = n;
        }
    }

    static INode ihead;

    public static int snippet3(int limit) throws InstantiationException {
        Class<? extends INode> c = getTestClass();
        int count = 0;
        try {
            while (count++ < limit) {
                @SuppressWarnings("deprecation")
                INode newHead = (INode) UNSAFE.allocateInstance(c);
                newHead.setNext(ihead);
                ihead = newHead;
                GraalDirectives.controlFlowAnchor();
            }
            return 1;
        } catch (OutOfMemoryError oome) {
            ihead = null;
            System.gc();
            if (LOG_OOME_HAPPENED) {
                GraalDirectives.log("[3]Out of memory happend all good..\n");
            }
            if (GraalDirectives.inCompiledCode()) {
                /*
                 * Graal does not support throwing and catching exceptions out of foreign calls into
                 * hotspot, thus this will never be true. We will deopt on the slowpath oome above
                 * in the try block.
                 */
                GraalDirectives.log("[3]... and we are even in compiled code..\n");
            }
            return 4;
        }
    }

    public void oomeAndCompile3() throws Throwable {
        resetCache();
        try {
            snippet3(10);
            S = 0;
            OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
            Assert.assertEquals(4, getCode(getResolvedJavaMethod("snippet3"), opt).executeVarargs(Integer.MAX_VALUE));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public static int snippet4(int limit) throws InstantiationException {
        int count = 0;
        try {
            while (count++ < limit) {
                @SuppressWarnings("deprecation")
                INode newHead = (INode) UNSAFE.allocateInstance(foldInHighTier(IINode.class));
                newHead.setNext(ihead);
                ihead = newHead;
                GraalDirectives.controlFlowAnchor();
            }
            return 1;
        } catch (OutOfMemoryError oome) {
            ihead = null;
            System.gc();
            if (LOG_OOME_HAPPENED) {
                GraalDirectives.log("[3]Out of memory happend all good..\n");
            }
            if (GraalDirectives.inCompiledCode()) {
                /*
                 * Graal does not support throwing and catching exceptions out of foreign calls into
                 * hotspot, thus this will never be true. We will deopt on the slowpath oome above
                 * in the try block.
                 */
                GraalDirectives.log("[3]... and we are even in compiled code..\n");
            }
            return 5;
        }
    }

    public void oomeAndCompile4() throws Throwable {
        /*
         * In this test we want to ensure that optimizing DynamicNewInstanceWithException to regular
         * NewInstanceWithException works.
         */
        midTierChecker = new Checker() {

            @Override
            public void check(StructuredGraph g) {
                Assert.assertEquals(0, g.getNodes().filter(DynamicNewInstanceWithExceptionNode.class).count());
                Assert.assertEquals(1, g.getNodes().filter(NewInstanceWithExceptionNode.class).count());
            }
        };
        resetCache();
        try {
            snippet4(10);
            S = 0;
            OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
            Assert.assertEquals(5, getCode(getResolvedJavaMethod("snippet4"), opt).executeVarargs(Integer.MAX_VALUE));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
        midTierChecker = null;
    }

    static Object[] Os = new T[100];

    public static int snippet5(int limit) {
        Class<?> arrayClass = getTestClass();
        try {
            for (int i = 0; i < limit; i++) {
                Object[] s = (Object[]) Array.newInstance(arrayClass, Os.length * 2);
                Os = s;
                GraalDirectives.controlFlowAnchor();
            }
            return 1;
        } catch (OutOfMemoryError oome) {
            Os = null;
            System.gc();
            S = 42;
            if (LOG_OOME_HAPPENED) {
                GraalDirectives.log("[1]Out of memory happend all good..\n");
            }
            if (GraalDirectives.inCompiledCode()) {
                /*
                 * Graal does not support throwing and catching exceptions out of foreign calls into
                 * hotspot, thus this will never be true. We will deopt on the slowpath oome above
                 * in the try block.
                 */
                GraalDirectives.log("[2]... and we are even in compiled code..\n");
            }
            return 6;
        }
    }

    public void oomeAndCompile5() throws Throwable {
        Os = new Object[1];
        resetCache();
        try {
            snippet5(10);
            S = 0;
            OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
            Assert.assertEquals(6, getCode(getResolvedJavaMethod("snippet5"), opt).executeVarargs(Integer.MAX_VALUE));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public static int snippet6(int limit) {
        try {
            for (int i = 0; i < limit; i++) {
                Object[] s = (Object[]) Array.newInstance(foldInHighTier(IINode.class), Os.length * 2);
                Os = s;
                GraalDirectives.controlFlowAnchor();
            }
            return 1;
        } catch (OutOfMemoryError oome) {
            Os = null;
            System.gc();
            S = 42;
            if (LOG_OOME_HAPPENED) {
                GraalDirectives.log("[1]Out of memory happend all good..\n");
            }
            if (GraalDirectives.inCompiledCode()) {
                /*
                 * Graal does not support throwing and catching exceptions out of foreign calls into
                 * hotspot, thus this will never be true. We will deopt on the slowpath oome above
                 * in the try block.
                 */
                GraalDirectives.log("[2]... and we are even in compiled code..\n");
            }
            return 7;
        }
    }

    public void oomeAndCompile6() throws Throwable {
        Os = new Object[1];
        /*
         * In this test we want to ensure that optimizing DynamicNewInstanceWithException to regular
         * NewInstanceWithException works.
         */
        midTierChecker = new Checker() {

            @Override
            public void check(StructuredGraph g) {
                Assert.assertEquals(0, g.getNodes().filter(DynamicNewArrayWithExceptionNode.class).count());
                Assert.assertEquals(1, g.getNodes().filter(NewArrayWithExceptionNode.class).count());
            }
        };
        resetCache();
        try {
            snippet6(10);
            S = 0;
            OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
            Assert.assertEquals(7, getCode(getResolvedJavaMethod("snippet6"), opt).executeVarargs(Integer.MAX_VALUE));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
        midTierChecker = null;
    }

    public static int snippet7(int limit) {
        try {
            for (int i = 0; i < limit; i++) {
                T[][] s = new T[TsMult.length * 8][TsMult.length * 8];
                TsMult = s;
                GraalDirectives.controlFlowAnchor();
            }
            return 1;
        } catch (OutOfMemoryError oome) {
            TsMult = null;
            System.gc();
            S = 42;
            if (LOG_OOME_HAPPENED) {
                GraalDirectives.log("[1]Out of memory happend all good..\n");
            }
            if (GraalDirectives.inCompiledCode()) {
                /*
                 * Graal does not support throwing and catching exceptions out of foreign calls into
                 * hotspot, thus this will never be true. We will deopt on the slowpath oome above
                 * in the try block.
                 */
                GraalDirectives.log("[2]... and we are even in compiled code..\n");
            }
            return 8;
        }
    }

    public void oomeAndCompile7() throws InvalidInstalledCodeException {
        TsMult = new T[100][100];
        highTierChecker = new Checker() {
            @Override
            public void check(StructuredGraph g) {
                // Assert.assertEquals(0, g.getNodes().filter(NewMultiArrayNode.class).count());
            }
        };
        resetCache();
        // already OOME
        snippet7(10);
        TsMult = new T[100][100];
        S = 0;
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        try {
            Assert.assertEquals(8, getCode(getResolvedJavaMethod("snippet7"), opt).executeVarargs(Integer.MAX_VALUE));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void runSubprocessTest(Runnable r, String... args) throws IOException, InterruptedException {
        List<String> newArgs = new ArrayList<>();
        Collections.addAll(newArgs, args);
        // Filter out any explicitly selected GC
        newArgs.remove("-XX:+UseZGC");
        newArgs.remove("-XX:+UseG1GC");
        newArgs.remove("-XX:+UseParallelGC");

        if (TRACE_DEOPT) {
            newArgs.add("-XX:+UnlockDiagnosticVMOptions");
            newArgs.add("-XX:+TraceDeoptimization");
        }

        if (DEBUG_SUB_PROCESS) {
            newArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y");
        }

        SubprocessUtil.Subprocess sp = launchSubprocess(() -> {
            r.run();
        }, newArgs.toArray(new String[0]));
        if (sp != null) {
            // in the sub process itself we cannot spawn another one
            for (String s : sp.output) {
                TTY.printf("%s%n", s);
            }
        }
    }

    public static class TestNewInstanceWithException1 extends TestNewInstanceWithException {

        @Test
        public void testNewArrayWithException() throws IOException, InterruptedException {
            runSubprocessTest(() -> {
                try {
                    oomeAndCompile();
                } catch (InvalidInstalledCodeException e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }, "-Xmx32m");
        }
    }

    public static class TestNewInstanceWithException2 extends TestNewInstanceWithException {
        @Test
        public void testNewInstanceWithException() throws IOException, InterruptedException {
            runSubprocessTest(() -> {
                try {
                    oomeAndCompile2();
                } catch (InvalidInstalledCodeException e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }, "-Xmx32m");
        }
    }

    public static class TestNewInstanceWithException3 extends TestNewInstanceWithException {
        @Test
        public void testDynamicNewInstanceWithException() throws Throwable {
            runSubprocessTest(() -> {
                try {
                    oomeAndCompile3();
                } catch (Throwable e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }, "-Xmx32m");
        }
    }

    public static class TestNewInstanceWithException4 extends TestNewInstanceWithException {
        @Test
        public void testDynamicNewInstanceWithExceptionCanonToInstanceWithException() throws Throwable {
            runSubprocessTest(() -> {
                try {
                    oomeAndCompile4();
                } catch (Throwable e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }, "-Xmx32m");
        }
    }

    public static class TestNewInstanceWithException5 extends TestNewInstanceWithException {
        @Test
        public void testDynamicNewArrayWithException() throws Throwable {
            runSubprocessTest(() -> {
                try {
                    oomeAndCompile5();
                } catch (Throwable e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }, "-Xmx32m");
        }
    }

    public static class TestNewInstanceWithException6 extends TestNewInstanceWithException {
        @Test
        public void testDynamicNewArrayWithExceptionCanonToArrayWithException() throws Throwable {
            runSubprocessTest(() -> {
                try {
                    oomeAndCompile6();
                } catch (Throwable e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }, "-Xmx32m");
        }
    }

    public static class TestNewInstanceWithException7 extends TestNewInstanceWithException {
        @Test
        public void testNewMultiArrayWithException() throws Throwable {
            runSubprocessTest(() -> {
                try {
                    oomeAndCompile7();
                } catch (Throwable e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }, "-Xmx32m");
        }
    }
}
