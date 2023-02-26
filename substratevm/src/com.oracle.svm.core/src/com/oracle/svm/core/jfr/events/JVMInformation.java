/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr.events;

import java.lang.management.ManagementFactory;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.Uninterruptible;

public class JVMInformation {

    private String jvmName;
    private String jvmVersion;
    private String jvmArguments;
    private String jvmFlags;
    private String javaArguments;
    private long jvmStartTime;
    private long jvmPid;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getJvmName() {
        return jvmName;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getJvmVersion() {
        return jvmVersion;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getJvmArguments() {
        return jvmArguments;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getJvmFlags() {
        return jvmFlags;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getJavaArguments() {
        return javaArguments;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getJvmStartTime() {
        return jvmStartTime;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getJvmPid() {
        return jvmPid;
    }

    public static JVMInformation getJVMInfo() {
        JVMInformation jvmInfo = new JVMInformation();

        if (ImageSingletons.contains(JavaMainWrapper.JavaMainSupport.class)) {
            JavaMainWrapper.JavaMainSupport support = ImageSingletons.lookup(JavaMainWrapper.JavaMainSupport.class);

            jvmInfo.jvmName = System.getProperty("java.vm.name");
            jvmInfo.jvmVersion = System.getProperty("java.vm.version");
            jvmInfo.jvmArguments = getVmArgs(support);
            jvmInfo.jvmFlags = "";
            jvmInfo.javaArguments = support.getJavaCommand();
            jvmInfo.jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
            jvmInfo.jvmPid = ManagementFactory.getRuntimeMXBean().getPid();
        }

        return jvmInfo;
    }

    private static String getVmArgs(JavaMainWrapper.JavaMainSupport support) {
        StringBuilder vmArgs = new StringBuilder();

        for (String arg : support.getInputArguments()) {
            vmArgs.append(arg).append(' ');
        }
        return vmArgs.toString().trim();
    }
}
