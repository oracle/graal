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

import java.io.FileDescriptor;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.MappedByteBuffer;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.thread.VirtualThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.ref.CleanerFactory;

public final class DisallowedImageHeapObjects {
    public interface DisallowedObjectReporter {
        /**
         * Raise an exception for a disallowed object, including the provided message, suggestions
         * on how to solve the situation, and any available information on the object's origin.
         */
        RuntimeException raise(String msg, Object obj, String initializerAction);
    }

    private static final Class<?> CANCELLABLE_CLASS;
    private static final Class<?> JDK_VIRTUAL_THREAD_CLASS;
    private static final Class<?> CONTINUATION_CLASS;
    private static final Method CONTINUATION_IS_STARTED_METHOD;
    private static final Class<?> CLEANER_CLEANABLE_CLASS;
    private static final Class<?> LEGACY_CLEANER_CLASS;
    static {
        CANCELLABLE_CLASS = ReflectionUtil.lookupClass(false, "sun.nio.fs.Cancellable");
        JDK_VIRTUAL_THREAD_CLASS = ReflectionUtil.lookupClass(true, "java.lang.VirtualThread");
        CONTINUATION_CLASS = ReflectionUtil.lookupClass(true, "jdk.internal.vm.Continuation");
        CONTINUATION_IS_STARTED_METHOD = (CONTINUATION_CLASS == null) ? null : ReflectionUtil.lookupMethod(CONTINUATION_CLASS, "isStarted");
        CLEANER_CLEANABLE_CLASS = ReflectionUtil.lookupClass(false, "jdk.internal.ref.CleanerImpl$CleanerCleanable");
        LEGACY_CLEANER_CLASS = ReflectionUtil.lookupClass(false, "jdk.internal.ref.Cleaner");
    }

