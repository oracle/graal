/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;

final class HostAccessor extends Accessor {

    static final HostAccessor ACCESSOR = new HostAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final JDKSupport JDKSERVICES = ACCESSOR.jdkSupport();
    static final InteropSupport INTEROP = ACCESSOR.interopSupport();
    static final ExceptionSupport EXCEPTION = ACCESSOR.exceptionSupport();
    static final RuntimeSupport RUNTIME = ACCESSOR.runtimeSupport();
    static final EngineSupport ENGINE = ACCESSOR.engineSupport();

    static final class HostImpl extends HostSupport {

        private HostImpl() {
        }

        @Override
        public TruffleLanguage<?> createDefaultHostLanguage(AbstractPolyglotImpl polyglot, AbstractHostAccess access) {
            return new HostLanguage(polyglot, access);
        }

        @Override
        public boolean isHostBoundaryValue(Object obj) {
            return (obj instanceof HostObject) ||
                            (obj instanceof HostFunction) ||
                            (obj instanceof HostException) ||
                            (obj instanceof HostContext) ||
                            (obj instanceof HostProxy);
        }

        @Override
        public Object convertPrimitiveLossLess(Object value, Class<?> requestedType) {
            return HostUtil.convertLossLess(value, requestedType, InteropLibrary.getFactory().getUncached(value));
        }

        @Override
        public Object convertPrimitiveLossy(Object value, Class<?> requestedType) {
            InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
            Object result = HostUtil.convertLossLess(value, requestedType, interop);
            if (result == null) {
                result = HostUtil.convertLossy(value, requestedType, interop);
            }
            return result;
        }

        @Override
        public boolean isDisconnectedHostProxy(Object value) {
            return HostProxy.isProxyGuestObject(null, value);
        }

        @Override
        public boolean isDisconnectedHostObject(Object obj) {
            return HostObject.isInstance(null, obj);
        }

        @Override
        public Object unboxDisconnectedHostObject(Object hostValue) {
            return HostObject.valueOf(null, hostValue);
        }

        @Override
        public Object unboxDisconnectedHostProxy(Object hostValue) {
            return HostProxy.toProxyHostObject(null, hostValue);
        }

        @Override
        public Object toDisconnectedHostObject(Object hostValue) {
            if (hostValue instanceof Class) {
                return HostObject.forClass((Class<?>) hostValue, null);
            } else {
                return HostObject.forObject(hostValue, null);
            }
        }

        @Override
        public Object toDisconnectedHostProxy(Proxy hostValue) {
            return HostProxy.toProxyGuestObject(null, hostValue);
        }

        @Override
        public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue, TargetMappingPrecedence precedence) {
            return new HostTargetMapping(sourceType, targetType, acceptsValue, convertValue, precedence);
        }

        @Override
        public Object getHostNull() {
            return HostObject.NULL;
        }

        @Override
        public boolean isPrimitiveTarget(Class<?> c) {
            return HostToTypeNode.isPrimitiveTarget(c);
        }

        @Override
        public boolean isGuestToHostRootNode(RootNode root) {
            return root instanceof GuestToHostRootNode;
        }

        @Override
        public boolean isHostLanguage(Class<?> languageClass) {
            return languageClass == HostLanguage.class;
        }
    }

}
