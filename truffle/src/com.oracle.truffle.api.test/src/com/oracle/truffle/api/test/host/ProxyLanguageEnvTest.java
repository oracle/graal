/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;

import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public abstract class ProxyLanguageEnvTest {
    protected Context context;
    protected Env env;

    @Before
    public void before() {
        context = Context.newBuilder().allowAllAccess(true).build();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(Env contextEnv) {
                env = contextEnv;
                return super.createContext(contextEnv);
            }
        });
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertNotNull(env);
    }

    @After
    public void after() {
        context.leave();
        context.close();
    }

    void assertThrowsExceptionWithCause(Callable<?> callable, Class<? extends Exception> exception) {
        try {
            callable.call();
            fail("Expected " + exception.getSimpleName() + " but no exception was thrown");
        } catch (Exception e) {
            List<Class<? extends Throwable>> causes = new ArrayList<>();
            Throwable cause = e;
            while (cause != null) {
                if (cause.getClass() == exception) {
                    return;
                }
                causes.add(cause.getClass());
                if (env.isHostException(cause)) {
                    cause = env.asHostException(cause);
                } else {
                    cause = cause.getCause();
                }
            }
            fail("Expected " + exception.getSimpleName() + ", got " + causes);
        }
    }

    protected TruffleObject asTruffleObject(Object javaObj) {
        Object value = env.asGuestValue(javaObj);
        if (value instanceof TruffleObject) {
            return (TruffleObject) value;
        } else {
            return (TruffleObject) env.asBoxedGuestValue(javaObj);
        }
    }

    TruffleObject toJavaClass(TruffleObject obj) {
        try {
            return (TruffleObject) InteropLibrary.getFactory().getUncached().readMember(obj, "class");
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    protected TruffleObject asTruffleHostSymbol(Class<?> clazz) {
        return (TruffleObject) env.lookupHostSymbol(clazz.getTypeName());
    }

    protected <T> T asJavaObject(Class<T> type, Object truffleObject) {
        return context.asValue(truffleObject).as(type);
    }
}
