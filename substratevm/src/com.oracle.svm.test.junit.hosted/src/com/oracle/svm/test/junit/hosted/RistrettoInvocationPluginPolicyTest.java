/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.junit.hosted;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.ClassFile.ACC_SUPER;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPhase;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPlugins;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoInvocationPluginPolicy;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoParser;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Parses generated protected calls with Ristretto's parser and runtime invocation plugins. The
 * explicit policy entries must have intrinsic representations consisting of side-effect-free nodes
 * or exception-aware operations with valid states and dispatch. Image metadata is supplied by JVMCI;
 * the plugin implementations and local-handler restriction are the production implementations.
 */
public class RistrettoInvocationPluginPolicyTest extends GraalCompilerTest {
    private final ProbeLoader loader = new ProbeLoader(getClass().getClassLoader());
    private int nextProbe;

    private record ParsedProbe(StructuredGraph graph, List<Node> pluginNodes, boolean applied, int invokeBci) {
    }

    /// Defines independent named classes, including the receiver subtype needed for protected clone.
    private static final class ProbeLoader extends ClassLoader {
        ProbeLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            Class<?> type = defineClass(name, bytes, 0, bytes.length);
            resolveClass(type);
            return type;
        }
    }

    @Test
    public void testPolicyGraphRepresentations() throws ReflectiveOperationException {
        List<Throwable> failures = new ArrayList<>();
        for (String name : RistrettoInvocationPluginPolicy.getMethodsAllowedWithLocalExceptionHandler()) {
            Method target = resolveTarget(name);
            // Class-name folding requires a constant Class receiver.
            boolean constantReceiver = target.getDeclaringClass() == Class.class &&
                            target.getName().equals("getName");
            for (BytecodeExceptionMode mode : new BytecodeExceptionMode[]{BytecodeExceptionMode.CheckAll, BytecodeExceptionMode.OmitAll}) {
                try {
                    ParsedProbe parsed = parseProbe(target, constantReceiver, true, mode);
                    if (target.getDeclaringClass() == Class.class && target.getName().equals("desiredAssertionStatus")) {
                        assertFallback(parsed, target);
                    } else {
                        assertRepresentation(parsed);
                    }
                    if (mode == BytecodeExceptionMode.CheckAll && !Modifier.isStatic(target.getModifiers()) && !constantReceiver) {
                        List<BytecodeExceptionNode> receiverExceptions = parsed.graph().getNodes().filter(BytecodeExceptionNode.class).stream().filter(
                                        exception -> exception
                                                        .getExceptionKind() == BytecodeExceptionNode.BytecodeExceptionKind.NULL_POINTER &&
                                                        exception.stateAfter().bci == parsed.invokeBci()).toList();
                        Assert.assertFalse("Missing receiver null exception at the invocation BCI", receiverExceptions.isEmpty());
                        for (BytecodeExceptionNode exception : receiverExceptions) {
                            assertCaught(exception.next());
                        }
                    }
                    System.out.println("Graph policy PASS: " + name + " " + mode);
                } catch (Throwable failure) {
                    failures.add(new AssertionError(name + " " + mode, failure));
                    System.out.println("Graph policy FAIL: " + name + " " + mode + " " + failure);
                }
            }
        }
        if (!failures.isEmpty()) {
            AssertionError failure = new AssertionError("Failed policy graphs: " + failures.size());
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    @Test
    public void testRejectsNonExceptionAwareStateSplit() throws ReflectiveOperationException {
        // Directives are deliberately exempt from the ordinary-method representation restriction.
        ParsedProbe parsed = parseProbe(GraalDirectives.class.getDeclaredMethod("sideEffect", int.class), false, true, BytecodeExceptionMode.CheckAll);
        Assert.assertTrue(parsed.applied());
        AssertionError failure = Assert.assertThrows(AssertionError.class, () -> assertRepresentation(parsed));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("Unsupported side-effecting state split"));
    }

    @Test
    public void testRejectsWrongExceptionContinuation() throws ReflectiveOperationException {
        ParsedProbe parsed = parseProbe(Object.class.getDeclaredMethod("clone"), false, true, BytecodeExceptionMode.CheckAll);
        assertRepresentation(parsed);
        List<ReturnNode> handlerReturns = parsed.graph().getNodes(ReturnNode.TYPE).stream()
                        .filter(node -> node.result().isJavaConstant() && node.result().asJavaConstant().isNull()).toList();
        Assert.assertEquals(1, handlerReturns.size());
        ReturnNode handlerReturn = handlerReturns.getFirst();
        handlerReturn.replaceFirstInput(handlerReturn.result(), parsed.graph().getParameter(0));
        Assert.assertSame(parsed.graph().getParameter(0), handlerReturn.result());
        Assert.assertTrue(parsed.graph().verify());
        AssertionError failure = Assert.assertThrows(AssertionError.class, () -> assertRepresentation(parsed));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("generated catch result"));
    }

    @Test
    public void testRejectsTerminatingExceptionBranch() throws ReflectiveOperationException {
        ParsedProbe parsed = parseProbe(Object.class.getDeclaredMethod("clone"), false, true, BytecodeExceptionMode.CheckAll);
        assertRepresentation(parsed);
        StructuredGraph graph = parsed.graph();
        WithExceptionNode throwing = graph.getNodes().filter(WithExceptionNode.class).first();
        AbstractBeginNode exception = throwing.exceptionEdge();
        FixedNode continuation = exception.next();
        exception.setNext(null);
        BeginNode caught = graph.add(new BeginNode());
        caught.setNext(continuation);
        UnreachableBeginNode terminated = graph.add(new UnreachableBeginNode());
        terminated.setNext(graph.add(new UnreachableControlSinkNode()));
        exception.setNext(graph.add(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(graph.getParameter(0))), caught, terminated, BranchProbabilityData.unknown())));
        Assert.assertTrue(graph.verify());
        AssertionError failure = Assert.assertThrows(AssertionError.class, () -> assertRepresentation(parsed));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("generated catch result"));
    }

    @Test
    public void testRejectsWrongExceptionState() throws ReflectiveOperationException {
        ParsedProbe parsed = parseProbe(Object.class.getDeclaredMethod("clone"), false, true, BytecodeExceptionMode.CheckAll);
        assertRepresentation(parsed);
        StructuredGraph graph = parsed.graph();
        ExceptionObjectNode exception = graph.getNodes().filter(ExceptionObjectNode.class).first();
        FrameState original = exception.stateAfter();
        int returnBci = graph.method().getExceptionHandlers()[0].getEndBCI();
        FrameState invalidForDeoptimization = original.duplicate();
        invalidForDeoptimization.invalidateForDeoptimization();
        FrameState[] invalid = {
                        original.duplicateModified(graph, returnBci, FrameState.StackState.Rethrow, JavaKind.Void, null, null, null),
                        original.duplicateModified(graph, parsed.invokeBci(), FrameState.StackState.AfterPop, JavaKind.Void, null, null, null),
                        original.duplicateModified(graph, parsed.invokeBci(), FrameState.StackState.Rethrow, JavaKind.Object,
                                        new JavaKind[]{JavaKind.Object}, new ValueNode[]{graph.getParameter(0)}, null),
                        invalidForDeoptimization
        };
        for (FrameState state : invalid) {
            exception.setStateAfter(state);
            Assert.assertTrue(graph.verify());
            AssertionError failure = Assert.assertThrows(AssertionError.class, () -> assertRepresentation(parsed));
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("Exception state"));
        }
        exception.setStateAfter(original);
        assertRepresentation(parsed);
    }

    @Test
    public void testConstantReceiverPreconditions() throws ReflectiveOperationException {
        for (String name : new String[]{"getName", "desiredAssertionStatus"}) {
            Method target = Class.class.getDeclaredMethod(name);
            assertFallback(parseProbe(target, false, true, BytecodeExceptionMode.CheckAll), target);
        }
    }

    @Test
    public void testAssertionStatusRemainsDynamic() throws ReflectiveOperationException {
        Method target = Class.class.getDeclaredMethod("desiredAssertionStatus");
        for (BytecodeExceptionMode mode : new BytecodeExceptionMode[]{BytecodeExceptionMode.CheckAll, BytecodeExceptionMode.OmitAll}) {
            assertFallback(parseProbe(target, true, true, mode), target);
        }
    }

    @Test
    public void testDisabledFoldAndUnlistedPlugin() throws ReflectiveOperationException {
        Method securityManager = System.class.getDeclaredMethod("getSecurityManager");
        assertFallback(parseProbe(securityManager, false, false, BytecodeExceptionMode.CheckAll), securityManager);
        Method unlisted = Math.class.getDeclaredMethod("addExact", int.class, int.class);
        assertFallback(parseProbe(unlisted, false, true, BytecodeExceptionMode.CheckAll), unlisted);
    }

    @Test
    public void testCallerSensitiveLookup() throws ReflectiveOperationException {
        Method target = MethodHandles.class.getDeclaredMethod("lookup");
        ParsedProbe parsed = parseProbe(target, false, true, BytecodeExceptionMode.CheckAll);
        Assert.assertEquals(1, parsed.graph().getNodes(ReturnNode.TYPE).count());
        MethodHandles.Lookup lookup = getSnippetReflection().asObject(MethodHandles.Lookup.class, parsed.graph().getNodes(ReturnNode.TYPE).first().result().asJavaConstant());
        Assert.assertEquals(getSnippetReflection().originalClass(parsed.graph().method().getDeclaringClass()), lookup.lookupClass());
        assertFallback(parseProbe(target, false, true, BytecodeExceptionMode.CheckAll, _ -> null), target);
    }

    /// Resolves the production policy itself; there is no second handwritten target inventory.
    private Method resolveTarget(String name) throws ReflectiveOperationException {
        int separator = name.lastIndexOf('.', name.indexOf('('));
        for (Method method : Class.forName(name.substring(0, separator)).getDeclaredMethods()) {
            if (getMetaAccess().lookupJavaMethod(method).format("%H.%n(%p)").equals(name)) {
                return method;
            }
        }
        throw new AssertionError("No method for policy entry " + name);
    }

    /**
     * Builds a parameterized call in a real exception-table range. Only successful Class constant
     * folds use a constant receiver. The catch returns the return kind's zero/null value, making
     * caught control flow distinct from unwinding out of the generated method. Final receiver classes
     * make inherited protected calls statically bindable before invocation-plugin lookup.
     */
    private Method generateProbe(Method target, boolean constantReceiver) throws ReflectiveOperationException {
        String name = getClass().getName() + "$GraphProbe" + nextProbe++;
        ClassDesc probeClass = ClassDesc.of(name);
        ClassDesc owner = ClassDesc.ofDescriptor(target.getDeclaringClass().descriptorString());
        ClassDesc result = ClassDesc.ofDescriptor(target.getReturnType().descriptorString());
        boolean protectedCall = Modifier.isProtected(target.getModifiers());
        List<ClassDesc> arguments = new ArrayList<>();
        if (!Modifier.isStatic(target.getModifiers()) && !constantReceiver) {
            arguments.add(protectedCall ? probeClass : owner);
        }
        for (Class<?> parameter : target.getParameterTypes()) {
            arguments.add(ClassDesc.ofDescriptor(parameter.descriptorString()));
        }
        MethodTypeDesc probeType = MethodTypeDesc.of(result, arguments.toArray(ClassDesc[]::new));
        MethodTypeDesc targetType = MethodTypeDesc.of(result, Arrays.stream(target.getParameterTypes())
                        .map(type -> ClassDesc.ofDescriptor(type.descriptorString())).toArray(ClassDesc[]::new));
        byte[] bytes = ClassFile.of().build(probeClass, builder -> builder.withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                        .withInterfaceSymbols(ClassDesc.of(Cloneable.class.getName()))
                        .withMethodBody("callerLookup", MethodTypeDesc.of(ClassDesc.of(MethodHandles.Lookup.class.getName())), ACC_PUBLIC | ACC_STATIC,
                                        code -> code.invokestatic(ClassDesc.of(MethodHandles.class.getName()), "lookup",
                                                        MethodTypeDesc.of(ClassDesc.of(MethodHandles.Lookup.class.getName()))).areturn())
                        .withMethodBody("probe", probeType, ACC_PUBLIC | ACC_STATIC, code -> {
                            Label start = code.newLabel();
                            Label end = code.newLabel();
                            Label handler = code.newLabel();
                            code.labelBinding(start);
                            if (constantReceiver) {
                                code.ldc(ClassDesc.of(String.class.getName()));
                            }
                            int slot = 0;
                            for (ClassDesc argument : arguments) {
                                TypeKind kind = TypeKind.from(argument).asLoadable();
                                code.loadLocal(kind, slot);
                                slot += kind.slotSize();
                            }
                            if (Modifier.isStatic(target.getModifiers())) {
                                code.invokestatic(owner, target.getName(), targetType);
                            } else {
                                code.invokevirtual(protectedCall ? probeClass : owner, target.getName(), targetType);
                            }
                            TypeKind kind = TypeKind.from(result).asLoadable();
                            code.labelBinding(end).return_(kind).labelBinding(handler).pop();
                            switch (kind) {
                                case REFERENCE -> code.aconst_null();
                                case LONG -> code.lconst_0();
                                case FLOAT -> code.fconst_0();
                                case DOUBLE -> code.dconst_0();
                                default -> code.iconst_0();
                            }
                            code.return_(kind).exceptionCatchAll(start, end, handler);
                        }));
        Class<?> type = loader.define(name, bytes);
        Class.forName(name, true, loader);
        return Arrays.stream(type.getDeclaredMethods()).filter(method -> method.getName().equals("probe")).findFirst().orElseThrow();
    }

    /**
     * Supplies host metadata to the real parser, with no inline plugin and no replacement intrinsic.
     * Capturing nodes during the actual invocation plugin isolates its state splits from unrelated
     * graph scaffolding. State checks happen after the parser has finished wiring the graph.
     */
    private ParsedProbe parseProbe(Method target, boolean constantReceiver, boolean foldSecurityManager, BytecodeExceptionMode mode) throws ReflectiveOperationException {
        return parseProbe(target, constantReceiver, foldSecurityManager, mode, context -> ReflectionUtil.invokeMethod(
                        ReflectionUtil.lookupMethod(getSnippetReflection().originalClass(context.getMethod().getDeclaringClass()), "callerLookup"), null));
    }

    /// Parses with the actual runtime plugins and an explicit caller-sensitive metadata service.
    private ParsedProbe parseProbe(Method target, boolean constantReceiver, boolean foldSecurityManager, BytecodeExceptionMode mode,
                    Function<GraphBuilderContext, MethodHandles.Lookup> lookupForCaller) throws ReflectiveOperationException {
        Method probe = generateProbe(target, constantReceiver);
        ResolvedJavaMethod targetMethod = getMetaAccess().lookupJavaMethod(target);
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        RistrettoGraphBuilderPlugins.setRuntimeGraphBuilderPlugins(plugins, foldSecurityManager, lookupForCaller);
        StandardGraphBuilderPlugins.registerInvocationPlugins(getSnippetReflection(), plugins.getInvocationPlugins(), true, true, false, false);
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true).withBytecodeExceptionMode(mode);
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).method(getMetaAccess().lookupJavaMethod(probe)).build();
        List<ParsedProbe> applications = new ArrayList<>();
        GraphBuilderPhase phase = new RistrettoGraphBuilderPhase(config) {
            @Override
            protected Instance createInstance(CoreProviders providers, GraphBuilderConfiguration instanceConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialContext) {
                return new Instance(providers, instanceConfig, optimisticOpts, initialContext) {
                    @Override
                    protected BytecodeParser createBytecodeParser(StructuredGraph parsedGraph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
                        return new RistrettoParser(this, parsedGraph, parent, method, entryBCI, intrinsicContext) {
                            @Override
                            public MetaAccessProvider getMetaAccess() {
                                return providers.getMetaAccess();
                            }

                            @Override
                            protected boolean tryInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod invoked, JavaKind resultType) {
                                Assert.assertEquals("Unexpected call or ordinary inlining in generated probe", targetMethod, invoked);
                                Graph.Mark mark = parsedGraph.getMark();
                                int invokeBci = bci();
                                boolean applied = super.tryInvocationPlugin(invokeKind, args, invoked, resultType);
                                applications.add(new ParsedProbe(parsedGraph, parsedGraph.getNewNodes(mark).snapshot(), applied, invokeBci));
                                return applied;
                            }
                        };
                    }
                };
            }
        };
        phase.apply(graph, new HighTierContext(getProviders(), null, OptimisticOptimizations.NONE));
        Assert.assertEquals("The generated probe must attempt exactly one plugin", 1, applications.size());
        Assert.assertTrue(graph.verify());
        return applications.getFirst();
    }

    /// Checks an intrinsic representation without a per-method table of expected node class names.
    private static void assertRepresentation(ParsedProbe parsed) {
        Assert.assertTrue("The runtime invocation plugin did not apply", parsed.applied());
        for (MethodCallTargetNode invoke : parsed.graph().getNodes().filter(MethodCallTargetNode.class)) {
            Assert.fail("Residual invocation instead of intrinsic representation: " + invoke.targetMethod());
        }
        for (Node node : parsed.pluginNodes()) {
            if (!node.isAlive()) {
                continue;
            }
            if (node instanceof StateSplit split && split.hasSideEffect()) {
                Assert.assertTrue("Unsupported side-effecting state split: " + node,
                                node instanceof WithExceptionNode || node instanceof BytecodeExceptionNode || node instanceof ExceptionObjectNode);
                Assert.assertNotNull("Missing state after " + node, split.stateAfter());
            }
            if (node instanceof WithExceptionNode throwing) {
                Assert.assertNotNull("Missing normal successor: " + node, throwing.next());
                Assert.assertNotNull("Missing exception successor: " + node, throwing.exceptionEdge());
                assertCaught(throwing.exceptionEdge());
            } else if (node instanceof BytecodeExceptionNode exception) {
                Assert.assertNotNull(exception.next());
                assertCaught(exception.next());
            }
        }
        for (FrameState state : parsed.graph().getNodes(FrameState.TYPE)) {
            Assert.assertEquals(parsed.graph().method(), state.getMethod());
            Assert.assertTrue("Non-bytecode state in parsed caller: " + state, state.bci >= 0 && state.bci < state.getMethod().getCodeSize());
        }
        for (Node node : parsed.graph().getNodes()) {
            if (node instanceof ExceptionObjectNode exception) {
                assertExceptionState(parsed, exception.stateAfter(), exception);
            } else if (node instanceof BytecodeExceptionNode exception) {
                assertExceptionState(parsed, exception.stateAfter(), exception);
            }
        }
        Assert.assertTrue("Exception escaped the generated local handler", parsed.graph().getNodes(UnwindNode.TYPE).isEmpty());
    }

    /// Exception-entry deoptimization must dispatch from the protected invoke with this exception.
    private static void assertExceptionState(ParsedProbe parsed, FrameState state, ValueNode exception) {
        Assert.assertNotNull("Exception state is missing", state);
        Assert.assertEquals("Exception state is outside the protected invocation", parsed.invokeBci(), state.bci);
        Assert.assertTrue("Exception state must rethrow", state.rethrowException());
        Assert.assertTrue("Exception state must support deoptimization", state.isValidForDeoptimization());
        Assert.assertEquals("Exception state must contain one exception", 1, state.stackSize());
        Assert.assertSame("Exception state contains a different value", exception, state.stackAt(0));
    }

    /// Requires every emitted exception path to terminate at the generated catch's zero/null return.
    private static void assertCaught(Node first) {
        Set<Node> visited = new HashSet<>();
        Set<Node> caught = new HashSet<>();
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.add(first);
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (!visited.add(node)) {
                continue;
            }
            Assert.assertFalse("Exception edge unwinds past the local handler", node instanceof UnwindNode);
            if (node instanceof ReturnNode ret) {
                Assert.assertTrue("Exception edge does not return the generated catch result",
                                ret.result().isJavaConstant() && ret.result().asJavaConstant().isDefaultForKind());
                caught.add(node);
            }
            for (Node successor : node.cfgSuccessors()) {
                pending.add(successor);
            }
        }
        // A node is caught only when all its successors are caught. Other sinks and cycles cannot
        // enter this set, even if another branch reaches a valid catch return.
        int previousSize;
        do {
            previousSize = caught.size();
            for (Node node : visited) {
                if (caught.contains(node)) {
                    continue;
                }
                boolean hasSuccessor = false;
                boolean allCaught = true;
                for (Node successor : node.cfgSuccessors()) {
                    hasSuccessor = true;
                    allCaught &= caught.contains(successor);
                }
                if (hasSuccessor && allCaught) {
                    caught.add(node);
                }
            }
        } while (caught.size() != previousSize);
        Assert.assertTrue("Exception edge does not terminate at the generated catch result", caught.contains(first));
    }

    /// Requires a declined or disallowed plugin to preserve the ordinary invocation.
    private void assertFallback(ParsedProbe parsed, Method target) {
        Assert.assertFalse("Plugin unexpectedly applied", parsed.applied());
        ResolvedJavaMethod expected = getMetaAccess().lookupJavaMethod(target);
        Assert.assertTrue("Missing normal invoke fallback", parsed.graph().getNodes().filter(MethodCallTargetNode.class).stream()
                        .anyMatch(call -> call.targetMethod().equals(expected)));
    }
}
