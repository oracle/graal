/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management.libgraal.runtime;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalScope;


final class Factory extends Thread {

    private static final int POLL_INTERVAL_MS = 2000;

    private MBeanServer platformMBeanServer;

    private Factory() {
        super("HotSpotGraalManagement Bean Registration");
        this.setPriority(Thread.MIN_PRIORITY);
        this.setDaemon(true);
        LibGraal.registerNativeMethods(runtime(), HotSpotToSVMCalls.class);
    }

    @Override
    public void run() {
        System.out.println("Running factory Thread.");
        while (poll()) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                // Be verbose about unexpected interruption and then continue
                e.printStackTrace(TTY.out);
            }
        }
    }

    private synchronized boolean poll() {
        if (platformMBeanServer == null) {
            try {
                ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
                if (!servers.isEmpty()) {
                    platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
                    process();
                }
            } catch (SecurityException | UnsatisfiedLinkError | NoClassDefFoundError | UnsupportedOperationException e) {
                // Without permission to find or create the MBeanServer,
                // we cannot process any Graal mbeans.
                // Various other errors can occur in the ManagementFactory (JDK-8076557)
                return false;
            }
        } else {
            process();
        }
        return true;
    }

    private void process() {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            System.out.println("Enter");
            try {
                long[] svmRegistrations =  HotSpotToSVMCalls.pollRegistrations(LibGraalScope.getIsolateThread());
                for (long svmRegistration : svmRegistrations) {
                    System.out.println("Processing....");
                    try {
                        SVMHotSpotGraalRuntimeMBean bean = new SVMHotSpotGraalRuntimeMBean(svmRegistration);
                        String name = HotSpotToSVMCalls.getRegistrationName(LibGraalScope.getIsolateThread(), svmRegistration);
                        System.out.println("\tRegistering bean: " + name);
                        platformMBeanServer.registerMBean(bean, new ObjectName("org.graalvm.compiler.hotspot:type=" + name));
                        System.out.println("\tRegistered");
                    } catch (MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
                        e.printStackTrace(TTY.out);
                    }
                }
                HotSpotToSVMCalls.finishRegistration(LibGraalScope.getIsolateThread(), svmRegistrations);
                System.out.println("Done");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    static Factory create() {
        Factory factory = new Factory();
        factory.start();
        return factory;
    }
}
