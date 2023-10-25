/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.hotspot;

import jdk.graal.compiler.core.phases.CommunityCompilerConfiguration;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.truffle.host.InjectImmutableFrameFieldsPhase;
import jdk.graal.compiler.truffle.host.HostInliningPhase;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;

import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.code.Architecture;

/**
 * Central place to register Truffle related compiler phases and plugins for host Java compilation
 * on HotSpot.
 * <p>
 * Note that this configuration is also used as basis for Truffle guest compilation on HotSpot.
 * Therefore make sure that phases which are only relevant for host compilations are explicitly
 * disabled for Truffle guest compilation in
 * {@link HotSpotTruffleCompilerImpl#create(TruffleCompilerRuntime)}.
 * <p>
 * Note that on SubstrateVM TruffleBaseFeature and TruffleFeature must be used for this purpose,
 * this configuration is NOT loaded. So make sure SVM configuration is in sync if you make changes
 * here.
 */
public final class TruffleCommunityCompilerConfiguration extends CommunityCompilerConfiguration {

    @Override
    public HighTier createHighTier(OptionValues options) {
        HighTier highTier = super.createHighTier(options);
        installCommunityHighTier(options, highTier);
        return highTier;
    }

    public static void installCommunityHighTier(OptionValues options, HighTier defaultHighTier) {
        HostInliningPhase.install(defaultHighTier, options);
        InjectImmutableFrameFieldsPhase.install(defaultHighTier, options);
    }

    @Override
    public void registerGraphBuilderPlugins(Architecture arch, Plugins plugins, OptionValues options, Replacements replacements) {
        super.registerGraphBuilderPlugins(arch, plugins, options, replacements);
        registerCommunityGraphBuilderPlugins(arch, plugins, options, replacements);
    }

    public static void registerCommunityGraphBuilderPlugins(Architecture arch, Plugins plugins, OptionValues options, Replacements replacements) {
        HostInliningPhase.installInlineInvokePlugin(plugins, options);
        plugins.getInvocationPlugins().defer(new Runnable() {
            @Override
            public void run() {
                TruffleInvocationPlugins.register(arch, plugins.getInvocationPlugins(), replacements);
            }
        });
    }

}
