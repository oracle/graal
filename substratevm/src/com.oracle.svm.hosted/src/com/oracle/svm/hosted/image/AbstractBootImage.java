/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

public abstract class AbstractBootImage {

    protected final HostedMetaAccess metaAccess;
    protected final HostedUniverse universe;
    protected final NativeLibraries nativeLibs;
    protected final NativeImageHeap heap;
    protected final ClassLoader imageClassLoader;
    protected final NativeImageCodeCache codeCache;
    protected final List<HostedMethod> entryPoints;
    protected int resultingImageSize; // for statistical output

    public enum NativeImageKind {
        SHARED_LIBRARY(false) {
            @Override
            public String getFilenameSuffix() {
                switch (ObjectFile.getNativeFormat()) {
                    case ELF:
                        return ".so";
                    case MACH_O:
                        return ".dylib";
                    case PECOFF:
                        return ".dll";
                    default:
                        throw new AssertionError("unreachable");
                }
            }

            @Override
            public String getFilenamePrefix() {
                return ObjectFile.getNativeFormat() == ObjectFile.Format.PECOFF ? "" : "lib";
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

        public String getFilenamePrefix() {
            return "";
        }

        public String getFilename(String basename) {
            return getFilenamePrefix() + basename + getFilenameSuffix();
        }
    }

    protected final NativeImageKind kind;

    protected AbstractBootImage(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap, NativeImageCodeCache codeCache,
                    List<HostedMethod> entryPoints, ClassLoader imageClassLoader) {
        this.kind = k;
        this.universe = universe;
        this.metaAccess = metaAccess;
        this.nativeLibs = nativeLibs;
        this.heap = heap;
        this.codeCache = codeCache;
        this.entryPoints = entryPoints;
        this.imageClassLoader = imageClassLoader;
    }

    public NativeImageKind getBootImageKind() {
        return kind;
    }

    public int getImageSize() {
        return resultingImageSize;
    }

    public NativeLibraries getNativeLibs() {
        return nativeLibs;
    }

    /**
     * Build the image. Calling this method is a precondition to calling {@link #write}. It
     * typically finalizes content of the object. It does not build debug information.
     */
    public abstract void build(DebugContext debug);

    /**
     * Write the image to the named file. This also writes debug information -- either to the same
     * or a different file, as decided by the implementation of {@link #getOrCreateDebugObjectFile}.
     * If {@link #getOrCreateDebugObjectFile} is not called, no debug information is written.
     */
    public abstract LinkerInvocation write(DebugContext debug, Path outputDirectory, Path tempDirectory, String imageName, BeforeImageWriteAccessImpl config);

    /**
     * Returns the ObjectFile.Section within the image, if any, whose vaddr defines the image's base
     * vaddr.
     */
    public abstract ObjectFile.Section getTextSection();

    // factory method
    public static AbstractBootImage create(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap,
                    NativeImageCodeCache codeCache, List<HostedMethod> entryPoints, ClassLoader classLoader) {
        switch (k) {
            case SHARED_LIBRARY:
                return new SharedLibraryViaCCBootImage(universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, classLoader);
            default:
                return new ExecutableViaCCBootImage(k, universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, classLoader);
        }
    }

    public abstract String[] makeLaunchCommand(AbstractBootImage.NativeImageKind k, String imageName, Path binPath, Path workPath, java.lang.reflect.Method method);

    public NativeImageCodeCache getCodeCache() {
        return codeCache;
    }

    public NativeImageHeap getHeap() {
        return heap;
    }

    public abstract ObjectFile getOrCreateDebugObjectFile();

    public boolean requiresCustomDebugRelocation() {
        return false;
    }

    public AbstractBootImage.NativeImageKind getKind() {
        return kind;
    }
}
