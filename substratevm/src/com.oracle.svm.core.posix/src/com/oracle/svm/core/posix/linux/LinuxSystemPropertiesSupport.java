/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.posix.PosixSystemPropertiesSupport;
import com.oracle.svm.core.posix.headers.Stdlib;
import com.oracle.svm.core.posix.headers.Utsname;

public class LinuxSystemPropertiesSupport extends PosixSystemPropertiesSupport {

    @Override
    protected String tmpdirValue() {
        /*
         * The initial value of `java.io.tmpdir` is hard coded in libjava when building the JDK. So
         * to be completely correct, we would have to use the value from libjava, but since it is
         * normally initialized to `/tmp` via `P_tmpdir`, this should be fine for now.
         */
        return "/tmp";
    }

    private static final String DEFAULT_LIBPATH = "/usr/lib64:/lib64:/lib:/usr/lib";

    @Override
    protected String javaLibraryPathValue() {
        /*
         * Adapted from `os::init_system_properties_values` in `src/hotspot/os/linux/os_linux.cpp`,
         * but omits HotSpot specifics.
         */
        CCharPointer ldLibraryPath;
        try (CCharPointerHolder name = CTypeConversion.toCString("LD_LIBRARY_PATH")) {
            ldLibraryPath = Stdlib.getenv(name.get());
        }

        if (ldLibraryPath.isNull()) {
            return DEFAULT_LIBPATH;
        }
        return CTypeConversion.toJavaString(ldLibraryPath) + ":" + DEFAULT_LIBPATH;
    }

    @Override
    protected String osNameValue() {
        Utsname.utsname name = StackValue.get(Utsname.utsname.class);
        if (Utsname.uname(name) >= 0) {
            return CTypeConversion.toJavaString(name.sysname());
        }
        return "Unknown";
    }

    @Override
    protected String osVersionValue() {
        Utsname.utsname name = StackValue.get(Utsname.utsname.class);
        if (Utsname.uname(name) >= 0) {
            return CTypeConversion.toJavaString(name.release());
        }
        return "Unknown";
    }
}

@AutomaticFeature
class LinuxSystemPropertiesFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(SystemPropertiesSupport.class, new LinuxSystemPropertiesSupport());
    }
}
