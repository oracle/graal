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

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.config.ProxyConfigurationParser;
import com.oracle.svm.reflect.hosted.ReflectionFeature;
import com.oracle.svm.reflect.proxy.DynamicProxySupport;

@AutomaticFeature
public final class DynamicProxyFeature implements Feature {

    public static class Options {

        @Option(help = "file:doc-files/ProxyConfigurationFilesHelp.txt", type = OptionType.User)//
        public static final HostedOptionKey<String> DynamicProxyConfigurationFiles = new HostedOptionKey<>("");

        @Option(help = "Resources describing program elements to be made available for reflection (see ProxyConfigurationFiles).", type = OptionType.User)//
        public static final HostedOptionKey<String> DynamicProxyConfigurationResources = new HostedOptionKey<>("");
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(ReflectionFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;

        DynamicProxySupport dynamicProxySupport = new DynamicProxySupport(access.getImageClassLoader().getClassLoader());
        ImageSingletons.add(DynamicProxyRegistry.class, dynamicProxySupport);

        ProxyConfigurationParser parser = new ProxyConfigurationParser(access.getImageClassLoader(), dynamicProxySupport);
        parser.parseAndRegisterConfigurations("dynamic proxy", Options.DynamicProxyConfigurationFiles, Options.DynamicProxyConfigurationResources);
    }
}
