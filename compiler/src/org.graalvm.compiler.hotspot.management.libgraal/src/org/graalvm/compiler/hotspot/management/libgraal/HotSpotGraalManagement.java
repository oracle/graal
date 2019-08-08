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
package org.graalvm.compiler.hotspot.management.libgraal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.graalvm.compiler.debug.TTY;

import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.management.HotSpotGraalRuntimeMBean;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(HotSpotGraalManagementRegistration.class)
public final class HotSpotGraalManagement implements HotSpotGraalManagementRegistration {

    private HotSpotGraalRuntimeMBean bean;
    private String name;
    private volatile boolean needsRegistration = true;
    HotSpotGraalManagement nextDeferred;

    public HotSpotGraalManagement() {
    }

    @Override
    public void initialize(HotSpotGraalRuntime runtime) {
        if (bean == null) {
            if (runtime.getManagement() != this) {
                throw new IllegalArgumentException("Cannot initialize a second management object for runtime " + runtime.getName());
            }
            try {
                name = runtime.getName().replace(':', '_');
                bean = new HotSpotGraalRuntimeMBean(new ObjectName("org.graalvm.compiler.hotspot:type=" + name), runtime);
                Factory.enqueue(this);
            } catch (MalformedObjectNameException err) {
                err.printStackTrace(TTY.out);
            }
        } else if (bean.getRuntime() != runtime) {
            throw new IllegalArgumentException("Cannot change the runtime a management interface is associated with");
        }
    }

    @Override
    public ObjectName poll(boolean sync) {
        if (bean == null || needsRegistration) {
            return null;
        }
        return bean.getObjectName();
    }

    HotSpotGraalRuntimeMBean getBean() {
        return bean;
    }

    void finishRegistration() {
        needsRegistration = false;
    }

    String getName() {
        return name;
    }

    static final class Factory implements Supplier<HotSpotGraalManagementRegistration> {

        private static Queue<HotSpotGraalManagement> registrations = new ArrayDeque<>();

        Factory() {
        }

        @Override
        public HotSpotGraalManagementRegistration get() {
            return new HotSpotGraalManagement();
        }

        static synchronized List<HotSpotGraalManagement> drain() {
            if (registrations.isEmpty()) {
                return Collections.emptyList();
            } else {
                List<HotSpotGraalManagement> res = new ArrayList<>(registrations);
                registrations.clear();
                return res;
            }
        }

        private static synchronized HotSpotGraalManagement enqueue(HotSpotGraalManagement instance) {
            registrations.add(instance);
            return instance;
        }
    }
}
