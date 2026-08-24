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
 * This implementation has a lot of potential for future optimizations, see GR-78935.
 */
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class RuntimeInternedStrings {
    final ConcurrentHashMap<Key, WeakKey> internedStrings = new ConcurrentHashMap<>();
    private final ReferenceQueue<String> queue = new ReferenceQueue<>();
    @Stable private long hashSeed;

    static RuntimeInternedStrings singleton() {
        return ImageSingletons.lookup(RuntimeInternedStrings.class);
    }

    String intern(String value) {
        assert value != null;
        removeStaleEntries();

        /* Check if there is a matching string in the image heap. */
        String imageInterned = StringInternSupport.findImageInternedString(value);
        if (imageInterned != null) {
            return imageInterned;
        }

        /* Check if there is a matching string in the runtime map. */
        int hash = hash(value);
        WeakKey existing = internedStrings.get(new StrongKey(value, hash));
        String interned = existing == null ? null : existing.get();
        if (interned != null) {
            return interned;
        }

        /* Try to store the string in the runtime map. */
        WeakKey candidate = new WeakKey(value, queue, hash);
        while (true) {
            existing = internedStrings.putIfAbsent(candidate, candidate);
            if (existing == null) {
                return value;
            }

            interned = existing.get();
            if (interned != null) {
                candidate.clear();
                return interned;
            }
        }
    }

    private void removeStaleEntries() {
        WeakKey stale;
        while ((stale = (WeakKey) queue.poll()) != null) {
            internedStrings.remove(stale, stale);
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

        WeakKey(String value, ReferenceQueue<String> queue, int hashCode) {
            super(value, queue);
            this.hashCode = hashCode;
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
