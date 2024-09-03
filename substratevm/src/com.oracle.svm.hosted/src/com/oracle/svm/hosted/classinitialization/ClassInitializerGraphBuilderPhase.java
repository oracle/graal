/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import com.oracle.svm.hosted.phases.SharedGraphBuilderPhase;

import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ClassInitializerGraphBuilderPhase extends SharedGraphBuilderPhase {
    public ClassInitializerGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {
        super(clearWordTypes(providers), graphBuilderConfig, optimisticOpts, null);
    }

    /**
     * We do not want any word-type checking when parsing the class initializers, because we do not
     * have the graph builder plugins for word types installed either. Passing null as the WordTypes
     * disables the word type checks in the bytecode parser.
     */
    private static Providers clearWordTypes(CoreProviders providers) {
        WordTypes wordTypes = null;
        return new Providers(providers.getMetaAccess(),
                        providers.getCodeCache(),
                        providers.getConstantReflection(),
                        providers.getConstantFieldProvider(),
                        providers.getForeignCalls(),
                        providers.getLowerer(),
                        providers.getReplacements(),
                        providers.getStampProvider(),
                        providers.getPlatformConfigurationProvider(),
                        providers.getMetaAccessExtensionProvider(),
                        providers.getSnippetReflection(),
                        wordTypes,
                        providers.getLoopsDataProvider(),
                        providers.getIdentityHashCodeProvider());
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new ClassInitializerBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
    }

    static class ClassInitializerBytecodeParser extends SharedBytecodeParser {
        ClassInitializerBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, true, false);
        }
    }
}
