/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.strings;

import com.oracle.svm.shared.BuildPhaseProvider.AfterHeapLayout;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Arrays;
import java.util.function.Consumer;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public class ImageInternedStrings {

    /**
     * The native image contains a lot of interned strings. All Java String literals, and all class
     * names, are interned per Java specification. We don't want the memory overhead of a hash table
     * entry, so we store them (sorted) in this String[] array. At run time, this array is searched
     * before the runtime intern set. Image strings are returned directly and are not added to the
     * runtime set.
     * <p>
     * The array is sorted by {@link #compare}: by the unsigned {@link #mix mixed}
     * {@link String#hashCode}, then by {@link String#compareTo}. Image strings carry their hash
     * code, computed at build time, so a lookup compares hash codes and calls
     * {@link String#compareTo} only for strings with the same hash code. The order is total, so a
     * lookup takes O(log n) steps even if many image strings share a hash code. The table cannot
     * change at run time, so strings chosen to collide cannot make it slower.
     * <p>
     * The field is set late during image generation, so the value is not available during static
     * analysis and compilation.
     */
    @UnknownObjectField(availability = AfterHeapLayout.class) private String[] internedStringTable;

    /**
     * A directory into {@link #internedStringTable}: the strings whose {@link #mix mixed} hash code
     * has {@code b} as its top bits are at indices {@code bucketStart[b]} (inclusive) to
     * {@code bucketStart[b + 1]} (exclusive). The number of buckets, {@code bucketStart.length - 1},
     * is a power of two of about a quarter of the strings, so the directory costs about one byte
     * per string and a lookup searches a few strings only. Without it (null), a lookup searches the
     * whole table in the same order.
     */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) private int[] bucketStart;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void setImageInternedStrings(String[] newImageInternedStrings) {
        assert internedStringTable == null : "Container objects already set";
        String[] sortedImageInternedStrings = Arrays.copyOf(newImageInternedStrings, newImageInternedStrings.length);
        Arrays.sort(sortedImageInternedStrings, ImageInternedStrings::compare);
        this.internedStringTable = sortedImageInternedStrings;
        this.bucketStart = buildBucketStart(sortedImageInternedStrings);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int[] buildBucketStart(String[] sorted) {
        int bucketBits = Math.max(1, 31 - Integer.numberOfLeadingZeros(Math.max(1, sorted.length >>> 2)));
        int buckets = 1 << bucketBits;
        int shift = Integer.SIZE - bucketBits;
        int[] starts = new int[buckets + 1];
        int index = 0;
        for (int bucket = 0; bucket < buckets; bucket++) {
            starts[bucket] = index;
            while (index < sorted.length && mix(sorted[index].hashCode()) >>> shift == bucket) {
                index++;
            }
        }
        starts[buckets] = index;
        assert index == sorted.length : "Interned strings not sorted by their mixed hash code";
        return starts;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void forEachContainerClass(Consumer<Class<?>> consumer) {
        consumer.accept(String[].class);
        consumer.accept(int[].class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void forEachContainerObject(Consumer<Object> consumer) {
        assert internedStringTable != null && bucketStart != null : "Container objects not set yet";
        consumer.accept(internedStringTable);
        consumer.accept(bucketStart);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getContainerRoot() {
        assert internedStringTable != null : "Container root not set yet";
        return internedStringTable;
    }

    /**
     * Spreads the hash code over the top bits, which select the bucket. The multiplier is odd, so
     * two strings have the same mixed hash code exactly if they have the same hash code.
     */
    private static int mix(int hashCode) {
        return hashCode * 0x9E3779B9;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int compare(String a, String b) {
        int result = Integer.compareUnsigned(mix(a.hashCode()), mix(b.hashCode()));
        return result != 0 ? result : a.compareTo(b);
    }

    public String find(String string) {
        String[] table = internedStringTable;
        int mixed = mix(string.hashCode());
        int low = 0;
        int high = table.length;
        int[] starts = bucketStart;
        if (starts != null) {
            int bucket = mixed >>> (Integer.numberOfLeadingZeros(starts.length - 1) + 1);
            low = starts[bucket];
            high = starts[bucket + 1];
        }
        while (low < high) {
            int middle = (low + high) >>> 1;
            String candidate = table[middle];
            int result = Integer.compareUnsigned(mix(candidate.hashCode()), mixed);
            if (result == 0) {
                result = candidate.compareTo(string);
            }
            if (result < 0) {
                low = middle + 1;
            } else if (result > 0) {
                high = middle;
            } else {
                return candidate;
            }
        }
        return null;
    }
}

/**
 * Lazily installs the default {@link ImageInternedStrings} singleton unless a replacement is
 * already registered.
 */
@AutomaticallyRegisteredFeature
class ImageInternedStringsFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(ImageInternedStrings.class)) {
            ImageInternedStrings singleton = new ImageInternedStrings();
            ImageSingletons.add(ImageInternedStrings.class, singleton);
        }
    }
}
