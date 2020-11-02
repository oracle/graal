/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import static com.oracle.svm.jfr.PredefinedJFCSubstitition.DEFAULT_JFC;
import static com.oracle.svm.jfr.PredefinedJFCSubstitition.PROFILE_JFC;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.jfr.internal.SecuritySupport;

@TargetClass(value = jdk.jfr.internal.SecuritySupport.class, onlyWith = JfrEnabled.class)
public final class Target_jdk_jfr_internal_SecuritySupport {
    // Checkstyle: stop
    @Alias //
    @RecomputeFieldValue(isFinal = true, kind = Kind.Reset) //
    public static SecuritySupport.SafePath JFC_DIRECTORY;

    @Alias //
    @InjectAccessors(UserHomeAccessor.class) //
    static SecuritySupport.SafePath USER_HOME;

    @Alias //
    @InjectAccessors(TmpDirAccessor.class) //
    static SecuritySupport.SafePath JAVA_IO_TMPDIR;
    // Checkstyle: resume

    @Alias
    static native SecuritySupport.SafePath getPathInProperty(String prop, String subPath);

    @Substitute
    public static List<SecuritySupport.SafePath> getPredefinedJFCFiles() {
        List<SecuritySupport.SafePath> list = new ArrayList<>();
        ClassLoader loader = PredefinedJFCSubstitition.class.getClassLoader();
        URL defaultJfc = loader.getResource(DEFAULT_JFC);
        URL profileJfc = loader.getResource(PROFILE_JFC);

        list.add(new SecuritySupport.SafePath(defaultJfc.toString()));
        list.add(new SecuritySupport.SafePath(profileJfc.toString()));
        return list;
    }

    static class UserHomeAccessor {

        private static volatile SecuritySupport.SafePath userHome;

        static SecuritySupport.SafePath get() {
            if (userHome == null) {
                userHome = Target_jdk_jfr_internal_SecuritySupport.getPathInProperty("user.home", null);
            }
            return userHome;
        }
    }

    static class TmpDirAccessor {

        private static volatile SecuritySupport.SafePath tmpDir;

        static SecuritySupport.SafePath get() {
            if (tmpDir == null) {
                tmpDir = Target_jdk_jfr_internal_SecuritySupport.getPathInProperty("java.io.tmpdir", null);
            }
            return tmpDir;
        }
    }
}
