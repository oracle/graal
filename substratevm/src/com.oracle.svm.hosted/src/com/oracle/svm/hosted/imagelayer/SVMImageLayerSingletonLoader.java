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

import static com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.INITIAL_LAYER_ONLY_TRAIT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonKeyData;
import com.oracle.svm.hosted.snapshot.singleton.ImageSingletonObjectData;
import com.oracle.svm.hosted.snapshot.singleton.KeyStoreEntryData;
import com.oracle.svm.hosted.snapshot.util.SnapshotAdapters;
import com.oracle.svm.shared.ImageLayerBuildingSupportProvider;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl.SingletonInfo;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.LayeredInstallationKindSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.JavaConstant;

public class SVMImageLayerSingletonLoader {
    private final HostedImageLayerBuildingSupport imageLayerBuildingSupport;
    private final SharedLayerSnapshotData.Loader snapshot;
    private Map<Class<?>, Integer> initialLayerOnlySingletonConstantIds;
    private Map<Class<?>, Integer> singletonKeyToRegistrationCallbackKeyStoreId;

    public SVMImageLayerSingletonLoader(HostedImageLayerBuildingSupport imageLayerBuildingSupport, SharedLayerSnapshotData.Loader snapshot) {
        this.imageLayerBuildingSupport = imageLayerBuildingSupport;
        this.snapshot = snapshot;
    }

    ImageSingletonLoader createImageSingletonLoader(int keyStoreId) {
        EconomicMap<String, Object> keyStore = EconomicMap.create();
        for (KeyStoreEntryData.Loader entry : snapshot.getKeyStoreInstances().get(keyStoreId).getKeyStore()) {
            KeyStoreEntryData.Value.Loader v = entry.getValue();
            Object value = switch (v.kind()) {
                case I -> v.getI();
                case J -> v.getJ();
                case STR -> v.getStr();
                case IL -> SnapshotAdapters.toIntArray(v.getIl());
                case ZL -> SnapshotAdapters.toBooleanArray(v.getZl());
                case STRL -> SnapshotAdapters.toStringArray(v.getStrl());
                case NOT_IN_SCHEMA -> throw new IllegalStateException("Unexpected value: " + v.kind());
            };
            keyStore.put(entry.getKey(), value);
        }

        return new ImageSingletonLoaderImpl(keyStore, snapshot);
    }

    /**
     * Load singletons installed in previous layers.
     * 
     * @return a mapping from singleton objects or {@link SingletonInfo} placeholders to all the
     *         keys they should be mapped to.
     */
    public Map<Object, EconomicSet<Class<?>>> loadImageSingletons() {
        ArrayList<Object> singletonObjects = new ArrayList<>(snapshot.getSingletonObjects().size());
        Map<Class<?>, Integer> initialLayerKeyToIdMap = new HashMap<>();
        Map<Class<?>, Integer> singletonKeyToKeyStoreIdMap = new HashMap<>();
        int curIdx = 0;
        for (ImageSingletonObjectData.Loader obj : snapshot.getSingletonObjects()) {
            assert obj.getKeyStoreId() != SVMImageLayerSnapshotUtil.UNDEFINED_KEY_STORE_ID : "No KeyStore associated with Singleton: " + obj.getClassName();
            VMError.guarantee(obj.getId() == curIdx, "Singleton index mismatch %s %s", obj, curIdx);
            curIdx++;
            ImageSingletonLoader imageSingletonLoader = createImageSingletonLoader(obj.getKeyStoreId());

            // create singleton object instance
            Object result;
            try {
                String singletonInstantiatorClass = obj.getSingletonInstantiatorClass();
                Class<?> clazz = imageLayerBuildingSupport.lookupClass(false, singletonInstantiatorClass);
                var instance = (SingletonLayeredCallbacks.LayeredSingletonInstantiator<?>) ReflectionUtil.newInstance(clazz);
                result = instance.createFromLoader(imageSingletonLoader);
                Class<?> instanceClass = imageLayerBuildingSupport.lookupClass(false, obj.getClassName());
                VMError.guarantee(result.getClass().equals(instanceClass));
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere("Failed to recreate image singleton", t);
            }

            singletonObjects.add(result);
        }

        Map<Object, EconomicSet<Class<?>>> singletonInitializationMap = new HashMap<>();
        LayeredInstallationKindSingletonTrait[] initialLayerOnly = new LayeredInstallationKindSingletonTrait[]{INITIAL_LAYER_ONLY_TRAIT};
        for (ImageSingletonKeyData.Loader entry : snapshot.getSingletonKeys()) {
            String className = entry.getKeyClassName();
            LayeredPersistFlags persistFlags = LayeredPersistFlags.values()[entry.getPersistFlag()];
            int singletonObjId = entry.getObjectId();
            if (persistFlags == LayeredPersistFlags.CREATE) {
                assert singletonObjId != SVMImageLayerSnapshotUtil.UNDEFINED_SINGLETON_OBJ_ID : "Create image singletons should be linked to an object";
                Object singletonObject = singletonObjects.get(singletonObjId);
                Class<?> clazz = imageLayerBuildingSupport.lookupClass(false, className);
                singletonInitializationMap.computeIfAbsent(singletonObject, _ -> EconomicSet.create());
                singletonInitializationMap.get(singletonObject).add(clazz);
            } else if (persistFlags == LayeredPersistFlags.FORBIDDEN) {
                assert singletonObjId == SVMImageLayerSnapshotUtil.UNDEFINED_SINGLETON_OBJ_ID : "Unrestored image singleton should not be linked to an object";
                Class<?> clazz = imageLayerBuildingSupport.lookupClass(false, className);

                SingletonInfo forbiddenObject;
                if (entry.getIsInitialLayerOnly()) {
                    int constantId = entry.getConstantId();
                    initialLayerKeyToIdMap.put(clazz, constantId);
                    forbiddenObject = SingletonInfo.forbiddenSingletonInfo(initialLayerOnly);
                } else {
                    forbiddenObject = SingletonInfo.forbiddenSingletonInfo();
                }
                singletonInitializationMap.computeIfAbsent(forbiddenObject, _ -> EconomicSet.create());
                singletonInitializationMap.get(forbiddenObject).add(clazz);
            } else if (persistFlags == LayeredPersistFlags.CALLBACK_ON_REGISTRATION) {
                Class<?> clazz = imageLayerBuildingSupport.lookupClass(false, className);
                int keyStoreId = entry.getKeyStoreId();
                assert keyStoreId != SVMImageLayerSnapshotUtil.UNDEFINED_KEY_STORE_ID;
                var prev = singletonKeyToKeyStoreIdMap.put(clazz, keyStoreId);
                assert prev == null : clazz;
            } else {
                assert persistFlags == LayeredPersistFlags.NOTHING : "Unexpected PersistFlags value: " + persistFlags;
                assert singletonObjId == SVMImageLayerSnapshotUtil.UNDEFINED_SINGLETON_OBJ_ID : "Unrestored image singleton should not be linked to an object";
                assert entry.getKeyStoreId() == SVMImageLayerSnapshotUtil.UNDEFINED_KEY_STORE_ID : "Singleton should not have a key store associated with it";
            }
        }

        initialLayerOnlySingletonConstantIds = Collections.unmodifiableMap(initialLayerKeyToIdMap);
        singletonKeyToRegistrationCallbackKeyStoreId = Collections.unmodifiableMap(singletonKeyToKeyStoreIdMap);

        return singletonInitializationMap;
    }

