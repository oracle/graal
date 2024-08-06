/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.util;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * A set of providers, some of which may not be present (i.e., null).
 */
public class Providers implements CoreProviders {

    protected final MetaAccessProvider metaAccess;
    protected final ConstantReflectionProvider constantReflection;
    protected final ConstantFieldProvider constantFieldProvider;
    protected final LoweringProvider lowerer;
    protected final Replacements replacements;
    protected final StampProvider stampProvider;
    protected final ForeignCallsProvider foreignCalls;
    protected final PlatformConfigurationProvider platformConfigurationProvider;
    protected final MetaAccessExtensionProvider metaAccessExtensionProvider;
    protected final LoopsDataProvider loopsDataProvider;
    protected final CodeCacheProvider codeCache;
    protected final SnippetReflectionProvider snippetReflection;
    protected final WordTypes wordTypes;
    protected final IdentityHashCodeProvider identityHashCodeProvider;

    public Providers(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    ForeignCallsProvider foreignCalls, LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, PlatformConfigurationProvider platformConfigurationProvider,
                    MetaAccessExtensionProvider metaAccessExtensionProvider, SnippetReflectionProvider snippetReflection, WordTypes wordTypes, LoopsDataProvider loopsDataProvider,
                    IdentityHashCodeProvider identityHashCodeProvider) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.constantFieldProvider = constantFieldProvider;
        this.lowerer = lowerer;
        this.replacements = replacements;
        this.stampProvider = stampProvider;
        this.foreignCalls = foreignCalls;
        this.platformConfigurationProvider = platformConfigurationProvider;
        this.metaAccessExtensionProvider = metaAccessExtensionProvider;
        this.loopsDataProvider = loopsDataProvider;
        this.codeCache = codeCache;
        this.snippetReflection = snippetReflection;
        this.wordTypes = wordTypes;
        this.identityHashCodeProvider = identityHashCodeProvider;
    }

    public Providers(Providers copyFrom) {
        this(copyFrom.getMetaAccess(), copyFrom.getCodeCache(), copyFrom.getConstantReflection(), copyFrom.getConstantFieldProvider(), copyFrom.getForeignCalls(), copyFrom.getLowerer(),
                        copyFrom.getReplacements(), copyFrom.getStampProvider(), copyFrom.getPlatformConfigurationProvider(), copyFrom.getMetaAccessExtensionProvider(),
                        copyFrom.getSnippetReflection(), copyFrom.getWordTypes(), copyFrom.getLoopsDataProvider(), copyFrom.getIdentityHashCodeProvider());
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    @Override
    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    @Override
    public ConstantFieldProvider getConstantFieldProvider() {
        return constantFieldProvider;
    }

    @Override
    public LoweringProvider getLowerer() {
        return lowerer;
    }

    @Override
    public Replacements getReplacements() {
        return replacements;
    }

    @Override
    public StampProvider getStampProvider() {
        return stampProvider;
    }

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return foreignCalls;
    }

    @Override
    public PlatformConfigurationProvider getPlatformConfigurationProvider() {
        return platformConfigurationProvider;
    }

    @Override
    public MetaAccessExtensionProvider getMetaAccessExtensionProvider() {
        return metaAccessExtensionProvider;
    }

    @Override
    public LoopsDataProvider getLoopsDataProvider() {
        return loopsDataProvider;
    }

    @Override
    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    @Override
    public IdentityHashCodeProvider getIdentityHashCodeProvider() {
        return identityHashCodeProvider;
    }

    @Override
    public WordTypes getWordTypes() {
        return wordTypes;
    }

    public Providers copyWith(ConstantReflectionProvider substitution) {
        assert this.getClass() == Providers.class : "must override";
        return new Providers(metaAccess, codeCache, substitution, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider,
                        snippetReflection, wordTypes, loopsDataProvider, identityHashCodeProvider);
    }

    public Providers copyWith(ConstantFieldProvider substitution) {
        assert this.getClass() == Providers.class : "must override";
        return new Providers(metaAccess, codeCache, constantReflection, substitution, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider,
                        snippetReflection, wordTypes, loopsDataProvider, identityHashCodeProvider);
    }

    public Providers copyWith(Replacements substitution) {
        assert this.getClass() == Providers.class : "must override in " + getClass();
        return new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, substitution, stampProvider, platformConfigurationProvider,
                        metaAccessExtensionProvider, snippetReflection, wordTypes, loopsDataProvider, identityHashCodeProvider);
    }

    public Providers copyWith(MetaAccessExtensionProvider substitution) {
        assert this.getClass() == Providers.class : getClass() + " must override";
        return new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, substitution,
                        snippetReflection, wordTypes, loopsDataProvider, identityHashCodeProvider);
    }
}
