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
package com.oracle.truffle.api.staticobject.test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class StaticObjectModelTest {
    static final boolean ARRAY_BASED_STORAGE = TruffleOptions.AOT || Boolean.getBoolean("com.oracle.truffle.api.staticobject.ArrayBasedStorage");

    TruffleLanguage<?> testLanguage;
    Context context;

    @Before
    public void setup() {
        context = Context.newBuilder(SomTestLanguage.TEST_LANGUAGE_ID).build();
        context.initialize(SomTestLanguage.TEST_LANGUAGE_ID);
        context.enter();
        testLanguage = SomTestLanguage.getCurrentContext().getLanguage();
    }

    @After
    public void teardown() {
        context.leave();
        context.close();
    }

    String guessGeneratedFieldName(StaticProperty property) {
        assert !ARRAY_BASED_STORAGE;
        // The format of generated field names with the field-based storage might change at any
        // time. Do not depend on it!
        if (property instanceof DefaultStaticProperty) {
            return ((DefaultStaticProperty) property).getId();
        } else {
            try {
                Method getId = StaticProperty.class.getDeclaredMethod("getId");
                getId.setAccessible(true);
                return (String) getId.invoke(property);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Registration(id = SomTestLanguage.TEST_LANGUAGE_ID, name = SomTestLanguage.TEST_LANGUAGE_NAME)
    public static class SomTestLanguage extends TruffleLanguage<SomTestContext> {
        static final String TEST_LANGUAGE_NAME = "Test Language for the Static Object Model";
        static final String TEST_LANGUAGE_ID = "som-test";

        @Override
        protected SomTestContext createContext(Env env) {
            return new SomTestContext(this);
        }

        static SomTestContext getCurrentContext() {
            return getCurrentContext(SomTestLanguage.class);
        }
    }

    static class SomTestContext {
        private final SomTestLanguage language;

        SomTestContext(SomTestLanguage language) {
            this.language = language;
        }

        SomTestLanguage getLanguage() {
            return language;
        }
    }
}
