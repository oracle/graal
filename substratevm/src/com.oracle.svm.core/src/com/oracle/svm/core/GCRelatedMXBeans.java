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
package com.oracle.svm.core;

import java.lang.management.PlatformManagedObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.util.UserError;

public class GCRelatedMXBeans {

    private static final Set<Class<? extends PlatformManagedObject>> gcRelatedMXBeanClasses;

    private final Map<Class<?>, Object> platformManagedObjectsMap = new HashMap<>();
    private final Set<PlatformManagedObject> platformManagedObjectsSet = Collections.newSetFromMap(new IdentityHashMap<>());

    static {
        Set<Class<? extends PlatformManagedObject>> values = new HashSet<>();
        addGCRelatedMXBeanClass(java.lang.management.MemoryMXBean.class, values);
        addGCRelatedMXBeanClass(com.sun.management.GarbageCollectorMXBean.class, values);
        addGCRelatedMXBeanClass(java.lang.management.MemoryPoolMXBean.class, values);
        addGCRelatedMXBeanClass(java.lang.management.BufferPoolMXBean.class, values);

        gcRelatedMXBeanClasses = Collections.unmodifiableSet(values);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public GCRelatedMXBeans() {
    }

    private static void addGCRelatedMXBeanClass(Class<? extends PlatformManagedObject> clazz, Set<Class<? extends PlatformManagedObject>> values) {
        for (Class<?> superinterface : clazz.getInterfaces()) {
            if (superinterface != PlatformManagedObject.class && PlatformManagedObject.class.isAssignableFrom(superinterface)) {
                addGCRelatedMXBeanClass(superinterface.asSubclass(PlatformManagedObject.class), values);
            }
        }
        values.add(clazz);
    }

    public static <T extends PlatformManagedObject> boolean isGCRelatedMXBean(Class<T> clazz) {
        return gcRelatedMXBeanClasses.contains(clazz);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public <T extends PlatformManagedObject> void addPlatformMXBeanSingleton(Class<T> clazz, T object) {
        if (!clazz.isInterface()) {
            throw UserError.abort("Key for registration of a PlatformManagedObject must be an interface");
        }
        ManagementSupport.doAddPlatformManagedObjectSingleton(platformManagedObjectsMap, platformManagedObjectsSet, clazz, object);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public <T extends PlatformManagedObject> void addPlatformMXBeanList(Class<T> clazz, List<T> objects) {
        if (!clazz.isInterface()) {
            throw UserError.abort("Key for registration of a PlatformManagedObject must be an interface");
        }
        ManagementSupport.doAddPlatformManagedObjectList(platformManagedObjectsMap, platformManagedObjectsSet, clazz, objects);
    }

    public <T extends PlatformManagedObject> Object getPlatformMXBeanObject(Class<T> mxbeanInterface) {
        return platformManagedObjectsMap.get(mxbeanInterface);
    }

    public boolean contains(PlatformManagedObject object) {
        return platformManagedObjectsSet.contains(object);
    }

    public Set<PlatformManagedObject> getPlatformManagedObjectsSet() {
        return Collections.unmodifiableSet(platformManagedObjectsSet);
    }

    public Set<Class<? extends PlatformManagedObject>> getUsedInterfaces() {
        return gcRelatedMXBeanClasses;
    }

}
