/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;

/**
 * See {@link OptimizedLoomTest} for other runtimes.
 */
public class DefaultLoomTest {

    @Test
    public void test() throws InterruptedException, IOException {
        // Run in subprocess with default truffle runtime. Drop DynamicCompilationThresholds since
        // it does not exist in the default truffle runtime.
        SubprocessTestUtils.executeInSubprocess(DefaultLoomTest.class, () -> {
            try {
                Assume.assumeTrue(Truffle.getRuntime() instanceof DefaultTruffleRuntime);
                Assume.assumeTrue(LoomUtils.isLoomAvailable());

                Thread t = LoomUtils.startVirtualThread(() -> {
                    try (Context c = Context.create()) {
                        c.eval("sl", "function main() {}");
                    }
                });
                t.join();
            } catch (InterruptedException e) {
                Assert.fail(e.getMessage());
            }
        }, "--enable-preview", "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime", "~~-Dpolyglot.engine.DynamicCompilationThresholds");
    }
}
