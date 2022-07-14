/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.management.ManagementFeature;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.jdk.management.SubstrateThreadMXBean;
import com.oracle.svm.core.posix.headers.Time;

public class PosixSubstrateThreadMXBean extends SubstrateThreadMXBean {

    @Override
    public boolean isCurrentThreadCpuTimeSupported() {
        return true;
    }

    @Override
    public boolean isThreadCpuTimeEnabled() {
        return true;
    }

    @Override
    public void setThreadCpuTimeEnabled(boolean enable) {
    }

    @Override
    public long getCurrentThreadCpuTime() {
        Time.timespec time = StackValue.get(Time.timespec.class);
        if (Time.NoTransitions.clock_gettime(Time.CLOCK_THREAD_CPUTIME_ID(), time) != 0) {
            return -1;
        }
        return TimeUnit.SECONDS.toNanos(time.tv_sec()) + time.tv_nsec();
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@AutomaticFeature
class PosixSubstrateThreadMXBeanFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(ManagementFeature.class);
    }

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        ManagementSupport.getSingleton().addPlatformManagedObjectSingleton(ThreadMXBean.class, new PosixSubstrateThreadMXBean());
    }
}
