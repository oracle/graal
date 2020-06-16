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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.management.ManagementFeature;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.jdk.management.SubstrateOperatingSystemMXBean;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Resource;
import com.oracle.svm.core.posix.headers.Times;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.darwin.DarwinStat;
import com.oracle.svm.core.posix.headers.linux.LinuxStat;
import com.oracle.svm.core.util.VMError;
import com.sun.management.UnixOperatingSystemMXBean;

class PosixSubstrateOperatingSystemMXBean extends SubstrateOperatingSystemMXBean implements UnixOperatingSystemMXBean {

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

    @Override
    public long getMaxFileDescriptorCount() {
        Resource.rlimit rlp = StackValue.get(Resource.rlimit.class);
        if (Resource.getrlimit(Resource.RLIMIT_NOFILE(), rlp) < 0) {
            throwUnchecked(PosixUtils.newIOExceptionWithLastError("getrlimit failed"));
        }
        return rlp.rlim_cur().rawValue();
    }

    @Override
    public long getOpenFileDescriptorCount() {
        int maxFileDescriptor = Unistd.getdtablesize();
        long count = 0;
        for (int i = 0; i <= maxFileDescriptor; i++) {
            if (fstat(i) == 0 || CErrorNumber.getCErrorNumber() != Errno.EBADF()) {
                count++;
            }
        }
        return count;
    }

    private static int fstat(int fd) {
        if (Platform.includedIn(Platform.LINUX.class)) {
            LinuxStat.stat64 stat = StackValue.get(LinuxStat.stat64.class);
            return LinuxStat.fstat64(fd, stat);
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            DarwinStat.stat64 stat = StackValue.get(DarwinStat.stat64.class);
            return DarwinStat.fstat64(fd, stat);
        } else {
            throw VMError.shouldNotReachHere("Unsupported platform");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable exception) throws T {
        throw (T) exception;
    }
}

@AutomaticFeature
class PosixSubstrateOperatingSystemMXBeanFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(ManagementFeature.class);
    }

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        ManagementSupport.getSingleton().addPlatformManagedObjectSingleton(UnixOperatingSystemMXBean.class, new PosixSubstrateOperatingSystemMXBean());
    }
}
