/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

final class DefaultRuntimeAccessor extends Accessor {

    private static final DefaultRuntimeAccessor ACCESSOR = new DefaultRuntimeAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final JDKSupport JDK = ACCESSOR.jdkSupport();
    static final EngineSupport ENGINE = ACCESSOR.engineSupport();
    static final InteropSupport INTEROP = ACCESSOR.interopSupport();

    private DefaultRuntimeAccessor() {
    }

    static final class DefaultRuntimeSupport extends RuntimeSupport {

        DefaultRuntimeSupport(Object permission) {
            super(permission);
        }

        @Override
        public IndirectCallNode createUncachedIndirectCall() {
            return DefaultIndirectCallNode.createUncached();
        }

        @Override
        public void onLoopCount(Node source, int iterations) {
            // do nothing
        }

        @Override
        public OptionDescriptors getCompilerOptionDescriptors() {
            return OptionDescriptors.EMPTY;
        }

        @Override
        public boolean isGuestCallStackFrame(StackTraceElement e) {
            String methodName = e.getMethodName();
            return (methodName.startsWith(DefaultCallTarget.CALL_BOUNDARY_METHOD_PREFIX)) && e.getClassName().equals(DefaultCallTarget.class.getName());
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
        public void reloadEngineOptions(Object runtimeData, OptionValues optionValues) {
        }

        @Override
        public void onEngineClosed(Object runtimeData) {
        }

        @Override
        public OutputStream getConfiguredLogStream() {
            return null;
        }

        @Override
        public String getSavedProperty(String key) {
            return System.getProperty(key);
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

        @SuppressWarnings("unchecked")
        @Override
        public <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
            return (T) value;
        }

        @Override
        public void reportPolymorphicSpecialize(Node source) {
        }

    }

}
