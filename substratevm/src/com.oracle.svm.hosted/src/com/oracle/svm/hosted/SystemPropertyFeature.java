/*
 * Copyright (c)  2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.phases.util.Providers;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@AutomaticFeature
public class SystemPropertyFeature implements GraalFeature {

    private final Set<String> systemPropertyCalls = ConcurrentHashMap.newKeySet();

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        if (!systemPropertyCalls.isEmpty()) {
            StringBuilder sb = new StringBuilder("\n\nNative image undefined system properties are used in the program:\n");
            for (String s : systemPropertyCalls) {
                sb.append("    ").append(s).append("\n");
            }
            sb.append("The returned value of calling System.getProperty with these keys will be null in native image.\n");
            sb.append("Please make sure the expected property values are specified with -D when running the native image program or just avoid using them.\n\n");
            System.out.println(sb.toString());
        }
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(invocationPlugins, System.class);
        r.register1("getProperty", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name) {
                if (name.isConstant()) {
                    String propertyKey = snippetReflection.asObject(String.class, name.asJavaConstant());
                    if (SystemPropertiesSupport.isUndefined(propertyKey)) {
                        systemPropertyCalls.add("Undefined property: " + propertyKey + " is used at " + b.getCode().asStackTraceElement(b.bci()));
                    }
                }
                return false;
            }
        });
    }
}
