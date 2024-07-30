/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterHeapLayout;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredImageSingleton
public final class StringInternSupport implements MultiLayeredImageSingleton {

    interface SetGenerator {
        Set<String> generateSet();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Field getInternedStringsField() {
        return ReflectionUtil.lookupField(StringInternSupport.class, "internedStrings");
    }

    /** The String intern table at run time. */
    private final ConcurrentHashMap<String, String> internedStrings;

    /**
     * The native image contains a lot of interned strings. All Java String literals, and all class
     * names, are interned per Java specification. We don't want the memory overhead of an hash
     * table entry, so we store them (sorted) in this String[] array. When a string is interned at
     * run time, it is added to the real hash map, so we pay the (logarithmic) cost of the array
     * access only once per string.
     *
     * The field is set late during image generation, so the value is not available during static
     * analysis and compilation.
     */
    @UnknownObjectField(availability = AfterHeapLayout.class) private String[] imageInternedStrings;

    @Platforms(Platform.HOSTED_ONLY.class) private Object priorLayersInternedStrings;

    @Platforms(Platform.HOSTED_ONLY.class) private IdentityHashMap<String, String> internedStringsIdentityMap;

    @Platforms(Platform.HOSTED_ONLY.class)
    public StringInternSupport() {
        this(Set.of());
    }

    private StringInternSupport(Object obj) {
        this.internedStrings = new ConcurrentHashMap<>(16, 0.75f, 1);
        this.priorLayersInternedStrings = obj;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setImageInternedStrings(String[] newImageInternedStrings) {
        assert !ImageLayerBuildingSupport.buildingImageLayer();
        this.imageInternedStrings = newImageInternedStrings;
    }

    @SuppressWarnings("unchecked")
    @Platforms(Platform.HOSTED_ONLY.class)
    public String[] layeredSetImageInternedStrings(Set<String> layerInternedStrings) {
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

        Arrays.sort(currentLayerInternedStrings);

        this.imageInternedStrings = currentLayerInternedStrings;

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            internedStringsIdentityMap = new IdentityHashMap<>(priorInternedStrings.size() + currentLayerInternedStrings.length);
            for (var value : priorInternedStrings) {
                String internedVersion = value.intern();
                internedStringsIdentityMap.put(internedVersion, internedVersion);
            }
            Arrays.stream(currentLayerInternedStrings).forEach(internedString -> internedStringsIdentityMap.put(internedString, internedString));
        }

        return imageInternedStrings;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public IdentityHashMap<String, String> getInternedStringsIdentityMap() {
        assert internedStringsIdentityMap != null;
        return internedStringsIdentityMap;
    }

    String intern(String str) {
        String result = internedStrings.get(str);
        if (result != null) {
            return result;
        } else {
            return doIntern(str);
        }
    }

    private String doIntern(String str) {
        String result = str;
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            StringInternSupport[] layers = MultiLayeredImageSingleton.getAllLayers(StringInternSupport.class);
            for (StringInternSupport layer : layers) {
                String[] layerImageInternedStrings = layer.imageInternedStrings;
                int imageIdx = Arrays.binarySearch(layerImageInternedStrings, str);
                if (imageIdx >= 0) {
                    result = layerImageInternedStrings[imageIdx];
                    break;
                }
            }
        } else {
            int imageIdx = Arrays.binarySearch(imageInternedStrings, str);
            if (imageIdx >= 0) {
                result = imageInternedStrings[imageIdx];
            }
        }
        String oldValue = internedStrings.putIfAbsent(result, result);
        if (oldValue != null) {
            result = oldValue;
        }
        return result;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        // This can be switched to use constant ids in the future
        List<String> newPriorInternedStrings = new ArrayList<>(internedStringsIdentityMap.size());

        newPriorInternedStrings.addAll(internedStringsIdentityMap.keySet());

        writer.writeStringList("internedStrings", newPriorInternedStrings);
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        SetGenerator gen = (() -> Set.of(loader.readStringList("internedStrings").toArray(new String[0])));

        return new StringInternSupport(gen);
    }
}
