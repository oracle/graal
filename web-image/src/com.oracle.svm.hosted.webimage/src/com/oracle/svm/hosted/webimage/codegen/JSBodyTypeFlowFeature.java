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

package com.oracle.svm.hosted.webimage.codegen;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Set;

import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.webimage.codegen.node.InterceptJSInvokeNode;
import com.oracle.svm.hosted.webimage.util.ReflectUtil;
import com.oracle.svm.webimage.platform.WebImagePlatform;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Inserts {@link InterceptJSInvokeNode} into the IR at any invoke that may target a
 * {@link org.graalvm.webimage.api.JS} annotated method to deal with types that may leak to
 * JavaScript.
 * <p>
 * TODO GR-62854 Merge into JSBodyFeature once it is enabled by default
 */
@AutomaticallyRegisteredFeature
@Platforms(WebImagePlatform.class)
public class JSBodyTypeFlowFeature implements InternalFeature {
    /**
     * The set of methods that are potentially overridden by a JS-annotated method.
     * <p>
     * Any call to such a method needs to have a {@link InterceptJSInvokeNode} attached to it.
     */
    private Set<Method> jsOverridden;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;
        ImageClassLoader imageClassLoader = accessImpl.getImageClassLoader();
        jsOverridden = ReflectUtil.findBaseMethodsOfJSAnnotated(imageClassLoader);
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        plugins.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (canBeJavaScriptCall((AnalysisMethod) method)) {
                    InterceptJSInvokeNode intercept = b.append(new InterceptJSInvokeNode(method, b.bci()));
                    for (final ValueNode arg : args) {
                        intercept.arguments().add(arg);
                    }
                }
                return false;
            }

            private boolean canBeJavaScriptCall(AnalysisMethod method) {
                Executable executable;
                try {
                    executable = method.getJavaMethod();
                } catch (Throwable e) {
                    // Either a substituted method, or a method with a malformed bytecode signature.
                    // This is most likely not a JS-annotated method.
                    return false;
                }
                if (executable instanceof Method) {
                    return jsOverridden.contains(executable);
                }
                // Not a normal method (constructor).
                return false;
            }

        });
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        jsOverridden = null;
    }
}
