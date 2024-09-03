/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport.NativeLibrary;

@AutomaticallyRegisteredImageSingleton
public final class NativeLibrarySupport extends NativeLibraries {
    // Essentially a revised implementation of the relevant methods in OpenJDK's ClassLoader

    public interface LibraryInitializer {
        boolean isBuiltinLibrary(String name);

        void initialize(NativeLibrary lib);
    }

    public static NativeLibrarySupport singleton() {
        return ImageSingletons.lookup(NativeLibrarySupport.class);
    }

    private final ReentrantLock lock = new ReentrantLock();

    private final List<NativeLibrary> knownLibraries = new CopyOnWriteArrayList<>();

    private final Deque<NativeLibrary> currentLoadContext = new ArrayDeque<>();

    private LibraryInitializer libraryInitializer;

    NativeLibrarySupport() {
    }

    @Platforms(HOSTED_ONLY.class)
    public void registerLibraryInitializer(LibraryInitializer initializer) {
        assert this.libraryInitializer == null;
        this.libraryInitializer = initializer;
    }

    @Platforms(HOSTED_ONLY.class)
    public void preregisterUninitializedBuiltinLibrary(String name) {
        knownLibraries.add(PlatformNativeLibrarySupport.singleton().createLibrary(name, true));
    }

    @Platforms(HOSTED_ONLY.class)
    public boolean isPreregisteredBuiltinLibrary(String name) {
        return knownLibraries.stream().anyMatch(l -> l.isBuiltin() && l.getCanonicalIdentifier().equals(name));
    }

    @Override
    protected boolean addLibrary(String canonical, boolean builtin) {
        return addLibrary(builtin, canonical, true);
    }

    private boolean addLibrary(boolean asBuiltin, String canonical, boolean initialize) {
        lock.lock();
        try {
            NativeLibrary lib = null;
            for (NativeLibrary known : knownLibraries) {
                if (canonical.equals(known.getCanonicalIdentifier())) {
                    if (known.isLoaded()) {
                        return true;
                    } else {
                        assert known.isBuiltin() : "non-built-in libraries must always have been loaded";
                        assert asBuiltin : "must have tried loading as built-in first";
                        lib = known; // load and initialize below
                        break;
                    }
                }
            }
            if (asBuiltin && lib == null && (libraryInitializer == null || !libraryInitializer.isBuiltinLibrary(canonical))) {
                return false;
            }
            // Libraries can load libraries during initialization, avoid recursion with a stack
            for (NativeLibrary loading : currentLoadContext) {
                if (canonical.equals(loading.getCanonicalIdentifier())) {
                    return true;
                }
            }
            boolean created = false;
            if (lib == null) {
                lib = PlatformNativeLibrarySupport.singleton().createLibrary(canonical, asBuiltin);
                created = true;
            }
            currentLoadContext.push(lib);
            try {
                if (!lib.load()) {
                    return false;
                }
                /*
                 * Initialization of a library must be skipped if it can be initialized at most once
                 * per process and another isolate has already initialized it. However, the library
                 * must be (marked as) loaded above so it cannot be loaded and initialized later.
                 */
                if (initialize && libraryInitializer != null) {
                    libraryInitializer.initialize(lib);
                }
            } finally {
                NativeLibrary top = currentLoadContext.pop();
                assert top == lib;
            }
            if (created) {
                knownLibraries.add(lib);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PointerBase findSymbol(String name) {
        lock.lock();
        try {
            return findSymbol(knownLibraries, name);
        } finally {
            lock.unlock();
        }
    }

    public void registerInitializedBuiltinLibrary(String name) {
        boolean success = addLibrary(true, name, false);
        assert success;
    }
}
