/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.*;
import org.junit.internal.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.test.*;

/**
 * Base class for Graal compiler unit tests.
 * <p>
 * White box tests for Graal compiler transformations use this pattern:
 * <ol>
 * <li>Create a graph by {@linkplain #parse(String) parsing} a method.</li>
 * <li>Manually modify the graph (e.g. replace a parameter node with a constant).</li>
 * <li>Apply a transformation to the graph.</li>
 * <li>Assert that the transformed graph is equal to an expected graph.</li>
 * </ol>
 * <p>
 * See {@link InvokeHintsTest} as an example of a white box test.
 * <p>
 * Black box tests use the {@link #test(String, Object...)} or
 * {@link #testN(int, String, Object...)} to execute some method in the interpreter and compare its
 * result against that produced by a Graal compiled version of the method.
 * <p>
 * These tests will be run by the {@code mx unittest} command.
 */
public abstract class GraalCompilerTest extends GraalTest {

    private final GraalCodeCacheProvider codeCache;
    private final MetaAccessProvider metaAccess;
    protected final Replacements replacements;
    protected final Backend backend;
    protected final Suites suites;

    public GraalCompilerTest() {
        this.replacements = Graal.getRequiredCapability(Replacements.class);
        this.metaAccess = Graal.getRequiredCapability(MetaAccessProvider.class);
        this.codeCache = Graal.getRequiredCapability(GraalCodeCacheProvider.class);
        this.backend = Graal.getRequiredCapability(Backend.class);
        this.suites = Graal.getRequiredCapability(SuitesProvider.class).createSuites();
    }

    @BeforeClass
    public static void initializeDebugging() {
        DebugEnvironment.initialize(System.out);
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph graph) {
        assertEquals(expected, graph, false);
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph graph, boolean excludeVirtual) {
        String expectedString = getCanonicalGraphString(expected, excludeVirtual);
        String actualString = getCanonicalGraphString(graph, excludeVirtual);
        String mismatchString = "mismatch in graphs:\n========= expected =========\n" + expectedString + "\n\n========= actual =========\n" + actualString;

        if (!excludeVirtual && expected.getNodeCount() != graph.getNodeCount()) {
            Debug.dump(expected, "Node count not matching - expected");
            Debug.dump(graph, "Node count not matching - actual");
            Assert.fail("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + graph.getNodeCount() + "\n" + mismatchString);
        }
        if (!expectedString.equals(actualString)) {
            Debug.dump(expected, "mismatching graphs - expected");
            Debug.dump(graph, "mismatching graphs - actual");
            Assert.fail(mismatchString);
        }
    }

    protected void assertConstantReturn(StructuredGraph graph, int value) {
        String graphString = getCanonicalGraphString(graph, false);
        Assert.assertEquals("unexpected number of ReturnNodes: " + graphString, graph.getNodes().filter(ReturnNode.class).count(), 1);
        ValueNode result = graph.getNodes().filter(ReturnNode.class).first().result();
        Assert.assertTrue("unexpected ReturnNode result node: " + graphString, result.isConstant());
        Assert.assertEquals("unexpected ReturnNode result kind: " + graphString, result.asConstant().getKind(), Kind.Int);
        Assert.assertEquals("unexpected ReturnNode result: " + graphString, result.asConstant().asInt(), value);
    }

    protected static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual) {
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);

        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;

