/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.CEntryPointPrologueBailoutNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode.UtilityAction;
import com.oracle.svm.core.graal.nodes.ReadHeapBaseFixedNode;
import com.oracle.svm.core.graal.nodes.ReadIsolateThreadFixedNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class CEntryPointSupport implements GraalFeature {
    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        registerEntryPointActionsPlugins(invocationPlugins);
        registerCurrentIsolatePlugins(invocationPlugins);
    }

    private static void registerEntryPointActionsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CEntryPointActions.class);
        r.register1("enterCreateIsolate", CEntryPointCreateIsolateParameters.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode parameters) {
                b.addPush(JavaKind.Int, CEntryPointEnterNode.createIsolate(parameters));
                return true;
            }
        });
        r.register2("enterAttachThread", Isolate.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate, ValueNode ensureJavaThreadNode) {
                if (!ensureJavaThreadNode.isConstant()) {
                    b.bailout("Parameter ensureJavaThread of enterAttachThread must be a compile time constant");
                }
                b.addPush(JavaKind.Int, CEntryPointEnterNode.attachThread(isolate, ensureJavaThreadNode.asJavaConstant().asInt() != 0));
                return true;
            }
        });
        r.register1("enter", IsolateThread.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode thread) {
                b.addPush(JavaKind.Int, CEntryPointEnterNode.enter(thread));
                return true;
            }
        });
        r.register1("enterIsolate", Isolate.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Int, CEntryPointEnterNode.enterIsolate(isolate, false));
                return true;
            }
        });
        r.register1("enterIsolateFromCrashHandler", Isolate.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Int, CEntryPointEnterNode.enterIsolate(isolate, true));
                return true;
            }
        });
        InvocationPlugin bailoutPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new CEntryPointPrologueBailoutNode(value));
                return true;
            }
        };
        r.register1("bailoutInPrologue", WordBase.class, bailoutPlugin);
        r.register1("bailoutInPrologue", long.class, bailoutPlugin);
        r.register1("bailoutInPrologue", double.class, bailoutPlugin);
        r.register1("bailoutInPrologue", boolean.class, bailoutPlugin);
        r.register0("bailoutInPrologue", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new CEntryPointPrologueBailoutNode(null));
                return true;
            }
        });
        r.register0("leave", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                StateSplitProxyNode proxy = new StateSplitProxyNode(null);
                b.add(proxy);
                b.setStateAfter(proxy);
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.Leave));
                return true;
            }
        });
        r.register0("leaveDetachThread", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                StateSplitProxyNode proxy = new StateSplitProxyNode(null);
                b.add(proxy);
                b.setStateAfter(proxy);
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.DetachThread));
                return true;
            }
        });
        r.register0("leaveTearDownIsolate", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                StateSplitProxyNode proxy = new StateSplitProxyNode(null);
                b.add(proxy);
                b.setStateAfter(proxy);
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.TearDownIsolate));
                return true;
            }
        });
        r.register2("failFatally", int.class, CCharPointer.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                b.add(new CEntryPointUtilityNode(UtilityAction.FailFatally, arg1, arg2));
                return true;
            }
        });
        r.register1("isCurrentThreadAttachedTo", Isolate.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Boolean, new CEntryPointUtilityNode(UtilityAction.IsAttached, isolate));
                return true;
            }
        });
    }

    private static void registerCurrentIsolatePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CurrentIsolate.class);
        r.register0("getCurrentThread", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (SubstrateOptions.MultiThreaded.getValue()) {
                    b.addPush(JavaKind.Object, new ReadIsolateThreadFixedNode());
                } else if (SubstrateOptions.SpawnIsolates.getValue()) {
                    ValueNode heapBase = b.add(new ReadHeapBaseFixedNode());
                    ConstantNode addend = b.add(ConstantNode.forIntegerKind(FrameAccess.getWordKind(), CEntryPointSetup.SINGLE_ISOLATE_TO_SINGLE_THREAD_ADDEND));
                    b.addPush(JavaKind.Object, new AddNode(heapBase, addend));
                } else {
                    b.addPush(JavaKind.Object, ConstantNode.forIntegerKind(FrameAccess.getWordKind(), CEntryPointSetup.SINGLE_THREAD_SENTINEL.rawValue()));
                }
                return true;
            }
        });
        r.register0("getIsolate", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (SubstrateOptions.SpawnIsolates.getValue()) {
                    b.addPush(JavaKind.Object, new ReadHeapBaseFixedNode());
                } else {
                    b.addPush(JavaKind.Object, ConstantNode.forIntegerKind(FrameAccess.getWordKind(), CEntryPointSetup.SINGLE_ISOLATE_SENTINEL.rawValue()));
                }
                return true;
            }
        });
    }
}
