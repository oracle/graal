/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.compiletasks;

import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.phases.PrepareLongEmulationPhase;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

public class Long64PreparationTask implements CompletionExecutor.DebugContextRunnable {
    private final HostedMethod method;
    private final CoreProviders providers;
    private final OptionValues options;

    public Long64PreparationTask(HostedMethod method, CoreProviders providers, OptionValues options) {
        this.method = method;
        this.providers = providers;
        this.options = options;
    }

    @Override
    @SuppressWarnings("try")
    public void run(DebugContext debug) {
        final StructuredGraph graph = method.compilationInfo.createGraph(debug, options, CompilationIdentifier.INVALID_COMPILATION_ID, true);
        try (DebugContext.Scope s = debug.scope("(prepare long64)", graph, method, this)) {
            new PrepareLongEmulationPhase().apply(graph, providers);
            CanonicalizerPhase.create().apply(graph, providers);
            method.compilationInfo.encodeGraph(graph);
        } catch (Throwable throwable) {
            throw debug.handle(throwable);
        }
    }
}
