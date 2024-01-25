/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted.runtimecompilation;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.graal.TruffleRuntimeCompilationSupport;
import com.oracle.svm.graal.meta.SubstrateConstantFieldProvider;
import com.oracle.svm.graal.meta.SubstrateConstantReflectionProvider;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The set of provider objects that are installed via {@link GraalGraphObjectReplacer}.
 */
public class SubstrateProviders {
    private final SubstrateMetaAccess metaAccess;
    private final ConstantFieldProvider constantFieldProvider;
    private final ConstantReflectionProvider constantReflection;
    private final SnippetReflectionProvider snippetReflectionProvider;

    SubstrateProviders(AnalysisMetaAccess aMetaAccess, SubstrateMetaAccess metaAccess, WordTypes wordTypes) {
        this.metaAccess = metaAccess;
        this.constantFieldProvider = new SubstrateConstantFieldProvider(aMetaAccess);
        this.constantReflection = new SubstrateConstantReflectionProvider(this.metaAccess);
        this.snippetReflectionProvider = new SubstrateSnippetReflectionProvider(wordTypes);
    }

    protected SubstrateProviders(SubstrateMetaAccess metaAccess, ConstantFieldProvider constantFieldProvider, ConstantReflectionProvider constantReflection,
                    SnippetReflectionProvider snippetReflection) {
        this.metaAccess = metaAccess;
        this.constantFieldProvider = constantFieldProvider;
        this.constantReflection = constantReflection;
        this.snippetReflectionProvider = snippetReflection;
    }

    public MetaAccessProvider getMetaAccessProvider() {
        return metaAccess;
    }

    public ConstantFieldProvider getConstantFieldProvider() {
        return constantFieldProvider;
    }

    public ConstantReflectionProvider getConstantReflectionProvider() {
        return constantReflection;
    }

    public SnippetReflectionProvider getSnippetReflectionProvider() {
        return snippetReflectionProvider;
    }

    public ForeignCallsProvider getForeignCallsProvider() {
        return TruffleRuntimeCompilationSupport.getRuntimeConfig().getProviders().getForeignCalls();
    }
}
