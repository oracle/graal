/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.BitSet;
import java.util.Map;
import java.util.function.Function;

import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;

import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.SnippetParameterInfo;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class RistrettoReplacements implements Replacements {
    private final SubstrateReplacements svmReplacements;

    public RistrettoReplacements(SubstrateReplacements svmReplacements) {
        this.svmReplacements = svmReplacements;
    }

    @Override
    public CoreProviders getProviders() {
        return svmReplacements.getProviders();
    }

    @Override
    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return svmReplacements.getGraphBuilderPlugins();
    }

    @Override
    public Map<SnippetTemplate.CacheKey, SnippetTemplate> getTemplatesCache() {
        return svmReplacements.getTemplatesCache();
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        return svmReplacements.getIntrinsifyingPlugin(method);
    }

    @Override
    public DebugContext openSnippetDebugContext(String idPrefix, ResolvedJavaMethod method, DebugContext outer, OptionValues options) {
        return svmReplacements.openSnippetDebugContext(idPrefix, method, outer, options);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options) {
        Function<Object, Object> t = o -> {
            if (o instanceof SubstrateType substrateType) {
                return RistrettoType.create((InterpreterResolvedJavaType) substrateType.getHub().getInterpreterType());
            } else if (o instanceof SubstrateMethod substrateMethod &&
                            /*
                             * Note that we exclude native methods and other snippets here. If we
                             * call a native methods its normally a node intrinsic, if we call
                             * another snippet method we also will not have interpreter methods for
                             * it.
                             */
                            !isSnippet(substrateMethod) && !substrateMethod.isNative()) {
                InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) substrateMethod.getDeclaringClass().getHub().getInterpreterType();
                for (var iMeth : iType.getDeclaredMethods()) {
                    if (iMeth.getName().equals(substrateMethod.getName()) && iMeth.getSignature().toString().equals(substrateMethod.getSignature().toString())) {
                        return RistrettoMethod.create(iMeth);
                    }
                }
                throw GraalError.shouldNotReachHere("Cannot find iMethod for " + substrateMethod.getName());
            } else if (o instanceof SubstrateField substrateField) {
                InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) substrateField.getDeclaringClass().getHub().getInterpreterType();
                if (substrateField.isStatic()) {
                    for (var iField : iType.getStaticFields()) {
                        if (iField.getName().equals(substrateField.getName())) {
                            return RistrettoField.create((InterpreterResolvedJavaField) iField);
                        }
                    }
                } else {
                    for (var iField : iType.getInstanceFields(true)) {
                        if (iField.getName().equals(substrateField.getName())) {
                            return RistrettoField.create((InterpreterResolvedJavaField) iField);
                        }
                    }
                }
                throw GraalError.shouldNotReachHere("Cannot find iField for " + substrateField.getName());
            }
            return o;
        };
        return svmReplacements.getSnippet(method, args, trackNodeSourcePosition, options, t);
    }

    @Override
    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        return svmReplacements.getSnippetParameterInfo(method);
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        return svmReplacements.isSnippet(method);
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        svmReplacements.registerSnippet(method, original, receiver, trackNodeSourcePosition, options);
    }

    @Override
    public StructuredGraph getInlineSubstitution(ResolvedJavaMethod method, int invokeBci, boolean isInOOMETry, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, StructuredGraph.AllowAssumptions allowAssumptions, OptionValues options) {
        return svmReplacements.getInlineSubstitution(method, invokeBci, isInOOMETry, inlineControl, trackNodeSourcePosition,
                        replaceePosition, allowAssumptions, options);
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, OptionValues options) {
        return svmReplacements.hasSubstitution(method, options);
    }

    @Override
    public BytecodeProvider getDefaultReplacementBytecodeProvider() {
        return svmReplacements.getDefaultReplacementBytecodeProvider();
    }

    @Override
    public void registerSnippetTemplateCache(SnippetTemplateCache snippetTemplates) {
        svmReplacements.registerSnippetTemplateCache(snippetTemplates);
    }

    @Override
    public <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass) {
        return svmReplacements.getSnippetTemplateCache(templatesClass);
    }

    @Override
    public void closeSnippetRegistration() {
        svmReplacements.closeSnippetRegistration();
    }

    @Override
    public JavaKind getWordKind() {
        return svmReplacements.getWordKind();
    }

    @Override
    public <T> T getInjectedArgument(Class<T> type) {
        return svmReplacements.getInjectedArgument(type);
    }

    @Override
    public Stamp getInjectedStamp(Class<?> type) {
        return svmReplacements.getInjectedStamp(type);
    }
}
