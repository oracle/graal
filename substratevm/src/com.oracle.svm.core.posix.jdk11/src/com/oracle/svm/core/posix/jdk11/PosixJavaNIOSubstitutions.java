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
package com.oracle.svm.core.posix.jdk11;

import static com.oracle.svm.core.posix.PosixJavaNIOSubstitutions.convertReturnVal;
import static com.oracle.svm.core.posix.headers.Unistd.write;

import java.io.FileDescriptor;
import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Statvfs;

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class PosixJavaNIOSubstitutions {

    @TargetClass(className = "sun.nio.ch.IOUtil", onlyWith = JDK11OrLater.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_IOUtil {
        // ported from {jdk11}/src/java.base/unix/native/libnio/ch/IOUtil.c
        // 107 JNIEXPORT jint JNICALL
        // 108 Java_sun_nio_ch_IOUtil_write1(JNIEnv *env, jclass cl, jint fd, jbyte b)
        @Substitute
        static int write1(int fd, byte b) throws IOException {
            // 110 char c = (char)b;
            CCharPointer c = StackValue.get(CCharPointer.class);
            c.write(b);
            // 111 return convertReturnVal(env, write(fd, &c, 1), JNI_FALSE);
            return convertReturnVal(write(fd, c, WordFactory.unsigned(1)), false);
        }
    }

    @TargetClass(className = "sun.nio.ch.FileDispatcherImpl", onlyWith = JDK11OrLater.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_FileDispatcherImpl {

        /* Do not re-format commented out C code. @formatter:off */
        // ported from {jdk11}/src/java.base/unix/native/libnio/ch/FileDispatcherImpl.c
        //   320  JNIEXPORT jint JNICALL
        //   321  Java_sun_nio_ch_FileDispatcherImpl_setDirect0(JNIEnv *env, jclass clazz,
        //   322                                             jobject fdo)
        @Substitute
        static int setDirect0(FileDescriptor fdo) throws IOException {
            /* ( Allow names with underscores: Checkstyle: stop */

            //   324      jint fd = fdval(env, fdo);
            int fd = PosixUtils.getFD(fdo);
            //   325      jint result;
            int result;
            
            //   326  #ifdef MACOSX
            //   327      struct statvfs file_stat;
            //   328  #else
            //   329      struct statvfs64 file_stat;
            //   330  #endif

            Statvfs.statvfs file_stat = StackValue.get(Statvfs.statvfs.class);

            //   332  #if defined(O_DIRECT) || defined(F_NOCACHE) || defined(DIRECTIO_ON)
            if (IsDefined.LINUX() || IsDefined.MACOSX()) {
                //   333  #ifdef O_DIRECT
                if (IsDefined.LINUX()) {
                    //   334      jint orig_flag;
                    int orig_flag;
                    //   335      orig_flag = fcntl(fd, F_GETFL);
                    orig_flag = Fcntl.fcntl(fd, Fcntl.F_GETFL());
                    //   336      if (orig_flag == -1) {
                    if (orig_flag == -1) {
                        //   337          JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
                        //   338          return -1;
                        throw new IOException(PosixUtils.lastErrorString("DirectIO setup failed"));
                    }
                    //   340      result = fcntl(fd, F_SETFL, orig_flag | O_DIRECT);
                    result = Fcntl.fcntl(fd, Fcntl.F_SETFD(), orig_flag | Fcntl.O_DIRECT());
                    //   341      if (result == -1) {
                    if (result == -1) {
                        //   342          JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
                        //   343          return result;
                        throw new IOException(PosixUtils.lastErrorString("DirectIO setup failed"));
                    }
                } else if (IsDefined.MACOSX()) {
                    //   345  #elif F_NOCACHE
                    //   346      result = fcntl(fd, F_NOCACHE, 1);
                    result = Fcntl.fcntl(fd, Fcntl.F_NOCACHE(), 1);
                    //   347      if (result == -1) {
                    if (result == -1) {
                        //   348          JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
                        //   349          return result;
                        throw new IOException(PosixUtils.lastErrorString("DirectIO setup failed"));
                    }
                }
                // ** Ignored section **
                //   351  #elif DIRECTIO_ON
                //   352      result = directio(fd, DIRECTIO_ON);
                //   353      if (result == -1) {
                //   354          JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
                //   355          return result;
                //   356      }
                //   357  #endif

                //   358  #ifdef MACOSX
                //   359      result = fstatvfs(fd, &file_stat);
                //   360  #else
                //   361      result = fstatvfs64(fd, &file_stat);
                //   362  #endif
                result = Statvfs.fstatvfs(fd, file_stat);

                //   363      if(result == -1) {
                if (result == -1) {
                    //   364          JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
                    //   365          return result;
                    throw new IOException(PosixUtils.lastErrorString("DirectIO setup failed"));
                    //   366      } else {
                } else {
                    //   367          result = (int)file_stat.f_frsize;
                    result = (int) file_stat.f_frsize();
                }
            } else {
                //   369  #else
                //   370      result == -1;
                result = -1;
                //   371  #endif
            }
            //   372      return result;
            return result;

            /* } Allow names with underscores: Checkstyle: resume */
        }
        /* @formatter:on */
    }
}
