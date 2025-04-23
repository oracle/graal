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
package com.oracle.svm.hosted;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.ClassValueSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

/**
 * This feature reads ClassValues created by the hosted environment and stores them into the image.
 */
@AutomaticallyRegisteredFeature
public final class ClassValueFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        /*
         * We must record all ClassValue instances seen to ensure they are properly patched from the
         * hosted environment into the substrate world.
         */
        Map<ClassValue<?>, Map<Class<?>, Object>> values = ClassValueSupport.getValues();
        ((FeatureImpl.DuringSetupAccessImpl) access).registerObjectReachableCallback(ClassValue.class, (a1, obj, reason) -> values.computeIfAbsent(obj, k -> new ConcurrentHashMap<>()));
    }

    private static final java.lang.reflect.Field IDENTITY = ReflectionUtil.lookupField(ClassValue.class, "identity");
    private static final java.lang.reflect.Field CLASS_VALUE_MAP = ReflectionUtil.lookupField(Class.class, "classValueMap");

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        /*
         * Checking all ClassValues to see if there is anything stored in the hosted environment
         * which needs to be patched into the substrate world.
         */
        Map<ClassValue<?>, Map<Class<?>, Object>> values = ClassValueSupport.getValues();

        FeatureImpl.DuringAnalysisAccessImpl impl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        List<AnalysisType> types = impl.getUniverse().getTypes();

        Set<Object> mapsToRescan = new HashSet<>();
        try {
            for (AnalysisType t : types) {
                if (!t.isReachable()) {
                    continue;
                    /*
                     * If the type is not reachable, then ClassValues associated with it are
                     * unneeded.
                     */
                }

                /*
                 * Directly calling ClassValue.get(Class) will cause a value to be computed.
                 * Therefore, instead we query the Class#classValueMap. For a given class, its
                 * classValueMap will contain a map of all user ClassValue objects which have a
                 * value for the class.
                 */
                var clazz = t.getJavaClass();
                var classValueMap = (Map<?, ?>) CLASS_VALUE_MAP.get(clazz);
                if (classValueMap == null) {
                    continue;
                }

                /*
                 * Check all reachable ClassValues instances to see if new mappings exist within the
                 * hostedCallValueMap which need to be placed within the svm objects.
                 */
                for (Map.Entry<ClassValue<?>, Map<Class<?>, Object>> svmClassValueEntry : values.entrySet()) {
                    Map<Class<?>, Object> svmClassValueMap = svmClassValueEntry.getValue();
                    if (!svmClassValueMap.containsKey(clazz)) {
                        ClassValue<?> classValue = svmClassValueEntry.getKey();
                        Object classValueMapKey = IDENTITY.get(classValue);
                        if (classValueMap.containsKey(classValueMapKey)) {
                            Object value = classValue.get(clazz);
                            svmClassValueMap.put(clazz, value == null ? ClassValueSupport.NULL_MARKER : value);
                            if (value != null) {
                                mapsToRescan.add(svmClassValueMap);
                            }
                        }
                    }
                }
            }
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        int numTypes = impl.getUniverse().getTypes().size();
        mapsToRescan.forEach(impl::rescanObject);
        if (numTypes != impl.getUniverse().getTypes().size()) {
            access.requireAnalysisIteration();
        }
    }
}
