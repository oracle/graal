/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.hosted.imagelayer.SVMImageLayerLoader.getBooleans;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.svm.shaded.org.capnproto.Text;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.InitialLayerOnlyImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonKey;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonObject;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.KeyStoreEntry;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;

public class SVMImageLayerSingletonLoader {
    private final HostedImageLayerBuildingSupport imageLayerBuildingSupport;
    private final SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader snapshot;
    private Map<Class<?>, Integer> initialLayerOnlySingletonConstantIds;

    public SVMImageLayerSingletonLoader(HostedImageLayerBuildingSupport imageLayerBuildingSupport, SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader snapshot) {
        this.imageLayerBuildingSupport = imageLayerBuildingSupport;
        this.snapshot = snapshot;
    }

    public Map<Object, Set<Class<?>>> loadImageSingletons(Object forbiddenObject) {
        Map<Integer, Object> idToObjectMap = new HashMap<>();
        Map<Class<?>, Integer> initialLayerKeyToIdMap = new HashMap<>();
        for (ImageSingletonObject.Reader obj : snapshot.getSingletonObjects()) {
            String className = obj.getClassName().toString();

            EconomicMap<String, Object> keyStore = EconomicMap.create();
            for (KeyStoreEntry.Reader entry : obj.getStore()) {
                KeyStoreEntry.Value.Reader v = entry.getValue();
                Object value = switch (v.which()) {
                    case I -> v.getI();
                    case J -> v.getJ();
                    case STR -> v.getStr().toString();
                    case IL -> Stream.of(v.getIl()).flatMapToInt(r -> IntStream.range(0, r.size()).map(r::get)).toArray();
                    case ZL -> getBooleans(v.getZl());
                    case STRL -> StreamSupport.stream(v.getStrl().spliterator(), false).map(Text.Reader::toString).toArray(String[]::new);
                    case _NOT_IN_SCHEMA -> throw new IllegalStateException("Unexpected value: " + v.which());
                };
                keyStore.put(entry.getKey().toString(), value);
            }

            // create singleton object instance
            Object result;
            try {
                Class<?> clazz = imageLayerBuildingSupport.lookupClass(false, className);
                Method createMethod = ReflectionUtil.lookupMethod(clazz, "createFromLoader", ImageSingletonLoader.class);
                result = createMethod.invoke(null, new ImageSingletonLoaderImpl(keyStore, snapshot));
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere("Failed to recreate image singleton", t);
            }

            idToObjectMap.put(obj.getId(), result);
        }

        Map<Object, Set<Class<?>>> singletonInitializationMap = new HashMap<>();
        for (ImageSingletonKey.Reader entry : snapshot.getSingletonKeys()) {
            String className = entry.getKeyClassName().toString();
            PersistFlags persistInfo = PersistFlags.values()[entry.getPersistFlag()];
            int id = entry.getObjectId();
            if (persistInfo == PersistFlags.CREATE) {
                assert id != -1 : "Create image singletons should be linked to an object";
                Object singletonObject = idToObjectMap.get(id);
                Class<?> clazz = imageLayerBuildingSupport.lookupClass(false, className);
                singletonInitializationMap.computeIfAbsent(singletonObject, (k) -> new HashSet<>());
                singletonInitializationMap.get(singletonObject).add(clazz);
            } else if (persistInfo == PersistFlags.FORBIDDEN) {
                assert id == -1 : "Unrestored image singleton should not be linked to an object";
                Class<?> clazz = imageLayerBuildingSupport.lookupClass(false, className);
                singletonInitializationMap.computeIfAbsent(forbiddenObject, (k) -> new HashSet<>());
                singletonInitializationMap.get(forbiddenObject).add(clazz);
                if (InitialLayerOnlyImageSingleton.class.isAssignableFrom(clazz)) {
                    int constantId = entry.getConstantId();
                    if (constantId != -1) {
                        initialLayerKeyToIdMap.put(clazz, constantId);
                    }
                }
            } else {
                assert persistInfo == PersistFlags.NOTHING : "Unexpected PersistFlags value: " + persistInfo;
                assert id == -1 : "Unrestored image singleton should not be linked to an object";
            }
        }

        initialLayerOnlySingletonConstantIds = Map.copyOf(initialLayerKeyToIdMap);

        return singletonInitializationMap;
    }

    public JavaConstant loadInitialLayerOnlyImageSingleton(Class<?> key) {
        int constantId = initialLayerOnlySingletonConstantIds.getOrDefault(key, -1);
        if (constantId != -1) {
            return imageLayerBuildingSupport.getLoader().getOrCreateConstant(constantId);
        }
        throw UserError.abort("Unable to load InitialLayerOnlyImageSingleton: %s. Please override accessibleInFutureLayers if you want this singleton to be reachable in future layers.", key);
    }

    public Class<?> lookupClass(boolean optional, String className) {
        return imageLayerBuildingSupport.lookupClass(optional, className);
    }

    public static class ImageSingletonLoaderImpl implements ImageSingletonLoader {
        private final UnmodifiableEconomicMap<String, Object> keyStore;
        private final SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader snapshotReader;

        ImageSingletonLoaderImpl(UnmodifiableEconomicMap<String, Object> keyStore, SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader snapshotReader) {
            this.keyStore = keyStore;
            this.snapshotReader = snapshotReader;
        }

        @Override
        public List<Boolean> readBoolList(String keyName) {
            boolean[] l = (boolean[]) keyStore.get(keyName);
            return IntStream.range(0, l.length).mapToObj(i -> l[i]).toList();
        }

        @Override
        public int readInt(String keyName) {
            return (int) keyStore.get(keyName);
        }

        @Override
        public List<Integer> readIntList(String keyName) {
            int[] l = (int[]) keyStore.get(keyName);
            return IntStream.of(l).boxed().toList();
        }

        @Override
        public long readLong(String keyName) {
            return (long) keyStore.get(keyName);
        }

        @Override
        public String readString(String keyName) {
            return (String) keyStore.get(keyName);
        }

        @Override
        public List<String> readStringList(String keyName) {
            return List.of((String[]) keyStore.get(keyName));
        }

        public SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader getSnapshotReader() {
            return snapshotReader;
        }
    }
}
