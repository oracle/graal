/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.RuntimeRandomness;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = StringInternSupport.LayeredCallbacks.class)
public final class StringInternSupport {

    interface SetGenerator {
        Set<String> generateSet();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Field getInternedStringsField() {
        return ReflectionUtil.lookupField(RuntimeInternedStrings.class, "internedStrings");
    }

    @Platforms(Platform.HOSTED_ONLY.class) private Object priorLayersInternedStrings;

    @Platforms(Platform.HOSTED_ONLY.class) private IdentityHashMap<String, String> internedStringsIdentityMap;

    @Platforms(Platform.HOSTED_ONLY.class)
    public StringInternSupport() {
        this.priorLayersInternedStrings = Set.of();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setImageInternedStrings(String[] newImageInternedStrings) {
        assert !ImageLayerBuildingSupport.buildingImageLayer();
        getImageInternedStringsImpl().setImageInternedStrings(newImageInternedStrings);
    }

    @SuppressWarnings("unchecked")
    @Platforms(Platform.HOSTED_ONLY.class)
    public void layeredSetImageInternedStrings(Set<String> layerInternedStrings) {
        assert ImageLayerBuildingSupport.buildingImageLayer();
        /*
         * When building a layered image, it is possible that this string has been interned in a
         * prior layer. Thus, we must filter the interned string away from this array.
         *
         * In addition, the hashcode for the string should match across layers.
         */
        String[] currentLayerInternedStrings;
        Set<String> priorInternedStrings;
        if (priorLayersInternedStrings instanceof SetGenerator generator) {
            priorInternedStrings = generator.generateSet();
        } else {
            priorInternedStrings = (Set<String>) priorLayersInternedStrings;
        }
        // don't need this anymore
        priorLayersInternedStrings = null;

        if (priorInternedStrings.isEmpty()) {
            currentLayerInternedStrings = layerInternedStrings.toArray(String[]::new);
        } else {
            currentLayerInternedStrings = layerInternedStrings.stream().filter(value -> !priorInternedStrings.contains(value)).toArray(String[]::new);
        }

        getImageInternedStringsImpl().setImageInternedStrings(currentLayerInternedStrings);

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            internedStringsIdentityMap = new IdentityHashMap<>(priorInternedStrings.size() + currentLayerInternedStrings.length);
            for (var value : priorInternedStrings) {
                String internedVersion = value.intern();
                internedStringsIdentityMap.put(internedVersion, internedVersion);
            }
            Arrays.stream(currentLayerInternedStrings).forEach(internedString -> internedStringsIdentityMap.put(internedString, internedString));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public IdentityHashMap<String, String> getInternedStringsIdentityMap() {
        assert internedStringsIdentityMap != null;
        return internedStringsIdentityMap;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void forEachContainerClass(Consumer<Class<?>> consumer) {
        getImageInternedStringsImpl().forEachContainerClass(consumer);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void forEachContainerObject(Consumer<Object> consumer) {
        getImageInternedStringsImpl().forEachContainerObject(consumer);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Object getContainerRoot() {
        return getImageInternedStringsImpl().getContainerRoot();
    }

    public static String intern(String str) {
        return RuntimeInternedStrings.singleton().intern(str);
    }

    static String findImageInternedString(String str) {
        for (ImageInternedStrings layer : MultiLayeredImageSingleton.getAllLayers(ImageInternedStrings.class)) {
            String found = layer.find(str);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Object getImageInternedStrings() {
        return getImageInternedStringsImpl();
    }

    /**
     * Intentionally returns an Object to avoid exposing the implementation to callers, which should
     * use methods like {@link #forEachContainerObject} instead.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static ImageInternedStrings getImageInternedStringsImpl() {
        return LayeredImageSingletonSupport.singleton().lookup(ImageInternedStrings.class, false, true);
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<StringInternSupport>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, StringInternSupport singleton) {
                    // This can be switched to use constant ids in the future
                    List<String> newPriorInternedStrings = new ArrayList<>(singleton.internedStringsIdentityMap.size());

                    newPriorInternedStrings.addAll(singleton.internedStringsIdentityMap.keySet());

                    writer.writeStringList("internedStrings", newPriorInternedStrings);
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, StringInternSupport singleton) {
                    singleton.priorLayersInternedStrings = (SetGenerator) (() -> Set.of(loader.readStringList("internedStrings").toArray(new String[0])));
                }
            });
        }
    }
}

/**
 * Implements {@link String#intern} at run time. It first searches the strings interned in the image
 * layers through {@link StringInternSupport#findImageInternedString}. Image strings are returned
 * directly and are not added to the runtime set. Other strings are stored as weak references in a
 * concurrent set and can be garbage collected. A keyed content hash prevents predictable hash
 * collisions. Stale entries are removed when another string is interned.
 * <p>
 * A lossy {@link #cache} answers repeated lookups before any of that, see there.
 * <p>
 * This implementation has a lot of potential for future optimizations, see GR-78935.
 */
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class RuntimeInternedStrings {
    private static final int CACHE_WAYS = 4;
    private static final int CACHE_MIN_LENGTH = 256;
    private static final int CACHE_MAX_LENGTH = 1 << 17;

    private static final Unsafe U = Unsafe.getUnsafe();

    final ConcurrentHashMap<Key, WeakKey> internedStrings = new ConcurrentHashMap<>();
    private final ReferenceQueue<String> queue = new ReferenceQueue<>();
    @Stable private long hashSeed;

    /**
     * A lossy cache in front of the image search and {@link #internedStrings}. A slot holds a string
     * of the image heap or the {@link WeakKey} of a string in {@link #internedStrings}; the
     * {@link String#hashCode} of the string selects a set of {@link #CACHE_WAYS} slots and, within
     * it, the slot that is probed first and replaced when the set is full. A hit returns exactly
     * what the search would return: a {@link WeakKey} whose referent is still reachable is the only
     * entry for its content, because entries are removed only once they are cleared, and the
     * garbage collector clears all weak references to a string at once. The cache never holds a
     * run-time string strongly, so interned strings can still be collected, and a lookup probes one
     * set only: strings chosen to collide just miss and take the keyed path. Lookups do not write
     * to the cache or to this object.
     * <p>
     * A run-time string enters the cache when it is looked up again after its insert, so strings
     * that are interned once, such as unique ones, never touch it.
     * <p>
     * The cache is allocated by the first string stored into it. When a store finds its set full,
     * the cache grows to two slots per run-time interned string, up to {@link #CACHE_MAX_LENGTH}
     * slots; a new array starts empty.
     */
    private volatile Object[] cache;

    static RuntimeInternedStrings singleton() {
        return ImageSingletons.lookup(RuntimeInternedStrings.class);
    }

    String intern(String value) {
        assert value != null;
        int stringHash = value.hashCode();
        String cached = lookupCache(value, stringHash);
        if (cached != null) {
            return cached;
        }
        removeStaleEntries();

        /* Check if there is a matching string in the image heap. */
        String imageInterned = StringInternSupport.findImageInternedString(value);
        if (imageInterned != null) {
            storeInCache(stringHash, imageInterned);
            return imageInterned;
        }

        /* Check if there is a matching string in the runtime map. */
        int hash = hash(value);
        WeakKey existing = internedStrings.get(new StrongKey(value, hash));
        String interned = existing == null ? null : existing.get();
        if (interned != null) {
            storeInCache(stringHash, existing);
            return interned;
        }

        /* Try to store the string in the runtime map. */
        WeakKey candidate = new WeakKey(value, queue, hash, stringHash);
        while (true) {
            existing = internedStrings.putIfAbsent(candidate, candidate);
            if (existing == null) {
                return value;
            }

            interned = existing.get();
            if (interned != null) {
                candidate.clear();
                storeInCache(stringHash, existing);
                return interned;
            }
        }
    }

    private String lookupCache(String value, int stringHash) {
        Object[] slots = cache;
        if (slots == null) {
            return null;
        }
        long set = setOffset(stringHash, slots.length);
        int first = firstWay(stringHash, slots.length);
        for (int i = 0; i < CACHE_WAYS; i++) {
            String candidate = cachedString(U.getReferenceAcquire(slots, set + (first ^ i) * slotScale()), stringHash);
            if (candidate != null && candidate.equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    /** The string of a cache slot if it has this hash code and is still reachable, else null. */
    private static String cachedString(Object entry, int stringHash) {
        if (entry instanceof WeakKey key) {
            return key.stringHash == stringHash ? key.get() : null;
        }
        /* An image string: its hash code was computed at build time, so this does not write. */
        String imageString = (String) entry;
        return imageString != null && imageString.hashCode() == stringHash ? imageString : null;
    }

    /**
     * Stores an image string or a {@link WeakKey} that is reachable into its set: into a free slot
     * or the slot of a cleared key if there is one, otherwise into the slot it is probed in first.
     */
    private void storeInCache(int stringHash, Object entry) {
        Object[] slots = cache;
        if (slots == null) {
            slots = new Object[CACHE_MIN_LENGTH];
            cache = slots;
        }
        long set = setOffset(stringHash, slots.length);
        int first = firstWay(stringHash, slots.length);
        int target = -1;
        for (int i = 0; i < CACHE_WAYS && target < 0; i++) {
            Object present = U.getReferenceAcquire(slots, set + (first ^ i) * slotScale());
            if (present == entry) {
                return;
            }
            if (present == null || (present instanceof WeakKey key && key.get() == null)) {
                target = first ^ i;
            }
        }
        U.putReferenceRelease(slots, set + (target < 0 ? first : target) * slotScale(), entry);
        if (target < 0) {
            maybeGrowCache(slots);
        }
    }

    /**
     * Called when a store found its set full: replaces the cache by an empty one with two slots per
     * run-time interned string if it has fewer.
     */
    private void maybeGrowCache(Object[] slots) {
        if (slots.length < CACHE_MAX_LENGTH && cache == slots) {
            long wanted = 2 * internedStrings.mappingCount();
            if (wanted > slots.length) {
                cache = new Object[(int) Math.min(CACHE_MAX_LENGTH, Long.highestOneBit(wanted) << 1)];
            }
        }
    }

    /**
     * The offset of the first slot of the set that a string with this hash code uses: the top bits
     * of the mixed hash code, which spreads hash codes in arithmetic progressions (such as those of
     * strings that differ in a numeric suffix) evenly over the sets.
     */
    private static long setOffset(int stringHash, int length) {
        int set = mix(stringHash) >>> (Integer.SIZE - setBits(length));
        return U.arrayBaseOffset(Object[].class) + (long) set * CACHE_WAYS * slotScale();
    }

    /** The slot of its set in which a string with this hash code is probed first. */
    private static int firstWay(int stringHash, int length) {
        return (mix(stringHash) >>> (Integer.SIZE - setBits(length) - Integer.numberOfTrailingZeros(CACHE_WAYS))) & (CACHE_WAYS - 1);
    }

    private static int setBits(int length) {
        return Integer.numberOfTrailingZeros(length / CACHE_WAYS);
    }

    private static int mix(int stringHash) {
        return stringHash * 0x9E3779B9;
    }

    private static long slotScale() {
        return U.arrayIndexScale(Object[].class);
    }

    private void removeStaleEntries() {
        WeakKey stale;
        while ((stale = (WeakKey) queue.poll()) != null) {
            internedStrings.remove(stale, stale);
            Object[] slots = cache;
            if (slots != null) {
                long set = setOffset(stale.stringHash, slots.length);
                for (int way = 0; way < CACHE_WAYS; way++) {
                    long offset = set + way * slotScale();
                    if (U.getReference(slots, offset) == stale) {
                        U.compareAndSetReference(slots, offset, stale, null);
                    }
                }
            }
        }
    }

    private int hash(String value) {
        long seed = getHashSeed();
        return HalfSipHash.hash(seed, value);
    }

    private long getHashSeed() {
        if (hashSeed != 0) {
            return hashSeed;
        }
        return initializeHashSeed();
    }

    private synchronized long initializeHashSeed() {
        if (hashSeed == 0) {
            long seed = RuntimeRandomness.instance().getNonBlockingRandom().nextLong();
            hashSeed = seed != 0 ? seed : 1;
        }
        return hashSeed;
    }

    private static boolean keyEquals(Key key, Object other) {
        if (key == other) {
            return true;
        }
        if (other instanceof Key otherKey) {
            String value = key.get();
            return value != null && value.equals(otherKey.get());
        }
        return false;
    }

    private interface Key {
        String get();
    }

    private static final class WeakKey extends WeakReference<String> implements Key {
        private final int hashCode;
        /** The {@link String#hashCode} of the referent: selects its slot in the cache. */
        private final int stringHash;

        WeakKey(String value, ReferenceQueue<String> queue, int hashCode, int stringHash) {
            super(value, queue);
            this.hashCode = hashCode;
            this.stringHash = stringHash;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            return keyEquals(this, other);
        }
    }

    /**
     * A temporary strong key used to look up an existing weak entry without creating a weak
     * reference.
     */
    private static final class StrongKey implements Key {
        private final String value;
        private final int hashCode;

        StrongKey(String value, int hashCode) {
            this.value = value;
            this.hashCode = hashCode;
        }

        @Override
        public String get() {
            return value;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            return keyEquals(this, other);
        }
    }
}

/**
 * Within layered images we must eagerly register {@link RuntimeInternedStrings#internedStrings} as
 * accessed. This is because some builder code queries whether it exists and will otherwise omit
 * required information from the build.
 */
@AutomaticallyRegisteredFeature
class LayeredStringInternFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerAsAccessed(StringInternSupport.getInternedStringsField());
    }
}
