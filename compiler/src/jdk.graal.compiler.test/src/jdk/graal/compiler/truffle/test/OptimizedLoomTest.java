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
package jdk.graal.compiler.truffle.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import com.oracle.truffle.api.test.SubprocessTestUtils;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * See {@link DefaultLoomTest} for the default runtime.
 */
public class OptimizedLoomTest {

    @Test
    public void test() throws InterruptedException, IOException {
        SubprocessTestUtils.newBuilder(OptimizedLoomTest.class, () -> {
            try {
                Assume.assumeTrue(LoomUtils.isLoomAvailable());
                Thread t = LoomUtils.startVirtualThread(() -> {
                    try (Context c = Context.create()) {
                        c.eval("sl", "function main() {}");
                    } catch (IllegalStateException e) {
                        assertTrue(e.getMessage().equals("Using polyglot contexts on Java virtual threads is currently not supported with an optimizing Truffle runtime. " +
                                        "As a workaround you may add the -Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime JVM argument to switch to a non-optimizing runtime when using virtual threads. " +
                                        "Please note that performance is severly reduced in this mode. Loom support for optimizing runtimes will be added in a future release."));
                        return;
                    }
                    Assert.fail("Loom should not be supported yet.");
                });
                t.join();
            } catch (InterruptedException e) {
                Assert.fail(e.getMessage());
            }
        }).prefixVmOption("--enable-preview").run();
    }

}
