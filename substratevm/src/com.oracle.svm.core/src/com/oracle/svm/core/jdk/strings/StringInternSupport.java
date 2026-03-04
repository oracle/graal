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

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;

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
        String result = RuntimeInternedStrings.getInternedStrings().get(str);
        if (result != null) {
            return result;
        } else {
            return doIntern(str);
        }
    }

    private static String doIntern(String str) {
        String result = str;
        for (ImageInternedStrings layer : MultiLayeredImageSingleton.getAllLayers(ImageInternedStrings.class)) {
            String found = layer.find(str);
            if (found != null) {
                result = found;
                break;
            }
        }
        String oldValue = RuntimeInternedStrings.getInternedStrings().putIfAbsent(result, result);
        if (oldValue != null) {
            result = oldValue;
        }
        return result;
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

@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
class RuntimeInternedStrings {

    /** The String intern table at run time. */
    final ConcurrentHashMap<String, String> internedStrings = new ConcurrentHashMap<>(16, 0.75f, 1);

    static ConcurrentHashMap<String, String> getInternedStrings() {
        return ImageSingletons.lookup(RuntimeInternedStrings.class).internedStrings;
    }
}

/**
 * Within layered images we must eagerly register {@link RuntimeInternedStrings#internedStrings} as
 * accessed. This is because some builder code queries whether it exists and will otherwise omit
 * required information from the build.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
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
