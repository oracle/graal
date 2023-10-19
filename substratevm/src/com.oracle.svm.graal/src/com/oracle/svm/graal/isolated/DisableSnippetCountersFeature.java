/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import jdk.compiler.graal.api.replacements.SnippetReflectionProvider;
import jdk.compiler.graal.hotspot.SymbolicSnippetEncoder;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.graphbuilderconf.NodePlugin;
import jdk.compiler.graal.phases.util.Providers;
import jdk.compiler.graal.replacements.SnippetCounter;
import jdk.compiler.graal.replacements.SnippetIntegerHistogram;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Adapted from code of {@link SymbolicSnippetEncoder}.
 */
final class DisableSnippetCountersPlugin implements NodePlugin {
    private static final String snippetCounterName = 'L' + SnippetCounter.class.getName().replace('.', '/') + ';';
    private static final String snippetIntegerHistogramName = 'L' + SnippetIntegerHistogram.class.getName().replace('.', '/') + ';';

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        if (field.getName().equals("group") && field.getDeclaringClass().getName().equals(snippetCounterName)) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(JavaConstant.NULL_POINTER, b.getMetaAccess()));
            return true;
        }
        if (field.getType().getName().equals(snippetCounterName)) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(b.getSnippetReflection().forObject(SnippetCounter.DISABLED_COUNTER), b.getMetaAccess()));
            return true;
        }
        if (field.getType().getName().equals(snippetIntegerHistogramName)) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(b.getSnippetReflection().forObject(SnippetIntegerHistogram.DISABLED_COUNTER), b.getMetaAccess()));
            return true;
        }
        return false;
    }
}

/**
 * Disables snippet counters because they need a {@link SnippetReflectionProvider} which is not
 * fully supported for cross-isolate compilations.
 */
@AutomaticallyRegisteredFeature
final class DisableSnippetCountersFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.supportCompileInIsolates();
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (reason == ParsingReason.JITCompilation) {
            plugins.appendNodePlugin(new DisableSnippetCountersPlugin());
        }
    }
}
