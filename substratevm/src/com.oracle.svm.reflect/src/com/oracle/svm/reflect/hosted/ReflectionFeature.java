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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ReflectionConfigurationParser;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.snippets.ReflectionPlugins;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

@AutomaticFeature
public final class ReflectionFeature implements GraalFeature {

    private AnnotationSubstitutionProcessor annotationSubstitutions;

    private ReflectionDataBuilder reflectionData;
    private ImageClassLoader loader;
    private SVMHost hostVM;
    private int loadedConfigurations;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        hostVM = access.getHostVM();

        ReflectionSubstitution subst = new ReflectionSubstitution(access.getMetaAccess().getWrapped(), access.getHostVM().getClassInitializationSupport(), access.getImageClassLoader());
        access.registerSubstitutionProcessor(subst);
        ImageSingletons.add(ReflectionSubstitution.class, subst);

        reflectionData = new ReflectionDataBuilder(access);
        ImageSingletons.add(RuntimeReflectionSupport.class, reflectionData);

        ReflectionConfigurationParser<Class<?>> parser = ConfigurationParserUtils.create(reflectionData, access.getImageClassLoader());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "reflection",
                        ConfigurationFiles.Options.ReflectionConfigurationFiles, ConfigurationFiles.Options.ReflectionConfigurationResources,
                        ConfigurationFiles.REFLECTION_NAME);

        loader = access.getImageClassLoader();
        annotationSubstitutions = ((Inflation) access.getBigBang()).getAnnotationSubstitutionProcessor();
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
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(FallbackFeature.class)) {
            return;
        }
        FallbackFeature.FallbackImageRequest reflectionFallback = ImageSingletons.lookup(FallbackFeature.class).reflectionFallback;
        if (reflectionFallback != null && loadedConfigurations == 0) {
            throw reflectionFallback;
        }
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        /*
         * The reflection invocation plugins need to be registered only when reflection is enabled
         * since it adds Field and Method objects to the image heap which otherwise are not allowed.
         */
        ReflectionPlugins.registerInvocationPlugins(loader, snippetReflection, annotationSubstitutions, invocationPlugins, hostVM, analysis, hosted);
    }
}
