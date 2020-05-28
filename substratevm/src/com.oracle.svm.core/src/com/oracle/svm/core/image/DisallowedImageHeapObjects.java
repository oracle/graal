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
package com.oracle.svm.core.image;

// Checkstyle: allow reflection

import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.MappedByteBuffer;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

public final class DisallowedImageHeapObjects {
    public interface DisallowedObjectReporter {
        /**
         * Raise an exception for a disallowed object, including the provided message, suggestions
         * on how to solve the situation, and any available information on the object's origin.
         */
        RuntimeException raise(String msg, Object obj, String initializerAction);
    }

    private static final Class<?> CANCELLABLE_CLASS;
    static {
        // Checkstyle: stop
        try {
            CANCELLABLE_CLASS = Class.forName("sun.nio.fs.Cancellable");
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
        // Checkstyle: resume
    }

    public static void check(Object obj, DisallowedObjectReporter reporter) {
        /* Started Threads can not be in the image heap. */
        if (obj instanceof Thread) {
            final Thread asThread = (Thread) obj;
            if (asThread.getState() != Thread.State.NEW && asThread.getState() != Thread.State.TERMINATED) {
                throw reporter.raise("Detected a started Thread in the image heap. " +
                                "Threads running in the image generator are no longer running at image run time.",
                                asThread, "Try avoiding to initialize the class that caused initialization of the Thread.");
            }
        }
        /* FileDescriptors can not be in the image heap. */
        if (obj instanceof FileDescriptor) {
            final FileDescriptor asFileDescriptor = (FileDescriptor) obj;
            /* Except for a few well-known FileDescriptors. */
            if (!((asFileDescriptor == FileDescriptor.in) || (asFileDescriptor == FileDescriptor.out) || (asFileDescriptor == FileDescriptor.err) || (!asFileDescriptor.valid()))) {
                throw reporter.raise("Detected a FileDescriptor in the image heap. " +
                                "File descriptors opened during image generation are no longer open at image run time, and the files might not even be present anymore at image run time.",
                                asFileDescriptor, "Try avoiding to initialize the class that caused initialization of the FileDescriptor.");
            }
        }
        /* Direct ByteBuffers can not be in the image heap. */
        if (obj instanceof MappedByteBuffer) {
            MappedByteBuffer buffer = (MappedByteBuffer) obj;
            /*
             * We allow 0-length non-file-based direct buffers, see comment on
             * Target_java_nio_DirectByteBuffer.
             */
            if (buffer.capacity() != 0 || getFileDescriptor(buffer) != null) {
                throw reporter.raise("Detected a direct/mapped ByteBuffer in the image heap. " +
                                "A direct ByteBuffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image run time." +
                                "A mapped ByteBuffer references a file descriptor, which is no longer open and mapped at run time. ",
                                buffer, "Try avoiding to initialize the class that caused initialization of the MappedByteBuffer.");
            }
        } else if (obj instanceof Buffer && ((Buffer) obj).isDirect()) {
            throw reporter.raise("Detected a direct Buffer in the image heap. " +
                            "A direct Buffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image run time.",
                            obj, "Try avoiding to initialize the class that caused initialization of the direct Buffer.");
        }

        /* ZipFiles can not be in the image heap. */
        if (obj instanceof java.util.zip.ZipFile) {
            throw reporter.raise("Detected a ZipFile object in the image heap. " +
                            "A ZipFile object contains pointers to unmanaged C memory and file descriptors, and these resources are no longer available at image run time.",
                            obj, "Try avoiding to initialize the class that caused initialization of the direct Buffer.");
        }

        if (CANCELLABLE_CLASS.isInstance(obj)) {
            throw reporter.raise("Detected an instance of a class that extends " + CANCELLABLE_CLASS.getTypeName() + ": " + obj.getClass().getTypeName() + ". " +
                            "It contains a pointer to unmanaged C memory, which is no longer available at image run time.", obj,
                            "Try avoiding to initialize the class that caused initialization of the object.");
        }
    }

    private static final Field FILE_DESCRIPTOR_FIELD = ReflectionUtil.lookupField(MappedByteBuffer.class, "fd");

    private static FileDescriptor getFileDescriptor(MappedByteBuffer buffer) {
        try {
            return (FileDescriptor) FILE_DESCRIPTOR_FIELD.get(buffer);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
