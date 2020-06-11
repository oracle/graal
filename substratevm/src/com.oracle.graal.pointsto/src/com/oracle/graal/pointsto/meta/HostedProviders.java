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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class HostedProviders extends Providers {

    private GraphBuilderConfiguration.Plugins graphBuilderPlugins;

    public HostedProviders(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    ForeignCallsProvider foreignCalls, LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, SnippetReflectionProvider snippetReflection,
                    WordTypes wordTypes, PlatformConfigurationProvider platformConfigurationProvider, MetaAccessExtensionProvider metaAccessExtensionProvider) {
        super(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider,
                        snippetReflection, wordTypes);
    }

    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    public void setGraphBuilderPlugins(GraphBuilderConfiguration.Plugins graphBuilderPlugins) {
        this.graphBuilderPlugins = graphBuilderPlugins;
    }

    @Override
    public Providers copyWith(ConstantReflectionProvider substitution) {
        assert this.getClass() == HostedProviders.class : "must override in " + getClass();
        return new HostedProviders(getMetaAccess(), getCodeCache(), substitution, getConstantFieldProvider(), getForeignCalls(), getLowerer(), getReplacements(), getStampProvider(),
                        getSnippetReflection(), getWordTypes(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider());
    }

    @Override
    public Providers copyWith(ConstantFieldProvider substitution) {
        assert this.getClass() == HostedProviders.class : "must override in " + getClass();
        return new HostedProviders(getMetaAccess(), getCodeCache(), getConstantReflection(), substitution, getForeignCalls(), getLowerer(), getReplacements(), getStampProvider(),
                        getSnippetReflection(), getWordTypes(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider());
    }

    @Override
    public Providers copyWith(Replacements substitution) {
        assert this.getClass() == HostedProviders.class : "must override in " + getClass();
        return new HostedProviders(getMetaAccess(), getCodeCache(), getConstantReflection(), getConstantFieldProvider(), getForeignCalls(), getLowerer(), substitution, getStampProvider(),
                        getSnippetReflection(), getWordTypes(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider());
    }
}
