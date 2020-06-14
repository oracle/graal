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

//Checkstyle: stop
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.ObjectName;

import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.util.VMError;

import sun.management.Util;
//Checkstyle: resume

final class SubstrateRuntimeMXBean implements RuntimeMXBean {

    private static final String MSG = "RuntimeMXBean methods";

    private long startMillis = 0;

    @Platforms(Platform.HOSTED_ONLY.class)
    SubstrateRuntimeMXBean() {
        RuntimeSupport.getRuntimeSupport().addInitializationHook(this::initialize);
    }

    void initialize() {
        /* Set the start time of the VM. */
        startMillis = System.currentTimeMillis();
    }

    @Override
    public List<String> getInputArguments() {
        if (ImageSingletons.contains(JavaMainWrapper.JavaMainSupport.class)) {
            return ImageSingletons.lookup(JavaMainWrapper.JavaMainSupport.class).getInputArguments();
        }
        return Collections.emptyList();
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getName() {
        long id;
        String hostName;
        try {
            id = ProcessProperties.getProcessID();
        } catch (Throwable t) {
            id = GraalServices.getGlobalTimeStamp();
        }
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "localhost";
        }
        return id + "@" + hostName;
    }

    /* All remaining methods are unsupported on Substrate VM. */

    @Override
    public String getVmName() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getVmVendor() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getVmVersion() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getSpecName() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getSpecVendor() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getSpecVersion() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getManagementSpecVersion() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getClassPath() {
        return System.getProperty("java.class.path");
    }

    @Override
    public String getLibraryPath() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public boolean isBootClassPathSupported() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getBootClassPath() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getUptime() {
        return System.currentTimeMillis() - startMillis;
    }

    @Override
    public long getStartTime() {
        assert startMillis > 0 : "SubstrateRuntimeMXBean.getStartTime: Should have set SubstrateRuntimeMXBean.startMillis.";
        return startMillis;
    }

    /** Copied from {@code sun.management.RuntimeImpl#getSystemProperties()}. */
    @Override
    public Map<String, String> getSystemProperties() {
        Properties sysProps = System.getProperties();
        Map<String, String> map = new HashMap<>();

        // Properties.entrySet() does not include the entries in
        // the default properties. So use Properties.stringPropertyNames()
        // to get the list of property keys including the default ones.
        Set<String> keys = sysProps.stringPropertyNames();
        for (String k : keys) {
            String value = sysProps.getProperty(k);
            map.put(k, value);
        }

        return map;
    }
}
