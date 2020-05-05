/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.replacements.PEGraphDecoder;

import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.graal.GraalSupport;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstratePEGraphDecoder extends PEGraphDecoder {

    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache = EconomicMap.create();

    public SubstratePEGraphDecoder(Architecture architecture, StructuredGraph graph, CoreProviders providers, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins,
                    InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin, NodePlugin[] nodePlugins, ResolvedJavaMethod peRootForInlining,
                    SourceLanguagePositionProvider sourceLanguagePosition) {
        super(architecture, graph, providers, loopExplosionPlugin, invocationPlugins, inlineInvokePlugins, parameterPlugin, nodePlugins,
                        peRootForInlining, sourceLanguagePosition);
    }

    @Override
    protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, MethodSubstitutionPlugin plugin, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution,
                    boolean trackNodeSourcePosition) {
        /*
         * The EncodedGraph instance also serves as a cache for some information during decoding,
         * e.g., the start offsets of encoded nodes. So it is beneficial to have a cache of the
         * actual EncodedGraph objects.
         */
        EncodedGraph result = graphCache.get(method);
        if (result == null) {
            result = createGraph(method, trackNodeSourcePosition);
        }
        return result;
    }

    private EncodedGraph createGraph(ResolvedJavaMethod method, boolean trackNodeSourcePosition) {
        EncodedGraph result = GraalSupport.encodedGraph((SharedRuntimeMethod) method, trackNodeSourcePosition);
        if (result == null) {
            throw shouldNotReachHere("Graph not available for runtime compilation: " + method.format("%H.%n(%p)"));
        }
        graphCache.put(method, result);
        return result;
    }
}
