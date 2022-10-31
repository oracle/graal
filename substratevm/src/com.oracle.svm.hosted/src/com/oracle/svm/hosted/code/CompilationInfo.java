/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphDecoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.deopt.DeoptTest;
import com.oracle.svm.core.deopt.Specialize;
import com.oracle.svm.hosted.code.CompileQueue.CompileFunction;
import com.oracle.svm.hosted.code.CompileQueue.ParseFunction;
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
    private OptionValues compileOptions;

    protected boolean isTrivialMethod;

    protected boolean canDeoptForTesting;

    /**
     * The constant arguments for a {@link DeoptTest} method called by a {@link Specialize} method.
     * Note: this is only used for testing.
     */
    protected ConstantNode[] specializedArguments;

    /* Custom parsing and compilation code that is executed instead of that of CompileQueue */
    protected ParseFunction customParseFunction;
    protected CompileFunction customCompileFunction;

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

    public boolean isDeoptEntry(int bci, boolean duringCall, boolean rethrowException) {
        return method.isDeoptTarget() && (method.getMultiMethod(MultiMethod.ORIGINAL_METHOD).compilationInfo.canDeoptForTesting ||
                        SubstrateCompilationDirectives.singleton().isDeoptEntry(method, bci, duringCall, rethrowException));
    }

    /**
     * Returns whether this bci was registered as a potential deoptimization entrypoint via
     * {@link SubstrateCompilationDirectives#registerDeoptEntry}.
     */
    public boolean isRegisteredDeoptEntry(int bci, boolean duringCall, boolean rethrowException) {
        return method.isDeoptTarget() && SubstrateCompilationDirectives.singleton().isDeoptTarget(method) &&
                        SubstrateCompilationDirectives.singleton().isDeoptEntry(method, bci, duringCall, rethrowException);
    }

    public boolean canDeoptForTesting() {
        return canDeoptForTesting;
    }

    public CompilationGraph getCompilationGraph() {
        return compilationGraph;
    }

    @SuppressWarnings("try")
    public StructuredGraph createGraph(DebugContext debug, CompilationIdentifier compilationId, boolean decode) {
        var graph = new StructuredGraph.Builder(compileOptions, debug)
                        .method(method)
                        .recordInlinedMethods(false)
                        .trackNodeSourcePosition(getCompilationGraph().getEncodedGraph().trackNodeSourcePosition())
                        .compilationId(compilationId)
                        .build();

        if (decode) {
            try (var s = debug.scope("CreateGraph", graph, method)) {
                var decoder = new GraphDecoder(AnalysisParsedGraph.HOST_ARCHITECTURE, graph);
                decoder.decode(getCompilationGraph().getEncodedGraph());
            } catch (Throwable ex) {
                throw debug.handle(ex);
            }
        }
        return graph;
    }

    public void encodeGraph(StructuredGraph graph) {
        compilationGraph = CompilationGraph.encode(graph);
    }

    public void setCompileOptions(OptionValues compileOptions) {
        this.compileOptions = compileOptions;
    }

    public OptionValues getCompileOptions() {
        return compileOptions;
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

    public boolean hasDefaultParseFunction() {
        return customCompileFunction == null;
    }
}
