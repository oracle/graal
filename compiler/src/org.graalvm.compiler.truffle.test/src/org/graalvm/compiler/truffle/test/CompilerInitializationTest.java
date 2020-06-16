/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.polyglot.Context;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.ReflectionUtils;

public class CompilerInitializationTest {

    @Before
    public void resetCompiler() {
        Assume.assumeFalse("This test does not apply to SVM runtime where the compiler is initialized eagerly.", TruffleOptions.AOT);
        try {
            Method m = Truffle.getRuntime().getClass().getMethod("resetCompiler");
            m.invoke(Truffle.getRuntime());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testDefault() throws Exception {
        assertTruffleCompilerInitialized(false);
        Context context = Context.newBuilder().allowExperimentalOptions(true).//
                        option("engine.BackgroundCompilation", "false").build();

        // no compiler needed until the context is actually used
        assertTruffleCompilerInitialized(false);
        context.eval(InstrumentationTestLanguage.ID, "EXPRESSION");
        // since background compilation is off we can assume the compiler is initialized
        // afterwards. With background compilation on this would be racy and hard to test.
        assertTruffleCompilerInitialized(true);
        context.close();
    }

    @Test
    public void testNoCompilation() throws Exception {
        /*
         * Same behavior as testDefault even if compilation is disabled. Important to ensure that
         * truffle call boundary methods are installed.
         */
        assertTruffleCompilerInitialized(false);
        Context context = Context.newBuilder().allowExperimentalOptions(true).//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.Compilation", "false").//
                        build();

        assertTruffleCompilerInitialized(false);
        context.eval(InstrumentationTestLanguage.ID, "EXPRESSION");
        assertTruffleCompilerInitialized(true);
        context.close();
    }

    public static void assertTruffleCompilerInitialized(boolean expected) throws Exception {
        if (expected) {
            assertNotNull(compilerField().get(Truffle.getRuntime()));
        } else {
            assertNull(compilerField().get(Truffle.getRuntime()));
        }
    }

    private static Field compilerField() throws NoSuchFieldException {
        Field f = GraalTruffleRuntime.class.getDeclaredField("truffleCompiler");
        ReflectionUtils.setAccessible(f, true);
        return f;
    }

}
