/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport.NativeLibrary;

@AutomaticFeature
class NativeLibrarySupportFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        NativeLibrarySupport.initialize();
    }
}

public final class NativeLibrarySupport {
    // Essentially a revised implementation of the relevant methods in OpenJDK's ClassLoader

    static void initialize() {
        ImageSingletons.add(NativeLibrarySupport.class, new NativeLibrarySupport());
    }

    public static NativeLibrarySupport singleton() {
        return ImageSingletons.lookup(NativeLibrarySupport.class);
    }

    private final ReentrantLock lock = new ReentrantLock();

    private final List<NativeLibrary> loadedLibraries = new ArrayList<>();

    private final Deque<NativeLibrary> currentLoadContext = new ArrayDeque<>();

    private String[] paths;

    private NativeLibrarySupport() {
    }

    public void loadLibrary(String name, boolean isAbsolute) {
        if (paths == null) {
            String[] tokens = System.getProperty("java.library.path", "").split(File.pathSeparator);
            paths = Arrays.stream(tokens).map(t -> t.isEmpty() ? "." : t).toArray(String[]::new);
        }

        if (isAbsolute) {
            if (loadLibrary0(new File(name))) {
                return;
            }
            throw new UnsatisfiedLinkError("Can't load library: " + name);
        }

        String libname = System.mapLibraryName(name);
        for (String path : paths) {
            File libpath = new File(path, libname);
            if (loadLibrary0(libpath)) {
                return;
            }
            File altpath = Target_java_lang_ClassLoaderHelper.mapAlternativeName(libpath);
            if (altpath != null && loadLibrary0(libpath)) {
                return;
            }
        }
        throw new UnsatisfiedLinkError("no " + name + " in java.library.path");
    }

    private boolean loadLibrary0(File file) {
        File canonical;
        try {
            canonical = file.getCanonicalFile();
        } catch (IOException e) {
            return false;
        }

        lock.lock();
        try {
            for (NativeLibrary lib : loadedLibraries) {
                if (lib.getCanonicalPath().equals(canonical)) {
                    return true;
                }
            }
            // Libraries can load libraries during initialization, avoid recursion with a stack
            for (NativeLibrary lib : currentLoadContext) {
                if (lib.getCanonicalPath().equals(canonical)) {
                    return true; // already being loaded
                }
            }

            NativeLibrary lib = PlatformNativeLibrarySupport.singleton().create(canonical);
            currentLoadContext.push(lib);
            try {
                lib.load();
            } finally {
                NativeLibrary top = currentLoadContext.pop();
                assert top == lib;
            }
            if (lib.isLoaded()) {
                // TODO: for JNI, check if there is a JNI_OnLoad(), call it, rethrow any exceptions,
                // remember the returned JNI version; in case of an error, unload library again.

                loadedLibraries.add(lib);
                return true;
            }
            return false;

        } finally {
            lock.unlock();
        }
    }

    public PointerBase findSymbol(String name) {
        lock.lock();
        try {
            for (NativeLibrary lib : loadedLibraries) {
                PointerBase entry = lib.findSymbol(name);
                if (entry.isNonNull()) {
                    return entry;
                }
            }
            return WordFactory.nullPointer();
        } finally {
            lock.unlock();
        }
    }
}
