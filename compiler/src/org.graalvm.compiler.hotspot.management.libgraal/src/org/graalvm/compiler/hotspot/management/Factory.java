/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * A factory thread creating the {@link LibGraalMBean} instances for {@link DynamicMBean}s in
 * libgraal heap and registering them to {@link MBeanServer}.
 */
@Platforms(value = Platform.HOSTED_ONLY.class)
public final class Factory extends Thread {

    private static final String DOMAIN_GRAALVM_HOTSPOT = "org.graalvm.compiler.hotspot";
    private static final String TYPE_LIBGRAAL = "Libgraal";
    private static final String ATTR_TYPE = "type";
    private static final int POLL_INTERVAL_MS = 2000;
    private static volatile Factory instance;
    private MBeanServer platformMBeanServer;
    private AggregatedMemoryPoolBean aggregatedMemoryPoolBean;
    private Map<Long, ObjectName[]> mbeansForActiveIsolate;

    /**
     * Set of isolates yet to be processed for MBean registrations.
     */
    private final Set<Long> pendingIsolates;

    @SuppressWarnings("try")
    private Factory() {
        super("Libgraal MBean Registration");
        this.pendingIsolates = new LinkedHashSet<>();
        this.setPriority(Thread.MIN_PRIORITY);
        this.setDaemon(true);
        // The Factory class is a singleton, it's guaranteed that native method registration is
        // performed at most once. The Factory instance is never created by the isolate shutdown
        // hook as the unregister method is called by the isolate only if the Factory was already
        // created by the registration thread.
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            LibGraal.registerNativeMethods(JMXToLibGraalCalls.class);
        }
    }

    /**
     * Main loop waiting for {@link DynamicMBean} creation in libgraal heap. When a new
     * {@link DynamicMBean} is created in the libgraal heap this thread creates a new
     * {@link LibGraalMBean} encapsulating the {@link DynamicMBean} and registers it to
     * {@link MBeanServer}.
     */
    @Override
    public void run() {
        while (true) {
            try {
                synchronized (this) {
                    // Wait until there are deferred registrations to process
                    while (pendingIsolates.isEmpty()) {
                        wait();
                    }
                    try {
                        poll();
                    } catch (SecurityException | UnsatisfiedLinkError | NoClassDefFoundError | UnsupportedOperationException e) {
                        // Without permission to find or create the MBeanServer,
                        // we cannot process any Graal mbeans.
                        // Various other errors can occur in the ManagementFactory (JDK-8076557)
                        break;
                    }
                }
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                // Be verbose about unexpected interruption and then continue
                e.printStackTrace(TTY.out);
            }
        }
    }

    /**
     * Called by {@code MBeanProxy} in libgraal heap to notify this factory of an isolate with
     * {@link DynamicMBean}s that needs registration.
     */
    synchronized void signalRegistrationRequest(long isolate) {
        pendingIsolates.add(isolate);
        notify();
    }

    /**
     * Called by {@code MBeanProxy} in libgraal heap when the isolate is closing to unregister its
     * {@link DynamicMBean}s.
     */
    synchronized void unregister(long isolate) {
        // Remove pending registration requests
        if (pendingIsolates.remove(isolate)) {
            // The libgraal compiler notifies the Factory about a new runtime by calling the
            // #signalRegistrationRequest(long) method.
            // The #signalRegistrationRequest(long) method only puts the isolate into the
            // #pendingIsolatesand set and notifies the working thread.
            // The working thread processes the pending isolates asynchronously in the #run() and
            // removes them from the #pendingIsolates set.
            // When the #pendingIsolates contains the isolate the isolate was not yet processed and
            // there are no registered MBeans to remove from MBeanServer.
            return;
        }
        MBeanServer mBeanServer = findMBeanServer();
        if (mBeanServer == null) {
            // Nothing registered yet.
            return;
        }
        // The mbeansForActiveIsolate can be null when Factory#process() failed with an exception.
        ObjectName[] objectNames = mbeansForActiveIsolate == null ? null : mbeansForActiveIsolate.remove(isolate);
        if (objectNames != null) {
            for (ObjectName objectName : objectNames) {
                try {
                    if (aggregatedMemoryPoolBean != null && isLibGraalMBean(objectName)) {
                        aggregatedMemoryPoolBean.removeDelegate(objectName);
                    }
                    if (mBeanServer.isRegistered(objectName)) {
                        mBeanServer.unregisterMBean(objectName);
                    }
                } catch (MBeanRegistrationException | InstanceNotFoundException e) {
                    e.printStackTrace(TTY.out);
                }
            }
        }
    }

    /**
     * In case of successful {@link MBeanServer} initialization creates {@link LibGraalMBean}s for
     * pending libgraal {@link DynamicMBean}s and registers them.
     *
     * @return {@code true} if {@link LibGraalMBean}s were successfuly registered, {@code false}
     *         when {@link MBeanServer} is not yet available and {@code poll} should be retried.
     * @throws SecurityException can be thrown by {@link MBeanServer}
     * @throws UnsatisfiedLinkError can be thrown by {@link MBeanServer}
     * @throws NoClassDefFoundError can be thrown by {@link MBeanServer}
     * @throws UnsupportedOperationException can be thrown by {@link MBeanServer}
     */
    private boolean poll() {
        MBeanServer mBeanServer = findMBeanServer();
        if (mBeanServer != null) {
            return process();
        } else {
            return false;
        }
    }

    /**
     * Returns a {@link MBeanServer} if it already exists.
     */
    private MBeanServer findMBeanServer() {
        assert Thread.holdsLock(this);
        if (platformMBeanServer == null) {
            ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
            if (!servers.isEmpty()) {
                platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            }
        }
        return platformMBeanServer;
    }

    /**
     * Creates {@link LibGraalMBean}s for pending libgraal {@link DynamicMBean}s and registers them
     * {@link MBeanServer}.
     *
     * @return {@code true}
     * @throws SecurityException can be thrown by {@link MBeanServer}
     * @throws UnsatisfiedLinkError can be thrown by {@link MBeanServer}
     * @throws NoClassDefFoundError can be thrown by {@link MBeanServer}
     * @throws UnsupportedOperationException can be thrown by {@link MBeanServer}
     */
    private boolean process() {
        for (Iterator<Long> iter = pendingIsolates.iterator(); iter.hasNext();) {
            long isolate = iter.next();
            iter.remove();
            try (LibGraalScope scope = new LibGraalScope(isolate)) {
                long isolateThread = scope.getIsolateThreadAddress();
                long[] handles = JMXToLibGraalCalls.pollRegistrations(isolateThread);
                if (handles.length > 0) {
                    List<ObjectName> objectNames = new ArrayList<>(handles.length);
                    try {
                        for (long handle : handles) {
                            LibGraalMBean bean = new LibGraalMBean(isolate, handle);
                            String name = JMXToLibGraalCalls.getObjectName(isolateThread, handle);
                            try {
                                ObjectName objectName = new ObjectName(name);
                                objectNames.add(objectName);
                                if (isLibGraalMBean(objectName)) {
                                    if (aggregatedMemoryPoolBean == null) {
                                        aggregatedMemoryPoolBean = new AggregatedMemoryPoolBean(bean, objectName);
                                        platformMBeanServer.registerMBean(aggregatedMemoryPoolBean, aggregatedMemoryPoolBean.getObjectName());
                                    } else {
                                        aggregatedMemoryPoolBean.addDelegate(bean, objectName);
                                    }
                                }
                                platformMBeanServer.registerMBean(bean, objectName);
                            } catch (MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
                                e.printStackTrace(TTY.out);
                            }
                        }
                    } finally {
                        if (mbeansForActiveIsolate == null) {
                            mbeansForActiveIsolate = new HashMap<>();
                        }
                        mbeansForActiveIsolate.put(isolate, objectNames.toArray(new ObjectName[objectNames.size()]));
                    }
                }
            }
        }
        return true;
    }

    /**
     * Tests if the given object name represents a libgraal MBean.
     */
    private static boolean isLibGraalMBean(ObjectName objectName) {
        Hashtable<String, String> props = objectName.getKeyPropertyList();
        return DOMAIN_GRAALVM_HOTSPOT.equals(objectName.getDomain()) && TYPE_LIBGRAAL.equals(props.get(ATTR_TYPE));
    }

    /**
     * Returns a factory for registering the {@link LibGraalMBean} instances into
     * {@link MBeanServer}. If the factory does not exist it is created and its registration thread
     * is started.
     */
    static Factory getInstance() {
        Factory res = instance;
        if (res == null) {
            synchronized (LibGraalMBean.class) {
                res = instance;
                if (res == null) {
                    try {
                        res = new Factory();
                        res.start();
                        instance = res;
                    } catch (LinkageError e) {
                        Throwable cause = findCause(e);
                        throw throwUnchecked(RuntimeException.class, cause);
                    }
                }
            }
        }
        return res;
    }

    private static Throwable findCause(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T throwUnchecked(Class<T> exceptionClass, Throwable exception) throws T {
        throw (T) exception;
    }
}