    public JavaConstant loadInitialLayerOnlyImageSingleton(Class<?> key) {
        int constantId = initialLayerOnlySingletonConstantIds.getOrDefault(key, -1);
        if (constantId != -1) {
            return imageLayerBuildingSupport.getLoader().getOrCreateConstant(constantId);
        }
        throw UserError.abort("Unable to load InitialLayerOnlyImageSingleton: %s", key);
    }

    public boolean hasRegistrationCallback(Class<?> key) {
        if (key == ImageLayerBuildingSupportProvider.class) {
            /*
             * This singleton is added very early, before prior layer singletons and metadata has
             * been loaded. Note this singleton does not have a registration callback associated
             * with it.
             */
            return false;
        }
        return singletonKeyToRegistrationCallbackKeyStoreId.containsKey(key);
    }

    public ImageSingletonLoader getImageSingletonLoader(Class<?> key) {
        assert hasRegistrationCallback(key);
        return createImageSingletonLoader(singletonKeyToRegistrationCallbackKeyStoreId.get(key));
    }

    public Class<?> lookupClass(boolean optional, String className) {
        return imageLayerBuildingSupport.lookupClass(optional, className);
    }

    public static class ImageSingletonLoaderImpl implements ImageSingletonLoader {
        private final UnmodifiableEconomicMap<String, Object> keyStore;
        private final SharedLayerSnapshotData.Loader snapshotLoader;

        ImageSingletonLoaderImpl(UnmodifiableEconomicMap<String, Object> keyStore, SharedLayerSnapshotData.Loader snapshotLoader) {
            this.keyStore = keyStore;
            this.snapshotLoader = snapshotLoader;
        }

        private Object get(String keyName) {
            var result = keyStore.get(keyName);
            VMError.guarantee(result != null, "Key %s does not exist in KeyStore", keyName);
            return result;
        }

        @Override
        public List<Boolean> readBoolList(String keyName) {
            boolean[] l = (boolean[]) get(keyName);
            return IntStream.range(0, l.length).mapToObj(i -> l[i]).toList();
        }

        @Override
        public int readInt(String keyName) {
            return (int) get(keyName);
        }

        @Override
        public List<Integer> readIntList(String keyName) {
            int[] l = (int[]) get(keyName);
            return IntStream.of(l).boxed().toList();
        }

        @Override
        public long readLong(String keyName) {
            return (long) get(keyName);
        }

        @Override
        public String readString(String keyName) {
            return (String) get(keyName);
        }

        @Override
        public List<String> readStringList(String keyName) {
            return List.of((String[]) get(keyName));
        }

        public SharedLayerSnapshotData.Loader getSnapshotLoader() {
            return snapshotLoader;
        }
    }
}
