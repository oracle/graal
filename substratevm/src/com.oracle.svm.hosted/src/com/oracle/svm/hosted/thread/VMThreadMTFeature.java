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
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.ForceFixedRegisterReads;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.nodes.ReadRegisterFixedNode;
import com.oracle.svm.core.graal.nodes.ReadRegisterFloatingNode;
import com.oracle.svm.core.graal.thread.AddressOfVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.CompareAndSetVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.LoadVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.StoreVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.VMThreadLocalMTObjectReferenceWalker;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.MemoryBarriers;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Collects all VM thread local variables during native image generation and assigns them their
 * offset in the {@link IsolateThread} data structure.
 */
@AutomaticFeature
public class VMThreadMTFeature implements GraalFeature {

    private final VMThreadLocalCollector threadLocalCollector = new VMThreadLocalCollector();
    private final VMThreadLocalMTObjectReferenceWalker objectReferenceWalker = new VMThreadLocalMTObjectReferenceWalker();
    private FastThreadLocal threadLocalAtOffsetZero;

    public int getVMThreadSize() {
        assert objectReferenceWalker.vmThreadSize != -1 : "not yet initialized";
        return objectReferenceWalker.vmThreadSize;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess config) {
        config.registerObjectReplacer(threadLocalCollector);
        Heap.getHeap().getGC().registerObjectReferenceWalker(objectReferenceWalker);
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
                    ValueNode threadNode = currentThread(b, false);
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
            Registration r = new Registration(invocationPlugins, type);
            /* getAddress() method without the VMThread parameter. */
            r.register1("getAddress", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode threadNode = currentThread(b, true);
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
                ValueNode threadNode = currentThread(b, false);
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
                ValueNode threadNode = currentThread(b, false);
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

    private static ValueNode currentThread(GraphBuilderContext b, boolean usedForAddress) {
        /*
         * A floating node to access the VMThread is more efficient: it allows value numbering of
         * multiple accesses, and it does not copy the VMThread from the fixed register into a
         * virtual register. But for deoptimization target methods, we must not do value numbering
         * because the VMThread is not part of the FrameState and therefore not restored during
         * deoptimization. And when computing the address of a VMThreadLocal, we must not have the
         * VMThread in a fixed register because the it can end up in a FrameState (and a FrameState
         * must not directly reference a fixed register).
         */
        boolean isDeoptTarget = b.getMethod() instanceof SharedMethod && ((SharedMethod) b.getMethod()).isDeoptTarget();

        /*
         * Due to the fact that the LLVM backend handles reading the thread pointer in entry point
         * methods as a stack slot load instead of a direct register access, a ReadRegisterFixedNode
         * should be emitted in those cases so that the register read doesn't get hoisted above
         * where the thread pointer gets stored in the stack slot.
         */
        boolean forceFixedReads = GuardedAnnotationAccess.isAnnotationPresent(b.getMethod(), ForceFixedRegisterReads.class);
        if (isDeoptTarget || usedForAddress || forceFixedReads) {
            return b.add(ReadRegisterFixedNode.forIsolateThread());
        } else {
            return b.add(ReadRegisterFloatingNode.forIsolateThread());
        }
    }

    private boolean handleGet(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, boolean isVolatile) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        if (isVolatile) {
            b.add(new MembarNode(MemoryBarriers.JMM_PRE_VOLATILE_READ));
        }
        b.addPush(targetMethod.getSignature().getReturnKind(), new LoadVMThreadLocalNode(b.getMetaAccess(), threadLocalInfo, threadNode, BarrierType.NONE));
        if (isVolatile) {
            b.add(new MembarNode(MemoryBarriers.JMM_POST_VOLATILE_READ));
        }
        return true;
    }

    private boolean handleSet(GraphBuilderContext b, Receiver receiver, ValueNode threadNode, ValueNode valueNode, boolean isVolatile) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        if (isVolatile) {
            b.add(new MembarNode(MemoryBarriers.JMM_PRE_VOLATILE_WRITE));
        }
        b.add(new StoreVMThreadLocalNode(threadLocalInfo, threadNode, valueNode, BarrierType.NONE));
        if (isVolatile) {
            b.add(new MembarNode(MemoryBarriers.JMM_POST_VOLATILE_WRITE));
        }
        return true;
    }

    private boolean handleCompareAndSet(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode expect, ValueNode update) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        b.addPush(targetMethod.getSignature().getReturnKind(), new CompareAndSetVMThreadLocalNode(threadLocalInfo, threadNode, expect, update));
        return true;
    }

    private boolean handleGetAddress(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, receiver.get());
        b.addPush(targetMethod.getSignature().getReturnKind(), new AddressOfVMThreadLocalNode(threadLocalInfo, threadNode));
        return true;
    }

    public void setThreadLocalAtOffsetZero(FastThreadLocal threadLocal) {
        VMError.guarantee(objectReferenceWalker.vmThreadSize < 0, "VM thread locals have already been placed");
        VMError.guarantee(threadLocalAtOffsetZero == null, "may not be set more than once");
        threadLocalAtOffsetZero = threadLocal;
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
        List<VMThreadLocalInfo> sortedThreadLocalInfos = threadLocalCollector.sortThreadLocals(config, threadLocalAtOffsetZero);
        SubstrateReferenceMap referenceMap = new SubstrateReferenceMap();
        int nextOffset = 0;
        for (VMThreadLocalInfo info : sortedThreadLocalInfos) {
            assert nextOffset % Math.min(8, info.sizeInBytes) == 0 : "alignment mismatch: " + info.sizeInBytes + ", " + nextOffset;

            if (info.isObject) {
                referenceMap.markReferenceAtOffset(nextOffset, true);
            }
            info.offset = nextOffset;
            nextOffset += info.sizeInBytes;
        }
        VMError.guarantee(threadLocalAtOffsetZero == null || threadLocalCollector.getInfo(threadLocalAtOffsetZero).offset == 0);

        InstanceReferenceMapEncoder encoder = new InstanceReferenceMapEncoder();
        encoder.add(referenceMap);
        NonmovableArray<Byte> referenceMapEncoding = encoder.encodeAll();
        objectReferenceWalker.vmThreadReferenceMapEncoding = NonmovableArrays.getHostedArray(referenceMapEncoding);
        objectReferenceWalker.vmThreadReferenceMapIndex = encoder.lookupEncoding(referenceMap);
        objectReferenceWalker.vmThreadSize = nextOffset;

        /* Remember the final sorted list. */
        VMThreadLocalInfos.setInfos(sortedThreadLocalInfos);
    }

    public int offsetOf(FastThreadLocal threadLocal) {
        return threadLocalCollector.getInfo(threadLocal).offset;
    }
}
