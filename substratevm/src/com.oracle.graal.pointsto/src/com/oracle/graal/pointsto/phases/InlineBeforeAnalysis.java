/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.phases;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;

/**
 * Inlining before the static analysis improves the precision of the analysis especially when
 * constants are propagated. So the goal is to inline callees that are folded to constants.
 *
 * Sometimes, constant folding of callees requires quite deep inlining, when constants are
 * propagated through chains of wrapper methods. So we want to be able to look deep for potential
 * inlining. On the other hand, only very small methods can be inlined before analysis in order to
 * not bloat the type flow graph and later on machine code for infrequently executed methods. So we
 * do not want to use the regular method inlining mechanism that propagates argument constants into
 * a possibly large graph and then runs the CanonicalizerPhase. Instead, we leverage the graph
 * decoding also used by the Truffle partial evaluator. When a direct method invoke is seen during
 * decoding, we by default go into the callee. When the callee produces too many nodes (configurable
 * via the {@link InlineBeforeAnalysisPolicy}) inlining is aborted, i.e., already created nodes from
 * the callee are deleted and a non-inlined invoke is created instead.
 */
public class InlineBeforeAnalysis {

    public static class Options {
        @Option(help = "Deprecated, option no longer has any effect", deprecated = true, deprecationMessage = "It no longer has any effect, and no replacement is available")//
        public static final OptionKey<Boolean> InlineBeforeAnalysis = new OptionKey<>(true);
    }

    @SuppressWarnings("try")
    public static StructuredGraph decodeGraph(BigBang bb, AnalysisMethod method, AnalysisParsedGraph analysisParsedGraph) {
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getId());
        DebugContext debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).description(description).build();

        StructuredGraph result = new StructuredGraph.Builder(bb.getOptions(), debug, bb.getHostVM().allowAssumptions(method))
                        .method(method)
                        .trackNodeSourcePosition(analysisParsedGraph.getEncodedGraph().trackNodeSourcePosition())
                        .recordInlinedMethods(analysisParsedGraph.getEncodedGraph().isRecordingInlinedMethods())
                        .build();

        try (DebugContext.Scope s = debug.scope("InlineBeforeAnalysis", result)) {
            InlineBeforeAnalysisGraphDecoder decoder = bb.getHostVM().createInlineBeforeAnalysisGraphDecoder(bb, method, result);
            decoder.decode(method);
            debug.dump(DebugContext.BASIC_LEVEL, result, "InlineBeforeAnalysis after decode");
            return result;
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }
}
