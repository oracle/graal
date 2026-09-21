/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.util.VMError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.threadlocal.VMThreadLocalOffsetProvider;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocal;
import com.oracle.svm.guest.staging.util.ObservableImageHeapMapProvider;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.options.Option;

/**
 * Collects all {@link FastThreadLocal} instances that are actually used by the application.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class VMThreadLocalCollector implements Function<Object, Object>, VMThreadLocalOffsetProvider {

    private static final int LARGE_ENTRY_SIZE = 16;

    public static class Options {
        @Option(help = "Ensure all create ThreadLocals have unique names")//
        public static final HostedOptionKey<Boolean> ValidateUniqueThreadLocalNames = new HostedOptionKey<>(false);
    }

    Map<FastThreadLocal, VMThreadLocalInfo> threadLocals;
    Map<VMThreadLocalInfo, FastThreadLocal> infoToThreadLocals;
    private List<VMThreadLocalInfo> sortedThreadLocalInfos;

    private boolean sealed;
    private final boolean validateUniqueNames;
    private final Set<String> seenNames;
    private SubstrateReferenceMap referenceMap;

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
                localInfo = threadLocals.computeIfAbsent(threadLocal, _ -> {
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

    @Override
    public int offsetOf(FastThreadLocal threadLocal) {
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

    private static int calculateSize(VMThreadLocalInfo info) {
        if (info.sizeSupplier != null) {
            int unalignedSize = info.sizeSupplier.getAsInt();
            assert unalignedSize > 0;
            return NumUtil.roundUp(unalignedSize, 8);
        } else {
            return ObjectLayout.singleton().sizeInBytes(info.storageKind);
        }
    }

    /**
     * Calculates the sizes of all thread locals, assigns offsets, and orders the result by those
     * offsets. This operation must be called exactly once.
     *
     * @return the size of the thread-local storage
     */
    public final int layoutThreadLocals() {
        VMError.guarantee(sortedThreadLocalInfos == null && referenceMap == null, "VM thread locals can only be laid out once");
        sealed = true;

        /* Compute the size of each thread-local. */
        ArrayList<VMThreadLocalInfo> tl = new ArrayList<>(threadLocals.values());
        for (VMThreadLocalInfo info : tl) {
            assert info.sizeInBytes == -1;
            info.sizeInBytes = calculateSize(info);
        }

        /* Sort the thread-locals into their processing order. */
        tl.sort(VMThreadLocalCollector::compareThreadLocal);

        /* Layout the thread-locals and assign offsets to them. */
        int size = assignOffsets(tl);

        /* Use that offset to sort the thread-locals into their final order. */
        tl.sort(Comparator.comparingInt(info -> info.offset));

        /* Build a reference map for thread-locals that have an object type. */
        referenceMap = new SubstrateReferenceMap();
        for (VMThreadLocalInfo info : tl) {
            if (info.isObject) {
                referenceMap.markReferenceAtOffset(info.offset, true);
            }
        }

        sortedThreadLocalInfos = tl;
        return size;
    }

    /**
     * Assigns aligned offsets to all thread locals. Constrained entries are placed first. Small
     * unconstrained entries reuse alignment gaps, while large entries are appended at the end.
     * The input is in deterministic processing order. Subclasses may override this method to use a
     * different layout. The result is sorted by offset after this method returns.
     */
    protected int assignOffsets(ArrayList<VMThreadLocalInfo> tl) {
        BitSet usedBytes = new BitSet();
        List<VMThreadLocalInfo> unconstrained = new ArrayList<>();

        /* Place constrained thread locals first so gap fillers cannot move them. */
        for (VMThreadLocalInfo info : tl) {
            if (info.maxOffset == Integer.MAX_VALUE) {
                unconstrained.add(info);
            } else {
                int offset = findGap(usedBytes, info);
                if (offset < 0) {
                    offset = alignOffset(usedBytes.length(), info);
                }
                placeThreadLocal(usedBytes, info, offset);
            }
        }

        /*
         * Fill alignment gaps with small unconstrained locals and append large entries last. Keeping
         * large entries after small locals may add up to seven bytes of alignment padding, but it
         * preserves compact offsets for small entries and reduces generated code size.
         */
        for (VMThreadLocalInfo info : unconstrained) {
            if (isLargeEntry(info)) {
                int offset = alignOffset(usedBytes.length(), info);
                placeThreadLocal(usedBytes, info, offset);
            } else {
                int offset = findGap(usedBytes, info);
                if (offset < 0) {
                    offset = alignOffset(usedBytes.length(), info);
                }
                placeThreadLocal(usedBytes, info, offset);
            }
        }

        return usedBytes.length();
    }

    private static int alignOffset(int offset, VMThreadLocalInfo info) {
        return NumUtil.roundUp(offset, alignment(info));
    }

    private static int alignment(VMThreadLocalInfo info) {
        return Math.min(8, info.sizeInBytes);
    }

    private static void placeThreadLocal(BitSet usedBytes, VMThreadLocalInfo info, int offset) {
        assert info.offset == -1;
        if (offset > info.maxOffset) {
            throw VMError.shouldNotReachHere("Cannot place thread local " + info.name + " within its maximum offset " + info.maxOffset + ": computed offset is " + offset);
        }
        int endOffset = offset + info.sizeInBytes;
        assert usedBytes.previousSetBit(endOffset - 1) < offset;
        info.offset = offset;
        usedBytes.set(offset, endOffset);
    }

    /**
     * Finds the smallest gap that satisfies the entry's alignment and maximum offset.
     * Ties favor lower offsets.
     *
     * @return the offset, or {@code -1} if no gap fits
     */
    private static int findGap(BitSet usedBytes, VMThreadLocalInfo info) {
        int candidateOffset = -1;
        int candidateSize = -1;
        int endOffset = usedBytes.length();
        int gapStart = usedBytes.nextClearBit(0);
        while (gapStart < endOffset) {
            int gapEnd = usedBytes.nextSetBit(gapStart);
            assert gapEnd >= 0;
            int gapSize = gapEnd - gapStart;
            int offset = alignOffset(gapStart, info);
            if (offset <= info.maxOffset && offset + info.sizeInBytes <= gapEnd) {
                if (candidateOffset == -1 || gapSize < candidateSize) {
                    candidateOffset = offset;
                    candidateSize = gapSize;
                }
            }
            gapStart = usedBytes.nextClearBit(gapEnd);
        }
        return candidateOffset;
    }

    public SubstrateReferenceMap getReferenceMap() {
        assert referenceMap != null;
        return referenceMap;
    }

    public List<VMThreadLocalInfo> getSortedThreadLocalInfos() {
        assert sortedThreadLocalInfos != null;
        return sortedThreadLocalInfos;
    }

    /**
     * Defines the processing order for assigning the final thread-local layout. Constrained entries
     * are ordered by their latest aligned start plus {@code sizeInBytes}, so entries whose complete
     * storage must fit earliest come first. Within the remaining groups, the order reduces alignment
     * padding and places large entries last. Names provide the final deterministic tie-breaker.
     */
    private static int compareThreadLocal(VMThreadLocalInfo info1, VMThreadLocalInfo info2) {
        if (info1 == info2) {
            return 0;
        }

        boolean constrained1 = info1.maxOffset != Integer.MAX_VALUE;
        boolean constrained2 = info2.maxOffset != Integer.MAX_VALUE;
        int result = -Boolean.compare(constrained1, constrained2);
        if (result == 0 && constrained1) {
            /* Order by latest aligned start plus sizeInBytes, ascending. */
            result = Long.compare(latestPermittedEnd(info1), latestPermittedEnd(info2));
        }
        if (result == 0) {
            boolean largeEntry1 = isLargeEntry(info1);
            boolean largeEntry2 = isLargeEntry(info2);
            /* Place large entries after small thread locals. */
            result = Boolean.compare(largeEntry1, largeEntry2);
            if (result == 0) {
                /*
                 * Sort the first group from largest to smallest to minimize alignment padding.
                 * Sort large entries from smallest to largest so more of them start at low offsets.
                 */
                result = largeEntry1 ? Integer.compare(info1.sizeInBytes, info2.sizeInBytes) : -Integer.compare(info1.sizeInBytes, info2.sizeInBytes);
            }
            if (result == 0) {
                /* Let non-object entries consume gaps so objects are more likely contiguous. */
                result = Boolean.compare(info1.isObject, info2.isObject);
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

    /**
     * Returns the end of an entry when it starts at its latest aligned permitted offset. Sorting by
     * this value prioritizes entries that have the least placement flexibility.
     */
    private static long latestPermittedEnd(VMThreadLocalInfo info) {
        int alignment = alignment(info);
        int latestAlignedStart = info.maxOffset - info.maxOffset % alignment;
        return (long) latestAlignedStart + info.sizeInBytes;
    }

    private static boolean isLargeEntry(VMThreadLocalInfo info) {
        return info.sizeInBytes >= LARGE_ENTRY_SIZE;
    }

    private static ValueNode unPi(ValueNode n) {
        ValueNode cur = n;
        while (cur instanceof PiNode) {
            cur = ((PiNode) cur).object();
        }
        return cur;
    }
}
