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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.graalvm.polyglot.Context;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;

class StaticObjectModelTest {
    @Registration(id = TestLanguage.TEST_LANGUAGE_ID, name = TestLanguage.TEST_LANGUAGE_ID)
    public static class TestLanguage extends TruffleLanguage<TestContext> {
        static final String TEST_LANGUAGE_ID = "StaticObjectModelTest_TestLanguage";

        @Override
        protected TestContext createContext(Env env) {
            return new TestContext(this);
        }
    }

    static class TestContext {
        private final TestLanguage language;

        TestContext(TestLanguage language) {
            this.language = language;
        }

        TestLanguage getLanguage() {
            return language;
        }

        private static final ContextReference<TestContext> REFERENCE = ContextReference.create(TestLanguage.class);

        public static TestContext get(Node node) {
            return REFERENCE.get(node);
        }

    }

    static class TestEnvironment implements AutoCloseable {
        private final boolean arrayBased;
        final boolean relaxChecks;
        final TruffleLanguage<?> testLanguage;
        final Context context;

        TestEnvironment(TestConfiguration config) {
            this.arrayBased = config.arrayBased;
            this.relaxChecks = config.relaxChecks;
            context = Context.newBuilder(TestLanguage.TEST_LANGUAGE_ID).//
                            allowExperimentalOptions(true).//
                            option("engine.StaticObjectStorageStrategy", this.arrayBased ? "array-based" : "field-based").//
                            option("engine.RelaxStaticObjectSafetyChecks", this.relaxChecks ? "true" : "false").//
                            build();
            context.initialize(TestLanguage.TEST_LANGUAGE_ID);
            context.enter();
            testLanguage = TestContext.get(null).getLanguage();
            context.leave();
        }

        public boolean isArrayBased() {
            return arrayBased;
        }

        public boolean isFieldBased() {
            return !arrayBased;
        }

        @Override
        public void close() {
            context.close();
        }
    }

    static class TestConfiguration {
        private final boolean arrayBased;
        private final boolean relaxChecks;

        TestConfiguration(boolean arrayBased, boolean relaxChecks) {
            this.arrayBased = arrayBased;
            this.relaxChecks = relaxChecks;
        }

        @Override
        public String toString() {
            return (arrayBased ? "Array-based" : "Field-based") + " storage " + (relaxChecks ? "without" : "with") + " safety checks";
        }
    }

    static TestConfiguration[] getTestConfigurations() {
        ArrayList<TestConfiguration> tests = new ArrayList<>();
        // AOT mode does not yet support field-based storage
        boolean[] arrayBased = TruffleOptions.AOT ? new boolean[]{true} : new boolean[]{true, false};
        boolean[] relaxChecks = new boolean[]{true, false};
        for (boolean ab : arrayBased) {
            for (boolean rc : relaxChecks) {
                tests.add(new TestConfiguration(ab, rc));
            }
        }
        return tests.toArray(new TestConfiguration[tests.size()]);
    }

    static String guessGeneratedFieldName(StaticProperty property) {
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
}
