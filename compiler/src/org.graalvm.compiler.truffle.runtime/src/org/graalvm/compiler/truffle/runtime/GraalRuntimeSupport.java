/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.impl.Accessor.RuntimeSupport;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.services.Services;

final class GraalRuntimeSupport extends RuntimeSupport {

    GraalRuntimeSupport(Object permission) {
        super(permission);
    }

    @Override
    public IndirectCallNode createUncachedIndirectCall() {
        return OptimizedIndirectCallNode.createUncached();
    }

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
    public OptionDescriptors getCompilerOptionDescriptors() {
        return PolyglotCompilerOptions.getDescriptors();
    }

    @Override
    public boolean isGuestCallStackFrame(StackTraceElement e) {
        return (e.getMethodName().equals(OptimizedCallTarget.EXECUTE_ROOT_NODE_METHOD_NAME) && e.getClassName().equals(OptimizedCallTarget.class.getName())) ||
                        (e.getMethodName().equals(CALL_INLINED_METHOD_NAME) &&
                                        e.getClassName().equals(GraalRuntimeSupport.class.getName()));

    }

    /**
     * Initializes the argument profile with a custom profile without calling it. A call target must
     * never be called prior initialization of argument types. Also the argument types must be final
     * if used in combination with {@link #callProfiled(CallTarget, Object...)}.
     */
    @Override
    public void initializeProfile(CallTarget target, Class<?>[] argumentTypes) {
        ((OptimizedCallTarget) target).initializeArgumentTypes(argumentTypes);
    }

    @Override
    public <T extends Node> BlockNode<T> createBlockNode(T[] elements, ElementExecutor<T> executor) {
        return new OptimizedBlockNode<>(elements, executor);
    }

    @Override
    public void reloadEngineOptions(Object runtimeData, OptionValues optionValues) {
        ((EngineData) runtimeData).loadOptions(optionValues);
    }

    @Override
    public void onEngineClosed(Object runtimeData) {
        GraalTruffleRuntime.getRuntime().onEngineClosed((EngineData) runtimeData);
    }

    @Override
    public OutputStream getConfiguredLogStream() {
        return TruffleDebugOptions.getConfiguredLogStream();
    }

    @Override
    public String getSavedProperty(String key) {
        return Services.getSavedProperties().get(key);
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
            GraalRuntimeAccessor.LANGUAGE.onThrowable(callNode, optimizedCallTarget, t, null);
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

}
