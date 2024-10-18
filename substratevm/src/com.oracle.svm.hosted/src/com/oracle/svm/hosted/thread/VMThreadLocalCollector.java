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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.util.ObservableImageHeapMapProvider;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.options.Option;

/**
 * Collects all {@link FastThreadLocal} instances that are actually used by the application.
 */
public class VMThreadLocalCollector implements Function<Object, Object>, LayeredImageSingleton {

    public static class Options {
        @Option(help = "Ensure all create ThreadLocals have unique names")//
        public static final HostedOptionKey<Boolean> ValidateUniqueThreadLocalNames = new HostedOptionKey<>(false);
    }

    Map<FastThreadLocal, VMThreadLocalInfo> threadLocals;
    Map<VMThreadLocalInfo, FastThreadLocal> infoToThreadLocals;
    private boolean sealed;
    final boolean validateUniqueNames;
    final Set<String> seenNames;

    public VMThreadLocalCollector() {
        this(false);
    }

    protected VMThreadLocalCollector(boolean validateUniqueNames) {
        this.validateUniqueNames = validateUniqueNames || Options.ValidateUniqueThreadLocalNames.getValue();
        seenNames = validateUniqueNames ? ConcurrentHashMap.newKeySet() : null;
    }

    public void installThreadLocalMap() {
        assert threadLocals == null : threadLocals;
        threadLocals = ObservableImageHeapMapProvider.create();
        infoToThreadLocals = new ConcurrentHashMap<>();
    }

    public VMThreadLocalInfo forFastThreadLocal(FastThreadLocal threadLocal) {
        VMThreadLocalInfo localInfo = threadLocals.get(threadLocal);
        if (localInfo == null) {
            if (sealed) {
                throw VMError.shouldNotReachHere("VMThreadLocal must have been discovered during static analysis");
            } else {
                VMThreadLocalInfo newInfo = new VMThreadLocalInfo(threadLocal);
                localInfo = threadLocals.computeIfAbsent(threadLocal, tl -> {
                    infoToThreadLocals.putIfAbsent(newInfo, threadLocal);
                    return newInfo;
                });
                if (localInfo == newInfo && validateUniqueNames) {
                    /*
                     * Ensure this name is unique.
                     */
                    VMError.guarantee(seenNames.add(threadLocal.getName()), "Two VMThreadLocals have the same name: %s", threadLocal.getName());
                }
            }
        }
        return localInfo;
    }

    @Override
    public Object apply(Object source) {
        if (source instanceof FastThreadLocal fastThreadLocal) {
            forFastThreadLocal(fastThreadLocal);
        }
        /*
         * We want to collect all instances without actually replacing them, so we always return the
         * source object.
         */
        return source;
    }

    public int getOffset(FastThreadLocal threadLocal) {
        VMThreadLocalInfo result = threadLocals.get(threadLocal);
        return result.offset;
    }

    public VMThreadLocalInfo findInfo(GraphBuilderContext b, ValueNode threadLocalNode) {
        if (!threadLocalNode.isConstant()) {
            throw shouldNotReachHere("Accessed VMThreadLocal is not a compile time constant: " + b.getMethod().asStackTraceElement(b.bci()) + " - node " + unPi(threadLocalNode));
        }

        FastThreadLocal threadLocal = b.getSnippetReflection().asObject(FastThreadLocal.class, threadLocalNode.asJavaConstant());
        VMThreadLocalInfo result = threadLocals.get(threadLocal);
        assert result != null;
        return result;
    }

    public FastThreadLocal getThreadLocal(VMThreadLocalInfo vmThreadLocalInfo) {
        return infoToThreadLocals.get(vmThreadLocalInfo);
    }

    protected static int calculateSize(VMThreadLocalInfo info) {
        if (info.sizeSupplier != null) {
            int unalignedSize = info.sizeSupplier.getAsInt();
            assert unalignedSize > 0;
            return NumUtil.roundUp(unalignedSize, 8);
        } else {
            return ConfigurationValues.getObjectLayout().sizeInBytes(info.storageKind);
        }
    }

    private List<VMThreadLocalInfo> sortedThreadLocalInfos;
    private SubstrateReferenceMap referenceMap;

    public void sortThreadLocals() {
        assert sortedThreadLocalInfos == null && referenceMap == null;

        sealed = true;
        for (VMThreadLocalInfo info : threadLocals.values()) {
            assert info.sizeInBytes == -1;
            info.sizeInBytes = calculateSize(info);
        }

        sortedThreadLocalInfos = new ArrayList<>(threadLocals.values());
        sortedThreadLocalInfos.sort(VMThreadLocalCollector::compareThreadLocal);
    }

    public int sortAndAssignOffsets() {
        sortThreadLocals();

        referenceMap = new SubstrateReferenceMap();
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

        return nextOffset;
    }

    public SubstrateReferenceMap getReferenceMap() {
        assert referenceMap != null;
        return referenceMap;
    }

    public List<VMThreadLocalInfo> getSortedThreadLocalInfos() {
        assert sortedThreadLocalInfos != null;
        return sortedThreadLocalInfos;
    }

    private static int compareThreadLocal(VMThreadLocalInfo info1, VMThreadLocalInfo info2) {
        if (info1 == info2) {
            return 0;
        }

        /* Order by priority: lower maximum offsets first. */
        int result = Integer.compare(info1.maxOffset, info2.maxOffset);
        if (result == 0) {
            /* Order by size to avoid padding. */
            result = -Integer.compare(info1.sizeInBytes, info2.sizeInBytes);
            if (result == 0) {
                /* Ensure that all objects are contiguous. */
                result = -Boolean.compare(info1.isObject, info2.isObject);
                if (result == 0) {
                    /*
                     * Make the order deterministic by sorting by name. This is arbitrary, we can
                     * come up with any better ordering.
                     */
                    result = info1.name.compareTo(info2.name);
                }
            }
        }
        return result;
    }

    private static ValueNode unPi(ValueNode n) {
        ValueNode cur = n;
        while (cur instanceof PiNode) {
            cur = ((PiNode) cur).object();
        }
        return cur;
    }

    @Override
    public final EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        return PersistFlags.NOTHING;
    }
}
