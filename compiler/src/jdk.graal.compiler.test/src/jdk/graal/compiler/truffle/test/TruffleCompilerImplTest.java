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
package jdk.graal.compiler.truffle.test;

import java.util.concurrent.atomic.AtomicBoolean;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.vm.ci.meta.SpeculationLog;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public abstract class TruffleCompilerImplTest extends GraalCompilerTest {

    private volatile TruffleCompilerImpl truffleCompiler;
    private final AtomicBoolean compilerInitialized = new AtomicBoolean();
    private Context activeContext;

    protected TruffleCompilerImplTest() {
        if (!TruffleOptions.AOT) {
            OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
            TruffleCompiler compiler = runtime.newTruffleCompiler();
            Assume.assumeTrue("cannot get whitebox interface to Truffle compiler", compiler instanceof TruffleCompilerImpl);
            this.truffleCompiler = (TruffleCompilerImpl) compiler;
        }
    }

    @Before
    public void onlyWhiteBox() {
        if (TruffleOptions.AOT) {
            TruffleCompiler compiler = OptimizedTruffleRuntime.getRuntime().getTruffleCompiler(getInitCallTarget());
            Assume.assumeTrue("cannot get whitebox interface to Truffle compiler", compiler instanceof TruffleCompilerImpl);
            this.truffleCompiler = (TruffleCompilerImpl) compiler;
        }
    }

    @Override
    protected Providers getProviders() {
        if (truffleCompiler == null) {
            return super.getProviders();
        }
        return getTruffleCompiler().getConfig().lastTier().providers();
    }

    @Override
    protected Backend getBackend() {
        if (truffleCompiler == null) {
            return super.getBackend();
        }
        return getTruffleCompiler().getConfig().lastTier().backend();
    }

    @Override
    protected SpeculationLog createSpeculationLog() {
        // SharedCodeCacheProvider does not implement createSpeculationLog
        return null;
    }

    public KnownTruffleTypes getTypes() {
        return getTruffleCompiler().getConfig().types();
    }

    protected final TruffleCompilerImpl getTruffleCompiler() {
        TruffleCompilerImpl compiler = getTruffleCompiler(getInitCallTarget());
        return compiler;
    }

    @SuppressWarnings("static-method")
    protected final OptimizedCallTarget getInitCallTarget() {
        return (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
    }

    protected final TruffleCompilerImpl getTruffleCompiler(OptimizedCallTarget callTarget) {
        if (compilerInitialized.compareAndSet(false, true)) {
            truffleCompiler.initialize(callTarget, true);
        }
        return truffleCompiler;
    }

    @Override
    protected CompilationIdentifier createCompilationId() {
        OptimizedCallTarget target = getInitCallTarget();
        TruffleCompilerImpl compiler = getTruffleCompiler(target);
        try (TruffleCompilation compilation = compiler.openCompilation(PartialEvaluationTest.newTask(), target)) {
            return compilation.getCompilationId();
        }
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
        // ProbeNode assertions have an effect on compilation. We turn them off.
        setupContext(builder.allowAllAccess(true).option("engine.InstrumentExceptionsAreThrown", "true").option("engine.AssertProbes", "false").build());
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
