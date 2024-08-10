/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.AbstractFastThreadLocal;
import com.oracle.truffle.api.impl.Accessor.RuntimeSupport;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime.CompilerOptionsDescriptors;

final class OptimizedRuntimeSupport extends RuntimeSupport {

    OptimizedRuntimeSupport(Object permission) {
        super(permission);
    }

    @Override
    public RootCallTarget newCallTarget(CallTarget source, RootNode rootNode) {
        assert OptimizedRuntimeAccessor.NODES.getCallTargetWithoutInitialization(rootNode) == null : "CallTarget for root node already initialized.";

        CompilerAsserts.neverPartOfCompilation();
        return OptimizedTruffleRuntime.getRuntime().createOptimizedCallTarget((OptimizedCallTarget) source, rootNode);
    }

    @Override
    public long getCallTargetId(CallTarget target) {
        if (target instanceof OptimizedCallTarget) {
            return ((OptimizedCallTarget) target).id;
        } else {
            return 0;
        }
    }

    @Override
    public boolean isLoaded(CallTarget callTarget) {
        return ((OptimizedCallTarget) callTarget).isLoaded();
    }

    @Override
    public void notifyOnLoad(CallTarget callTarget) {
        CompilerAsserts.neverPartOfCompilation();
        OptimizedCallTarget target = (OptimizedCallTarget) callTarget;
        OptimizedRuntimeAccessor.INSTRUMENT.onLoad(target.getRootNode());
        if (target.engine.compileAOTOnCreate) {
            if (target.prepareForAOT()) {
                target.compile(true);
            }
        }
        TruffleSplittingStrategy.newTargetCreated(target);
        target.setLoaded();
    }

    @ExplodeLoop
    @Override
    public void onLoopCount(Node source, int count) {
        CompilerAsserts.partialEvaluationConstant(source);

        Node node = source;
        Node parentNode = source != null ? source.getParent() : null;
        while (node != null) {
            if (node instanceof OptimizedOSRLoopNode) {
                ((OptimizedOSRLoopNode) node).reportChildLoopCount(count);
            }
            parentNode = node;
            node = node.getParent();
        }
        if (parentNode instanceof RootNode) {
            CallTarget target = ((RootNode) parentNode).getCallTarget();
            if (target instanceof OptimizedCallTarget) {
                ((OptimizedCallTarget) target).onLoopCount(count);
            }
        }
    }

    @Override
    public boolean pollBytecodeOSRBackEdge(BytecodeOSRNode osrNode) {
        CompilerAsserts.neverPartOfCompilation();
        TruffleSafepoint.poll((Node) osrNode);
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) osrNode.getOSRMetadata();
        if (osrMetadata == null) {
            osrMetadata = initializeBytecodeOSRMetadata(osrNode);
        }

