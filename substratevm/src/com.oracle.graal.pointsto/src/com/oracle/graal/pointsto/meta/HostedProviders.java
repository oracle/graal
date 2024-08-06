/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class HostedProviders extends Providers {

    private GraphBuilderConfiguration.Plugins graphBuilderPlugins;

    public HostedProviders(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    ForeignCallsProvider foreignCalls, LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, SnippetReflectionProvider snippetReflection,
                    WordTypes wordTypes, PlatformConfigurationProvider platformConfigurationProvider, MetaAccessExtensionProvider metaAccessExtensionProvider, LoopsDataProvider loopsDataProvider,
                    IdentityHashCodeProvider identityHashCodeProvider) {
        super(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider,
                        snippetReflection, wordTypes, loopsDataProvider, identityHashCodeProvider);
    }

    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    public void setGraphBuilderPlugins(GraphBuilderConfiguration.Plugins graphBuilderPlugins) {
        this.graphBuilderPlugins = graphBuilderPlugins;
    }

    @Override
    public HostedProviders copyWith(ConstantReflectionProvider substitution) {
        assert this.getClass() == HostedProviders.class : "must override in " + getClass();
        return new HostedProviders(getMetaAccess(), getCodeCache(), substitution, getConstantFieldProvider(), getForeignCalls(), getLowerer(), getReplacements(), getStampProvider(),
                        getSnippetReflection(), getWordTypes(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider(), getLoopsDataProvider(), getIdentityHashCodeProvider());
    }

    @Override
    public HostedProviders copyWith(ConstantFieldProvider substitution) {
        assert this.getClass() == HostedProviders.class : "must override in " + getClass();
        return new HostedProviders(getMetaAccess(), getCodeCache(), getConstantReflection(), substitution, getForeignCalls(), getLowerer(), getReplacements(), getStampProvider(),
                        getSnippetReflection(), getWordTypes(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider(), getLoopsDataProvider(), getIdentityHashCodeProvider());
    }

    @Override
    public HostedProviders copyWith(Replacements substitution) {
        assert this.getClass() == HostedProviders.class : "must override in " + getClass();
        return new HostedProviders(getMetaAccess(), getCodeCache(), getConstantReflection(), getConstantFieldProvider(), getForeignCalls(), getLowerer(), substitution, getStampProvider(),
                        getSnippetReflection(), getWordTypes(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider(), getLoopsDataProvider(), getIdentityHashCodeProvider());
    }
}
