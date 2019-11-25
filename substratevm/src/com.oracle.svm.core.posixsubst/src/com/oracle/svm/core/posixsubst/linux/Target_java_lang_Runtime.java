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
package com.oracle.svm.core.posixsubst.linux;

import static org.graalvm.nativeimage.UnmanagedMemory.calloc;
import static org.graalvm.nativeimage.UnmanagedMemory.free;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posixsubst.headers.Unistd;
import com.oracle.svm.core.posixsubst.headers.linux.LinuxSched;

@Platforms(DeprecatedPlatform.LINUX_SUBSTITUTION.class)
@TargetClass(java.lang.Runtime.class)
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {

    @Substitute
    public int availableProcessors() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            LinuxSched.cpu_set_t cpuSet;
            int size;
            int res;
            final int pid = Unistd.getpid();
            // try easy path
            cpuSet = StackValue.get(LinuxSched.cpu_set_t.class);
            size = SizeOf.get(LinuxSched.cpu_set_t.class);
            res = LinuxSched.sched_getaffinity(pid, size, cpuSet);
            if (res == 0) {
                return LinuxSched.CPU_COUNT_S(size, cpuSet);
            }
            // try with more CPUs in a loop
            size = Integer.highestOneBit(size) << 1;
            while (Integer.numberOfTrailingZeros(size) < 16) { // we have to give up at *some* point
                assert Integer.bitCount(size) == 1;
                cpuSet = calloc(size); // to be safe
                if (cpuSet.isNull()) {
                    throw new InternalError("Cannot determine CPU count");
                }
                try {
                    res = LinuxSched.sched_getaffinity(pid, size, cpuSet);
                    if (res == 0) {
                        return LinuxSched.CPU_COUNT_S(size, cpuSet);
                    }
                } finally {
                    free(cpuSet);
                }
                size <<= 1;
            }
            // give up
            res = (int) Unistd.sysconf(Unistd._SC_NPROCESSORS_ONLN());
            if (res == -1) {
                throw new InternalError("Cannot determine CPU count");
            }
            return res;
        } else {
            return 1;
        }
    }
}
