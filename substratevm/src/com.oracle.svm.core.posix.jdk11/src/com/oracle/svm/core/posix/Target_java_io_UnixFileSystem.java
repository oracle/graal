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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.posix.headers.Limits;
import com.oracle.svm.core.posix.headers.Unistd;

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@TargetClass(className = "java.io.UnixFileSystem", onlyWith = JDK11OrLater.class)
final class Target_java_io_UnixFileSystem_JDK11OrLater {

    /* { Do not re-format commented out C code. @formatter:off */
    //   514  JNIEXPORT jlong JNICALL
    //   515  Java_java_io_UnixFileSystem_getNameMax0(JNIEnv *env, jobject this,
    //   516                                          jstring pathname)
    //   517  {
    @Substitute //
    @SuppressWarnings({"unused"})
    /* native */ long getNameMax0(String pathName) {
        //   518      jlong length = -1;
        final long length;
        //   519      WITH_PLATFORM_STRING(env, pathname, path) {
        try (CTypeConversion.CCharPointerHolder path = CTypeConversion.toCString(pathName)) {
        //   520          length = (jlong)pathconf(path, _PC_NAME_MAX);
            length = Unistd.pathconf(path.get(), Unistd._PC_NAME_MAX());
        //   521      } END_PLATFORM_STRING(env, path);
        }
        //   522      return length != -1 ? length : (jlong)NAME_MAX;
        return length != -1 ? length : Limits.NAME_MAX();
        //   523  }
    }
    /* } Do not re-format commented out C code. @formatter:on */

}
