/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.layeredimagesingleton;

import java.lang.reflect.Array;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Adds support for layered image singleton features within traditional builds.
 */
@AutomaticallyRegisteredFeature
public class NonLayeredImageSingletonFeature implements InternalFeature, FeatureSingleton {

    ConcurrentHashMap<Class<?>, Object> multiLayeredArrays = new ConcurrentHashMap<>();

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return !ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins.getInvocationPlugins(), MultiLayeredImageSingleton.class);
        r.register(new InvocationPlugin.RequiredInvocationPlugin("getAllLayers", Class.class) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode classNode) {
                Class<?> key = b.getSnippetReflection().asObject(Class.class, classNode.asJavaConstant());

                Object singleton = LayeredImageSingletonSupport.singleton().runtimeLookup(key);
                boolean conditions = singleton.getClass().equals(key) &&
                                singleton instanceof MultiLayeredImageSingleton multiLayerSingleton &&
                                multiLayerSingleton.getImageBuilderFlags().contains(LayeredImageSingletonBuilderFlags.RUNTIME_ACCESS);
                VMError.guarantee(conditions, "Illegal singleton %s", singleton);

                var multiLayeredArray = multiLayeredArrays.computeIfAbsent(key, k -> {
                    var result = Array.newInstance(k, 1);
                    Array.set(result, 0, singleton);
                    return result;
                });

                b.addPush(JavaKind.Object, ConstantNode.forConstant(b.getSnippetReflection().forObject(multiLayeredArray), b.getMetaAccess()));
                return true;
            }
        });
    }
}
