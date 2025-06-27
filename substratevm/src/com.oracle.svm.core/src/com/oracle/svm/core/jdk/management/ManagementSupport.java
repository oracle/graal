/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.PlatformManagedObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.management.DynamicMBean;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;
import javax.management.StandardMBean;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.GCRelatedMXBeans;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.sun.jmx.mbeanserver.MXBeanLookup;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * This class provides the SVM-specific MX bean support, which is accessible in the JDK via
 * {@link ManagementFactory}. There are two mostly independent parts: the beans (implementations of
 * {@link PlatformManagedObject}) and the singleton
 * {@link ManagementFactory#getPlatformMBeanServer() MBean server}.
 * <p>
 * All MX beans ({@link PlatformManagedObject}s) that provide VM introspection are registered (but
 * not necessarily allocated) eagerly at image build time. Eager registration avoids the complicated
 * registry that the JDK maintains for lazy loading (see {@code PlatformComponent}). Note that there
 * is a separate data structure for GC-related MX beans (see {@link GCRelatedMXBeans}).
 * <p>
 * The {@link MBeanServer} (see {@link ManagementFactory#getPlatformMBeanServer()}) that makes all
 * MX beans available is allocated lazily at run time. This has advantages and disadvantages. The
 * {@link MBeanServer} and all the bean registrations are quite heavy-weight data structures. All
 * the attributes and operations of the beans are stored in several nested hash maps. Putting all of
 * that in the image heap would increase the image heap size, but also avoid the allocation at run
 * time on first access. Unfortunately, there are also many additional global caches for bean and
 * attribute lookup, for example in {@link MXBeanLookup}, {@code MXBeanIntrospector}, and
 * {@link MBeanServerFactory}. Beans from the host VM (that runs the image build) must not be made
 * available at runtime using these caches, i.e., a complicated re-build of the caches would be
 * necessary at image build time. Therefore, we opted to initialize the {@link MBeanServer} at run
 * time.
 * <p>
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
public final class ManagementSupport implements ThreadListener {
    private static final Class<? extends PlatformManagedObject> FLIGHT_RECORDER_MX_BEAN_CLASS = getFlightRecorderMXBeanClass();

    private final MXBeans mxBeans = new MXBeans();
    private final SubstrateThreadMXBean threadMXBean;

    /* Initialized lazily at run time. */
    private OperatingSystemMXBean osMXBean;
    private PlatformManagedObject flightRecorderMXBean;
    private MBeanServer platformMBeanServer;

    @Platforms(Platform.HOSTED_ONLY.class)
    ManagementSupport(SubstrateRuntimeMXBean runtimeMXBean, SubstrateThreadMXBean threadMXBean) {
        SubstrateClassLoadingMXBean classLoadingMXBean = new SubstrateClassLoadingMXBean();
        SubstrateCompilationMXBean compilationMXBean = new SubstrateCompilationMXBean();
        this.threadMXBean = threadMXBean;

        /* Register MXBeans. */
        mxBeans.addSingleton(java.lang.management.ClassLoadingMXBean.class, classLoadingMXBean);
        mxBeans.addSingleton(java.lang.management.CompilationMXBean.class, compilationMXBean);
        mxBeans.addSingleton(java.lang.management.RuntimeMXBean.class, runtimeMXBean);
        mxBeans.addSingleton(com.sun.management.ThreadMXBean.class, threadMXBean);

        /* Register MXBean suppliers for beans that need a more complex logic. */
        mxBeans.addSingleton(getOsMXBeanInterface(), (PlatformManagedObjectSupplier) this::getOsMXBean);
        if (FLIGHT_RECORDER_MX_BEAN_CLASS != null) {
            mxBeans.addSingleton(FLIGHT_RECORDER_MX_BEAN_CLASS, (PlatformManagedObjectSupplier) this::getFlightRecorderMXBean);
        }
    }

    @Fold
    public static ManagementSupport getSingleton() {
        return ImageSingletons.lookup(ManagementSupport.class);
    }

    public <T extends PlatformManagedObject> T getPlatformMXBean(Class<T> clazz) {
        Object result = getPlatformMXBeans0(clazz);
        if (result == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not a platform management interface");
        } else if (result instanceof List) {
            throw new IllegalArgumentException(clazz.getName() + " can have more than one instance");
        }
        return clazz.cast(resolveMXBean(result));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public PlatformManagedObject getPlatformMXBeanRaw(Class<? extends PlatformManagedObject> clazz) {
        return (PlatformManagedObject) getPlatformMXBeans0(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T extends PlatformManagedObject> List<T> getPlatformMXBeans(Class<T> clazz) {
        Object result = getPlatformMXBeans0(clazz);
        if (result == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not a platform management interface");
        } else if (result instanceof List) {
            return (List<T>) result;
        }
        return Collections.singletonList(clazz.cast(resolveMXBean(result)));
    }

    private Object getPlatformMXBeans0(Class<? extends PlatformManagedObject> clazz) {
        Object result = mxBeans.get(clazz);
        if (result == null) {
            result = GCRelatedMXBeans.mxBeans().get(clazz);
        } else {
            assert GCRelatedMXBeans.mxBeans().get(clazz) == null;
        }
        return result;
    }

    public Set<Class<? extends PlatformManagedObject>> getPlatformManagementInterfaces() {
        Set<Class<? extends PlatformManagedObject>> result = new HashSet<>(mxBeans.classToObject.keySet());
        result.addAll(GCRelatedMXBeans.mxBeans().classToObject.keySet());
        return Collections.unmodifiableSet(result);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Set<PlatformManagedObject> getPlatformManagedObjects() {
        Set<PlatformManagedObject> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(mxBeans.objects);
        result.addAll(GCRelatedMXBeans.mxBeans().objects);
        return result;
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed before the thread is fully started.")
    @Override
    public void beforeThreadStart(IsolateThread isolateThread, Thread javaThread) {
        threadMXBean.noteThreadStart(javaThread);
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed after Thread.exit.")
    @Override
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        threadMXBean.noteThreadFinish(javaThread);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isAllowedPlatformManagedObject(PlatformManagedObject object) {
        if (mxBeans.contains(object) || GCRelatedMXBeans.mxBeans().contains(object)) {
            /* Object is provided by our registry. */
            return true;
        }

        for (Class<? extends PlatformManagedObject> clazz : ManagementFactory.getPlatformManagementInterfaces()) {
            if (clazz.isInstance(object)) {
                if (ManagementFactory.getPlatformMXBeans(clazz).contains(object)) {
                    /*
                     * Object is provided by the hosting HotSpot VM. It must not be reachable in the
                     * image heap, because it is for the wrong VM.
                     */
                    return false;
                }
            }
        }

        /*
         * Object provided neither by our registry nor by the hosting HotSpot VM, i.e., it is
         * provided by the application. This is allowed.
         */
        return true;
    }

    public synchronized MBeanServer getPlatformMBeanServer() {
        if (platformMBeanServer == null) {
            /* Modified version of JDK 11: ManagementFactory.getPlatformMBeanServer */
            platformMBeanServer = MBeanServerFactory.createMBeanServer();
            for (PlatformManagedObject platformManagedObject : mxBeans.objects) {
                addMXBean(platformMBeanServer, resolveMXBean(platformManagedObject));
            }
            for (PlatformManagedObject platformManagedObject : GCRelatedMXBeans.mxBeans().objects) {
                addMXBean(platformMBeanServer, resolveMXBean(platformManagedObject));
            }
        }
        return platformMBeanServer;
    }

    private static PlatformManagedObject resolveMXBean(Object object) {
        assert object instanceof PlatformManagedObject;
        return object instanceof PlatformManagedObjectSupplier pmos ? pmos.get() : (PlatformManagedObject) object;
    }

    /* Modified version of JDK 11: ManagementFactory.addMXBean */
    private static void addMXBean(MBeanServer mbs, PlatformManagedObject pmo) {
        if (pmo == null) {
            return;
        }
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

    private synchronized OperatingSystemMXBean getOsMXBean() {
        if (osMXBean == null) {
            Object osMXBeanImpl = Platform.includedIn(InternalPlatform.PLATFORM_JNI.class)
                            ? new Target_com_sun_management_internal_OperatingSystemImpl(null)
                            : new Target_sun_management_BaseOperatingSystemImpl(null);
            osMXBean = SubstrateUtil.cast(osMXBeanImpl, OperatingSystemMXBean.class);
        }
        return osMXBean;
    }

    /**
     * Requires JFR support and that JMX is user-enabled because
     * {@code jdk.management.jfr.FlightRecorderMXBeanImpl} makes
     * {@code com.sun.jmx.mbeanserver.MBeanSupport} reachable.
     */
    private synchronized PlatformManagedObject getFlightRecorderMXBean() {
        if (!HasJfrSupport.get() || !JmxIncluded.get()) {
            return null;
        }
        if (flightRecorderMXBean == null) {
            flightRecorderMXBean = SubstrateUtil.cast(new Target_jdk_management_jfr_FlightRecorderMXBeanImpl(), PlatformManagedObject.class);
        }
        return flightRecorderMXBean;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unchecked")
    private static Class<? extends PlatformManagedObject> getFlightRecorderMXBeanClass() {
        var jfrModule = ModuleLayer.boot().findModule("jdk.management.jfr");
        if (jfrModule.isPresent()) {
            ManagementSupport.class.getModule().addReads(jfrModule.get());
            try {
                return (Class<? extends PlatformManagedObject>) Class.forName("jdk.management.jfr.FlightRecorderMXBean", false, Object.class.getClassLoader());
            } catch (ClassNotFoundException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Class<? extends PlatformManagedObject> getOsMXBeanInterface() {
        if (Platform.includedIn(InternalPlatform.PLATFORM_JNI.class)) {
            return Platform.includedIn(InternalPlatform.WINDOWS_BASE.class)
                            ? com.sun.management.OperatingSystemMXBean.class
                            : com.sun.management.UnixOperatingSystemMXBean.class;
        }
        return java.lang.management.OperatingSystemMXBean.class;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean verifyNoOverlappingMxBeans() {
        Set<Class<? extends PlatformManagedObject>> overlapping = new HashSet<>(mxBeans.classToObject.keySet());
        overlapping.retainAll(GCRelatedMXBeans.mxBeans().classToObject.keySet());
        return overlapping.isEmpty();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static class JdkManagementJfrModulePresent implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return FLIGHT_RECORDER_MX_BEAN_CLASS != null;
        }
    }

    /**
     * A {@link Supplier} that returns a {@link PlatformManagedObject}. By registering a supplier
     * instead of an actual {@link PlatformManagedObject} implementation, code will be executed at
     * run-time when someone tries to access the {@link PlatformManagedObject}.
     */
    public interface PlatformManagedObjectSupplier extends Supplier<PlatformManagedObject>, PlatformManagedObject {
        @Override
        default ObjectName getObjectName() {
            throw VMError.shouldNotReachHereOverrideInChild(); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static class MXBeans {
        /**
         * All {@link PlatformManagedObject}s structured by their interface. The same object can be
         * contained multiple times under different keys. The value is either the
         * {@link PlatformManagedObject} itself for singleton interfaces, or a {@link List} for
         * zero-or-more interfaces. Note that the list can be empty, denoting that the key is a
         * valid platform interface that can be queried but no implementations are registered.
         */
        private final Map<Class<? extends PlatformManagedObject>, Object> classToObject = new HashMap<>();

        /** All {@link PlatformManagedObject} as a flat set (no structure, no duplicates). */
        private final Set<PlatformManagedObject> objects = Collections.newSetFromMap(new IdentityHashMap<>());

        @Platforms(Platform.HOSTED_ONLY.class)
        public MXBeans() {
        }

        public Object get(Class<? extends PlatformManagedObject> clazz) {
            return classToObject.get(clazz);
        }

        public boolean contains(PlatformManagedObject object) {
            return objects.contains(object);
        }

        /**
         * Registers a {@link PlatformManagedObject} singleton for the provided interface and all
         * its superinterfaces.
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        @SuppressWarnings("unchecked")
        public void addSingleton(Class<? extends PlatformManagedObject> clazz, PlatformManagedObject object) {
            if (!clazz.isInterface()) {
                throw UserError.abort("Key for registration of a PlatformManagedObject must be an interface");
            }

            for (Class<?> superinterface : clazz.getInterfaces()) {
                if (superinterface != PlatformManagedObject.class && PlatformManagedObject.class.isAssignableFrom(superinterface)) {
                    addSingleton((Class<? extends PlatformManagedObject>) superinterface, object);
                }
            }

            Object existing = classToObject.get(clazz);
            if (existing != null) {
                throw UserError.abort("PlatformManagedObject already registered: %s", clazz.getName());
            }
            classToObject.put(clazz, object);
            objects.add(object);
        }

        /**
         * Adds a list of {@link PlatformManagedObject}s for the provided interface and all its
         * superinterfaces.
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        @SuppressWarnings("unchecked")
        public void addList(Class<? extends PlatformManagedObject> clazz, List<? extends PlatformManagedObject> beans) {
            if (!clazz.isInterface()) {
                throw UserError.abort("Key for registration of a PlatformManagedObject must be an interface");
            }

            for (Class<?> superinterface : clazz.getInterfaces()) {
                if (superinterface != PlatformManagedObject.class && PlatformManagedObject.class.isAssignableFrom(superinterface)) {
                    addList((Class<? extends PlatformManagedObject>) superinterface, beans);
                }
            }

            Object existing = classToObject.get(clazz);
            if (existing instanceof PlatformManagedObject) {
                throw UserError.abort("PlatformManagedObject already registered as a singleton: %s", clazz.getName());
            }

            ArrayList<PlatformManagedObject> newList = new ArrayList<>();
            if (existing != null) {
                newList.addAll((List<PlatformManagedObject>) existing);
            }
            newList.addAll(beans);
            newList.trimToSize();

            classToObject.put(clazz, Collections.unmodifiableList(newList));
            this.objects.addAll(beans);
        }
    }
}

// This is required because FlightRecorderMXBeanImpl is only accessible within its package.
@TargetClass(className = "jdk.management.jfr.FlightRecorderMXBeanImpl", onlyWith = ManagementSupport.JdkManagementJfrModulePresent.class)
final class Target_jdk_management_jfr_FlightRecorderMXBeanImpl {
    @Alias
    Target_jdk_management_jfr_FlightRecorderMXBeanImpl() {
    }
}
