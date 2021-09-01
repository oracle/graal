/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.spi;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class CoreProvidersImpl implements CoreProviders {
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
    protected final WordVerification wordVerification;

    protected CoreProvidersImpl(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, LoweringProvider lowerer,
                    Replacements replacements, StampProvider stampProvider, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfigurationProvider,
                    MetaAccessExtensionProvider metaAccessExtensionProvider, LoopsDataProvider loopsDataProvider, WordVerification wordVerification) {
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
        this.wordVerification = wordVerification;
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
    public WordVerification getWordVerification() {
        return wordVerification;
    }

    public CoreProvidersImpl copyWith(ConstantReflectionProvider substitution) {
        assert this.getClass() == CoreProvidersImpl.class : "must override in " + getClass();
        return new CoreProvidersImpl(metaAccess, substitution, constantFieldProvider, lowerer, replacements, stampProvider, foreignCalls, platformConfigurationProvider, metaAccessExtensionProvider,
                        loopsDataProvider, wordVerification);
    }

    public CoreProvidersImpl copyWith(ConstantFieldProvider substitution) {
        assert this.getClass() == CoreProvidersImpl.class : "must override in " + getClass();
        return new CoreProvidersImpl(metaAccess, constantReflection, substitution, lowerer, replacements, stampProvider, foreignCalls, platformConfigurationProvider, metaAccessExtensionProvider,
                        loopsDataProvider, wordVerification);
    }

    public CoreProvidersImpl copyWith(Replacements substitution) {
        assert this.getClass() == CoreProvidersImpl.class : "must override in " + getClass();
        return new CoreProvidersImpl(metaAccess, constantReflection, constantFieldProvider, lowerer, substitution, stampProvider, foreignCalls, platformConfigurationProvider,
                        metaAccessExtensionProvider, loopsDataProvider, wordVerification);
    }
}
