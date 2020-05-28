/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.management.HotSpotGraalRuntimeMBean;

/**
 * Dynamically registers a {@link HotSpotGraalRuntimeMBean}s created in libgraal heap with an
 * {@link MBeanServer} in the HotSpot heap. The instance is created by {@link HotSpotGraalRuntime}
 * using a factory injected by {@code LibGraalFeature}.
 */
public final class LibGraalHotSpotGraalManagement extends MBeanProxy<HotSpotGraalRuntimeMBean> implements HotSpotGraalManagementRegistration {

    public LibGraalHotSpotGraalManagement() {
    }

    /**
     * Creates a {@link HotSpotGraalRuntimeMBean} for given {@code runtime}. It first defines the
     * required classes in the HotSpot heap and starts the factory thread. Then it creates a
     * {@link HotSpotGraalRuntimeMBean} for given {@code runtime} and notifies the factory thread
     * about a new pending registration.
     *
     * @param runtime the runtime to create {@link HotSpotGraalRuntimeMBean} for
     * @param config the configuration used to obtain the {@code _jni_environment} offset
     */
    @Override
    public void initialize(HotSpotGraalRuntime runtime, GraalHotSpotVMConfig config) {
        if (!initializeJNI(config, runtime)) {
            return;
        }
        HotSpotGraalRuntimeMBean mbean = getBean();
        if (mbean == null) {
            if (runtime.getManagement() != this) {
                throw new IllegalArgumentException("Cannot initialize a second management object for runtime " + runtime.getName());
            }
            try {
                String beanName = nameWithIsolateId("org.graalvm.compiler.hotspot:type=" + runtime.getName().replace(':', '_'));
                ObjectName objectName = new ObjectName(beanName);
                mbean = new HotSpotGraalRuntimeMBean(objectName, runtime);
                initialize(mbean, beanName, objectName);
                enqueueForRegistrationAndNotify(this, runtime);
            } catch (MalformedObjectNameException err) {
                err.printStackTrace(TTY.out);
            }
        } else if (mbean.getRuntime() != runtime) {
            throw new IllegalArgumentException("Cannot change the runtime a management interface is associated with");
        }
    }

    @Override
    public ObjectName poll(boolean sync) {
        return poll();
    }

    /**
     * Factory for {@link LibGraalHotSpotGraalManagement}.
     */
    static final class Factory implements Supplier<HotSpotGraalManagementRegistration> {

        Factory() {
        }

        /**
         * Creates a new {@link LibGraalHotSpotGraalManagement} instance.
         */
        @Override
        public HotSpotGraalManagementRegistration get() {
            return new LibGraalHotSpotGraalManagement();
        }
    }
}