    public static void check(Object obj, DisallowedObjectReporter reporter) {
        if (((obj instanceof Random) && !(obj instanceof ThreadLocalRandom)) || obj instanceof SplittableRandom) {
            throw reporter.raise("Detected an instance of Random/SplittableRandom class in the image heap. " +
                            "Instances created during image generation have cached seed values and don't behave as expected.",
                            obj, "Try avoiding to initialize the class that caused initialization of the object.");
        }

        /* Started platform threads */
        if (obj instanceof Thread) {
            final Thread asThread = (Thread) obj;
            if (VirtualThreads.isSupported() && (VirtualThreads.singleton().isVirtual(asThread) || (JDK_VIRTUAL_THREAD_CLASS != null && JDK_VIRTUAL_THREAD_CLASS.isInstance(asThread)))) {
                // allowed unless the thread is mounted, in which case it references its carrier
                // thread and fails
            } else if (asThread.getState() != Thread.State.NEW && asThread.getState() != Thread.State.TERMINATED) {
                throw reporter.raise("Detected a started Thread in the image heap. " +
                                "Threads running in the image generator are no longer running at image runtime.",
                                asThread, "Prevent threads from starting during image generation, or a started thread from being included in the image.");
            }
        }
        if (SubstrateUtil.HOSTED && CONTINUATION_CLASS != null && CONTINUATION_CLASS.isInstance(obj)) {
            boolean isStarted;
            try {
                isStarted = (Boolean) CONTINUATION_IS_STARTED_METHOD.invoke(obj);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                isStarted = false;
            }
            if (isStarted) {
                throw reporter.raise("Detected a started Continuation in the image heap. " +
                                "Continuation state from the image generator cannot be used at image runtime.",
                                obj, "Prevent continuations from starting during image generation, or started continuations from being included in the image.");
            }
        }

        if (obj instanceof FileDescriptor) {
            final FileDescriptor asFileDescriptor = (FileDescriptor) obj;
            /* Exemptions for well-known FileDescriptors. */
            if (!((asFileDescriptor == FileDescriptor.in) || (asFileDescriptor == FileDescriptor.out) || (asFileDescriptor == FileDescriptor.err) || (!asFileDescriptor.valid()))) {
                throw reporter.raise("Detected a FileDescriptor in the image heap. " +
                                "File descriptors opened during image generation are no longer open at image runtime, and the files might not even be present anymore at image runtime.",
                                asFileDescriptor, "Try avoiding to initialize the class that caused initialization of the FileDescriptor.");
            }
        }

        if (obj instanceof MappedByteBuffer) {
            MappedByteBuffer buffer = (MappedByteBuffer) obj;
            /*
             * We allow 0-length non-file-based direct buffers, see comment on
             * Target_java_nio_DirectByteBuffer.
             */
            if (buffer.capacity() != 0 || getFileDescriptor(buffer) != null) {
                throw reporter.raise("Detected a direct/mapped ByteBuffer in the image heap. " +
                                "A direct ByteBuffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image runtime. " +
                                "A mapped ByteBuffer references a file descriptor, which is no longer open and mapped at run time.",
                                buffer, "Try avoiding to initialize the class that caused initialization of the MappedByteBuffer.");
            }
        } else if (obj instanceof Buffer && ((Buffer) obj).isDirect()) {
            throw reporter.raise("Detected a direct Buffer in the image heap. " +
                            "A direct Buffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image runtime.",
                            obj, "Try avoiding to initialize the class that caused initialization of the direct Buffer.");
        }

        if (obj instanceof Cleaner.Cleanable || LEGACY_CLEANER_CLASS.isInstance(obj)) {
            /*
             * Cleanable and jdk.internal.ref.Cleaner are used to release various resources such as
             * native memory, file descriptors, or timers, which are not available at image runtime.
             * By disallowing these objects, we detect when such resources are reachable.
             *
             * If a Cleanable is a nulled (Phantom)Reference, its problematic resource is already
             * unreachable, so we tolerate it.
             *
             * A CleanerCleanable serves only to keep a cleaner thread alive (without referencing
             * the Thread) and does nothing, so we also tolerate it. We should encounter at least
             * one such object for jdk.internal.ref.CleanerFactory.commonCleaner.
             *
             * Legacy jdk.internal.ref.Cleaner objects (formerly in sun.misc) should be used only by
             * DirectByteBuffer, which we already cover above, but other code could also use them.
             * If they have been nulled, we tolerate them, too.
             */
            if (!(obj instanceof Reference<?> && ((Reference<?>) obj).refersTo(null)) && !CLEANER_CLEANABLE_CLASS.isInstance(obj)) {
                throw reporter.raise("Detected an active instance of Cleanable or jdk.internal.ref.Cleaner in the image heap. This usually means that a resource " +
                                "such as a Timer, native memory, a file descriptor or another resource is reachable which is not available at image runtime.",
                                obj, "Prevent such objects being used during image generation, including by class initializers.");
            }
        }

        if (obj instanceof Cleaner && obj != CleanerFactory.cleaner()) {
            /* We handle the "common cleaner", CleanerFactory.cleaner(), in reference handling. */
            throw reporter.raise("Detected a java.lang.ref.Cleaner object in the image heap which uses a daemon thread that invokes " +
                            "cleaning actions, but threads running in the image generator are no longer running at image runtime.",
                            obj, "Prevent such objects being used during image generation, including by class initializers.");
        }

        if (obj instanceof java.util.zip.ZipFile) {
            throw reporter.raise("Detected a ZipFile object in the image heap. " +
                            "A ZipFile object contains pointers to unmanaged C memory and file descriptors, and these resources are no longer available at image runtime.",
                            obj, "Try avoiding to initialize the class that caused initialization of the ZipFile.");
        }

        if (CANCELLABLE_CLASS.isInstance(obj)) {
            throw reporter.raise("Detected an instance of a class that extends " + CANCELLABLE_CLASS.getTypeName() + ": " + obj.getClass().getTypeName() + ". " +
                            "It contains a pointer to unmanaged C memory, which is no longer available at image runtime.", obj,
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
