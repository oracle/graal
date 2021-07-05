/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.staticobject;

import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.polyglot.Context;

import java.io.Closeable;

final class StaticObjectTestEnvironment implements Closeable {
    final boolean arrayBased;
    final TruffleLanguage<?> testLanguage;
    final Context context;

    StaticObjectTestEnvironment(boolean arrayBased) {
        this.arrayBased = arrayBased;
        context = Context.newBuilder(TestLanguage.TEST_LANGUAGE_ID).//
                        allowExperimentalOptions(true).//
                        option("engine.StaticObjectStorageStrategy", this.arrayBased ? "array-based" : "field-based").//
                        build();
        context.initialize(TestLanguage.TEST_LANGUAGE_ID);
        context.enter();
        testLanguage = TestLanguage.getCurrentContext().getLanguage();
        context.leave();
    }

    @Override
    public void close() {
        context.close();
    }

    @Override
    public String toString() {
        return (arrayBased ? "Array-based" : "Field-based") + " storage";
    }

    static StaticObjectTestEnvironment[] getEnvironments() {
        return new StaticObjectTestEnvironment[]{new StaticObjectTestEnvironment(true), new StaticObjectTestEnvironment(false)};
    }

    @TruffleLanguage.Registration(id = TestLanguage.TEST_LANGUAGE_ID, name = TestLanguage.TEST_LANGUAGE_NAME)
    public static class TestLanguage extends TruffleLanguage<TestContext> {
        static final String TEST_LANGUAGE_NAME = "Test Language for PE tests of the Static Object Model";
        static final String TEST_LANGUAGE_ID = "som-pe-test";

        @Override
        protected TestContext createContext(Env env) {
            return new TestContext(this);
        }

        static TestContext getCurrentContext() {
            return getCurrentContext(TestLanguage.class);
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
    }
}
