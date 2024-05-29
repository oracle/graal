/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.deopt.DeoptTest;
import com.oracle.svm.core.deopt.Specialize;
import com.oracle.svm.hosted.code.CompileQueue.CompileFunction;
import com.oracle.svm.hosted.code.CompileQueue.ParseFunction;
import com.oracle.svm.hosted.code.CompileQueue.ParseHooks;
import com.oracle.svm.hosted.meta.HostedMethod;

public class CompilationInfo {

    protected final HostedMethod method;

    protected final AtomicBoolean inParseQueue = new AtomicBoolean(false);
    /**
     * No need for this flag to be atomic, because {@link CompileQueue#compilations} is used to
     * ensure each method is compiled only once.
     */
    protected boolean inCompileQueue;

    private volatile CompilationGraph compilationGraph;

    protected boolean isTrivialMethod;
    protected boolean trivialInliningDisabled;

    protected boolean canDeoptForTesting;

    /**
     * The constant arguments for a {@link DeoptTest} method called by a {@link Specialize} method.
     * Note: this is only used for testing.
     */
    protected ConstantNode[] specializedArguments;

    /* Custom parsing and compilation code that is executed instead of that of CompileQueue */
    protected ParseFunction customParseFunction;
    protected CompileFunction customCompileFunction;

    /*
     * Custom parsing hook which is called during the default parse method.
     */
    protected ParseHooks customParseHooks;

    /* Statistics collected before/during compilation. */
    protected long numNodesAfterParsing;
    protected long numNodesBeforeCompilation;
    protected long numNodesAfterCompilation;
    protected long numDeoptEntryPoints;
    protected long numDuringCallEntryPoints;

    /* Statistics collected when method is put into compile queue. */
    protected final AtomicLong numDirectCalls = new AtomicLong();
    protected final AtomicLong numVirtualCalls = new AtomicLong();
    protected final AtomicLong numEntryPointCalls = new AtomicLong();

    public CompilationInfo(HostedMethod method) {
        this.method = method;
    }

    public boolean isDeoptEntry(int bci, FrameState.StackState stackState) {
        return method.isDeoptTarget() && (method.getMultiMethod(MultiMethod.ORIGINAL_METHOD).compilationInfo.canDeoptForTesting ||
                        SubstrateCompilationDirectives.singleton().isRegisteredDeoptEntry(method, bci, stackState));
    }

    public boolean canDeoptForTesting() {
        return canDeoptForTesting;
    }

    public CompilationGraph getCompilationGraph() {
        return compilationGraph;
    }

    @SuppressWarnings("try")
    public StructuredGraph createGraph(DebugContext debug, OptionValues options, CompilationIdentifier compilationId, boolean decode) {
        var encodedGraph = getCompilationGraph().getEncodedGraph();
        var graph = new StructuredGraph.Builder(options, debug)
                        .method(method)
                        .trackNodeSourcePosition(encodedGraph.trackNodeSourcePosition())
                        .recordInlinedMethods(encodedGraph.isRecordingInlinedMethods())
                        .compilationId(compilationId)
                        .build();

        if (decode) {
            try (var s = debug.scope("CreateGraph", graph, method)) {
                var decoder = new GraphDecoder(AnalysisParsedGraph.HOST_ARCHITECTURE, graph);
                decoder.decode(encodedGraph);
            } catch (Throwable ex) {
                throw debug.handle(ex);
            }
        }
        return graph;
    }

    public void encodeGraph(StructuredGraph graph) {
        compilationGraph = CompilationGraph.encode(graph);
    }

    public void setCompilationGraph(CompilationGraph graph) {
        compilationGraph = graph;
    }

    public void clear() {
        compilationGraph = null;
        specializedArguments = null;
    }

    public boolean isTrivialMethod() {
        return isTrivialMethod;
    }

    public void setTrivialMethod(boolean trivial) {
        isTrivialMethod = trivial;
    }

    public boolean isTrivialInliningDisabled() {
        return trivialInliningDisabled;
    }

    public void setTrivialInliningDisabled(boolean trivialInliningDisabled) {
        this.trivialInliningDisabled = trivialInliningDisabled;
    }

    public void setCustomParseFunction(ParseFunction parseFunction) {
        this.customParseFunction = parseFunction;
    }

    public ParseFunction getCustomParseFunction() {
        return customParseFunction;
    }

    public void setCustomCompileFunction(CompileFunction compileFunction) {
        this.customCompileFunction = compileFunction;
    }

    public CompileFunction getCustomCompileFunction() {
        return customCompileFunction;
    }

    public void setCustomParseHooks(ParseHooks parseHooks) {
        this.customParseHooks = parseHooks;
    }

    public ParseHooks getCustomParseHooks() {
        return customParseHooks;
    }
}
