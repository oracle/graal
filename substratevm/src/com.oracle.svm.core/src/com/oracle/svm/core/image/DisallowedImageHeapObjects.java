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
import java.util.random.RandomGenerator;
import java.util.zip.ZipFile;

import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.SubstrateUtil;
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

    public static final Class<?> CANCELLABLE_CLASS = ReflectionUtil.lookupClass("sun.nio.fs.Cancellable");
    private static final Class<?> VIRTUAL_THREAD_CLASS = ReflectionUtil.lookupClass("java.lang.VirtualThread");
    public static final Class<?> CONTINUATION_CLASS = ReflectionUtil.lookupClass("jdk.internal.vm.Continuation");
    private static final Method CONTINUATION_IS_STARTED_METHOD = ReflectionUtil.lookupMethod(CONTINUATION_CLASS, "isStarted");
    private static final Class<?> CLEANER_CLEANABLE_CLASS = ReflectionUtil.lookupClass("jdk.internal.ref.CleanerImpl$CleanerCleanable");
    public static final Class<?> NIO_CLEANER_CLASS = ReflectionUtil.lookupClass("sun.nio.Cleaner");
    public static final Class<?> MEMORY_SEGMENT_CLASS = ReflectionUtil.lookupClass("java.lang.foreign.MemorySegment");
    public static final Class<?> SCOPE_CLASS = ReflectionUtil.lookupClass("java.lang.foreign.MemorySegment$Scope");

    public static void check(Object obj, DisallowedObjectReporter reporter) {
        if (obj instanceof SplittableRandom random) {
            onSplittableRandomReachable(random, reporter);
        } else if (obj instanceof Random random) {
            onRandomReachable(random, reporter);
        }

        /* Started platform threads */
        if (obj instanceof Thread asThread) {
            onThreadReachable(asThread, reporter);
        }
        if (SubstrateUtil.HOSTED && CONTINUATION_CLASS.isInstance(obj)) {
            onContinuationReachable(obj, reporter);
        }

        if (obj instanceof FileDescriptor) {
            onFileDescriptorReachable((FileDescriptor) obj, reporter);
        }

        if (obj instanceof Buffer buffer) {
            onBufferReachable(buffer, reporter);
        }

        if (obj instanceof Cleaner.Cleanable || NIO_CLEANER_CLASS.isInstance(obj)) {
            onCleanableReachable(obj, reporter);
        }

        if (obj instanceof Cleaner cleaner) {
            onCleanerReachable(cleaner, reporter);
        }

        if (obj instanceof ZipFile zipFile) {
            onZipFileReachable(zipFile, reporter);
        }

        if (CANCELLABLE_CLASS.isInstance(obj)) {
            onCancellableReachable(obj, reporter);
        }

        if (MEMORY_SEGMENT_CLASS.isInstance(obj) && ForeignSupport.isAvailable()) {
            ForeignSupport.singleton().onMemorySegmentReachable(obj, reporter);
        }

        if (SCOPE_CLASS.isInstance(obj) && ForeignSupport.isAvailable()) {
            ForeignSupport.singleton().onScopeReachable(obj, reporter);
        }
    }

    public static void onRandomReachable(Random random, DisallowedObjectReporter reporter) {
        if (!(random instanceof ThreadLocalRandom)) {
            onRandomGeneratorReachable(random, reporter);
        }
    }

    public static void onSplittableRandomReachable(SplittableRandom random, DisallowedObjectReporter reporter) {
        onRandomGeneratorReachable(random, reporter);
    }

    private static void onRandomGeneratorReachable(RandomGenerator random, DisallowedObjectReporter reporter) {
        throw reporter.raise("Detected an instance of Random/SplittableRandom class in the image heap. " +
                        "Instances created during image generation have cached seed values and don't behave as expected.",
                        random, "Try avoiding to initialize the class that caused initialization of the object.");
    }

    public static void onThreadReachable(Thread thread, DisallowedObjectReporter reporter) {
        if (VIRTUAL_THREAD_CLASS.isInstance(thread)) {
            // allowed unless the thread is mounted, in which case it references its carrier
            // thread and fails
        } else if (thread.getState() != Thread.State.NEW && thread.getState() != Thread.State.TERMINATED) {
            throw reporter.raise("Detected a started Thread in the image heap. Thread name: " + thread.getName() +
                            ". Threads running in the image generator are no longer running at image runtime.",
                            thread, "Prevent threads from starting during image generation, or a started thread from being included in the image.");
        }
    }

    public static void onContinuationReachable(Object continuation, DisallowedObjectReporter reporter) {
        VMError.guarantee(CONTINUATION_CLASS.isInstance(continuation));
        boolean isStarted;
        try {
            isStarted = (Boolean) CONTINUATION_IS_STARTED_METHOD.invoke(continuation);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            isStarted = false;
        }
        if (isStarted) {
            throw reporter.raise("Detected a started Continuation in the image heap. " +
                            "Continuation state from the image generator cannot be used at image runtime.",
                            continuation, "Prevent continuations from starting during image generation, or started continuations from being included in the image.");
        }
    }

    public static void onFileDescriptorReachable(FileDescriptor descriptor, DisallowedObjectReporter reporter) {
        /* Exemptions for well-known FileDescriptors. */
        if (!((descriptor == FileDescriptor.in) || (descriptor == FileDescriptor.out) || (descriptor == FileDescriptor.err) || (!descriptor.valid()))) {
            throw reporter.raise("Detected a FileDescriptor in the image heap. " +
                            "File descriptors opened during image generation are no longer open at image runtime, and the files might not even be present anymore at image runtime.",
                            descriptor, "Try avoiding to initialize the class that caused initialization of the FileDescriptor.");
        }
    }

    public static void onBufferReachable(Buffer buffer, DisallowedObjectReporter reporter) {
        if (buffer instanceof MappedByteBuffer mappedBuffer) {
            /*
             * We allow 0-length non-file-based direct buffers, see comment on
             * Target_java_nio_DirectByteBuffer.
             */
            if (mappedBuffer.capacity() != 0 || getFileDescriptor(mappedBuffer) != null) {
                throw reporter.raise("Detected a direct/mapped ByteBuffer in the image heap. " +
                                "A direct ByteBuffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image runtime. " +
                                "A mapped ByteBuffer references a file descriptor, which is no longer open and mapped at run time.",
                                mappedBuffer, "Try avoiding to initialize the class that caused initialization of the MappedByteBuffer.");
            }
        } else if (buffer.isDirect()) {
            throw reporter.raise("Detected a direct Buffer in the image heap. " +
                            "A direct Buffer has a pointer to unmanaged C memory, and C memory from the image generator is not available at image runtime.",
                            buffer, "Try avoiding to initialize the class that caused initialization of the direct Buffer.");
        }
    }

    public static void onCleanableReachable(Object cleanable, DisallowedObjectReporter reporter) {
        VMError.guarantee(cleanable instanceof Cleaner.Cleanable || NIO_CLEANER_CLASS.isInstance(cleanable));
        /*
         * Cleanable and sun.nio.Cleaner are used to release various resources such as native
         * memory, file descriptors, or timers, which are not available at image runtime. By
         * disallowing these objects, we detect when such resources are reachable.
         *
         * If a Cleanable is a nulled (Phantom)Reference, its problematic resource is already
         * unreachable, so we tolerate it.
         *
         * A CleanerCleanable serves only to keep a cleaner thread alive (without referencing the
         * Thread) and does nothing, so we also tolerate it. We should encounter at least one such
         * object for jdk.internal.ref.CleanerFactory.commonCleaner.
         *
         * Internal sun.nio.Cleaner objects (formerly in jdk.internal.ref, formerly in sun.misc)
         * should be used only by DirectByteBuffer, which we already cover above, but other code
         * could also use them. If they have been nulled, we tolerate them, too.
         */
        if (!(cleanable instanceof Reference<?> && ((Reference<?>) cleanable).refersTo(null)) && !CLEANER_CLEANABLE_CLASS.isInstance(cleanable)) {
            throw reporter.raise("Detected an active instance of Cleanable or sun.io.Cleaner in the image heap. This usually means that a resource " +
                            "such as a Timer, native memory, a file descriptor or another resource is reachable which is not available at image runtime.",
                            cleanable, "Prevent such objects being used during image generation, including by class initializers.");
        }
    }

    public static void onCleanerReachable(Cleaner cleaner, DisallowedObjectReporter reporter) {
        if (cleaner != CleanerFactory.cleaner()) {
            /* We handle the "common cleaner", CleanerFactory.cleaner(), in reference handling. */
            throw reporter.raise("Detected a java.lang.ref.Cleaner object in the image heap which uses a daemon thread that invokes " +
                            "cleaning actions, but threads running in the image generator are no longer running at image runtime.",
                            cleaner, "Prevent such objects being used during image generation, including by class initializers.");
        }
    }

    public static void onZipFileReachable(java.util.zip.ZipFile zipFile, DisallowedObjectReporter reporter) {
        throw reporter.raise("Detected a ZipFile object in the image heap. " +
                        "A ZipFile object contains pointers to unmanaged C memory and file descriptors, and these resources are no longer available at image runtime.",
                        zipFile, "Try avoiding to initialize the class that caused initialization of the ZipFile.");
    }

    public static void onCancellableReachable(Object cancellable, DisallowedObjectReporter reporter) {
        VMError.guarantee(CANCELLABLE_CLASS.isInstance(cancellable));
        throw reporter.raise("Detected an instance of a class that extends " + CANCELLABLE_CLASS.getTypeName() + ": " + cancellable.getClass().getTypeName() + ". " +
                        "It contains a pointer to unmanaged C memory, which is no longer available at image runtime.", cancellable,
                        "Try avoiding to initialize the class that caused initialization of the object.");
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
