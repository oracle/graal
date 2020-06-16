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
package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.core.posix.headers.Time.gettimeofday;
import static com.oracle.svm.core.posix.headers.darwin.DarwinTime.mach_absolute_time;
import static com.oracle.svm.core.posix.headers.darwin.DarwinTime.mach_timebase_info;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.headers.Time.timeval;
import com.oracle.svm.core.posix.headers.Time.timezone;
import com.oracle.svm.core.posix.headers.darwin.DarwinTime.MachTimebaseInfo;

@TargetClass(java.lang.System.class)
final class Target_java_lang_System_Darwin {

    @Substitute
    @Uninterruptible(reason = "Does basic math after a few simple system calls")
    private static long nanoTime() {
        final Util_java_lang_System utilJavaLangSystem = ImageSingletons.lookup(Util_java_lang_System.class);

        if (utilJavaLangSystem.fastTime) {
            return mach_absolute_time();
        }

        if (!utilJavaLangSystem.timeBaseValid) {
            MachTimebaseInfo timeBaseInfo = StackValue.get(MachTimebaseInfo.class);
            if (mach_timebase_info(timeBaseInfo) == 0) {
                if (timeBaseInfo.getdenom() == 1 && timeBaseInfo.getnumer() == 1) {
                    utilJavaLangSystem.fastTime = true;
                    return mach_absolute_time();
                }
                utilJavaLangSystem.factor = (double) timeBaseInfo.getnumer() / (double) timeBaseInfo.getdenom();
            }
            utilJavaLangSystem.timeBaseValid = true;
        }

        if (utilJavaLangSystem.factor != 0) {
            return (long) (mach_absolute_time() * utilJavaLangSystem.factor);
        }

        /* High precision time is not available, fall back to low precision. */
        timeval timeval = StackValue.get(timeval.class);
        timezone timezone = WordFactory.nullPointer();
        gettimeofday(timeval, timezone);
        return timeval.tv_sec() * 1_000_000_000L + timeval.tv_usec() * 1_000L;
    }

    @Substitute
    public static String mapLibraryName(String libname) {
        return "lib" + libname + ".dylib";
    }
}

/** Additional static-like fields for {@link Target_java_lang_System_Darwin}. */
final class Util_java_lang_System {
    boolean timeBaseValid = false;
    boolean fastTime = false;
    double factor = 0.0;

    Util_java_lang_System() {
        /* Nothing to do. */
    }
}

@AutomaticFeature
class DarwinSubsitutionsFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(Util_java_lang_System.class, new Util_java_lang_System());
    }
}

/** Dummy class to have a class with the file's name. */
public final class DarwinSubstitutions {
}
