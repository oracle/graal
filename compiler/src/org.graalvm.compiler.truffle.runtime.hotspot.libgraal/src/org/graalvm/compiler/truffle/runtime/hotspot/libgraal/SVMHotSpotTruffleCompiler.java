/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.compiler.truffle.runtime.hotspot.libgraal.LibGraalTruffleRuntime.getIsolateThreadId;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.OptionsEncoder;

/**
 * Encapsulates a handle to a {@link HotSpotTruffleCompiler} object in the SVM heap.
 */
final class SVMHotSpotTruffleCompiler extends SVMObject implements HotSpotTruffleCompiler {

    private final Map<CompilableTruffleAST, Reference<SVMTruffleCompilation>> activeCompilations = new WeakHashMap<>();

    SVMHotSpotTruffleCompiler(long handle) {
        super(handle);
    }

    @Override
    public TruffleCompilation openCompilation(CompilableTruffleAST compilable) {
        SVMTruffleCompilation compilation = new SVMTruffleCompilation(this, HotSpotToSVMCalls.openCompilation(getIsolateThreadId(), handle, compilable));
        activeCompilations.put(compilable, new WeakReference<>(compilation));
        return compilation;
    }

    @Override
    public TruffleDebugContext openDebugContext(Map<String, Object> options, TruffleCompilation compilation) {
        return IgvSupport.create(this, options, (SVMTruffleCompilation) compilation);
    }

    @Override
    public void doCompile(TruffleDebugContext debug,
                    TruffleCompilation compilation,
                    Map<String, Object> options,
                    TruffleInliningPlan inlining,
                    TruffleCompilationTask task,
                    TruffleCompilerListener listener) {
        byte[] encodedOptions = OptionsEncoder.encode(options);
        HotSpotToSVMCalls.doCompile(getIsolateThreadId(), handle, ((IgvSupport) debug).handle, ((SVMTruffleCompilation) compilation).handle, encodedOptions, inlining, task, listener);
    }

    @Override
    public String getCompilerConfigurationName() {
        return HotSpotToSVMCalls.getCompilerConfigurationName(getIsolateThreadId(), handle);
    }

    @Override
    public void shutdown() {
        HotSpotToSVMCalls.shutdown(getIsolateThreadId(), handle);
    }

    @Override
    public void installTruffleCallBoundaryMethods() {
        HotSpotToSVMCalls.installTruffleCallBoundaryMethods(getIsolateThreadId(), handle);
    }

    @Override
    public int pendingTransferToInterpreterOffset() {
        return HotSpotToSVMCalls.pendingTransferToInterpreterOffset(getIsolateThreadId(), handle);
    }

    void closeCompilation(SVMTruffleCompilation compilation) {
        activeCompilations.remove(compilation.getCompilable());
    }

    SVMTruffleCompilation findCompilation(CompilableTruffleAST compilable) {
        Reference<SVMTruffleCompilation> compilationRef = activeCompilations.get(compilable);
        return compilationRef == null ? null : compilationRef.get();
    }
}
