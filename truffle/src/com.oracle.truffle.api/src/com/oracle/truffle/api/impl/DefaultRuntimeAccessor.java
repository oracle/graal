/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class DefaultRuntimeAccessor extends Accessor {

    private static final DefaultRuntimeAccessor ACCESSOR = new DefaultRuntimeAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final EngineSupport ENGINE = ACCESSOR.engineSupport();
    static final InteropSupport INTEROP = ACCESSOR.interopSupport();
    static final FrameSupport FRAME = ACCESSOR.framesSupport();

    private DefaultRuntimeAccessor() {
    }

    static final class DefaultRuntimeSupport extends RuntimeSupport {

        DefaultRuntimeSupport(Object permission) {
            super(permission);
        }

        @Override
        public RootCallTarget newCallTarget(CallTarget sourceCallTarget, RootNode rootNode) {
            return new DefaultCallTarget(rootNode);
        }

        @Override
        public long getCallTargetId(CallTarget target) {
            if (target instanceof DefaultCallTarget) {
                return ((DefaultCallTarget) target).id;
            } else {
                return 0;
            }
        }

        @Override
        public boolean isLegacyCompilerOption(String key) {
            return false;
        }

        @Override
        public boolean isLoaded(CallTarget callTarget) {
            return ((DefaultCallTarget) callTarget).isLoaded();
        }

        @Override
        public void notifyOnLoad(CallTarget callTarget) {
            DefaultCallTarget target = (DefaultCallTarget) callTarget;
            DefaultRuntimeAccessor.INSTRUMENT.onLoad(target.getRootNode());
            target.setLoaded();
        }

        @Override
        public ThreadLocalHandshake getThreadLocalHandshake() {
            return DefaultThreadLocalHandshake.SINGLETON;
        }

        @Override
        public void onLoopCount(Node source, int iterations) {
            // do nothing
        }

        @Override
        public boolean pollBytecodeOSRBackEdge(BytecodeOSRNode osrNode) {
            return false;
        }

        @Override
        public Object tryBytecodeOSR(BytecodeOSRNode osrNode, int target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame) {
            return null;
        }

        @Override
        public void onOSRNodeReplaced(BytecodeOSRNode osrNode, Node oldNode, Node newNode, CharSequence reason) {
            // do nothing
        }

        @Override
        // Support for deprecated frame transfer: GR-38296
        public void transferOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target, int bytecodeTarget) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void transferOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target, int bytecodeTarget, Object targetMetadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void restoreOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OptionDescriptors getRuntimeOptionDescriptors() {
            return OptionDescriptors.EMPTY;
        }

        @Override
        public boolean isGuestCallStackFrame(StackTraceElement e) {
            String methodName = e.getMethodName();
            return (methodName.equals(DefaultCallTarget.CALL_BOUNDARY_METHOD)) && e.getClassName().equals(DefaultCallTarget.class.getName());
        }

        @Override
        public void initializeProfile(CallTarget target, Class<?>[] argumentTypes) {
            // nothing to do
        }

        @Override
        public <T extends Node> BlockNode<T> createBlockNode(T[] elements, ElementExecutor<T> executor) {
            return new DefaultBlockNode<>(elements, executor);
        }

        @Override
        public Assumption createAlwaysValidAssumption() {
            return DefaultAssumption.createAlwaysValid();
        }

        @Override
        public void onEngineClosed(Object runtimeData) {

        }

        @Override
        public Object callInlined(Node callNode, CallTarget target, Object... arguments) {
            return ((DefaultCallTarget) target).callDirectOrIndirect(callNode, arguments);
        }

        @Override
        public Object callProfiled(CallTarget target, Object... arguments) {
            return ((DefaultCallTarget) target).call(arguments);
        }

        @Override
        public Object[] castArrayFixedLength(Object[] args, int length) {
            return args;
        }

        @Override
        public void flushCompileQueue(Object runtimeData) {
            // default runtime has no compile queue.
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
            return (T) value;
        }

        @Override
        public void reportPolymorphicSpecialize(Node source) {
        }

        @Override
        public Object createRuntimeData(Object engine, OptionValues engineOptions, Function<String, TruffleLogger> loggerFactory, SandboxPolicy sandboxPolicy) {
            return null;
        }

        @Override
        public Object tryLoadCachedEngine(OptionValues runtimeData, Function<String, TruffleLogger> loggerFactory) {
            return null;
        }

        @Override
        public void onEngineCreate(Object engine, Object runtimeData) {

        }

        @Override
        public boolean isStoreEnabled(OptionValues options) {
            return false;
        }

        @Override
        public void onEnginePatch(Object runtimeData, OptionValues runtimeOptions, Function<String, TruffleLogger> loggerFactory, SandboxPolicy sandboxPolicy) {

        }

        @Override
        public boolean onEngineClosing(Object runtimeData) {
            return false;
        }

        @Override
        public boolean isOSRRootNode(RootNode rootNode) {
            return false;
        }

        @Override
        public int getObjectAlignment() {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unused")
        @Override
        public int getArrayBaseOffset(Class<?> componentType) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unused")
        @Override
        public int getArrayIndexScale(Class<?> componentType) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unused")
        @Override
        public int getBaseInstanceSize(Class<?> type) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unused")
        @Override
        public int[] getFieldOffsets(Class<?> type, boolean includePrimitive, boolean includeSuperclasses) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AbstractFastThreadLocal getContextThreadLocal() {
            return DefaultContextThreadLocal.SINGLETON;
        }

        @Override
        public <T> ThreadLocal<T> createTerminatingThreadLocal(Supplier<T> initialValue, Consumer<T> onThreadTermination) {
            return ThreadLocal.withInitial(initialValue);
        }
    }

}
