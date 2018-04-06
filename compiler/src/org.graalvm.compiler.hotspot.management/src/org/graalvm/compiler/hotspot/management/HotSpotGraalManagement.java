/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

/**
 * Dynamically registers an MBean with the {@link ManagementFactory#getPlatformMBeanServer()}. This
 * interface relies on regular {@linkplain #poll() polling} from Graal (e.g., each compilation) so
 * that the registration happens soon after the MBean server is created.
 */
@ServiceProvider(HotSpotGraalManagementRegistration.class)
public final class HotSpotGraalManagement implements HotSpotGraalManagementRegistration {

    private HotSpotGraalRuntimeMBean bean;
    private volatile boolean registeredWithServer;

    /**
     * Counter for creating unique {@link ObjectName}s.
     */
    private static final AtomicInteger counter = new AtomicInteger();

    @Override
    public void initialize(HotSpotGraalRuntime runtime) {
        if (bean == null) {
            try {
                String name = runtime.getClass().getSimpleName();
                ObjectName objectName = new ObjectName("org.graalvm.compiler.hotspot:type=" + name + counter.getAndIncrement());
                bean = new HotSpotGraalRuntimeMBean(objectName, runtime);
            } catch (MalformedObjectNameException err) {
                err.printStackTrace(TTY.out);
            }
        } else if (bean.getRuntime() != runtime) {
            throw new IllegalArgumentException("Cannot change the runtime a management interface is associated with");
        }
    }

    private static MBeanServer platformMBeanServer;

    /**
     * Gets the platform MBean server if it is initialized.
     */
    @SuppressFBWarnings(value = "LI_LAZY_INIT_STATIC", justification = "the result of ManagementFactory.getPlatformMBeanServer() is always the same")
    static MBeanServer getServer() {
        if (platformMBeanServer == null) {
            ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
            if (!servers.isEmpty()) {
                platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            }
        }
        return platformMBeanServer;
    }

    @Override
    public ObjectName poll() {
        if (bean == null) {
            // initialize() has not been called, it failed or registration failed
            return null;
        }
        MBeanServer server = getServer();
        if (server != null && !registeredWithServer) {
            synchronized (this) {
                if (!registeredWithServer) {
                    try {
                        server.registerMBean(bean, bean.getObjectName());
                    } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
                        e.printStackTrace(TTY.out);
                        // Registration failed - don't try again
                        bean = null;
                    }
                    registeredWithServer = true;
                }
            }
        }
        return !registeredWithServer ? null : bean.getObjectName();
    }
}
