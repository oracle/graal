/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertNotNull;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;

import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
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
            return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), obj, "class");
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    protected TruffleObject asTruffleHostSymbol(Class<?> clazz) {
        return (TruffleObject) env.lookupHostSymbol(clazz.getTypeName());
    }

    protected <T> T asJavaObject(Class<T> type, TruffleObject truffleObject) {
        return context.asValue(truffleObject).as(type);
    }
}
