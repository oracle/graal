/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.c.libc.CosmoLibC;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.posix.cosmo.headers.Limits;
import com.oracle.svm.core.posix.cosmo.headers.Stdlib;
import com.oracle.svm.core.posix.cosmo.headers.Unistd;
import com.oracle.svm.core.posix.cosmo.headers.Utsname;
import org.graalvm.word.impl.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.RuntimeSystemPropertiesSupport;

public class CosmoSystemPropertiesSupport extends SystemPropertiesSupport {

    /*
     * Initialization code is adapted from the JDK native code that initializes the system
     * properties, as found in src/solaris/native/java/lang/java_props_md.c
     */

    @Override
    protected String userNameValue() {
        String name = CosmoUtils.getUserName(Unistd.getuid());
        return name == null ? "?" : name;
    }

    @Override
    protected String userHomeValue() {
        String dir = CosmoUtils.getUserDir(Unistd.getuid());
        return dir == null ? "?" : dir;
    }

    @Override
    protected String userDirValue() {
        int bufSize = 4096; /* TODO (ahgamut): use MAXPATHLEN(); */
        CCharPointer buf = UnsafeStackValue.get(bufSize);
        if (Unistd.getcwd(buf, Word.unsigned(bufSize)).isNonNull()) {
            return CTypeConversion.toJavaString(buf);
        } else {
            throw new Error("Properties init: Could not determine current working directory.");
        }
    }

    @Override
    protected String javaIoTmpdirValue() {
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
        try (CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString("LD_LIBRARY_PATH")) {
            ldLibraryPath = Stdlib.getenv(name.get());
        }

        if (ldLibraryPath.isNull()) {
            return DEFAULT_LIBPATH;
        }
        return CTypeConversion.toJavaString(ldLibraryPath) + ":" + DEFAULT_LIBPATH;
    }

    @Override
    protected String osNameValue() {
        Utsname.utsname name = UnsafeStackValue.get(Utsname.utsname.class);
        if (Utsname.uname(name) >= 0) {
            return CTypeConversion.toJavaString(name.sysname());
        }
        return "Unknown";
    }

    @Override
    protected String osVersionValue() {
        Utsname.utsname name = UnsafeStackValue.get(Utsname.utsname.class);
        if (Utsname.uname(name) >= 0) {
            return CTypeConversion.toJavaString(name.release());
        }
        return "Unknown";
    }
}

@AutomaticallyRegisteredFeature
class CosmoSystemPropertiesFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (LibCBase.singleton() instanceof CosmoLibC) {
            ImageSingletons.add(RuntimeSystemPropertiesSupport.class, new CosmoSystemPropertiesSupport());
            /* GR-42971 - Remove once SystemPropertiesSupport.class ImageSingletons use is gone. */
            ImageSingletons.add(SystemPropertiesSupport.class, (SystemPropertiesSupport) ImageSingletons.lookup(RuntimeSystemPropertiesSupport.class));
        }
    }
}
