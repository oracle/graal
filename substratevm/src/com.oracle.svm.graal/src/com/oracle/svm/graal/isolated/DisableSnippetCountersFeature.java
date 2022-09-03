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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetIntegerHistogram;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Adapted from code of {@link org.graalvm.compiler.hotspot.SymbolicSnippetEncoder}.
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
            b.addPush(JavaKind.Object, ConstantNode.forConstant(SubstrateObjectConstant.forObject(SnippetCounter.DISABLED_COUNTER), b.getMetaAccess()));
            return true;
        }
        if (field.getType().getName().equals(snippetIntegerHistogramName)) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(SubstrateObjectConstant.forObject(SnippetIntegerHistogram.DISABLED_COUNTER), b.getMetaAccess()));
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
