/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.truffle.test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value.Optimized;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public class GenericFunctionProxyCompilationTest extends PartialEvaluationTest {

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface SumFunction {
        long apply(long left, int right);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ExecutableObject implements TruffleObject {
        private volatile boolean invalidate;

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            if (invalidate) {
                invalidate = false;
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return ((Number) arguments[0]).longValue() + ((Number) arguments[1]).intValue();
        }

        void invalidateNextExecution() {
            invalidate = true;
        }
    }

    @Test
    public void testGenericProxyCompilationAndInvalidation() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowExperimentalOptions(true).option("engine.BackgroundCompilation", "false").option("engine.CompileImmediately",
                        "true").build()) {
            ExecutableObject executable = new ExecutableObject();
            SumFunction function = context.asValue(executable).as(SumFunction.class);
            for (int iteration = 0; iteration < 2; iteration++) {
                for (int i = 0; i < 1000; i++) {
                    Assert.assertEquals(10000L + i, function.apply(10000L, i));
                }
                executable.invalidateNextExecution();
                Assert.assertEquals(42L, function.apply(40L, 2));
            }
        }
    }
}
