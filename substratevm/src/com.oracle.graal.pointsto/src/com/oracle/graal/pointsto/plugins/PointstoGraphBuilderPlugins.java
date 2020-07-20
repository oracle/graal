/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.plugins;

import java.util.Arrays;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;

import com.oracle.graal.pointsto.nodes.AnalysisArraysCopyOfNode;
import com.oracle.graal.pointsto.nodes.AnalysisObjectCloneNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PointstoGraphBuilderPlugins {

    public static void registerArraysPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Arrays.class).setAllowOverwrite(true);

        r.register2("copyOf", Object[].class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode newLength) {
                b.addPush(JavaKind.Object, new AnalysisArraysCopyOfNode(b.getInvokeReturnStamp(b.getAssumptions()).getTrustedStamp(), original, newLength));
                return true;
            }
        });

        r.register3("copyOf", Object[].class, int.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode newLength, ValueNode newObjectElementType) {
                if (newObjectElementType instanceof GetClassNode || newObjectElementType.isConstant()) {
                    // We can infer the concrete type of the new array
                    b.addPush(JavaKind.Object, new AnalysisArraysCopyOfNode(b.getInvokeReturnStamp(b.getAssumptions()).getTrustedStamp(), original, newLength, newObjectElementType));
                    return true;
                }
                return false;
            }
        });
    }

    public static void registerSystemPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, System.class).setAllowOverwrite(true);
        r.register5("arraycopy", Object.class, int.class, Object.class, int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length) {
                b.add(new BasicArrayCopyNode(BasicArrayCopyNode.TYPE, src, srcPos, dest, destPos, length, null, b.bci()));
                return true;
            }
        });
    }

    public static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Object.class);
        r.register1("clone", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Object, new AnalysisObjectCloneNode(MacroParams.of(b, targetMethod, object)));
                return true;
            }
        });
    }

}
