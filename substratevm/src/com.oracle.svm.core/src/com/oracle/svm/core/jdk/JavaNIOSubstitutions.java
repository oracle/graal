/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.nio.file.TempFileHelper")
final class Target_java_nio_file_TempFileHelper {

    @Alias @InjectAccessors(TempFileHelperRandomAccessors.class)//
    static SecureRandom random;

    @Alias @InjectAccessors(TempFileHelperDir.class)//
    static Path tmpdir;

    static final class TempFileHelperRandomAccessors {
        private static SecureRandom random;

        static SecureRandom get() {
            if (random == null) {
                random = new SecureRandom();
            }
            return random;
        }
    }

    static final class TempFileHelperDir {
        private static Path dir;

        static Path get() {
            if (dir == null) {
                dir = Paths.get(System.getProperty("java.io.tmpdir"));
            }
            return dir;
        }
    }
}

/**
 * Dummy class to have a class with the file's name.
 */
public final class JavaNIOSubstitutions {
}
