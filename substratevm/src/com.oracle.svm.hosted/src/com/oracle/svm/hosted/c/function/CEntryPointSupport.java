/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.function;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode.UtilityAction;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.hosted.nodes.ReadReservedRegister;

import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
public class CEntryPointSupport implements InternalFeature {
    @Override
    public void registerInvocationPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        registerEntryPointActionsPlugins(plugins.getInvocationPlugins());
        registerCurrentIsolatePlugins(plugins.getInvocationPlugins());
    }

    private static void registerEntryPointActionsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CEntryPointActions.class);
        r.register(new RequiredInvocationPlugin("enterCreateIsolate", CEntryPointCreateIsolateParameters.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode parameters) {
                b.addPush(JavaKind.Int, CEntryPointEnterNode.createIsolate(parameters));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("enterAttachThread", Isolate.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate, ValueNode ensureJavaThreadNode) {
                if (!ensureJavaThreadNode.isConstant()) {
                    throw b.bailout("Parameter ensureJavaThread of enterAttachThread must be a compile time constant");
                }
                b.addPush(JavaKind.Int, CEntryPointEnterNode.attachThread(isolate, false, ensureJavaThreadNode.asJavaConstant().asInt() != 0));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("enterAttachThread", Isolate.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate, ValueNode startedByIsolate, ValueNode ensureJavaThread) {
                if (!startedByIsolate.isConstant() || !ensureJavaThread.isConstant()) {
                    throw b.bailout("Parameters ensureJavaThread and startedByIsolate of enterAttachThread must be a compile time constant");
                }
                b.addPush(JavaKind.Int, CEntryPointEnterNode.attachThread(isolate, startedByIsolate.asJavaConstant().asInt() != 0, ensureJavaThread.asJavaConstant().asInt() != 0));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("enter", IsolateThread.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode thread) {
                b.addPush(JavaKind.Int, CEntryPointEnterNode.enter(thread));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("enterByIsolate", Isolate.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Int, CEntryPointEnterNode.enterByIsolate(isolate));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("leave") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                StateSplitProxyNode proxy = b.append(new StateSplitProxyNode());
                proxy.setStateAfter(b.getInvocationPluginBeforeState());
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.Leave));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("leaveDetachThread") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                StateSplitProxyNode proxy = b.append(new StateSplitProxyNode());
                proxy.setStateAfter(b.getInvocationPluginBeforeState());
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.DetachThread));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("leaveTearDownIsolate") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                StateSplitProxyNode proxy = b.append(new StateSplitProxyNode());
                proxy.setStateAfter(b.getInvocationPluginBeforeState());
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.TearDownIsolate));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("failFatally", int.class, CCharPointer.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                b.add(new CEntryPointUtilityNode(UtilityAction.FailFatally, arg1, arg2));

                /*
                 * FailFatally does not return, so we can cut out any control flow afterwards and
                 * set the probability of the IfNode that leads to this branch.
                 */
                LoweredDeadEndNode deadEndNode = b.add(new LoweredDeadEndNode());
                AbstractBeginNode prevBegin = AbstractBeginNode.prevBegin(deadEndNode);
                if (prevBegin != null && prevBegin.predecessor() instanceof IfNode) {
                    ((IfNode) prevBegin.predecessor()).setProbability(prevBegin, BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROFILE);
                }
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("isCurrentThreadAttachedTo", Isolate.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Boolean, new CEntryPointUtilityNode(UtilityAction.IsAttached, isolate));
                return true;
            }
        });
    }

    private static void registerCurrentIsolatePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CurrentIsolate.class);
        r.register(new RequiredInvocationPlugin("getCurrentThread") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, ReadReservedRegister.createReadIsolateThreadNode(b.getGraph()));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("getIsolate") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, ReadReservedRegister.createReadHeapBaseNode(b.getGraph()));
                return true;
            }
        });
    }
}
