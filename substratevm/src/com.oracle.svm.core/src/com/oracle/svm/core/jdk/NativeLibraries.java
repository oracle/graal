/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.impl.ProcessPropertiesSupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * Base for holders of native libraries. The implemented methods provide different ways of loading a
 * library. These are different not in the loading process but in the locations where the libraries
 * are searched.
 *
 * This base class offers no support for JNI libraries (e.g. JNI_OnLoad won't be called on library
 * load).
 */
public abstract class NativeLibraries {
    protected static PointerBase findSymbol(Collection<PlatformNativeLibrarySupport.NativeLibrary> knownLibraries, String name) {
        for (PlatformNativeLibrarySupport.NativeLibrary lib : knownLibraries) {
            PointerBase entry = lib.findSymbol(name);
            if (entry.isNonNull()) {
                return entry;
            }
        }
        return WordFactory.nullPointer();
    }

    /** The path of the directory containing the native image. */
    private String sysPath;
    /** Paths derived from the {@code java.library.path} system property. */
    private String[] usrPaths;

    /** Returns the directory containing the native image, or {@code null}. */
    @NeverInline("Reads the return address.")
    private static String getImageDirectory() {
        /*
         * While one might expect code for shared libraries to work for executables as well, this is
         * not necessarily the case. For example, `dladdr` on Linux returns `argv[0]` for
         * executables, which is completely useless when running an executable from `$PATH`, since
         * then `argv[0]` contains only the name of the executable.
         */
        String image = !SubstrateOptions.SharedLibrary.getValue() ? ProcessProperties.getExecutableName()
                        : ImageSingletons.lookup(ProcessPropertiesSupport.class).getObjectFile(KnownIntrinsics.readReturnAddress());
        return image != null ? new File(image).getParent() : null;
    }

    /**
     * Leaves name resolution to the underlying library loading mechanism (e.g. dlopen)
     */
    public void loadLibraryPlatformSpecific(String name) {
        if (addLibrary(name, false)) {
            return;
        }
        throw new UnsatisfiedLinkError("Can't load library: " + name);
    }

    public void loadLibraryPlatformSpecific(Path path) {
        try {
            loadLibraryPlatformSpecific(path.toRealPath().toString());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Can't load library: " + path);
        }
    }

    public void loadLibraryAbsolute(File file) {
        if (loadLibrary0(file, false)) {
            return;
        }
        throw new UnsatisfiedLinkError("Can't load library: " + file);
    }

    public void loadLibraryRelative(String name) {
        // Test if this is a built-in library
        if (loadLibrary0(new File(name), true)) {
            return;
        }
        if (usrPaths == null) {
            /*
             * Note that `sysPath` will be `null` if we fail to get the image directory in which
             * case we effectively fall back to using only `usrPaths`.
             */
            sysPath = getImageDirectory();
            String[] tokens = SubstrateUtil.split(System.getProperty("java.library.path", ""), File.pathSeparator);
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].isEmpty()) {
                    tokens[i] = ".";
                }
            }
            usrPaths = tokens;
        }
        String libname = System.mapLibraryName(name);
        if (sysPath != null && loadLibrary0(new File(sysPath, libname), false)) {
            return;
        }
        for (String path : usrPaths) {
            File libpath = new File(path, libname);
            if (loadLibrary0(libpath, false)) {
                return;
            }
            File altpath = Target_jdk_internal_loader_ClassLoaderHelper.mapAlternativeName(libpath);
            if (altpath != null && loadLibrary0(altpath, false)) {
                return;
            }
        }
        throw new UnsatisfiedLinkError("Can't load library: " + name + " | java.library.path = " + Arrays.toString(usrPaths));
    }

    private boolean loadLibrary0(File file, boolean builtin) {
        try {
            String canonical = builtin ? file.getName() : file.getCanonicalPath();
            return addLibrary(canonical, builtin);
        } catch (IOException e) {
            return false;
        }
    }

    protected abstract boolean addLibrary(String canonical, boolean builtin);

    public abstract PointerBase findSymbol(String name);
}
