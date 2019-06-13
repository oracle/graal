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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JavaLangSubstitutions.ClassValueSupport;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticFeature
public final class ClassValueFeature implements Feature {
    private final Map<ClassValue<?>, Map<Class<?>, Object>> values = new ConcurrentHashMap<>();

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ClassValueSupport support = new ClassValueSupport(values);
        ImageSingletons.add(ClassValueSupport.class, support);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::processObject);
    }

    private Object processObject(Object obj) {
        if (obj instanceof ClassValue) {
            values.putIfAbsent((ClassValue<?>) obj, new ConcurrentHashMap<>());
        }
        return obj;
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl impl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        List<AnalysisType> types = impl.getUniverse().getTypes();
        for (AnalysisType t : types) {
            if (!t.isInstantiated() && !t.isInTypeCheck()) {
                continue;
            }
            Class<?> clazz = t.getJavaClass();
            for (Map.Entry<ClassValue<?>, Map<Class<?>, Object>> e : values.entrySet()) {
                ClassValue<?> v = e.getKey();
                Map<Class<?>, Object> m = e.getValue();
                if (!m.containsKey(clazz) && hasValue(v, clazz)) {
                    m.put(clazz, v.get(clazz));
                    access.requireAnalysisIteration();
                }
            }
        }
    }

    private static final java.lang.reflect.Field IDENTITY = ReflectionUtil.lookupField(ClassValue.class, "identity");
    private static final java.lang.reflect.Field CLASS_VALUE_MAP = ReflectionUtil.lookupField(Class.class, "classValueMap");

    private static boolean hasValue(ClassValue<?> v, Class<?> c) {
        try {
            Map<?, ?> map = (Map<?, ?>) CLASS_VALUE_MAP.get(c);
            final Object id = IDENTITY.get(v);
            final boolean res = map != null && map.containsKey(id);
            return res;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
