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
package com.oracle.svm.hosted.image;

import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.MappedByteBuffer;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.classinitialization.ClassInitializationFeature;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.util.ImageGeneratorThreadMarker;
import com.oracle.svm.util.ReflectionUtil;

/**
 * Complain if there are types that can not move from the image generator heap to the image heap.
 */
@AutomaticFeature
public class DisallowedImageHeapObjectFeature implements Feature {

    private ClassInitializationSupport classInitialization;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        classInitialization = ((FeatureImpl.DuringSetupAccessImpl) access).getHostVM().getClassInitializationSupport();
        access.registerObjectReplacer(this::replacer);
    }

    private static final Class<?> CANCELLABLE_CLASS;
    static {
        try {
            CANCELLABLE_CLASS = Class.forName("sun.nio.fs.Cancellable");
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private Object replacer(Object original) {
        /* Started Threads can not be in the image heap. */
        if (original instanceof Thread) {
            final Thread asThread = (Thread) original;
            if (asThread instanceof ImageGeneratorThreadMarker) {
                return ((ImageGeneratorThreadMarker) asThread).asTerminated();
            }
            if (asThread.getState() != Thread.State.NEW && asThread.getState() != Thread.State.TERMINATED) {
                throw error("Detected a started Thread in the image heap. " +
                                "Threads running in the image generator are no longer running at image run time. " +
                                classInitialization.objectInstantiationTraceMessage(asThread, "Try avoiding to initialize the class that caused initialization of the Thread."));
            }
        }
        /* FileDescriptors can not be in the image heap. */
        if (original instanceof FileDescriptor) {
            final FileDescriptor asFileDescriptor = (FileDescriptor) original;
            /* Except for a few well-known FileDescriptors. */
            if (!((asFileDescriptor == FileDescriptor.in) || (asFileDescriptor == FileDescriptor.out) || (asFileDescriptor == FileDescriptor.err) || (!asFileDescriptor.valid()))) {
                throw error("Detected a FileDescriptor in the image heap. " +
                                "File descriptors opened during image generation are no longer open at image run time, and the files might not even be present anymore at image run time. " +
                                classInitialization.objectInstantiationTraceMessage(asFileDescriptor, "Try avoiding to initialize the class that caused initialization of the FileDescriptor."));
            }
        }
        /* Direct ByteBuffers can not be in the image heap. */
        if (original instanceof MappedByteBuffer) {
            MappedByteBuffer buffer = (MappedByteBuffer) original;
            /*
             * We allow 0-length non-file-based direct buffers, see comment on
             * Target_java_nio_DirectByteBuffer.
             */
            if (buffer.capacity() != 0 || getFileDescriptor(buffer) != null) {
                throw error("Detected a direct/mapped ByteBuffer in the image heap. " +
                                "A direct ByteBuffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image run time. " +
                                "A mapped ByteBuffer references a file descriptor, which is no longer open and mapped at run time. " +
                                classInitialization.objectInstantiationTraceMessage(buffer, "Try avoiding to initialize the class that caused initialization of the MappedByteBuffer."));
            }
        } else if (original instanceof Buffer && ((Buffer) original).isDirect()) {
            throw error("Detected a direct Buffer in the image heap. " +
                            "A direct Buffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image run time. " +
                            classInitialization.objectInstantiationTraceMessage(original, "Try avoiding to initialize the class that caused initialization of the direct Buffer."));
        }

        /* ZipFiles can not be in the image heap. */
        if (original instanceof java.util.zip.ZipFile) {
            throw error("Detected a ZipFile object in the image heap. " +
                            "A ZipFile object contains pointers to unmanaged C memory and file descriptors, and these resources are no longer available at image run time. " +
                            classInitialization.objectInstantiationTraceMessage(original, "Try avoiding to initialize the class that caused initialization of the direct Buffer."));
        }

        if (CANCELLABLE_CLASS.isInstance(original)) {
            throw error("Detected an instance of a class that extends " + CANCELLABLE_CLASS.getTypeName() + ": " + original.getClass().getTypeName() + ". " +
                            "It contains a pointer to unmanaged C memory, which is no longer available at image run time. " +
                            classInitialization.objectInstantiationTraceMessage(original, "Try avoiding to initialize the class that caused initialization of the object."));
        }

        return original;
    }

    private static RuntimeException error(String msg) {
        throw new UnsupportedFeatureException(msg + " " +
                        "The object was probably created by a class initializer and is reachable from a static field. " +
                        "You can request class initialization at image run time by using the option " +
                        SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.ClassInitialization, "<class-name>", "initialize-at-run-time") + ". " +
                        "Or you can write your own initialization methods and call them explicitly from your main entry point.");
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
