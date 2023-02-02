/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class TruffleCompilerImplTest extends GraalCompilerTest {

    private volatile TruffleCompilerImpl truffleCompiler;
    private final AtomicBoolean compilerInitialized = new AtomicBoolean();
    private Context activeContext;

    protected TruffleCompilerImplTest() {
        if (!TruffleOptions.AOT) {
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            TruffleCompiler compiler = runtime.newTruffleCompiler();
            Assume.assumeTrue("cannot get whitebox interface to Truffle compiler", compiler instanceof TruffleCompilerImpl);
            this.truffleCompiler = (TruffleCompilerImpl) compiler;
        }
    }

    @Before
    public void onlyWhiteBox() {
        if (TruffleOptions.AOT) {
            TruffleCompiler compiler = GraalTruffleRuntime.getRuntime().getTruffleCompiler((OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget());
            Assume.assumeTrue("cannot get whitebox interface to Truffle compiler", compiler instanceof TruffleCompilerImpl);
            this.truffleCompiler = (TruffleCompilerImpl) compiler;
        }
    }

    protected final TruffleCompilerImpl getTruffleCompiler(OptimizedCallTarget callTarget) {
        if (compilerInitialized.compareAndSet(false, true)) {
            truffleCompiler.initialize(GraalTruffleRuntime.getOptionsForCompiler(callTarget), callTarget, true);
        }
        return truffleCompiler;
    }

    @Override
    protected CompilationIdentifier createCompilationId() {
        OptimizedCallTarget target = (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
        return getTruffleCompiler(target).createCompilationIdentifier(target);
    }

    protected final void setupContext(Context newContext) {
        cleanup();
        newContext.enter();
        activeContext = newContext;
    }

    protected final void setupContext() {
        setupContext(Context.newBuilder());
    }

    protected final void setupContext(Context.Builder builder) {
        setupContext(builder.allowAllAccess(true).option("engine.InstrumentExceptionsAreThrown", "true").build());
    }

    protected final Context getContext() {
        return activeContext;
    }

    @After
    public final void cleanup() {
        if (activeContext != null) {
            activeContext.leave();
            activeContext.close();
            activeContext = null;
        }
    }
}
