/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.function.Supplier;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.Accessor.CallInlined;
import com.oracle.truffle.api.impl.Accessor.CallProfiled;
import com.oracle.truffle.api.impl.Accessor.CastUnsafe;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class GraalTVMCI extends TVMCI {

    @Override
    public void onLoopCount(Node source, int count) {
        Node node = source;
        Node parentNode = source != null ? source.getParent() : null;
        while (node != null) {
            if (node instanceof OptimizedOSRLoopNode) {
                ((OptimizedOSRLoopNode) node).reportChildLoopCount(count);
            }
            parentNode = node;
            node = node.getParent();
        }
        if (parentNode != null && parentNode instanceof RootNode) {
            CallTarget target = ((RootNode) parentNode).getCallTarget();
            if (target instanceof OptimizedCallTarget) {
                ((OptimizedCallTarget) target).onLoopCount(count);
            }
        }
    }

    @Override
    protected boolean isGuestCallStackFrame(StackTraceElement e) {
        return e.getMethodName().equals(OptimizedCallTarget.CALL_BOUNDARY_METHOD_NAME) && e.getClassName().equals(OptimizedCallTarget.class.getName());
    }

    /**
     * Initializes the argument profile with a custom profile without calling it. A call target must
     * never be called prior initialization of argument types. Also the argument types must be final
     * if used in combination with
     * {@link OptimizedCallTarget.OptimizedCallProfiled#call(CallTarget, Object...)}.
     */
    @Override
    protected void initializeProfile(CallTarget target, Class<?>[] argumentTypes) {
        ((OptimizedCallTarget) target).getCompilationProfile().initializeArgumentTypes(argumentTypes);
    }

    @Override
    protected OptionDescriptors getCompilerOptionDescriptors() {
        return PolyglotCompilerOptions.getDescriptors();
    }

    @Override
    protected OptionValues getCompilerOptionValues(RootNode rootNode) {
        return super.getCompilerOptionValues(rootNode);
    }

    void onFirstExecution(OptimizedCallTarget callTarget) {
        super.onFirstExecution(callTarget.getRootNode());
    }

    @Override
    protected void onLoad(RootNode rootNode) {
        super.onLoad(rootNode);
    }

    @Override
    protected void setCallTarget(RootNode root, RootCallTarget callTarget) {
        super.setCallTarget(root, callTarget);
    }

    @Override
    protected void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        super.markFrameMaterializeCalled(descriptor);
    }

    @Override
    protected void onThrowable(Node callNode, RootCallTarget root, Throwable e, Frame frame) {
        super.onThrowable(callNode, root, e, frame);
    }

    @Override
    protected boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        return super.getFrameMaterializeCalled(descriptor);
    }

    @Override
    public RootNode cloneUninitialized(RootNode root) {
        return super.cloneUninitialized(root);
    }

    @Override
    protected int adoptChildrenAndCount(RootNode root) {
        return super.adoptChildrenAndCount(root);
    }

    @Override
    public boolean isCloneUninitializedSupported(RootNode root) {
        return super.isCloneUninitializedSupported(root);
    }

    @Override
    protected IndirectCallNode createUncachedIndirectCall() {
        return OptimizedIndirectCallNode.createUncached();
    }

    @Override
    protected <T> T getOrCreateRuntimeData(RootNode rootNode, Supplier<T> constructor) {
        return super.getOrCreateRuntimeData(rootNode, constructor);
    }

    EngineData getEngineData(RootNode rootNode) {
        return getOrCreateRuntimeData(rootNode, EngineData.ENGINE_DATA_SUPPLIER);
    }

    @Override
    protected void reportPolymorphicSpecialize(Node source) {
        final RootNode rootNode = source.getRootNode();
        final OptimizedCallTarget callTarget = rootNode == null ? null : (OptimizedCallTarget) rootNode.getCallTarget();
        if (callTarget == null || callTarget.engineData.options.isLegacySplitting()) {
            return;
        }
        TruffleSplittingStrategy.newPolymorphicSpecialize(source, callTarget.engineData);
        callTarget.polymorphicSpecialize(source);
    }

    @Override
    protected CallInlined getCallInlined() {
        return new OptimizedCallTarget.OptimizedCallInlined();
    }

    @Override
    protected CallProfiled getCallProfiled() {
        return new OptimizedCallTarget.OptimizedCallProfiled();
    }

    @Override
    protected CastUnsafe getCastUnsafe() {
        return CAST_UNSAFE;
    }

    private static final GraalCastUnsafe CAST_UNSAFE = new GraalCastUnsafe();

    private static final class GraalCastUnsafe extends CastUnsafe {
        @Override
        public Object[] castArrayFixedLength(Object[] args, int length) {
            return OptimizedCallTarget.castArrayFixedLength(args, length);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
            return OptimizedCallTarget.unsafeCast(value, type, condition, nonNull, exact);
        }
    }

}
