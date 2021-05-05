/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public class TestProxyObjectNotReachable extends AbstractPolyglotTest {
    @ExportLibrary(InteropLibrary.class)
    static class ScopeObject implements TruffleObject {
        final Map<String, Object> members = Collections.synchronizedMap(new LinkedHashMap<>());

        ScopeObject() {
        }

        @ExportMessage
        boolean isScope() {
            return true;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return null;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean isMemberReadable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        Object readMember(@SuppressWarnings("unused") String key) {
            return null;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object writeMember(String key, Object value) {
            members.put(key, value);
            return value;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean isMemberModifiable(@SuppressWarnings("unused") String member) {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return LanguageWithScope.class;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return "global";
        }

    }

    static class LanguageWithScopeContext extends ProxyLanguage.LanguageContext {
        ScopeObject scope = new ScopeObject();

        LanguageWithScopeContext(TruffleLanguage.Env env) {
            super(env);
        }
    }

    static class LanguageWithScope extends ProxyLanguage {
        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageWithScopeContext(env);
        }

        @Override
        protected Object getScope(LanguageContext languageContext) {
            return ((LanguageWithScopeContext) languageContext).scope;
        }
    }

    @Test
    public void testRetainedSizeWithProxyObject() {
        Assume.assumeFalse(TruffleOptions.AOT);
        Assume.assumeFalse(Truffle.getRuntime() instanceof DefaultTruffleRuntime);
        setupEnv(Context.newBuilder(), new LanguageWithScope());
        context.getBindings(ProxyLanguage.ID).putMember("proxyObject", new ProxyObject() {
            @SuppressWarnings("unused") private final Context ctx = context;

            @Override
            public Object getMember(String key) {
                return null;
            }

            @Override
            public Object getMemberKeys() {
                return null;
            }

            @Override
            public boolean hasMember(String key) {
                return false;
            }

            @Override
            public void putMember(String key, Value value) {

            }
        });
        long retainedSize = instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), 16L * 1024L * 1024L, new AtomicBoolean(false));
        Assert.assertTrue(retainedSize > 0);
        Assert.assertTrue(retainedSize < 16L * 1024L * 1024L);
    }
}