        StringBuilder result = new StringBuilder();
        for (Block block : schedule.getCFG().getBlocks()) {
            result.append("Block " + block + " ");
            if (block == schedule.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ + " ");
            }
            result.append("\n");
            for (Node node : schedule.getBlockToNodesMap().get(block)) {
                if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode)) {
                    int id;
                    if (canonicalId.get(node) != null) {
                        id = canonicalId.get(node);
                    } else {
                        id = nextId++;
                        canonicalId.set(node, id);
                    }
                    String name = node instanceof ConstantNode ? node.toString(Verbosity.Name) : node.getClass().getSimpleName();
                    result.append("  " + id + "|" + name + (excludeVirtual ? "\n" : "    (" + node.usages().count() + ")\n"));
                }
            }
        }
        return result.toString();
    }

    protected GraalCodeCacheProvider getCodeCache() {
        return codeCache;
    }

    protected MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    /**
     * Parses a Java method to produce a graph.
     * 
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     */
    protected StructuredGraph parse(String methodName) {
        return parse(getMethod(methodName));
    }

    private static AtomicInteger compilationId = new AtomicInteger();

    /**
     * Compares two given objects for {@linkplain Assert#assertEquals(Object, Object) equality}.
     * Does a deep copy equality comparison if {@code expected} is an array.
     */
    protected void assertEquals(Object expected, Object actual) {
        if (expected != null && expected.getClass().isArray()) {
            Assert.assertTrue(expected != null);
            Assert.assertTrue(actual != null);
            Assert.assertEquals(expected.getClass(), actual.getClass());
            if (expected instanceof int[]) {
                Assert.assertArrayEquals((int[]) expected, (int[]) actual);
            } else if (expected instanceof byte[]) {
                Assert.assertArrayEquals((byte[]) expected, (byte[]) actual);
            } else if (expected instanceof char[]) {
                Assert.assertArrayEquals((char[]) expected, (char[]) actual);
            } else if (expected instanceof short[]) {
                Assert.assertArrayEquals((short[]) expected, (short[]) actual);
            } else if (expected instanceof float[]) {
                Assert.assertArrayEquals((float[]) expected, (float[]) actual, 0.0f);
            } else if (expected instanceof long[]) {
                Assert.assertArrayEquals((long[]) expected, (long[]) actual);
            } else if (expected instanceof double[]) {
                Assert.assertArrayEquals((double[]) expected, (double[]) actual, 0.0d);
            } else if (expected instanceof boolean[]) {
                new ExactComparisonCriteria().arrayEquals(null, expected, actual);
            } else if (expected instanceof Object[]) {
                Assert.assertArrayEquals((Object[]) expected, (Object[]) actual);
            } else {
                Assert.fail("non-array value encountered: " + expected);
            }
        } else {
            Assert.assertEquals(expected, actual);
        }
    }

    @SuppressWarnings("serial")
    public static class MultiCauseAssertionError extends AssertionError {

        private Throwable[] causes;

        public MultiCauseAssertionError(String message, Throwable... causes) {
            super(message);
            this.causes = causes;
        }

        @Override
        public void printStackTrace(PrintStream out) {
            super.printStackTrace(out);
            int num = 0;
            for (Throwable cause : causes) {
                if (cause != null) {
                    out.print("cause " + (num++));
                    cause.printStackTrace(out);
                }
            }
        }

        @Override
        public void printStackTrace(PrintWriter out) {
            super.printStackTrace(out);
            int num = 0;
            for (Throwable cause : causes) {
                if (cause != null) {
                    out.print("cause " + (num++) + ": ");
                    cause.printStackTrace(out);
                }
            }
        }
    }

    protected void testN(int n, final String name, final Object... args) {
        final List<Throwable> errors = new ArrayList<>(n);
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(i + ":" + name) {

                @Override
                public void run() {
                    try {
                        test(name, args);
                    } catch (Throwable e) {
                        errors.add(e);
                    }
                }
            };
            threads[i] = t;
            t.start();
        }
        for (int i = 0; i < n; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                errors.add(e);
            }
        }
        if (!errors.isEmpty()) {
            throw new MultiCauseAssertionError(errors.size() + " failures", errors.toArray(new Throwable[errors.size()]));
        }
    }

    protected Object referenceInvoke(Method method, Object receiver, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return method.invoke(receiver, args);
    }

    protected static class Result {

        final Object returnValue;
        final Throwable exception;

        public Result(Object returnValue, Throwable exception) {
            this.returnValue = returnValue;
            this.exception = exception;
        }

        @Override
        public String toString() {
            return exception == null ? returnValue == null ? "null" : returnValue.toString() : "!" + exception;
        }
    }

    /**
     * Called before a test is executed.
     */
    protected void before(@SuppressWarnings("unused") Method method) {
    }

    /**
     * Called after a test is executed.
     */
    protected void after() {
    }

    protected Result executeExpected(Method method, Object receiver, Object... args) {
        before(method);
        try {
            // This gives us both the expected return value as well as ensuring that the method to
            // be compiled is fully resolved
            return new Result(referenceInvoke(method, receiver, args), null);
        } catch (InvocationTargetException e) {
            return new Result(null, e.getTargetException());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            after();
        }
    }

    protected Result executeActual(Method method, Object receiver, Object... args) {
        before(method);
        Object[] executeArgs = argsWithReceiver(receiver, args);

        ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(method);
        checkArgs(javaMethod, executeArgs);

        InstalledCode compiledMethod = getCode(javaMethod, parse(method));
        try {
            return new Result(compiledMethod.executeVarargs(executeArgs), null);
        } catch (Throwable e) {
            return new Result(null, e);
        } finally {
            after();
        }
    }

    protected void checkArgs(ResolvedJavaMethod method, Object[] args) {
        JavaType[] sig = MetaUtil.signatureToTypes(method);
        Assert.assertEquals(sig.length, args.length);
        for (int i = 0; i < args.length; i++) {
            JavaType javaType = sig[i];
            Kind kind = javaType.getKind();
            Object arg = args[i];
            if (kind == Kind.Object) {
                if (arg != null && javaType instanceof ResolvedJavaType) {
                    ResolvedJavaType resolvedJavaType = (ResolvedJavaType) javaType;
                    Assert.assertTrue(resolvedJavaType + " from " + metaAccess.lookupJavaType(arg.getClass()), resolvedJavaType.isAssignableFrom(metaAccess.lookupJavaType(arg.getClass())));
                }
            } else {
                Assert.assertNotNull(arg);
                Assert.assertEquals(kind.toBoxedJavaClass(), arg.getClass());
            }
        }
    }

    /**
     * Prepends a non-null receiver argument to a given list or args.
     * 
     * @param receiver the receiver argument to prepend if it is non-null
     */
    protected Object[] argsWithReceiver(Object receiver, Object... args) {
        Object[] executeArgs;
        if (receiver == null) {
            executeArgs = args;
        } else {
            executeArgs = new Object[args.length + 1];
            executeArgs[0] = receiver;
            for (int i = 0; i < args.length; i++) {
                executeArgs[i + 1] = args[i];
            }
        }
        return executeArgs;
    }

    protected void test(String name, Object... args) {
        Method method = getMethod(name);
        Object receiver = Modifier.isStatic(method.getModifiers()) ? null : this;

        test(method, receiver, args);
    }

    protected void test(Method method, Object receiver, Object... args) {
        Result expect = executeExpected(method, receiver, args);
        if (getCodeCache() == null) {
            return;
        }
        testAgainstExpected(method, expect, receiver, args);
    }

    protected void testAgainstExpected(Method method, Result expect, Object receiver, Object... args) {
        testAgainstExpected(method, expect, Collections.<DeoptimizationReason> emptySet(), receiver, args);
    }

    protected Result executeActualCheckDeopt(Method method, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        Map<DeoptimizationReason, Integer> deoptCounts = new EnumMap<>(DeoptimizationReason.class);
        ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(method);
        ProfilingInfo profile = javaMethod.getProfilingInfo();
        for (DeoptimizationReason reason : shouldNotDeopt) {
            deoptCounts.put(reason, profile.getDeoptimizationCount(reason));
        }
        Result actual = executeActual(method, receiver, args);
        profile = javaMethod.getProfilingInfo(); // profile can change after execution
        for (DeoptimizationReason reason : shouldNotDeopt) {
            Assert.assertEquals((int) deoptCounts.get(reason), profile.getDeoptimizationCount(reason));
        }
        return actual;
    }

    protected void assertEquals(Result expect, Result actual) {
        if (expect.exception != null) {
            Assert.assertTrue("expected " + expect.exception, actual.exception != null);
            Assert.assertEquals(expect.exception.getClass(), actual.exception.getClass());
            Assert.assertEquals(expect.exception.getMessage(), actual.exception.getMessage());
        } else {
            if (actual.exception != null) {
                actual.exception.printStackTrace();
                Assert.fail("expected " + expect.returnValue + " but got an exception");
            }
            assertEquals(expect.returnValue, actual.returnValue);
        }
    }

    protected void testAgainstExpected(Method method, Result expect, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        Result actual = executeActualCheckDeopt(method, shouldNotDeopt, receiver, args);
        assertEquals(expect, actual);
    }

    private Map<ResolvedJavaMethod, InstalledCode> cache = new HashMap<>();

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     */
    protected InstalledCode getCode(final ResolvedJavaMethod method, final StructuredGraph graph) {
        return getCode(method, graph, false);
    }

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     * 
     * @param forceCompile specifies whether to ignore any previous code cached for the (method,
     *            key) pair
     */
    protected InstalledCode getCode(final ResolvedJavaMethod method, final StructuredGraph graph, boolean forceCompile) {
        if (!forceCompile) {
            InstalledCode cached = cache.get(method);
            if (cached != null) {
                if (cached.isValid()) {
                    return cached;
                }

            }
        }

        final int id = compilationId.incrementAndGet();

        InstalledCode installedCode = Debug.scope("Compiling", new Object[]{getCodeCache(), new DebugDumpScope(String.valueOf(id), true)}, new Callable<InstalledCode>() {

            public InstalledCode call() throws Exception {
                final boolean printCompilation = PrintCompilation.getValue() && !TTY.isSuppressed();
                if (printCompilation) {
                    TTY.println(String.format("@%-6d Graal %-70s %-45s %-50s ...", id, method.getDeclaringClass().getName(), method.getName(), method.getSignature()));
                }
                long start = System.currentTimeMillis();
                PhasePlan phasePlan = new PhasePlan();
                GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(metaAccess, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL);
                phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
                CallingConvention cc = getCallingConvention(getCodeCache(), Type.JavaCallee, graph.method(), false);
                final CompilationResult compResult = GraalCompiler.compileGraph(graph, cc, method, metaAccess, getCodeCache(), replacements, backend, getCodeCache().getTarget(), null, phasePlan,
                                OptimisticOptimizations.ALL, new SpeculationLog(), suites, new CompilationResult());
                if (printCompilation) {
                    TTY.println(String.format("@%-6d Graal %-70s %-45s %-50s | %4dms %5dB", id, "", "", "", System.currentTimeMillis() - start, compResult.getTargetCodeSize()));
                }
                return Debug.scope("CodeInstall", new Object[]{getCodeCache(), method}, new Callable<InstalledCode>() {

                    @Override
                    public InstalledCode call() throws Exception {
                        InstalledCode code = addMethod(method, compResult);
                        if (code == null) {
                            throw new GraalInternalError("Could not install code for " + MetaUtil.format("%H.%n(%p)", method));
                        }
                        if (Debug.isDumpEnabled()) {
                            Debug.dump(new Object[]{compResult, code}, "After code installation");
                        }
                        if (Debug.isLogEnabled()) {
                            DisassemblerProvider dis = Graal.getRuntime().getCapability(DisassemblerProvider.class);
                            if (dis != null) {
                                String text = dis.disassemble(code);
                                if (text != null) {
                                    Debug.log("Code installed for %s%n%s", method, text);
                                }
                            }
                        }

                        return code;
                    }
                });
            }
        });

        if (!forceCompile) {
            cache.put(method, installedCode);
        }
        return installedCode;
    }

    protected InstalledCode addMethod(final ResolvedJavaMethod method, final CompilationResult compResult) {
        return getCodeCache().addMethod(method, compResult);
    }

    /**
     * Parses a Java method to produce a graph.
     * 
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     */
    protected StructuredGraph parseProfiled(String methodName) {
        return parseProfiled(getMethod(methodName));
    }

    /**
     * Parses a Java method to produce a graph.
     */
    protected StructuredGraph parse(Method m) {
        return parse0(m, GraphBuilderConfiguration.getEagerDefault());
    }

    /**
     * Parses a Java method to produce a graph.
     */
    protected StructuredGraph parseProfiled(Method m) {
        return parse0(m, GraphBuilderConfiguration.getDefault());
    }

    /**
     * Parses a Java method in debug mode to produce a graph with extra infopoints.
     */
    protected StructuredGraph parseDebug(Method m) {
        GraphBuilderConfiguration gbConf = GraphBuilderConfiguration.getEagerDefault();
        gbConf.setEagerInfopointMode(true);
        return parse0(m, gbConf);
    }

    private StructuredGraph parse0(Method m, GraphBuilderConfiguration conf) {
        assert m.getAnnotation(Test.class) == null : "shouldn't parse method with @Test annotation: " + m;
        ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(m);
        StructuredGraph graph = new StructuredGraph(javaMethod);
        new GraphBuilderPhase(metaAccess, conf, OptimisticOptimizations.ALL).apply(graph);
        return graph;
    }

    protected PhasePlan getDefaultPhasePlan() {
        return getDefaultPhasePlan(false);
    }

    protected PhasePlan getDefaultPhasePlan(boolean eagerInfopointMode) {
        PhasePlan plan = new PhasePlan();
        GraphBuilderConfiguration gbConf = GraphBuilderConfiguration.getEagerDefault();
        gbConf.setEagerInfopointMode(eagerInfopointMode);
        plan.addPhase(PhasePosition.AFTER_PARSING, new GraphBuilderPhase(metaAccess, gbConf, OptimisticOptimizations.ALL));
        return plan;
    }
}
