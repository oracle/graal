/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;

import jdk.graal.compiler.debug.DebugContext;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

public abstract class AbstractImage {

    protected final HostedMetaAccess metaAccess;
    protected final HostedUniverse universe;
    protected final NativeLibraries nativeLibs;
    protected final NativeImageHeap heap;
    protected final ClassLoader imageClassLoader;
    protected final NativeImageCodeCache codeCache;
    protected final List<HostedMethod> entryPoints;
    protected int imageFileSize = -1; // for build output reporting
    protected int debugInfoSize = -1; // for build output reporting

    public enum NativeImageKind {
        SHARED_LIBRARY(false) {
            @Override
            public String getFilenameSuffix() {
                return switch (ObjectFile.getNativeFormat()) {
                    case ELF -> ".so";
                    case MACH_O -> ".dylib";
                    case PECOFF -> ".dll";
                    default -> throw new AssertionError("Unreachable");
                };
            }
        },
        EXECUTABLE(true),
        STATIC_EXECUTABLE(true);

        public final boolean isExecutable;
        public final String mainEntryPointName;

        NativeImageKind(boolean executable) {
            isExecutable = executable;
            mainEntryPointName = executable ? "main" : "run_main";
        }

        public String getFilenameSuffix() {
            return ObjectFile.getNativeFormat() == ObjectFile.Format.PECOFF ? ".exe" : "";
        }
    }

    protected final NativeImageKind imageKind;

    protected AbstractImage(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap, NativeImageCodeCache codeCache,
                    List<HostedMethod> entryPoints, ClassLoader imageClassLoader) {
        this.imageKind = k;
        this.universe = universe;
        this.metaAccess = metaAccess;
        this.nativeLibs = nativeLibs;
        this.heap = heap;
        this.codeCache = codeCache;
        this.entryPoints = entryPoints;
        this.imageClassLoader = imageClassLoader;
    }

    public NativeImageKind getImageKind() {
        return imageKind;
    }

    public int getImageFileSize() {
        assert imageFileSize > 0 : "imageFileSize read before being set; cannot be zero";
        return imageFileSize;
    }

    public int getDebugInfoSize() {
        assert debugInfoSize >= 0 : "debugInfoSize read before being set";
        return debugInfoSize;
    }

    public NativeLibraries getNativeLibs() {
        return nativeLibs;
    }

    /**
     * Build the image. Calling this method is a precondition to calling {@link #write}. It
     * typically finalizes content of the object. It does not build debug information.
     */
    public abstract void build(String imageName, DebugContext debug);

    /**
     * Write the image to the named file.
     */
    public abstract LinkerInvocation write(DebugContext debug, Path outputDirectory, Path tempDirectory, String imageName, BeforeImageWriteAccessImpl config);

    // factory method
    public static AbstractImage create(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap,
                    NativeImageCodeCache codeCache, List<HostedMethod> entryPoints, ClassLoader classLoader) {
        return switch (k) {
            case SHARED_LIBRARY ->
                new SharedLibraryImageViaCC(universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, classLoader);
            case EXECUTABLE, STATIC_EXECUTABLE ->
                new ExecutableImageViaCC(k, universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, classLoader);
        };
    }

    public abstract String[] makeLaunchCommand(AbstractImage.NativeImageKind k, String imageName, Path binPath, Path workPath, java.lang.reflect.Method method);

    public NativeImageCodeCache getCodeCache() {
        return codeCache;
    }

    public NativeImageHeap getHeap() {
        return heap;
    }

    public abstract long getImageHeapSize();

    public abstract ObjectFile getObjectFile();
}
