/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.truffle;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeSystemProperties;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.Providers;

@AutomaticallyRegisteredFeature
public class WebImageTruffleFeature implements InternalFeature {

    public static class Options {
        //@formatter:off
        @Option(help = "Adds Truffle to the image compilation")
        public static final HostedOptionKey<Boolean> EnableTruffle = new HostedOptionKey<>(false);
        //@formatter:on
    }

    public static boolean isEnabled() {
        return Options.EnableTruffle.getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Class<? extends Feature>> getRequiredFeatures() {
        var classNames = new String[]{
                        /*
                         * Make sure this runs after TruffleBaseFeature because we overwrite some
                         * its graph builder plugins
                         */
                        "com.oracle.svm.truffle.TruffleBaseFeature",
                        /*
                         * Run after WebImageFeature because that registers
                         * RuntimeSystemPropertiesSupport
                         */
                        "com.oracle.svm.hosted.webimage.WebImageFeature",
        };

        List<Class<? extends Feature>> featureClasses = new ArrayList<>(classNames.length);
        for (String className : classNames) {
            featureClasses.add((Class<? extends Feature>) ReflectionUtil.lookupClass(className));
        }

        return featureClasses;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isEnabled();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        WebImageTruffleGraphBuilderPlugins.register(plugins.getInvocationPlugins());
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // Exceptions from the default class initialization rules.
        // Similar to com.oracle.svm.hosted.jdk.JDKInitializationFeature

        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);

        rci.initializeAtBuildTime("com.oracle.truffle", "initialized by com.oracle.svm.truffle");
        rci.initializeAtBuildTime("org.graalvm.wasm", "initialized by com.oracle.svm.truffle");
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        /*
         * Turns off warning that truffle runs in interpreter mode without runtime compilation. Web
         * Image does not support runtime compilation.
         */
        RuntimeSystemProperties.register("polyglot.engine.WarnInterpreterOnly", "false");
    }
}
