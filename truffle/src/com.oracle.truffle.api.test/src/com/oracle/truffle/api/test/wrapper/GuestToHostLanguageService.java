/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

import java.lang.reflect.Type;
import java.util.function.Predicate;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;

public class GuestToHostLanguageService extends AbstractHostLanguageService {

    protected GuestToHostLanguageService(AbstractPolyglotImpl polyglot) {
        super(polyglot);
    }

    @Override
    public void release() {
    }

    @Override
    public void initializeHostContext(Object internalContext, Object context, HostAccess access, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        // we can just ignore the configuration of the host language
    }

    @Override
    public void throwHostLanguageException(String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addToHostClassPath(Object context, Object truffleFile) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object toGuestValue(Object context, Object hostValue, boolean asValue) {

        return hostValue;
    }

    @Override
    public Object asHostDynamicClass(Object context, Class<?> value) {
        // this should be supported by serializing the class name and loading it on the host
        throw new UnsupportedOperationException();
    }

    @Override
    public Object asHostStaticClass(Object context, Class<?> value) {
        // this should be supported by serializing the class name and loading it on the host
        throw new UnsupportedOperationException();
    }

    @Override
    public Object findDynamicClass(Object context, String classValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object findStaticClass(Object context, String classValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object createToHostTypeNode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T toHostType(Object hostNode, Object hostContext, Object value, Class<T> targetType, Type genericType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isHostValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object unboxHostObject(Object hostValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object unboxProxyObject(Object hostValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Throwable unboxHostException(Throwable hostValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object toHostObject(Object context, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuntimeException toHostException(Object hostContext, Throwable exception) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isHostException(Object exception) {
        return false;
    }

    @Override
    public boolean isHostFunction(Object obj) {
        return false;
    }

    @Override
    public boolean isHostObject(Object obj) {
        return false;
    }

    @Override
    public boolean isHostSymbol(Object obj) {
        return false;
    }

    @Override
    public Object createHostAdapter(Object hostContextObject, Object[] types, Object classOverrides) {
        return null;
    }

    @Override
    public boolean isHostProxy(Object value) {
        return false;
    }

    @Override
    public Object migrateValue(Object hostContext, Object value, Object valueContext) {
        return null;
    }

    @Override
    public Error toHostResourceError(Throwable hostException) {
        return null;
    }

    @Override
    public int findNextGuestToHostStackTraceElement(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
        return -1;
    }

    @Override
    public void pin(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void hostExit(int exitCode) {
        System.exit(exitCode);
    }
}
