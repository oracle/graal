/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.thread;

import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.thread.AddressOfVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.CompareAndSetVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.LoadVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.StoreVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.VMThreadLocalSTHolderNode;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.threadlocal.VMThreadLocalSTSupport;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Collects all VM thread local variables during native image generation and assigns them their
 * offset in the Object[] and byte[] array that hold the values.
 */
@AutomaticFeature
public class VMThreadSTFeature implements GraalFeature {

    private final VMThreadLocalCollector threadLocalCollector = new VMThreadLocalCollector();

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess config) {
        ImageSingletons.add(VMThreadLocalSTSupport.class, new VMThreadLocalSTSupport());
        config.registerObjectReplacer(threadLocalCollector);
    }

    /**
     * Intrinsify the {@code get()} and {@code set()} methods during bytecode parsing. We know that
     * every subclass of VMThreadLocal has the same methods. Only the signatures differ based on the
     * type of value.
     * <p>
     * The value is stored in the two arrays that are in the image heap: a Object[] array for thread
     * local object variables, and a byte[] array for all thread local primitive variables.
     * Therefore, we need the proper read/write barriers. The {@link IsolateThread} parameter is
     * ignored.
     */
    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        for (Class<? extends FastThreadLocal> threadLocalClass : VMThreadLocalInfo.THREAD_LOCAL_CLASSES) {
            Registration r = new Registration(invocationPlugins, threadLocalClass);
            Class<?> valueClass = VMThreadLocalInfo.getValueClass(threadLocalClass);

            registerAccessors(r, valueClass, false);
            registerAccessors(r, valueClass, true);

            /* compareAndSet() method without the VMThread parameter. */
            r.register3("compareAndSet", Receiver.class, valueClass, valueClass, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode expect, ValueNode update) {
                    return handleCompareAndSet(b, targetMethod, receiver, expect, update);
                }
            });
            /* get() method with the VMThread parameter. */
            r.register4("compareAndSet", Receiver.class, IsolateThread.class, valueClass, valueClass, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode expect, ValueNode update) {
                    return handleCompareAndSet(b, targetMethod, receiver, expect, update);
                }
            });
        }

        Class<?>[] typesWithGetAddress = new Class<?>[]{FastThreadLocalBytes.class, FastThreadLocalWord.class};
        for (Class<?> type : typesWithGetAddress) {
            Registration r = new Registration(invocationPlugins, type);
            /* getAddress() method without the VMThread parameter. */
            r.register1("getAddress", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    return handleGetAddress(b, targetMethod, receiver);
                }
            });
            /* getAddress() method with the VMThread parameter. */
            r.register2("getAddress", Receiver.class, IsolateThread.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
                    return handleGetAddress(b, targetMethod, receiver);
                }
            });
        }
    }

    private void registerAccessors(Registration r, Class<?> valueClass, boolean isVolatile) {
        /*
         * Volatile accesses do not need memory barriers in single-threaded mode, i.e., we register
         * the same plugin for normal and volatile accesses.
         */
        String suffix = isVolatile ? "Volatile" : "";

        /* get() method without the VMThread parameter. */
        r.register1("get" + suffix, Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return handleGet(b, targetMethod, receiver);
            }
        });
        /* get() method with the VMThread parameter. */
        r.register2("get" + suffix, Receiver.class, IsolateThread.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
                return handleGet(b, targetMethod, receiver);
            }
        });
        /* set() method without the VMThread parameter. */
        r.register2("set" + suffix, Receiver.class, valueClass, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode valueNode) {
                return handleSet(b, receiver, valueNode);
            }
        });
        /* set() method with the VMThread parameter. */
        r.register3("set" + suffix, Receiver.class, IsolateThread.class, valueClass, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode valueNode) {
                return handleSet(b, receiver, valueNode);
            }
        });
    }

    private boolean handleGet(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
        VMThreadLocalInfo info = threadLocalCollector.findInfo(b, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(info));
        b.addPush(targetMethod.getSignature().getReturnKind(), new LoadVMThreadLocalNode(b.getMetaAccess(), info, holder, BarrierType.ARRAY));
        return true;
    }

    private boolean handleSet(GraphBuilderContext b, Receiver receiver, ValueNode valueNode) {
        VMThreadLocalInfo info = threadLocalCollector.findInfo(b, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(info));
        StoreVMThreadLocalNode store = new StoreVMThreadLocalNode(info, holder, valueNode, BarrierType.ARRAY);
        b.add(store);
        assert store.stateAfter() != null : store + " has no state after with graph builder context " + b;
        return true;
    }

    private boolean handleCompareAndSet(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode expect, ValueNode update) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(threadLocalInfo));
        CompareAndSetVMThreadLocalNode cas = new CompareAndSetVMThreadLocalNode(threadLocalInfo, holder, expect, update);
        b.addPush(targetMethod.getSignature().getReturnKind(), cas);
        assert cas.stateAfter() != null : cas + " has no state after with graph builder context " + b;
        return true;
    }

    private boolean handleGetAddress(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(threadLocalInfo));
        b.addPush(targetMethod.getSignature().getReturnKind(), new AddressOfVMThreadLocalNode(threadLocalInfo, holder));
        return true;
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        /*
         * Update during analysis so that the static analysis sees all infos. After analysis only
         * the order is going to change.
         */
        if (VMThreadLocalInfos.setInfos(threadLocalCollector.threadLocals.values())) {
            access.requireAnalysisIteration();
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess config) {
        List<VMThreadLocalInfo> sortedThreadLocalInfos = threadLocalCollector.sortThreadLocals(config);
        ObjectLayout layout = ConfigurationValues.getObjectLayout();
        int nextObject = 0;
        int nextPrimitive = 0;
        for (VMThreadLocalInfo info : sortedThreadLocalInfos) {
            if (info.isObject) {
                info.offset = NumUtil.safeToInt(layout.getArrayElementOffset(JavaKind.Object, nextObject));
                nextObject += 1;
            } else {
                assert nextPrimitive % Math.min(8, info.sizeInBytes) == 0 : "alignment mismatch: " + info.sizeInBytes + ", " + nextPrimitive;
                info.offset = NumUtil.safeToInt(layout.getArrayElementOffset(JavaKind.Byte, nextPrimitive));
                nextPrimitive += info.sizeInBytes;
            }
        }

        VMThreadLocalSTSupport support = ImageSingletons.lookup(VMThreadLocalSTSupport.class);
        support.objectThreadLocals = new Object[nextObject];
        support.primitiveThreadLocals = new byte[nextPrimitive];

        /* Remember the final sorted list. */
        VMThreadLocalInfos.setInfos(sortedThreadLocalInfos);
    }
}
