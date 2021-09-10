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
package com.oracle.svm.reflect.proxy.hosted;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ProxyConfigurationParser;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.hosted.ConfigurationTypeResolver;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.reflect.hosted.ReflectionFeature;
import com.oracle.svm.reflect.proxy.DynamicProxySupport;

@AutomaticFeature
public final class DynamicProxyFeature implements Feature {
    private int loadedConfigurations;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(ReflectionFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;

        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        DynamicProxySupport dynamicProxySupport = new DynamicProxySupport(imageClassLoader.getClassLoader());
        ImageSingletons.add(DynamicProxyRegistry.class, dynamicProxySupport);
        ConfigurationTypeResolver typeResolver = new ConfigurationTypeResolver("resource configuration", imageClassLoader, NativeImageOptions.AllowIncompleteClasspath.getValue());
        ProxyRegistry proxyRegistry = new ProxyRegistry(typeResolver, dynamicProxySupport, imageClassLoader);
        ImageSingletons.add(ProxyRegistry.class, proxyRegistry);
        ProxyConfigurationParser parser = new ProxyConfigurationParser(proxyRegistry, ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "dynamic proxy",
                        ConfigurationFiles.Options.DynamicProxyConfigurationFiles, ConfigurationFiles.Options.DynamicProxyConfigurationResources,
                        ConfigurationFile.DYNAMIC_PROXY.getFileName());
    }

    private static ProxyRegistry proxyRegistry() {
        return ImageSingletons.lookup(ProxyRegistry.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        proxyRegistry().flushConditionalConfiguration(access);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        proxyRegistry().flushConditionalConfiguration(access);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(FallbackFeature.class)) {
            return;
        }
        FallbackFeature.FallbackImageRequest proxyFallback = ImageSingletons.lookup(FallbackFeature.class).proxyFallback;
        if (proxyFallback != null && loadedConfigurations == 0) {
            throw proxyFallback;
        }
    }
}
