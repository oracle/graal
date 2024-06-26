/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.hosted;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.PreMainSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.reflect.ReflectionFeature;

import java.io.IOException;
import java.util.jar.JarFile;

/**
 * This feature supports instrumentation in native image.
 */
@AutomaticallyRegisteredFeature
public class InstrumentFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.JavaAgent.getValue().values().isEmpty();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        /* Many instrumentation methods are called via reflection, e.g. premain. */
        return List.of(ReflectionFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl a = (FeatureImpl.AfterRegistrationAccessImpl) access;
        ClassLoader cl = a.getImageClassLoader().getClassLoader();
        PreMainSupport support = new PreMainSupport();
        ImageSingletons.add(PreMainSupport.class, support);

        List<String> agentOptions = SubstrateOptions.JavaAgent.getValue().values();
        for (String agentOption : agentOptions) {
            addPremainClass(support, cl, agentOption);
        }
    }

    private static void addPremainClass(PreMainSupport support, ClassLoader cl, String javaagentOption) {
        int separatorIndex = javaagentOption.indexOf("=");
        String agent;
        String premainClass = null;
        String options = "";
        // Get the agent file
        if (separatorIndex == -1) {
            agent = javaagentOption;
        } else {
            agent = javaagentOption.substring(0, separatorIndex);
            options = javaagentOption.substring(separatorIndex + 1);
        }
        // Read MANIFEST in agent jar
        try {
            JarFile agentJarFile = new JarFile(agent);
            premainClass = agentJarFile.getManifest().getMainAttributes().getValue("Premain-Class");
        } catch (IOException e) {
            // This should never happen because the image build process (HotSpot) already loaded the
            // agent during startup.
            throw UserError.abort(e, "Can't read the agent jar %s. Please check option %s", agent,
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.JavaAgent, ""));
        }

        try {
            Class<?> clazz = Class.forName(premainClass, false, cl);
            Method premain = findPremainMethod(premainClass, clazz);

            List<Object> args = new ArrayList<>();
            /* The first argument contains the premain options, which will be set at runtime. */
            args.add(options);
            if (premain.getParameterCount() == 2) {
                args.add(new PreMainSupport.NativeImageNoOpRuntimeInstrumentation());
            }

            support.registerPremainMethod(premainClass, premain, args.toArray(new Object[0]));
        } catch (ClassNotFoundException e) {
            throw UserError.abort("Could not register agent premain method because class %s was not found. Please check your %s setting.", premainClass,
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.JavaAgent, ""));
        }
    }

    /** Find the premain method from the given class. */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+25/src/java.instrument/share/classes/sun/instrument/InstrumentationImpl.java#L481-L548")
    private static Method findPremainMethod(String premainClass, Class<?> javaAgentClass) {
        try {
            return javaAgentClass.getDeclaredMethod("premain", String.class, Instrumentation.class);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return javaAgentClass.getDeclaredMethod("premain", String.class);
        } catch (NoSuchMethodException ignored) {
        }

        throw UserError.abort("Could not register agent premain method: class %s neither declares 'premain(String, Instrumentation)' nor 'premain(String)'.", premainClass);
    }
}
