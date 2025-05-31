/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.fs;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

import org.graalvm.nativeimage.Platform;
import org.graalvm.shadowed.com.google.common.jimfs.Configuration;
import org.graalvm.shadowed.com.google.common.jimfs.Jimfs;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;

import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

import jdk.graal.compiler.debug.GraalError;

public final class FileSystemInitializer {
    /**
     * Name of the file system provided to jimfs. This sort of acts as a namespace since jimfs
     * allows for the creation of multiple file systems.
     * <p>
     * However, for our purposes there is always ever going to be a single file system and it uses
     * this name.
     */
    public static final String FS_NAME = "NIVFS";

    private static final String WORKING_DIR = System.getProperty("user.dir");
    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    @JS("return runtime.prefetchedLibraryNames();")
    private static native JSObject prefetchedLibraryNames();

    @JS.Coerce
    @JS("return runtime.data.libraries[name];")
    private static native String prefetchedLibraryContent(String name);

    @JS.Coerce
    @JS("runtime.data.libraries[name] = null;")
    private static native void clearPrefetchedLibraryContent(String name);

    static FileSystem createFileSystem() {
        Configuration config = Configuration.unix().toBuilder().setWorkingDirectory(WORKING_DIR).setAttributeViews("basic", "posix").build();

        return Jimfs.newFileSystem(FS_NAME, config);
    }

    /**
     * Populates the empty file system with required folders and files.
     *
     * @implNote At this point, the default file system provider is not fully set up. Nothing should
     *           be called that uses it. Because of that any {@link java.nio.file.Path} instances
     *           should be obtained through the provided file system instance.
     */
    static void populate(FileSystem fileSystem) {
        try {
            // Create directory structure.
            Files.createDirectories(fileSystem.getPath("/root"));
            Files.createDirectories(fileSystem.getPath("/usr", "lib"));
            Files.createDirectories(fileSystem.getPath(TMP_DIR));
            Files.createDirectories(fileSystem.getPath(WORKING_DIR));
            Files.createDirectories(fileSystem.getPath("/dev"));
            Files.createFile(fileSystem.getPath("/dev", "random"));
            Files.createFile(fileSystem.getPath("/dev", "urandom"));

            /*
             * In the WasmLM and WasmGC backend, the @JS annotation is not supported and thus,
             * prefetched libraries neither because they can't be loaded from the JavaScript code.
             *
             * TODO GR-60603 Support @JS annotation in WasmGC backend
             */
            if (!Platform.includedIn(WebImageWasmLMPlatform.class) && !Platform.includedIn(WebImageWasmGCPlatform.class)) {
                // Store the prefetched libraries into the file system.
                JSObject prefetchedLibraryNames = prefetchedLibraryNames();

                int size = ((JSNumber) prefetchedLibraryNames.get("length")).asInt();
                for (int i = 0; i < size; i++) {
                    JSString jsName = (JSString) prefetchedLibraryNames.get(i);
                    String name = jsName.asString();
                    String content = prefetchedLibraryContent(name);
                    try {
                        Files.writeString(fileSystem.getPath("/usr", "lib", name), content);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to store shared library.", e);
                    }
                    clearPrefetchedLibraryContent(name);
                }
            }
        } catch (IOException e) {
            throw GraalError.shouldNotReachHere(e, "Error while trying to populate in-memory file system");
        }
    }

}
