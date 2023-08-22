/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
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
@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
public class VMThreadSTFeature implements InternalFeature {

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
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
        for (Class<? extends FastThreadLocal> threadLocalClass : VMThreadLocalInfo.THREAD_LOCAL_CLASSES) {
            Registration r = new Registration(plugins.getInvocationPlugins(), threadLocalClass);
            Class<?> valueClass = VMThreadLocalInfo.getValueClass(threadLocalClass);

            registerAccessors(r, snippetReflection, valueClass, false);
            registerAccessors(r, snippetReflection, valueClass, true);

            /* compareAndSet() method without the VMThread parameter. */
            r.register(new RequiredInvocationPlugin("compareAndSet", Receiver.class, valueClass, valueClass) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode expect, ValueNode update) {
                    return handleCompareAndSet(b, snippetReflection, targetMethod, receiver, expect, update);
                }
            });
            /* get() method with the VMThread parameter. */
            r.register(new RequiredInvocationPlugin("compareAndSet", Receiver.class, IsolateThread.class, valueClass, valueClass) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode expect, ValueNode update) {
                    return handleCompareAndSet(b, snippetReflection, targetMethod, receiver, expect, update);
                }
            });
        }

        Class<?>[] typesWithGetAddress = new Class<?>[]{FastThreadLocalBytes.class, FastThreadLocalWord.class};
        for (Class<?> type : typesWithGetAddress) {
            Registration r = new Registration(plugins.getInvocationPlugins(), type);
            /* getAddress() method without the VMThread parameter. */
            r.register(new RequiredInvocationPlugin("getAddress", Receiver.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    return handleGetAddress(b, snippetReflection, targetMethod, receiver);
                }
            });
            /* getAddress() method with the VMThread parameter. */
            r.register(new RequiredInvocationPlugin("getAddress", Receiver.class, IsolateThread.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
                    return handleGetAddress(b, snippetReflection, targetMethod, receiver);
                }
            });
        }
    }

    private void registerAccessors(Registration r, SnippetReflectionProvider snippetReflection, Class<?> valueClass, boolean isVolatile) {
        /*
         * Volatile accesses do not need memory barriers in single-threaded mode, i.e., we register
         * the same plugin for normal and volatile accesses.
         */
        String suffix = isVolatile ? "Volatile" : "";

        /* get() method without the VMThread parameter. */
        r.register(new RequiredInvocationPlugin("get" + suffix, Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return handleGet(b, snippetReflection, targetMethod, receiver);
            }
        });
        /* get() method with the VMThread parameter. */
        r.register(new RequiredInvocationPlugin("get" + suffix, Receiver.class, IsolateThread.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode) {
                return handleGet(b, snippetReflection, targetMethod, receiver);
            }
        });
        /* set() method without the VMThread parameter. */
        r.register(new RequiredInvocationPlugin("set" + suffix, Receiver.class, valueClass) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode valueNode) {
                return handleSet(b, snippetReflection, receiver, valueNode);
            }
        });
        /* set() method with the VMThread parameter. */
        r.register(new RequiredInvocationPlugin("set" + suffix, Receiver.class, IsolateThread.class, valueClass) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode threadNode, ValueNode valueNode) {
                return handleSet(b, snippetReflection, receiver, valueNode);
            }
        });
    }

    private boolean handleGet(GraphBuilderContext b, SnippetReflectionProvider snippetReflection, ResolvedJavaMethod targetMethod, Receiver receiver) {
        VMThreadLocalInfo info = threadLocalCollector.findInfo(b, snippetReflection, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(info));
        b.addPush(targetMethod.getSignature().getReturnKind(), new LoadVMThreadLocalNode(b.getMetaAccess(), info, holder, BarrierType.NONE, MemoryOrderMode.PLAIN));
        return true;
    }

    private boolean handleSet(GraphBuilderContext b, SnippetReflectionProvider snippetReflection, Receiver receiver, ValueNode valueNode) {
        VMThreadLocalInfo info = threadLocalCollector.findInfo(b, snippetReflection, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(info));
        StoreVMThreadLocalNode store = new StoreVMThreadLocalNode(info, holder, valueNode, BarrierType.ARRAY, MemoryOrderMode.PLAIN);
        b.add(store);
        assert store.stateAfter() != null : store + " has no state after with graph builder context " + b;
        return true;
    }

    private boolean handleCompareAndSet(GraphBuilderContext b, SnippetReflectionProvider snippetReflection, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode expect, ValueNode update) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, snippetReflection, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(threadLocalInfo));
        CompareAndSetVMThreadLocalNode cas = new CompareAndSetVMThreadLocalNode(threadLocalInfo, holder, expect, update);
        b.addPush(targetMethod.getSignature().getReturnKind(), cas);
        assert cas.stateAfter() != null : cas + " has no state after with graph builder context " + b;
        return true;
    }

    private boolean handleGetAddress(GraphBuilderContext b, SnippetReflectionProvider snippetReflection, ResolvedJavaMethod targetMethod, Receiver receiver) {
        VMThreadLocalInfo threadLocalInfo = threadLocalCollector.findInfo(b, snippetReflection, receiver.get());
        VMThreadLocalSTHolderNode holder = b.add(new VMThreadLocalSTHolderNode(threadLocalInfo));
        b.addPush(targetMethod.getSignature().getReturnKind(), new AddressOfVMThreadLocalNode(threadLocalInfo, holder));
        return true;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess config) {
        List<VMThreadLocalInfo> sortedThreadLocalInfos = threadLocalCollector.sortThreadLocals();
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
