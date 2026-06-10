/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import org.graalvm.home.impl.DefaultHomeFinder;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

/**
 * Unit-test support for temporarily overriding language homes.
 *
 * <p>
 * Tests use this helper instead of setting {@code org.graalvm.language.<id>.home} system
 * properties. The override is installed in {@link DefaultHomeFinder} reflectively.
 */
final class LanguageHomeSupport {

    private LanguageHomeSupport() {
    }

    /**
     * Overrides language homes for the duration of the returned closeable.
     *
     * @param newLanguageHomes language id to home directory mapping to expose to the test
     * @return a closeable that clears the test override
     */
    static AutoCloseable overrideLanguageHomes(Map<String, Path> newLanguageHomes) {
        resetLanguageHomes();
        AutoCloseable resetHomeFinder = (AutoCloseable) ReflectionUtils.invokeStatic(DefaultHomeFinder.class, "overrideLanguageHomes",
                        new Class<?>[]{Map.class}, newLanguageHomes);
        return () -> {
            try {
                resetHomeFinder.close();
            } finally {
                resetLanguageHomes();
            }
        };
    }

    private static void resetLanguageHomes() {
        try {
            final Class<?> langCacheClz = Class.forName("com.oracle.truffle.polyglot.LanguageCache", true, FileSystemsTest.class.getClassLoader());
            final Method reset = langCacheClz.getDeclaredMethod("resetNativeImageCacheLanguageHomes");
            ReflectionUtils.setAccessible(reset, true);
            reset.invoke(null);
        } catch (ReflectiveOperationException re) {
            throw new RuntimeException(re);
        }
    }

    /**
     * Overrides the home of a single language for the duration of the returned closeable.
     *
     * @param languageId the language id
     * @param languageHome the test language home
     * @return a closeable that clears the test override
     */
    static AutoCloseable overrideLanguageHomes(String languageId, Path languageHome) {
        return overrideLanguageHomes(Map.of(languageId, languageHome));
    }

    /**
     * Overrides the home of a single language in both the host and, when needed, native-image
     * isolate.
     *
     * @param engine the engine used to enter the isolate
     * @param language the language whose default id identifies the language home
     * @param languageHome the test language home
     * @return a closeable that clears the test override
     */
    static AutoCloseable overrideLanguageHomes(Engine engine, Class<? extends TruffleLanguage<?>> language, Path languageHome) {
        try (Context ctx = Context.newBuilder().engine(engine).build()) {
            String id = TestUtils.getDefaultLanguageId(language);
            if (TruffleTestAssumptions.isStrongEncapsulation()) {
                // Set language home in isolate
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, MarkAsLanguageHomeLanguage.class, "", id, languageHome.toString());
            }
            // Set language home in host
            return overrideLanguageHomes(id, languageHome);
        }
    }

    @TruffleLanguage.Registration
    public static final class MarkAsLanguageHomeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String languageId = (String) contextArguments[0];
            String languageHome = (String) contextArguments[1];
            overrideLanguageHomes(languageId, Path.of(languageHome));
            return null;
        }
    }
}
