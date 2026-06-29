/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.PEGraphDecoder;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This is a general inlining decoder that is used for both trivial inlining and single callsite
 * inlining. Different plugins allow for different functionality.
 */
class InliningGraphDecoder extends PEGraphDecoder {

    InliningGraphDecoder(StructuredGraph graph, Providers providers, InlineInvokePlugin inliningPlugin) {
        super(AnalysisParsedGraph.HOST_ARCHITECTURE, graph, providers, null,
                        null,
                        new InlineInvokePlugin[]{inliningPlugin},
                        null, null, null, null,
                        new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true, false);
    }

    @Override
    protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider) {
        return ((HostedMethod) method).compilationInfo.getCompilationGraph().getEncodedGraph();
    }
}
