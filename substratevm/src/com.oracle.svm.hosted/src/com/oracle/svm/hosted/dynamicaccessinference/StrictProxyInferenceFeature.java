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
package com.oracle.svm.hosted.dynamicaccessinference;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
public class StrictProxyInferenceFeature implements InternalFeature {

    private ConstantExpressionRegistry registry;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(StrictDynamicAccessInferenceFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        StrictDynamicAccessInferenceFeature.Options.Mode mode = StrictDynamicAccessInferenceFeature.Options.StrictDynamicAccessInference.getValue();
        return mode == StrictDynamicAccessInferenceFeature.Options.Mode.Enforce;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        registry = ConstantExpressionRegistry.singleton();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (reason != ParsingReason.PointsToAnalysis) {
            return;
        }

        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();

        Method getProxyClass = ReflectionUtil.lookupMethod(Proxy.class, "getProxyClass", ClassLoader.class, Class[].class);
        Method newProxyInstance = ReflectionUtil.lookupMethod(Proxy.class, "newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class);

        for (Method method : List.of(getProxyClass, newProxyInstance)) {
            invocationPlugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), method.getParameterTypes()) {
                @Override
                public boolean isDecorator() {
                    return true;
                }

                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    Class<?>[] interfaces = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 1, Class[].class);
                    return registerProxy(b, reason, interfaces);
                }
            });
        }
    }

    private boolean registerProxy(GraphBuilderContext b, ParsingReason reason, Class<?>[] interfaces) {
        if (interfaces == null) {
            return false;
        }

        b.add(ReachabilityRegistrationNode.create(() -> RuntimeProxyCreation.register(interfaces), reason));
        return true;
    }
}
