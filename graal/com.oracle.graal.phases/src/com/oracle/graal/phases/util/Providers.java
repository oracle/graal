/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.util;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.tiers.*;

/**
 * A set of providers, some of which may not be present (i.e., null).
 */
public class Providers implements CodeGenProviders {

    private final MetaAccessProvider metaAccess;
    private final CodeCacheProvider codeCache;
    private final LoweringProvider lowerer;
    private final ConstantReflectionProvider constantReflection;
    private final ForeignCallsProvider foreignCalls;
    private final Replacements replacements;

    public Providers(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ForeignCallsProvider foreignCalls, LoweringProvider lowerer,
                    Replacements replacements) {
        this.metaAccess = metaAccess;
        this.codeCache = codeCache;
        this.constantReflection = constantReflection;
        this.foreignCalls = foreignCalls;
        this.lowerer = lowerer;
        this.replacements = replacements;
    }

    public Providers(Providers copyFrom) {
        this(copyFrom.getMetaAccess(), copyFrom.getCodeCache(), copyFrom.getConstantReflection(), copyFrom.getForeignCalls(), copyFrom.getLowerer(), copyFrom.getReplacements());
    }

    public Providers(PhaseContext copyFrom) {
        this(copyFrom.getMetaAccess(), null, copyFrom.getConstantReflection(), null, copyFrom.getLowerer(), copyFrom.getReplacements());
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    public ForeignCallsProvider getForeignCalls() {
        return foreignCalls;
    }

    public LoweringProvider getLowerer() {
        return lowerer;
    }

    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    public Replacements getReplacements() {
        return replacements;
    }

    public Providers copyWith(MetaAccessProvider substitution) {
        return new Providers(substitution, codeCache, constantReflection, foreignCalls, lowerer, replacements);
    }

    public Providers copyWith(CodeCacheProvider substitution) {
        return new Providers(metaAccess, substitution, constantReflection, foreignCalls, lowerer, replacements);
    }

    public Providers copyWith(ConstantReflectionProvider substitution) {
        return new Providers(metaAccess, codeCache, substitution, foreignCalls, lowerer, replacements);
    }

    public Providers copyWith(ForeignCallsProvider substitution) {
        return new Providers(metaAccess, codeCache, constantReflection, substitution, lowerer, replacements);
    }

    public Providers copyWith(LoweringProvider substitution) {
        return new Providers(metaAccess, codeCache, constantReflection, foreignCalls, substitution, replacements);
    }

    public Providers copyWith(Replacements substitution) {
        return new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, substitution);
    }
}
