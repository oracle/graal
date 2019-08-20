/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.SubstrateOperatingSystemMXBean;
import com.oracle.svm.core.posix.headers.Times;
import com.oracle.svm.core.posix.headers.Unistd;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.impl.InternalPlatform;

@Platforms({InternalPlatform.LINUX_AND_JNI.class, InternalPlatform.DARWIN_AND_JNI.class})
class PosixSubstrateOperatingSystemMXBean extends SubstrateOperatingSystemMXBean {

    /**
     * Returns the CPU time used by the process on which the SVM process is running in nanoseconds.
     * The returned value is of nanoseconds precision but not necessarily nanoseconds accuracy. This
     * method returns <tt>-1</tt> if the the platform does not support this operation.
     *
     * @return the CPU time used by the process in nanoseconds, or <tt>-1</tt> if this operation is
     *         not supported.
     */
    @Override
    public long getProcessCpuTime() {
        long clkTck = Unistd.sysconf(Unistd._SC_CLK_TCK());
        if (clkTck == -1) {
            // sysconf failed - not able to get clock tick
            return -1;
        }
        long nsPerTick = TimeUnit.SECONDS.toNanos(1) / clkTck;
        Times.tms time = StackValue.get(Times.tms.class);
        Times.times(time);
        return (time.tms_utime() + time.tms_stime()) * nsPerTick;
    }

}

@Platforms({InternalPlatform.LINUX_AND_JNI.class, InternalPlatform.DARWIN_AND_JNI.class})
@AutomaticFeature
class PosixSubstrateOperatingSystemMXBeanFeature implements Feature {
    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        ImageSingletons.add(OperatingSystemMXBean.class, new PosixSubstrateOperatingSystemMXBean());
    }
}
