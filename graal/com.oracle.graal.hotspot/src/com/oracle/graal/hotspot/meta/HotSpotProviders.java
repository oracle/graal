/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import jdk.internal.jvmci.hotspot.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;

/**
 * Extends {@link Providers} to include a number of extra capabilities used by the HotSpot parts of
 * the compiler.
 */
public class HotSpotProviders extends Providers {

    private final SuitesProvider suites;
    private final HotSpotRegistersProvider registers;
    private final SnippetReflectionProvider snippetReflection;
    private final HotSpotWordTypes wordTypes;
    private final Plugins graphBuilderPlugins;

    public HotSpotProviders(MetaAccessProvider metaAccess, HotSpotCodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, HotSpotForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer, Replacements replacements, SuitesProvider suites, HotSpotRegistersProvider registers, SnippetReflectionProvider snippetReflection,
                    HotSpotWordTypes wordTypes, Plugins graphBuilderPlugins) {
        super(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, new HotSpotStampProvider());
        this.suites = suites;
        this.registers = registers;
        this.snippetReflection = snippetReflection;
        this.wordTypes = wordTypes;
        this.graphBuilderPlugins = graphBuilderPlugins;
    }

    @Override
    public HotSpotCodeCacheProvider getCodeCache() {
        return (HotSpotCodeCacheProvider) super.getCodeCache();
    }

    @Override
    public HotSpotForeignCallsProvider getForeignCalls() {
        return (HotSpotForeignCallsProvider) super.getForeignCalls();
    }

    public SuitesProvider getSuites() {
        return suites;
    }

    public HotSpotRegistersProvider getRegisters() {
        return registers;
    }

    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    public Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    public HotSpotWordTypes getWordTypes() {
        return wordTypes;
    }
}
