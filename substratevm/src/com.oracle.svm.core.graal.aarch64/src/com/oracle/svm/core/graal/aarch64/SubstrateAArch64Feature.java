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
package com.oracle.svm.core.graal.aarch64;

import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.replacements.aarch64.AArch64GraphBuilderPlugins;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig.ConfigKind;

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;

@AutomaticFeature
@Platforms(Platform.AARCH64.class)
class SubstrateAArch64Feature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {

        ImageSingletons.add(SubstrateRegisterConfigFactory.class, new SubstrateRegisterConfigFactory() {
            @Override
            public RegisterConfig newRegisterFactory(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, Boolean preserveFramePointer) {
                return new SubstrateAArch64RegisterConfig(config, metaAccess, target, preserveFramePointer);
            }
        });

        ImageSingletons.add(ReservedRegisters.class, new AArch64ReservedRegisters());

        if (!SubstrateOptions.useLLVMBackend()) {
            AArch64CalleeSavedRegisters.createAndRegister();

            ImageSingletons.add(SubstrateBackendFactory.class, new SubstrateBackendFactory() {
                @Override
                public SubstrateBackend newBackend(Providers newProviders) {
                    return new SubstrateAArch64Backend(newProviders);
                }
            });

            ImageSingletons.add(SubstrateLoweringProviderFactory.class, new SubstrateLoweringProviderFactory() {
                @Override
                public DefaultJavaLoweringProvider newLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                                MetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
                    return new SubstrateAArch64LoweringProvider(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target);
                }
            });

            ImageSingletons.add(TargetGraphBuilderPlugins.class, new AArch64GraphBuilderPlugins());
            ImageSingletons.add(SubstrateSuitesCreatorProvider.class, new SubstrateAArch64SuitesCreatorProvider());
        }
    }
}