        // metadata can be disabled during initialization (above) or dynamically after
        // failed compilation.
        if (osrMetadata.isDisabled()) {
            return false;
        } else {
            return osrMetadata.incrementAndPoll();
        }
    }

    private static BytecodeOSRMetadata initializeBytecodeOSRMetadata(BytecodeOSRNode osrNode) {
        Node node = (Node) osrNode;
        return node.atomic(() -> { // double checked locking
            BytecodeOSRMetadata metadata = (BytecodeOSRMetadata) osrNode.getOSRMetadata();
            if (metadata == null) {
                OptimizedCallTarget callTarget = (OptimizedCallTarget) node.getRootNode().getCallTarget();
                if (callTarget.engine.compilation && callTarget.getOptionValue(OptimizedRuntimeOptions.OSR)) {
                    metadata = new BytecodeOSRMetadata(osrNode,
                                    callTarget.getOptionValue(OptimizedRuntimeOptions.OSRCompilationThreshold),
                                    callTarget.getOptionValue(OptimizedRuntimeOptions.OSRMaxCompilationReAttempts));
                } else {
                    metadata = BytecodeOSRMetadata.DISABLED;
                }
                osrNode.setOSRMetadata(metadata);
            }
            return metadata;
        });
    }

    @Override
    public Object tryBytecodeOSR(BytecodeOSRNode osrNode, int target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame) {
        CompilerAsserts.neverPartOfCompilation();
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) osrNode.getOSRMetadata();
        return osrMetadata.tryOSR(target, interpreterState, beforeTransfer, parentFrame);
    }

    @Override
    public void onOSRNodeReplaced(BytecodeOSRNode osrNode, Node oldNode, Node newNode, CharSequence reason) {
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) osrNode.getOSRMetadata();
        if (osrMetadata != null) {
            osrMetadata.nodeReplaced(oldNode, newNode, reason);
        }
    }

    // Support for deprecated frame transfer: GR-38296
    @Override
    public void transferOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target, int bytecodeTarget) {
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) osrNode.getOSRMetadata();
        BytecodeOSRMetadata.OsrEntryDescription targetMetadata = osrMetadata.getLazyState().get(bytecodeTarget);
        osrMetadata.transferFrame((FrameWithoutBoxing) source, (FrameWithoutBoxing) target, bytecodeTarget, targetMetadata);
    }

    @Override
    public void transferOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target, int bytecodeTarget, Object targetMetadata) {
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) osrNode.getOSRMetadata();
        osrMetadata.transferFrame((FrameWithoutBoxing) source, (FrameWithoutBoxing) target, bytecodeTarget, targetMetadata);
    }

    @Override
    public void restoreOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target) {
        BytecodeOSRMetadata osrMetadata = (BytecodeOSRMetadata) osrNode.getOSRMetadata();
        osrMetadata.restoreFrame((FrameWithoutBoxing) source, (FrameWithoutBoxing) target);
    }

    @Override
    public ThreadLocalHandshake getThreadLocalHandshake() {
        return OptimizedTruffleRuntime.getRuntime().getThreadLocalHandshake();
    }

    @Override
    public OptionDescriptors getRuntimeOptionDescriptors() {
        return OptimizedTruffleRuntime.getRuntime().getOptionDescriptors();
    }

    @Override
    public boolean isGuestCallStackFrame(StackTraceElement e) {
        return e.getMethodName().equals(OptimizedCallTarget.EXECUTE_ROOT_NODE_METHOD_NAME) && e.getClassName().equals(OptimizedCallTarget.class.getName());

    }

    /**
     * Initializes the argument profile with a custom profile without calling it. A call target must
     * never be called prior initialization of argument types. Also the argument types must be final
     * if used in combination with {@link #callProfiled(CallTarget, Object...)}.
     */
    @Override
    public void initializeProfile(CallTarget target, Class<?>[] argumentTypes) {
        ((OptimizedCallTarget) target).initializeUnsafeArgumentTypes(argumentTypes);
    }

    @Override
    public <T extends Node> BlockNode<T> createBlockNode(T[] elements, ElementExecutor<T> executor) {
        return new OptimizedBlockNode<>(elements, executor);
    }

    @Override
    public Assumption createAlwaysValidAssumption() {
        return OptimizedAssumption.createAlwaysValid();
    }

    @Override
    public void reportPolymorphicSpecialize(Node source) {
        final RootNode rootNode = source.getRootNode();
        final OptimizedCallTarget callTarget = rootNode == null ? null : (OptimizedCallTarget) rootNode.getCallTarget();
        if (callTarget == null) {
            return;
        }
        TruffleSplittingStrategy.newPolymorphicSpecialize(source, callTarget.engine);
        callTarget.polymorphicSpecialize(source);
    }

    static final String CALL_INLINED_METHOD_NAME = "callInlined";

    @Override
    public Object callInlined(Node callNode, CallTarget target, Object... arguments) {
        final OptimizedCallTarget optimizedCallTarget = (OptimizedCallTarget) target;
        try {
            return optimizedCallTarget.callInlined(callNode, arguments);
        } catch (Throwable t) {
            OptimizedRuntimeAccessor.LANGUAGE.addStackFrameInfo(callNode, null, t, null);
            throw OptimizedCallTarget.rethrow(t);
        }
    }

    /**
     * Call without verifying the argument profile. Needs to be initialized by
     * {@link #initializeProfile(CallTarget, Class[])}. Potentially crashes the VM if the argument
     * profile is incompatible with the actual arguments. Use with caution.
     */
    @Override
    public Object callProfiled(CallTarget target, Object... arguments) {
        OptimizedCallTarget castTarget = (OptimizedCallTarget) target;
        assert castTarget.isValidArgumentProfile(arguments) : "Invalid argument profile. callProfiled requires to explicity initialize the profile.";
        return castTarget.doInvoke(arguments);
    }

    @Override
    public Object[] castArrayFixedLength(Object[] args, int length) {
        return OptimizedCallTarget.castArrayFixedLength(args, length);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
        return OptimizedCallTarget.unsafeCast(value, type, condition, nonNull, exact);
    }

    @Override
    public void flushCompileQueue(Object runtimeData) {
        EngineData engine = (EngineData) runtimeData;
        BackgroundCompileQueue queue = OptimizedTruffleRuntime.getRuntime().getCompileQueue();
        // compile queue might be null if no call target was yet created
        if (queue != null) {
            for (OptimizedCallTarget target : queue.getQueuedTargets(engine)) {
                target.cancelCompilation("Polyglot engine was closed.");
            }
        }
    }

    @Override
    public Object tryLoadCachedEngine(OptionValues options, Function<String, TruffleLogger> loggerFactory) {
        return OptimizedTruffleRuntime.getRuntime().getEngineCacheSupport().tryLoadingCachedEngine(options, loggerFactory);
    }

    @Override
    public boolean isStoreEnabled(OptionValues options) {
        return EngineCacheSupport.get().isStoreEnabled(options);
    }

    @Override
    public Object createRuntimeData(Object engine, OptionValues engineOptions, Function<String, TruffleLogger> loggerFactory, SandboxPolicy sandboxPolicy) {
        return new EngineData(engine, engineOptions, loggerFactory, sandboxPolicy);
    }

    @Override
    public void onEngineCreate(Object engine, Object runtimeData) {
        ((EngineData) runtimeData).onEngineCreated(engine);
    }

    @Override
    public void onEnginePatch(Object runtimeData, OptionValues runtimeOptions, Function<String, TruffleLogger> loggerFactory, SandboxPolicy sandboxPolicy) {
        ((EngineData) runtimeData).onEnginePatch(runtimeOptions, loggerFactory, sandboxPolicy);
    }

    @Override
    public boolean onEngineClosing(Object runtimeData) {
        return ((EngineData) runtimeData).onEngineClosing();
    }

    @Override
    public void onEngineClosed(Object runtimeData) {
        ((EngineData) runtimeData).onEngineClosed();
    }

    @Override
    public boolean isOSRRootNode(RootNode rootNode) {
        return rootNode instanceof BaseOSRRootNode;
    }

    @Override
    public int getObjectAlignment() {
        return OptimizedTruffleRuntime.getRuntime().getObjectAlignment();
    }

    @Override
    public int getArrayBaseOffset(Class<?> componentType) {
        return OptimizedTruffleRuntime.getRuntime().getArrayBaseOffset(componentType);
    }

    @Override
    public int getArrayIndexScale(Class<?> componentType) {
        return OptimizedTruffleRuntime.getRuntime().getArrayIndexScale(componentType);
    }

    @Override
    public int getBaseInstanceSize(Class<?> type) {
        return OptimizedTruffleRuntime.getRuntime().getBaseInstanceSize(type);
    }

    @Override
    public boolean isLegacyCompilerOption(String key) {
        return CompilerOptionsDescriptors.isLegacyOption(key);
    }

    @Override
    public int[] getFieldOffsets(Class<?> type, boolean includePrimitive, boolean includeSuperclasses) {
        return OptimizedTruffleRuntime.getRuntime().getFieldOffsets(type, includePrimitive, includeSuperclasses);
    }

    @Override
    public AbstractFastThreadLocal getContextThreadLocal() {
        AbstractFastThreadLocal local = OptimizedTruffleRuntime.getRuntime().getFastThreadLocalImpl();
        if (local == null) {
            return super.getContextThreadLocal();
        }
        return local;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ThreadLocal<T> createTerminatingThreadLocal(Supplier<T> initialValue, Consumer<T> onThreadTermination) {
        return OptimizedTruffleRuntime.createTerminatingThreadLocal(initialValue, onThreadTermination);
    }
}
