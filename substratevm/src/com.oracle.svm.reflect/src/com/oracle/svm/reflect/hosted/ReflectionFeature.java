/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

import java.util.function.BooleanSupplier;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.config.ReflectionConfigurationParser;
import com.oracle.svm.hosted.snippets.ReflectionPlugins;

@AutomaticFeature
public final class ReflectionFeature implements GraalFeature {

    public static class Options {
        @Option(help = "Enable support for reflection at run time")//
        public static final HostedOptionKey<Boolean> ReflectionEnabled = new HostedOptionKey<>(true);

        @Option(help = "file:doc-files/ReflectionConfigurationFilesHelp.txt", type = OptionType.User)//
        public static final HostedOptionKey<String> ReflectionConfigurationFiles = new HostedOptionKey<>("");

        @Option(help = "Resources describing program elements to be made available for reflection (see ReflectionConfigurationFiles).", type = OptionType.User)//
        public static final HostedOptionKey<String> ReflectionConfigurationResources = new HostedOptionKey<>("");
    }

    public static class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ReflectionFeature.class);
        }
    }

    public static class IsDisabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return !ImageSingletons.contains(ReflectionFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        Boolean result = Options.ReflectionEnabled.getValue();
        if (!result && (!Options.ReflectionConfigurationFiles.getValue().isEmpty() || !Options.ReflectionConfigurationResources.getValue().isEmpty())) {
            throw UserError.abort("The options " + Options.ReflectionConfigurationFiles.getName() + " and " + Options.ReflectionConfigurationResources.getName() +
                            " can only be used when the option " + Options.ReflectionEnabled.getName() + " is set to true");
        }
        return result;

    }

    private ReflectionDataBuilder reflectionData;
    private ImageClassLoader loader;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;

        ReflectionSubstitution subst = new ReflectionSubstitution(access.getMetaAccess().getWrapped(), access.getImageClassLoader());
        access.registerSubstitutionProcessor(subst);
        ImageSingletons.add(ReflectionSubstitution.class, subst);

        reflectionData = new ReflectionDataBuilder();
        ImageSingletons.add(RuntimeReflectionSupport.class, reflectionData);

        ReflectionConfigurationParser parser = new ReflectionConfigurationParser(reflectionData, access.getImageClassLoader());
        parser.parseAndRegisterConfigurations("reflection", Options.ReflectionConfigurationFiles, Options.ReflectionConfigurationResources);

        loader = access.getImageClassLoader();
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        reflectionData.duringAnalysis(access);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        reflectionData.afterAnalysis();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        /*
         * The reflection invocation plugins need to be registered only when reflection is enabled
         * since it adds Field and Method objects to the image heap which otherwise are not allowed.
         */
        ReflectionPlugins.registerInvocationPlugins(loader, snippetReflection, invocationPlugins, analysis, hosted);
    }
}
