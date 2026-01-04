/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.io;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.RandomAccessFile;

import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

public abstract class FDAccess {

    private static final FDAccess FILE_DESCRIPTOR = new FDAccess() {
        @Override
        public @JavaType(FileDescriptor.class) StaticObject get(@JavaType(Object.class) StaticObject objectWithFD, TruffleIO io) {
            return objectWithFD;
        }
    };

    private static final FDAccess FILE_INPUT_STREAM = new FDAccess() {
        @Override
        public @JavaType(FileInputStream.class) StaticObject get(@JavaType(Object.class) StaticObject objectWithFD, TruffleIO io) {
            return io.java_io_FileInputStream_fd.getObject(objectWithFD);
        }
    };

    private static final FDAccess FILE_OUTPUT_STREAM = new FDAccess() {
        @Override
        public @JavaType(FileDescriptor.class) StaticObject get(@JavaType(Object.class) StaticObject objectWithFD, TruffleIO io) {
            return io.java_io_FileOutputStream_fd.getObject(objectWithFD);
        }
    };

    private static final FDAccess RANDOM_ACCESS_FILE = new FDAccess() {
        @Override
        public @JavaType(FileDescriptor.class) StaticObject get(@JavaType(RandomAccessFile.class) StaticObject objectWithFD, TruffleIO io) {
            Checks.nullCheck(objectWithFD, io);
            return io.java_io_RandomAccessFile_fd.getObject(objectWithFD);
        }
    };

    public abstract @JavaType(FileDescriptor.class) StaticObject get(@JavaType(Object.class) StaticObject objectWithFD, TruffleIO io);

    public static FDAccess forFileDescriptor() {
        return FILE_DESCRIPTOR;
    }

    public static FDAccess forFileOutputStream() {
        return FILE_OUTPUT_STREAM;
    }

    public static FDAccess forFileInputStream() {
        return FILE_INPUT_STREAM;
    }

    public static FDAccess forRandomAccessFile() {
        return RANDOM_ACCESS_FILE;
    }
}
