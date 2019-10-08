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
package com.oracle.svm.graal.hosted;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.meta.SubstrateConstantFieldProvider;
import com.oracle.svm.graal.meta.SubstrateConstantReflectionProvider;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The set of provider objects that are installed via {@link GraalObjectReplacer}.
 */
public class GraalProviderObjectReplacements {
    private final SubstrateMetaAccess metaAccess;
    private final ConstantFieldProvider constantFieldProvider;
    private final ConstantReflectionProvider constantReflection;

    GraalProviderObjectReplacements(AnalysisMetaAccess aMetaAccess) {
        this.metaAccess = new SubstrateMetaAccess();
        this.constantFieldProvider = new SubstrateConstantFieldProvider(aMetaAccess);
        this.constantReflection = new SubstrateConstantReflectionProvider(metaAccess);
    }

    protected GraalProviderObjectReplacements(SubstrateMetaAccess metaAccess, ConstantFieldProvider constantFieldProvider, ConstantReflectionProvider constantReflection) {
        this.metaAccess = metaAccess;
        this.constantFieldProvider = constantFieldProvider;
        this.constantReflection = constantReflection;
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
        return GraalSupport.getRuntimeConfig().getSnippetReflection();
    }

    public ForeignCallsProvider getForeignCallsProvider() {
        return GraalSupport.getRuntimeConfig().getProviders().getForeignCalls();
    }
}
