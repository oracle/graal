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

import java.io.Closeable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

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

/**
 * This class provides a replacement for the static initialization in {@code DeleteOnExitHook}. I do
 * not want to use the files list developed during image generation, and I can not use the
 * {@link Runnable} registered during image generation to delete files in the running image.
 *
 * If, in the running image, someone registers a file to be deleted on exit, I lazily add a
 * {@code DeleteOnExitHook} to be run on during shutdown. I am using an injected accessor to
 * intercept all uses of {@code DeleteOnExitHook.files}, though I am only interested in the use in
 * {@code DeleteOnExitHook.add(String)}.
 */
@TargetClass(className = "java.io.DeleteOnExitHook")
final class Target_java_io_DeleteOnExitHook {

    /** Make this method more visible. */
    @Alias//
    static native void runHooks();

    /** This field is replaced by injected accessors. */
    @Alias @InjectAccessors(FilesAccessors.class)//
    private static LinkedHashSet<String> files;

    /** The accessor for the injected field for {@link #files}. */
    static final class FilesAccessors {

        /** The value of the injected field for {@link #files}. */
        private static volatile LinkedHashSet<String> injectedFiles = null;

        /** The get accessor for {@link Target_java_io_DeleteOnExitHook#files}. */
        static LinkedHashSet<String> getFiles() {
            initializeOnce();
            return injectedFiles;
        }

        /** The set accessor for {@link Target_java_io_DeleteOnExitHook#files}. */
        static void setFiles(LinkedHashSet<String> value) {
            initializeOnce();
            injectedFiles = value;
        }

        /** An initialization flag. */
        private static volatile boolean initialized = false;

        /** A lock to protect the initialization flag. */
        private static ReentrantLock lock = new ReentrantLock();

        static void initializeOnce() {
            if (!initialized) {
                lock.lock();
                try {
                    if (!initialized) {
                        try {
                            /*
                             * Register a shutdown hook.
                             *
                             * Compare this code to the static initializations done in {@link
                             * DeleteOnExitHook}, except I am short-circuiting the trampoline
                             * through {@link sun.misc.SharedSecrets#getJavaLangAccess()}.
                             */
                            Target_java_lang_Shutdown.add(2 /* Shutdown hook invocation order */,
                                            true /* register even if shutdown in progress */,
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    Target_java_io_DeleteOnExitHook.runHooks();
                                                }
                                            });
                        } catch (InternalError ie) {
                            /* Someone else has registered the shutdown hook at slot 2. */
                        } catch (IllegalStateException ise) {
                            /* Too late to register this shutdown hook. */
                        }
                        /* Initialize the {@link #injectedFiles} field. */
                        injectedFiles = new LinkedHashSet<>();
                        /* Announce that initialization is complete. */
                        initialized = true;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaIOSubstitutions {
}
