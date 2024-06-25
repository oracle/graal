/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.IdentityHashMap;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Snippets are parsed before the static analysis using {@link SubstrateReplacements}. This ensures
 * that snippets do not use analysis-specific nodes - they are parsed using the same
 * {@link BytecodeParser} subclass also used for parsing the snippets we use for runtime
 * compilation. The parsing uses the {@link AnalysisUniverse}.
 *
 * We cannot parse snippets again before compilation with the {@link HostedUniverse}: the static
 * analysis does not see the individual methods that are inlined into snippets, only the final graph
 * for snippets after aggressive inlining and constant folding. Therefore, many methods used inside
 * snippets are not marked as invoked.
 *
 * Therefore, we re-use the snippets parsed before static analysis also for compilation. We need to
 * transplant these snippets from the {@link AnalysisUniverse} to the {@link HostedUniverse}, i.e.,
 * we need to change out all metadata objects (types, methods, fields, ...). This is easy because
 * the snippets are encoded anyway, so we have a single Object[] array with all objects referenced
 * from the snippet graphs. The object replacement is done in
 * {@link AnalysisToHostedGraphTransplanter#replaceAnalysisObjects}.
 */
public class HostedReplacements extends SubstrateReplacements {

    private final HostedUniverse hUniverse;
    private final SubstrateReplacements aReplacements;

    public HostedReplacements(HostedUniverse hUniverse, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target, HostedProviders analysisProviders,
                    BytecodeProvider bytecodeProvider, WordTypes wordTypes) {
        super(providers, snippetReflection, bytecodeProvider, target, wordTypes, null);
        this.hUniverse = hUniverse;
        this.aReplacements = (SubstrateReplacements) analysisProviders.getReplacements();
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        /* We must have the snippet already available in the analysis replacements. */
        assert aReplacements.getSnippet(((HostedMethod) method).wrapped, null, null, null, trackNodeSourcePosition, null, options) != null;
    }

    @Override
    public void encodeSnippets() {
        /* Copy over all snippets from the analysis replacements, changing out metadata objects. */
        IdentityHashMap<Object, Object> mapping = new IdentityHashMap<>();
        super.copyFrom(aReplacements, obj -> AnalysisToHostedGraphTransplanter.replaceAnalysisObjects(obj, null, mapping, hUniverse));
    }
}
