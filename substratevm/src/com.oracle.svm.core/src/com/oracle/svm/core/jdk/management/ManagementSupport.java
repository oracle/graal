/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.management;

// Checkstyle: stop

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.DynamicMBean;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;
import javax.management.StandardMBean;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.UserError;
import com.sun.jmx.mbeanserver.MXBeanLookup;

// Checkstyle: resume
// Checkstyle: allow synchronization

/**
 * This class provides the SVM support implementation for the MXBean that provide VM introspection,
 * which is accessible in the JDK via {@link ManagementFactory}. There are two mostly independent
 * parts: The beans (implementations of {@link PlatformManagedObject}) themselves; and the singleton
 * {@link ManagementFactory#getPlatformMBeanServer() MBean server}.
 * 
 * Support for {@link PlatformManagedObject}: All MXBean that provide VM introspection are allocated
 * eagerly at image build time, and stored in {@link #platformManagedObjectsMap} as well as
 * {@link #platformManagedObjectsSet}. The registration is decentralized: while some general beans
 * are registered in this class, the OS specific and GC specific beans are registered in the
 * respective OS or GC code. To find all registrations, look for the usages of
 * {@link #addPlatformManagedObjectSingleton} and {@link #addPlatformManagedObjectList}. Eager
 * allocation of all the beans avoids the complicated registry that the JDK maintains for lazy
 * loading (the code in PlatformComponent - note that this code and even the package of the class is
 * significantly different between JDK 8 and JDK 11).
 * 
 * Support for {@link ManagementFactory#getPlatformMBeanServer()}: The {@link MBeanServer} that
 * makes all MXBean available too is allocated lazily at run time. This has advantages and
 * disadvantages. The {@link MBeanServer} and all the bean registrations is a quite heavyweight data
 * structure. All the attributes and operations of the beans are stored in several nested hash maps.
 * Putting all of that in the image heap would increase the image heap size, but also avoid the
 * allocation at run time on first access. Unfortunately, there are also many additional global
 * caches for bean and attribute lookup, for example in {@link MXBeanLookup}, MXBeanIntrospector,
 * and {@link MBeanServerFactory}. Beans from the hosting VM that runs the image build must not be
 * made available at runtime using these caches, i.e., a complicated re-build of the caches would be
 * necessary at image build time. Therefore we opted to inialize the {@link MBeanServer} at run
 * time.
 * 
 * This has two important consequences: 1) There must not be any {@link MBeanServer} in the image
 * heap, neither the singleton platform server nor a custom server created by the application.
 * Classes that cache a {@link MBeanServer} in a static final field must be initialized at run time.
 * 2) All the attribute lookup of the platform beans happens at run time during initialization.
 * Attributes are found using reflection (enumerating all methods of the bean interface). So the
 * attributes of the platform beans are only available via the {@link MBeanServer} if the methods
 * are manually registered for reflection by the application. There is no automatic registration of
 * all methods because that would lead to a significant number of unnecessary method registrations.
 * It is cumbersome to access attributes of platform beans via the {@link MBeanServer}, getting the
 * platform objects and directly calling methods on them is much easier and therefore the common use
 * case. We therefore believe that the automatic reflection registration is indeed unnecessary.
 */
public final class ManagementSupport {

    /**
     * All {@link PlatformManagedObject} structured by their interface. The same object can be
     * contained multiple times under different keys. The value is either the
     * {@link PlatformManagedObject} itself for singleton interfaces, or a {@link List} for
     * zero-or-more interfaces. Note that the list can be empty, denoting that the key is a valid
     * platform interface that can be queried but no implementations are registered.
     */
    final Map<Class<?>, Object> platformManagedObjectsMap;

    /** All {@link PlatformManagedObject} as a flat set without structure and without duplicates. */
    final Set<PlatformManagedObject> platformManagedObjectsSet;

    private final SubstrateClassLoadingMXBean classLoadingMXBean;
    private final SubstrateCompilationMXBean compilationMXBean;
    private final SubstrateRuntimeMXBean runtimeMXBean;
    private final SubstrateThreadMXBean threadMXBean;

    /** The singleton MBean server for the platform, initialized lazily at run time. */
    MBeanServer platformMBeanServer;

    @Platforms(Platform.HOSTED_ONLY.class)
    ManagementSupport() {
        platformManagedObjectsMap = new HashMap<>();
        platformManagedObjectsSet = Collections.newSetFromMap(new IdentityHashMap<>());

        classLoadingMXBean = new SubstrateClassLoadingMXBean();
        compilationMXBean = new SubstrateCompilationMXBean();
        runtimeMXBean = new SubstrateRuntimeMXBean();
        threadMXBean = new SubstrateThreadMXBean();

        /*
         * Register the platform objects defined in this package. Note that more platform objects
         * are registered in OS and GC specific code.
         */
        addPlatformManagedObjectSingleton(java.lang.management.ClassLoadingMXBean.class, classLoadingMXBean);
        addPlatformManagedObjectSingleton(java.lang.management.CompilationMXBean.class, compilationMXBean);
        addPlatformManagedObjectSingleton(java.lang.management.RuntimeMXBean.class, runtimeMXBean);
        addPlatformManagedObjectSingleton(com.sun.management.ThreadMXBean.class, threadMXBean);
        /*
         * The following platform objects must be registered as existing and valid, even though we
         * do not have an implementation yet.
         */
        addPlatformManagedObjectList(java.lang.management.MemoryPoolMXBean.class, Collections.emptyList());
        addPlatformManagedObjectList(java.lang.management.BufferPoolMXBean.class, Collections.emptyList());
    }

