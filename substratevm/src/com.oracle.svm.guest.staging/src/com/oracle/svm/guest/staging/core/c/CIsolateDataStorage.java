/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.core.c;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.shared.BuilderInvoked;
import com.oracle.svm.shared.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.shared.collections.ConcurrentIdentityHashMap;
import com.oracle.svm.shared.config.ObjectLayout;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.NumUtil;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

/**
 * Defines a section of memory where per-isolate singleton instances of native data structures can
 * be stored.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public final class CIsolateDataStorage {
    /*
     * Always align structures to 8 bytes, even on 32-bit platforms. Some data structures such as
     * pthread_mutex_t rely on this.
     */
    public static final int ALIGNMENT = Long.BYTES;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<String, CIsolateData<?>> usedEntries = new ConcurrentIdentityHashMap<>();

    /* By using a long[], less code needs to deal with the alignment. */
    @UnknownObjectField(availability = AfterHostedUniverse.class) //
    private long[] managedIsolateSectionData;

    @Platforms(Platform.HOSTED_ONLY.class)
    public CIsolateDataStorage() {
    }

    @GuestFold
    public static CIsolateDataStorage singleton() {
        return ImageSingletons.lookup(CIsolateDataStorage.class);
    }

    /**
     * Records an entry reported by the builder's reachability callback. Calls may be concurrent and
     * may report the same entry repeatedly. Entry names are compared by object identity.
     */
    @BuilderInvoked
    @Platforms(Platform.HOSTED_ONLY.class)
    public void collect(CIsolateData<?> data) {
        usedEntries.compute(data.getName(), (key, old) -> {
            VMError.guarantee(old == null || old == data, "The isolate data section already contains an entry for %s", key);
            return data;
        });
    }

    /**
     * Assigns aligned offsets in size/name order and allocates the backing storage. The builder
     * invokes this method once during {@code afterAnalysis}, after all reachable
     * {@code CIsolateData} entries have been collected.
     */
    @BuilderInvoked
    @Platforms(Platform.HOSTED_ONLY.class)
    public void layout() {
        CIsolateData<?>[] entries = usedEntries.values().toArray(new CIsolateData<?>[0]);
        Arrays.sort(entries, Comparator.comparingLong(CIsolateData<?>::getSize).thenComparing(CIsolateData<?>::getName));

        long offset = 0;
        for (CIsolateData<?> entry : entries) {
            offset = NumUtil.roundUp(offset, ALIGNMENT);
            entry.setOffset(offset);
            offset += entry.getSize();
        }

        assert managedIsolateSectionData == null;
        long arrayLength = Math.ceilDiv(offset, Long.BYTES);
        managedIsolateSectionData = new long[NumUtil.safeToInt(arrayLength)];
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public <T extends PointerBase> T get(CIsolateData<T> data) {
        assert data != null : "invalid isolate data section entry";
        Pointer base = Word.objectToUntrackedPointer(managedIsolateSectionData).add(arrayBaseOffset());
        Pointer result = base.add(Word.unsigned(data.getOffset()));
        assert UnsignedUtils.isAMultiple(result, Word.unsigned(ALIGNMENT));
        return (T) result;
    }

    @GuestFold
    static int arrayBaseOffset() {
        return ObjectLayout.singleton().getArrayBaseOffset(Long.BYTES);
    }
}
