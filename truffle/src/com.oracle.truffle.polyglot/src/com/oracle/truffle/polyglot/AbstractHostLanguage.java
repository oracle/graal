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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Predicate;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

public abstract class AbstractHostLanguage<C> extends TruffleLanguage<C> {

    protected AbstractHostLanguage(AbstractPolyglotImpl polyglot) {
        Objects.requireNonNull(polyglot);
    }

    protected abstract void initializeHostContext(Object internalContext, Object context, HostAccess access, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed,
                    boolean hostLookupAllowed);

    protected abstract void addToHostClassPath(Object context, TruffleFile truffleFile);

    protected abstract Object toGuestValue(Object context, Object hostValue);

    protected abstract Object asHostDynamicClass(Object context, Class<?> value);

    protected abstract Object asHostStaticClass(Object context, Class<?> value);

    protected abstract Object findDynamicClass(Object context, String classValue);

    protected abstract Object findStaticClass(Object context, String classValue);

    protected abstract Node createToHostTypeNode();

    protected abstract <T> T toHostType(Node hostNode, Object hostContext, Object value, Class<T> targetType, Type genericType);

    protected abstract boolean isHostValue(Object value);

    protected abstract Object unboxHostObject(Object hostValue);

    protected abstract Object unboxProxyObject(Object hostValue);

    protected abstract Throwable unboxHostException(Throwable hostValue);

    protected abstract Object toHostObject(Object context, Object value);

    protected abstract RuntimeException toHostException(Object hostContext, Throwable exception);

    protected abstract boolean isHostException(Throwable exception);

    protected abstract boolean isHostFunction(Object obj);

    protected abstract boolean isHostObject(Object obj);

    protected abstract boolean isHostSymbol(Object obj);

    protected abstract Object createHostAdapter(Object hostContextObject, Class<?>[] types, Object classOverrides);

    protected abstract boolean isHostProxy(Object value);

    protected abstract Object migrateHostObject(Object newContext, Object value);

    protected abstract Object migrateHostProxy(Object newContext, Object value);

    protected abstract Error toHostResourceError(Throwable hostException);

    protected abstract int findNextGuestToHostStackTraceElement(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex);

}
