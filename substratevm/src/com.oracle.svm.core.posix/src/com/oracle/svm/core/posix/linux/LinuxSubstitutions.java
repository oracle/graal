/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.core.posix.headers.Time.gettimeofday;
import static com.oracle.svm.core.posix.headers.linux.LinuxTime.CLOCK_MONOTONIC;
import static com.oracle.svm.core.posix.headers.linux.LinuxTime.clock_gettime;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.headers.Time.timespec;
import com.oracle.svm.core.posix.headers.Time.timeval;
import com.oracle.svm.core.posix.headers.Time.timezone;

@TargetClass(java.lang.System.class)
final class Target_java_lang_System_Linux {

    @Substitute
    @Uninterruptible(reason = "Does basic math after a simple system call")
    private static long nanoTime() {
        timespec timespec = StackValue.get(timespec.class);
        if (clock_gettime(CLOCK_MONOTONIC(), timespec) == 0) {
            return timespec.tv_sec() * 1_000_000_000L + timespec.tv_nsec();

        } else {
            /* High precision time is not available, fall back to low precision. */
            timeval timeval = StackValue.get(timeval.class);
            timezone timezone = WordFactory.nullPointer();
            gettimeofday(timeval, timezone);
            return timeval.tv_sec() * 1_000_000_000L + timeval.tv_usec() * 1_000L;
        }
    }

    @Substitute
    public static String mapLibraryName(String libname) {
        return "lib" + libname + ".so";
    }
}

/** Dummy class to have a class with the file's name. */
public final class LinuxSubstitutions {
}
