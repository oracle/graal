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

import static org.junit.Assert.assertEquals;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.concurrent.atomic.AtomicBoolean;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InvalidationCountTest {

    private TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope optionScope;

    @Before
    public void setUp() {
        optionScope = TruffleRuntimeOptions.overrideOptions(
                        SharedTruffleRuntimeOptions.TruffleBackgroundCompilation, false,
                        SharedTruffleRuntimeOptions.TruffleCompilationThreshold, 10);
    }

    @After
    public void tearDown() {
        if (optionScope != null) {
            optionScope.close();
        }
    }

    @Test
    public void testTransferToInterpreterAndInvalidate() throws Exception {
        AtomicBoolean invalidate = new AtomicBoolean();
        RootNode rootNode = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                if (invalidate.get()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                return true;
            }
        };
        OptimizedCallTarget callTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNode);
        warmUp(callTarget);
        assertEquals(0, callTarget.getCompilationProfile().getInvalidationCount());
        callTarget.call();
        assertEquals(0, callTarget.getCompilationProfile().getInvalidationCount());
        invalidate.set(true);
        callTarget.call();
        assertEquals(1, callTarget.getCompilationProfile().getInvalidationCount());
        invalidate.set(false);
        callTarget.call();
        assertEquals(1, callTarget.getCompilationProfile().getInvalidationCount());
        reprofile(callTarget);
        invalidate.set(true);
        callTarget.call();
        invalidate.set(false);
        assertEquals(2, callTarget.getCompilationProfile().getInvalidationCount());
    }

    @Test
    public void testTransferToInterpreter() throws Exception {
        AtomicBoolean transferToInterpreter = new AtomicBoolean();
        RootNode rootNode = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                if (transferToInterpreter.get()) {
                    CompilerDirectives.transferToInterpreter();
                    return false;
                }
                return false;
            }
        };
        OptimizedCallTarget callTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNode);
        warmUp(callTarget);
        assertEquals(0, callTarget.getCompilationProfile().getInvalidationCount());
        transferToInterpreter.set(true);
        reprofile(callTarget);
        int afterReprofileInvalidationCount = callTarget.getCompilationProfile().getInvalidationCount();
        callTarget.call();
        assertEquals(afterReprofileInvalidationCount, callTarget.getCompilationProfile().getInvalidationCount());
        callTarget.call();
        assertEquals(afterReprofileInvalidationCount, callTarget.getCompilationProfile().getInvalidationCount());
    }

    @Test
    public void testCallAssumptionInvalidation() throws Exception {
        AtomicBoolean invalidate = new AtomicBoolean();
        RootNode rootNode = new RootNode(null) {

            @CompilerDirectives.CompilationFinal private Assumption assumption = Truffle.getRuntime().createAssumption();

            @Override
            public Object execute(VirtualFrame frame) {
                if (invalidate.get()) {
                    if (!assumption.isValid()) {
                        throw new IllegalStateException("Invalid assumption");
                    }
                    assumption.invalidate();
                    assumption = Truffle.getRuntime().createAssumption();
                }
                return true;
            }
        };

        OptimizedCallTarget callTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNode);
        warmUp(callTarget);
        assertEquals(0, callTarget.getCompilationProfile().getInvalidationCount());
        callTarget.call();
        assertEquals(0, callTarget.getCompilationProfile().getInvalidationCount());
        invalidate.set(true);
        callTarget.call();
        invalidate.set(false);
        // Incremented twice, once by OptimizedAssumption.invalidateImpl and ence by
        // OptimizedCallTarget.callProxy
        assertEquals(2, callTarget.getCompilationProfile().getInvalidationCount());
        callTarget.call();
        assertEquals(2, callTarget.getCompilationProfile().getInvalidationCount());
        reprofile(callTarget);
        invalidate.set(true);
        callTarget.call();
        invalidate.set(false);
        assertEquals(4, callTarget.getCompilationProfile().getInvalidationCount());
    }

    private static void warmUp(OptimizedCallTarget callTarget, Object... args) {
        final int compilationThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationThreshold);
        execute(callTarget, compilationThreshold, args);
    }

    private static void reprofile(OptimizedCallTarget callTarget, Object... args) {
        final int reprofileThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleInvalidationReprofileCount);
        execute(callTarget, reprofileThreshold, args);
    }

    private static void execute(OptimizedCallTarget callTarget, int repeat, Object... args) {
        for (int i = 0; i < repeat; i++) {
            callTarget.call(args);
        }
    }
}
