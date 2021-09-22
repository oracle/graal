/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

final class PolyglotHostAccess extends AbstractHostAccess {

    final AbstractPolyglotImpl polyglot;

    protected PolyglotHostAccess(AbstractPolyglotImpl polyglot) {
        super(polyglot);
        this.polyglot = polyglot;
    }

    @Override
    public Object toGuestValue(Object polyglotContext, Object hostValue) {
        PolyglotContextImpl internalContext = (PolyglotContextImpl) polyglotContext;
        return toGuestValue(internalContext, hostValue);
    }

    static Object toGuestValue(PolyglotContextImpl context, Object hostValue) {
        if (hostValue instanceof Value) {
            Value receiverValue = (Value) hostValue;
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) context.getAPIAccess().getContext(receiverValue);
            PolyglotContextImpl valueContext = languageContext != null ? languageContext.context : null;
            Object valueReceiver = context.getAPIAccess().getReceiver(receiverValue);
            if (valueContext != context) {
                valueReceiver = context.migrateValue(valueReceiver, valueContext);
            }
            return valueReceiver;
        } else if (PolyglotWrapper.isInstance(hostValue)) {
            return context.migrateHostWrapper(PolyglotWrapper.asInstance(hostValue));
        }
        return hostValue;
    }

    @Override
    public <T> List<T> toList(Object internalContext, Object guestValue, boolean implementFunction, Class<T> elementClass, Type elementType) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotList.<T> create(context.getHostContext(), guestValue, implementFunction, elementClass, elementType);
    }

    @Override
    public <K, V> Map<K, V> toMap(Object internalContext, Object foreignObject, boolean implementsFunction, Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotMap.create(context.getHostContext(), foreignObject, implementsFunction, keyClass, keyType, valueClass, valueType);
    }

    @Override
    public <K, V> Entry<K, V> toMapEntry(Object internalContext, Object foreignObject, boolean implementsFunction, Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotMapEntry.create(context.getHostContext(), foreignObject, implementsFunction, keyClass, keyType, valueClass, valueType);
    }

    @Override
    public <T> Function<?, ?> toFunction(Object internalContext, Object function, Class<?> returnClass, Type returnType, Class<?> paramClass, Type paramType) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotFunction.create(context.getHostContext(), function, returnClass, returnType, paramClass, paramType);
    }

    @Override
    public Object toObjectProxy(Object internalContext, Class<?> clazz, Object obj) throws IllegalArgumentException {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotObjectProxyHandler.newProxyInstance(clazz, obj, context.getHostContext());
    }

    @Override
    public <T> T toFunctionProxy(Object internalContext, Class<T> functionalType, Object function) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotFunctionProxyHandler.create(functionalType, function, context.getHostContext());
    }

    @Override
    public <T> Iterable<T> toIterable(Object internalContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotIterable.create(context.getHostContext(), iterable, implementFunction, elementClass, elementType);
    }

    @Override
    public <T> Iterator<T> toIterator(Object internalContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotIterator.create(context.getHostContext(), iterable, implementFunction, elementClass, elementType);
    }

    @Override
    public PolyglotException toPolyglotException(Object internalContext, Throwable e) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotImpl.guestToHostException(context.getHostContext(), e, true);
    }

    @Override
    public Value toValue(Object internalContext, Object receiver) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return context.getHostContext().asValue(receiver);
    }

    @Override
    public String getValueInfo(Object internalContext, Object value) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        return PolyglotValueDispatch.getValueInfo(context, value);
    }

    @TruffleBoundary
    @Override
    public Value[] toValues(Object internalContext, Object[] values, int startIndex) {
        return (((PolyglotContextImpl) internalContext).getHostContext()).toHostValues(values, startIndex);
    }

    @TruffleBoundary
    @Override
    public Value[] toValues(Object internalContext, Object[] values) {
        return (((PolyglotContextImpl) internalContext).getHostContext()).toHostValues(values);
    }

    @Override
    public boolean isEngineException(RuntimeException e) {
        return e instanceof PolyglotEngineException;
    }

    @Override
    public RuntimeException toEngineException(RuntimeException e) {
        return new PolyglotEngineException(e);
    }

    @Override
    public RuntimeException unboxEngineException(RuntimeException e) {
        return ((PolyglotEngineException) e).e;
    }

    @Override
    public void rethrowPolyglotException(Object internalContext, PolyglotException e) {
        PolyglotContextImpl context = (PolyglotContextImpl) internalContext;
        APIAccess api = polyglot.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = ((PolyglotExceptionImpl) api.getReceiver(e));
        if (exceptionImpl.context == context || exceptionImpl.context == null || exceptionImpl.isHostException()) {
            // for values of the same context the TruffleException is allowed to be unboxed
            // for host exceptions no guest values are bound therefore it can also be
            // unboxed
            Throwable original = ((PolyglotExceptionImpl) api.getReceiver(e)).exception;
            if (original instanceof RuntimeException) {
                throw (RuntimeException) original;
            } else if (original instanceof Error) {
                throw (Error) original;
            }
        }
        // fall-through and treat it as any other host exception

    }

}
