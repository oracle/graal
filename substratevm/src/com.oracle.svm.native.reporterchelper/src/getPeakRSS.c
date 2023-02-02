/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#include <jni.h>

#if defined(__linux__)

#include <sys/resource.h>

JNIEXPORT jlong JNICALL Java_com_oracle_svm_hosted_ProgressReporterCHelper_getPeakRSS0(void *env, void * ignored) {
    struct rusage rusage;
    if (getrusage(RUSAGE_SELF, &rusage) == 0) {
        return (size_t)rusage.ru_maxrss * 1024; /* (in kilobytes) */
    } else {
        return -1;
    }
}

#elif defined(__APPLE__)

#include <unistd.h>
#include <sys/resource.h>
#include <mach/mach.h>

JNIEXPORT jlong JNICALL Java_com_oracle_svm_hosted_ProgressReporterCHelper_getPeakRSS0(void *env, void * ignored) {
    struct mach_task_basic_info info;
    mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;
    if (task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&info, &count) == KERN_SUCCESS) {
        return (size_t)info.resident_size_max;
    } else {
        return -1;
    }
}

#elif defined(_WIN64)

#include <windows.h>
#include <psapi.h>

JNIEXPORT jlong JNICALL Java_com_oracle_svm_hosted_ProgressReporterCHelper_getPeakRSS0(void *env, void * ignored) {
    PROCESS_MEMORY_COUNTERS memCounter;
    if (GetProcessMemoryInfo(GetCurrentProcess(), &memCounter, sizeof memCounter)) {
        return (size_t)memCounter.PeakWorkingSetSize;
    } else {
        return -1;
    }
}

#endif
