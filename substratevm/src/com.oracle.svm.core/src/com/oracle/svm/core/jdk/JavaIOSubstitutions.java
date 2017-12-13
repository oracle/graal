/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.Closeable;
import java.security.SecureRandom;
import java.util.List;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.io.FileDescriptor.class)
final class Target_java_io_FileDescriptor {

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private List<Closeable> otherParents;
}

@TargetClass(java.io.ObjectInputStream.class)
@SuppressWarnings({"static-method"})
final class Target_java_io_ObjectInputStream {

    @Substitute
    private Object readObject() {
        throw VMError.unsupportedFeature("ObjectInputStream.readObject()");
    }

    @Substitute
    private Object readUnshared() {
        throw VMError.unsupportedFeature("ObjectInputStream.readUnshared()");
    }
}

@TargetClass(java.io.ObjectOutputStream.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_io_ObjectOutputStream {

    @Substitute
    private void writeObject(Object obj) {
        throw VMError.unsupportedFeature("ObjectOutputStream.writeObject()");
    }

    @Substitute
    private void writeUnshared(Object obj) {
        throw VMError.unsupportedFeature("ObjectOutputStream.writeUnshared()");
    }
}

@TargetClass(className = "java.io.File$TempDirectory")
final class Target_java_io_File_TempDirectory {

    @Alias @InjectAccessors(FileTempDirectoryRandomAccessors.class)//
    private static SecureRandom random;

    static final class FileTempDirectoryRandomAccessors {
        private static SecureRandom random;

        static SecureRandom get() {
            if (random == null) {
                random = new SecureRandom();
            }
            return random;
        }
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaIOSubstitutions {
}
