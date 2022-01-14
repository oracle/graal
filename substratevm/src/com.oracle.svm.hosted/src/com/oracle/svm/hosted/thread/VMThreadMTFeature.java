/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.nodes.ReadReservedRegister;
import com.oracle.svm.core.graal.thread.AddressOfVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.CompareAndSetVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.LoadVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.StoreVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.VolatileLoadVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.VolatileStoreVMThreadLocalNode;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.threadlocal.VMThreadLocalMTSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Collects all VM thread local variables during native image generation and assigns them their
 * offset in the {@link IsolateThread} data structure.
 */
@AutomaticFeature
public class VMThreadMTFeature implements GraalFeature {

    private final VMThreadLocalCollector threadLocalCollector = new VMThreadLocalCollector();
    private final VMThreadLocalMTSupport threadLocalSupport = new VMThreadLocalMTSupport();

    public int getVMThreadSize() {
        assert threadLocalSupport.vmThreadSize != -1 : "not yet initialized";
        return threadLocalSupport.vmThreadSize;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess config) {
        ImageSingletons.add(VMThreadLocalMTSupport.class, threadLocalSupport);
        config.registerObjectReplacer(threadLocalCollector);
    }

    /**
     * Intrinsify the {@code get()} and {@code set()} methods during bytecode parsing. We know that
     * every subclass of VMThreadLocal has the same methods. Only the signatures differ based on the
     * type of value.
     * <p>
     * When the {@link IsolateThread} is not passed in as a parameter, we use the
     * {@link LoadVMThreadLocalNode current thread}. We do not need read/write barriers since we
     * access memory that we manage ourselfs.
     */
    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
        for (Class<? extends FastThreadLocal> threadLocalClass : VMThreadLocalInfo.THREAD_LOCAL_CLASSES) {
            Registration r = new Registration(plugins.getInvocationPlugins(), threadLocalClass);
            Class<?> valueClass = VMThreadLocalInfo.getValueClass(threadLocalClass);
            registerAccessors(r, valueClass, false);
            registerAccessors(r, valueClass, true);

            /* compareAndSet() method without the VMThread parameter. */
            r.register3("compareAndSet", Receiver.class, valueClass, valueClass, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode expect, ValueNode update) {
                    ValueNode threadNode = currentThread(b);
                    return handleCompareAndSet(b, targetMethod, receiver, threadNode, expect, update);
                }
            });
            /* get() method with the VMThread parameter. */
            r.register4("compareAndSet", Receiver.class, IsolateThread.class, valueClass, valueClass, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode expect, ValueNode update) {
                    return handleCompareAndSet(b, targetMethod, receiver, threadNode, expect, update);
                }
            });
        }

        Class<?>[] typesWithGetAddress = new Class<?>[]{FastThreadLocalBytes.class, FastThreadLocalWord.class};
        for (Class<?> type : typesWithGetAddress) {
            Registration r = new Registration(plugins.getInvocationPlugins(), type);
            /* getAddress() method without the VMThread parameter. */
            r.register1("getAddress", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode threadNode = currentThread(b);
                    return handleGetAddress(b, targetMethod, receiver, threadNode);
                }
            });
            /* getAddress() method with the VMThread parameter. */
            r.register2("getAddress", Receiver.class, IsolateThread.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
                    return handleGetAddress(b, targetMethod, receiver, threadNode);
                }
            });
        }
    }

    private void registerAccessors(Registration r, Class<?> valueClass, boolean isVolatile) {
        String suffix = isVolatile ? "Volatile" : "";

        /* get() method without the VMThread parameter. */
        r.register1("get" + suffix, Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode threadNode = currentThread(b);
                return handleGet(b, targetMethod, receiver, threadNode, isVolatile);
            }
        });
        /* get() method with the VMThread parameter. */
        r.register2("get" + suffix, Receiver.class, IsolateThread.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
                return handleGet(b, targetMethod, receiver, threadNode, isVolatile);
            }
        });
        /* set() method without the VMThread parameter. */
        r.register2("set" + suffix, Receiver.class, valueClass, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode valueNode) {
                ValueNode threadNode = currentThread(b);
                return handleSet(b, receiver, threadNode, valueNode, isVolatile);
            }
        });
        /* set() method with the VMThread parameter. */
        r.register3("set" + suffix, Receiver.class, IsolateThread.class, valueClass, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode valueNode) {
                return handleSet(b, receiver, threadNode, valueNode, isVolatile);
            }
        });
    }

    private static ValueNode currentThread(GraphBuilderContext b) {
        return b.add(ReadReservedRegister.createReadIsolateThreadNode(b.getGraph()));
    }

    private boolean handleGet(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, boolean isVolatile) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());

        LoadVMThreadLocalNode node;
        if (isVolatile) {
            node = new VolatileLoadVMThreadLocalNode(b.getMetaAccess(), threadLocalInfo, threadNode, BarrierType.NONE);
        } else {
            node = new LoadVMThreadLocalNode(b.getMetaAccess(), threadLocalInfo, threadNode, BarrierType.NONE);
        }
        b.addPush(targetMethod.getSignature().getReturnKind(), node);

        return true;
    }

    private boolean handleSet(GraphBuilderContext b, Receiver receiver, ValueNode threadNode, ValueNode valueNode, boolean isVolatile) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());

        StoreVMThreadLocalNode store;
        if (isVolatile) {
            store = b.add(new VolatileStoreVMThreadLocalNode(threadLocalInfo, threadNode, valueNode, BarrierType.NONE));
        } else {
            store = b.add(new StoreVMThreadLocalNode(threadLocalInfo, threadNode, valueNode, BarrierType.NONE));
        }
        assert store.stateAfter() != null : store + " has no state after with graph builder context " + b;
        return true;
    }

    private boolean handleCompareAndSet(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode expect, ValueNode update) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        CompareAndSetVMThreadLocalNode cas = new CompareAndSetVMThreadLocalNode(threadLocalInfo, threadNode, expect, update);
        b.addPush(targetMethod.getSignature().getReturnKind(), cas);
        assert cas.stateAfter() != null : cas + " has no state after with graph builder context " + b;
        return true;
    }

    private boolean handleGetAddress(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        b.addPush(targetMethod.getSignature().getReturnKind(), new AddressOfVMThreadLocalNode(threadLocalInfo, threadNode));
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
        List<VMThreadLocalInfo> sortedThreadLocalInfos = threadLocalCollector.sortThreadLocals();
        SubstrateReferenceMap referenceMap = new SubstrateReferenceMap();
        int nextOffset = 0;
        for (VMThreadLocalInfo info : sortedThreadLocalInfos) {
            int alignment = Math.min(8, info.sizeInBytes);
            nextOffset = NumUtil.roundUp(nextOffset, alignment);

            if (info.isObject) {
                referenceMap.markReferenceAtOffset(nextOffset, true);
            }
            info.offset = nextOffset;
            nextOffset += info.sizeInBytes;

            if (info.offset > info.maxOffset) {
                VMError.shouldNotReachHere("Too many thread local variables with maximum offset " + info.maxOffset + " defined");
            }
        }

        InstanceReferenceMapEncoder encoder = new InstanceReferenceMapEncoder();
        encoder.add(referenceMap);
        NonmovableArray<Byte> referenceMapEncoding = encoder.encodeAll();
        threadLocalSupport.vmThreadReferenceMapEncoding = NonmovableArrays.getHostedArray(referenceMapEncoding);
        threadLocalSupport.vmThreadReferenceMapIndex = encoder.lookupEncoding(referenceMap);
        threadLocalSupport.vmThreadSize = nextOffset;

        /* Remember the final sorted list. */
        VMThreadLocalInfos.setInfos(sortedThreadLocalInfos);
    }

    public int offsetOf(FastThreadLocal threadLocal) {
        return threadLocalCollector.getInfo(threadLocal).offset;
    }
}
