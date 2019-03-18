/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;

public class ValueToHostClassCastExceptionDeoptTest {
    private static TruffleRuntimeOptionsOverrideScope backgroundCompilationScope;
    private static TruffleRuntimeOptionsOverrideScope immediateCompilationScope;

    @BeforeClass
    public static void before() {
        backgroundCompilationScope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleBackgroundCompilation, false);
        immediateCompilationScope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileImmediately, true);
    }

    @AfterClass
    public static void after() {
        immediateCompilationScope.close();
        backgroundCompilationScope.close();
    }

    class CompilationCountingListener implements GraalTruffleRuntimeListener {

        int count = 0;

        @Override
        public void onCompilationStarted(OptimizedCallTarget target) {
            if (target.getName().equals("org.graalvm.polyglot.Value<HostFunction>.asNativePointer")) {
                count++;
            }
        }

    }

    /**
     * Tests an issue that arose when (with immediate compiling) an interop value (a java method) is
     * converted to anything via the "as" methods (asNativePointer in this case). The issue use to
     * manifest with repeated compilations caused by an exception being thrown (from
     * InteropAccessNode} during specialization, causing the state to constantly be 0.
     */
    @Test
    public void test() {
        final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
        final CompilationCountingListener listener = new CompilationCountingListener();
        runtime.addListener(listener);
        final Value toString = Context.newBuilder().allowHostAccess(HostAccess.ALL).build().asValue(String.class).getMember("toString");
        for (int i = 0; i < 10; i++) {
            try {
                toString.asNativePointer();
            } catch (ClassCastException e) {
                // Expected and ignored
            }
        }
        Assert.assertEquals("Too many compilations!", 2, listener.count);
    }
}
