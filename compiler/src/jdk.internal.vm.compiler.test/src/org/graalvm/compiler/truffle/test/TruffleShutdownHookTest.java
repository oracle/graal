/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.test.SubprocessTestUtils;

/**
 * Test for GR-47083.
 */
public class TruffleShutdownHookTest {

    @Test
    public void testRuntimeInit() throws IOException, InterruptedException {
        SubprocessTestUtils.newBuilder(getClass(), TruffleShutdownHookTest::inProcess).failOnNonZeroExit(true).//
                        postfixVmOption("-XX:+UseJVMCICompiler").// force Graal host compilation
                        onExit((p) -> {
                            String out = p.toString(System.lineSeparator());
                            if (out.contains("java.lang.IllegalStateException: Shutdown in progress")) {
                                throw new AssertionError(out);
                            }
                        }).run();
    }

    private static void inProcess() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
                        @Override
                        public Void visitFrame(FrameInstance frameInstance) {
                            return null;
                        }
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    @Test
    public void testEngineInit() throws IOException, InterruptedException {
        SubprocessTestUtils.newBuilder(getClass(), TruffleShutdownHookTest::inProcessEngineInit).failOnNonZeroExit(true).//
                        postfixVmOption("-XX:+UseJVMCICompiler").// force Graal host compilation
                        onExit((p) -> {
                            String out = p.toString(System.lineSeparator());
                            if (out.contains("java.lang.IllegalStateException: Shutdown in progress")) {
                                throw new AssertionError(out);
                            }
                        }).run();
    }

    private static void inProcessEngineInit() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    Engine.create().close();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

}
