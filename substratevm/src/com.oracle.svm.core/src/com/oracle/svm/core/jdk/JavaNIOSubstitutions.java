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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.security.SecureRandom;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(java.nio.file.Files.class)
final class Target_java_nio_file_Files {

    @Substitute
    private static String probeContentType(Path path) throws IOException {
        FilesSupport fs = ImageSingletons.lookup(FilesSupport.class);

        for (FileTypeDetector detector : fs.installedDetectors) {
            String result = detector.probeContentType(path);
            if (result != null) {
                return result;
            }
        }

        // fallback to default
        return fs.defaultFileTypeDetector.probeContentType(path);
    }
}

@TargetClass(className = "java.nio.file.TempFileHelper")
final class Target_java_nio_file_TempFileHelper {

    @Alias @InjectAccessors(TempFileHelperRandomAccessors.class)//
    static SecureRandom random;

    static final class TempFileHelperRandomAccessors {
        private static SecureRandom random;

        static SecureRandom get() {
            if (random == null) {
                random = new SecureRandom();
            }
            return random;
        }
    }
}

/**
 * Dummy class to have a class with the file's name.
 */
public final class JavaNIOSubstitutions {
}