    @Fold
    public static ManagementSupport getSingleton() {
        return ImageSingletons.lookup(ManagementSupport.class);
    }

    /**
     * Registers a new singleton {@link PlatformManagedObject} for the provided interface and all
     * its superinterfaces.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public <T extends PlatformManagedObject> void addPlatformManagedObjectSingleton(Class<T> clazz, T object) {
        if (!clazz.isInterface()) {
            throw UserError.abort("Key for registration of a PlatformManagedObject must be an interface");
        }
        doAddPlatformManagedObjectSingleton(clazz, object);
    }

    private void doAddPlatformManagedObjectSingleton(Class<?> clazz, PlatformManagedObject object) {
        for (Class<?> superinterface : clazz.getInterfaces()) {
            if (superinterface != PlatformManagedObject.class && PlatformManagedObject.class.isAssignableFrom(superinterface)) {
                doAddPlatformManagedObjectSingleton(superinterface, object);
            }
        }

        Object existing = platformManagedObjectsMap.get(clazz);
        if (existing != null) {
            throw UserError.abort("PlatformManagedObject already registered: " + clazz.getName());
        }
        platformManagedObjectsMap.put(clazz, object);
        platformManagedObjectsSet.add(object);
    }

    /**
     * Adds the provided list of {@link PlatformManagedObject} for the provided interface and all
     * its superinterfaces.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public <T extends PlatformManagedObject> void addPlatformManagedObjectList(Class<T> clazz, List<T> objects) {
        if (!clazz.isInterface()) {
            throw UserError.abort("Key for registration of a PlatformManagedObject must be an interface");
        }
        doAddPlatformManagedObjectList(clazz, objects);
    }

    private void doAddPlatformManagedObjectList(Class<?> clazz, List<? extends PlatformManagedObject> objects) {
        for (Class<?> superinterface : clazz.getInterfaces()) {
            if (superinterface != PlatformManagedObject.class && PlatformManagedObject.class.isAssignableFrom(superinterface)) {
                doAddPlatformManagedObjectList(superinterface, objects);
            }
        }

        Object existing = platformManagedObjectsMap.get(clazz);
        if (existing instanceof PlatformManagedObject) {
            throw UserError.abort("PlatformManagedObject already registered as a singleton: " + clazz.getName());
        }

        ArrayList<Object> newList = new ArrayList<>();
        if (existing != null) {
            newList.addAll((List<?>) existing);
        }
        newList.addAll(objects);
        newList.trimToSize();

        platformManagedObjectsMap.put(clazz, Collections.unmodifiableList(newList));
        platformManagedObjectsSet.addAll(objects);
    }

    public boolean isRegisteredPlatformManagedObject(PlatformManagedObject object) {
        return platformManagedObjectsSet.contains(object);
    }

    public void noteThreadStart(Thread thread) {
        threadMXBean.noteThreadStart(thread);
    }

    public void noteThreadFinish(Thread thread) {
        threadMXBean.noteThreadFinish(thread);
    }

    synchronized MBeanServer getPlatformMBeanServer() {
        if (platformMBeanServer == null) {
            /* Modified version of JDK 11: ManagementFactory.getPlatformMBeanServer */
            platformMBeanServer = MBeanServerFactory.createMBeanServer();
            for (PlatformManagedObject platformManagedObject : platformManagedObjectsSet) {
                addMXBean(platformMBeanServer, platformManagedObject);
            }
        }
        return platformMBeanServer;
    }

    /* Modified version of JDK 11: ManagementFactory.addMXBean */
    private static void addMXBean(MBeanServer mbs, PlatformManagedObject pmo) {
        ObjectName oname = pmo.getObjectName();
        // Make DynamicMBean out of MXBean by wrapping it with a StandardMBean
        final DynamicMBean dmbean;
        if (pmo instanceof DynamicMBean) {
            dmbean = DynamicMBean.class.cast(pmo);
        } else if (pmo instanceof NotificationEmitter) {
            dmbean = new StandardEmitterMBean(pmo, null, true, (NotificationEmitter) pmo);
        } else {
            dmbean = new StandardMBean(pmo, null, true);
        }
        try {
            mbs.registerMBean(dmbean, oname);
        } catch (JMException ex) {
            throw new RuntimeException(ex);
        }
    }

    Set<Class<?>> getPlatformManagementInterfaces() {
        return Collections.unmodifiableSet(platformManagedObjectsMap.keySet());
    }

    <T extends PlatformManagedObject> T getPlatformMXBean(Class<T> mxbeanInterface) {
        Object result = platformManagedObjectsMap.get(mxbeanInterface);
        if (result == null) {
            throw new IllegalArgumentException(mxbeanInterface.getName() + " is not a platform management interface");
        } else if (result instanceof List) {
            throw new IllegalArgumentException(mxbeanInterface.getName() + " can have more than one instance");
        }
        return mxbeanInterface.cast(result);
    }

    @SuppressWarnings("unchecked")
    <T extends PlatformManagedObject> List<T> getPlatformMXBeans(Class<T> mxbeanInterface) {
        Object result = platformManagedObjectsMap.get(mxbeanInterface);
        if (result == null) {
            throw new IllegalArgumentException(mxbeanInterface.getName() + " is not a platform management interface");
        }
        if (result instanceof List) {
            return (List<T>) result;
        } else {
            return Collections.singletonList(mxbeanInterface.cast(result));
        }
    }
}
