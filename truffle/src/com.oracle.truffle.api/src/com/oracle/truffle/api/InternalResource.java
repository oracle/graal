/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Represents an internal resource of a language that can be lazily unpacked to a cache user
 * directory.
 *
 * @since 23.1
 */
public interface InternalResource {

    /**
     * Unpacks all resources to a given target directory. The target directory is guaranteed to be
     * writable and the unpacking is synchronized by a file system lock. If a resource was
     * previously cached then {@link #versionHash(Env)} is invoked and the version string is
     * compared. If it matches then {@link #unpackFiles(Path,Env)} will not be invoked and the
     * directory will be used as previously unpacked. The target directory is guaranteed to exist
     * and guaranteed to be empty.
     * <p>
     * Ideally the result of this method should be idempotent in order to be safely cachable. Care
     * should be taken, if system properties are used that change the result of this method. It is
     * safe to use <code>"os.arch"</code> and <code>"os.name"</code> as internal resources are never
     * cached or moved across operating system architectures.
     * <p>
     * No guest language code must run as part of this method.
     *
     * @param targetDirectory the target directory to extract files to
     * @since 23.1
     */
    void unpackFiles(Path targetDirectory, Env env) throws IOException;

    /**
     * Returns the version hash to be used for this resource. It is the responsibility of the
     * implementer to ensure that this version hash is unique. For example, a SHA-512 could be a
     * good version identifier for file based resources.
     *
     * @since 23.1
     */
    String versionHash(Env env);

    /**
     * Access to common utilities for unpacking resource files.
     *
     * @since 23.1
     */
    final class Env {

        private final BooleanSupplier contextPreinitializationCheck;

        Env(BooleanSupplier contextPreinitializationCheck) {
            this.contextPreinitializationCheck = Objects.requireNonNull(contextPreinitializationCheck);
        }

        /**
         * Returns {@code true} if the engine causing the resource unpacking is being
         * pre-initialized.
         *
         * @since 23.1
         */
        public boolean inContextPreinitialization() {
            return contextPreinitializationCheck.getAsBoolean();
        }

        /**
         * Returns {@code true} if resource unpacking happens during the native image build.
         *
         * @since 23.1
         */
        public boolean inNativeImageBuild() {
            return TruffleOptions.AOT;
        }

        /**
         * Returns the current processor architecture. The value can be used to resolve an
         * architecture specific files during resource unpacking.
         *
         * @since 23.1
         */
        public CPUArchitecture getCPUArchitecture() {
            return CPUArchitecture.getCurrent();
        }

        /**
         * Returns the current operating system. The value can be used to resolve an OS specific
         * files during resource unpacking.
         *
         * @since 23.1
         */
        public OS getOS() {
            return OS.getCurrent();
        }

        /**
         * Reads a version hash from a resource in the {@code owner} module.
         *
         * @param owner refers to the module that contains the version hash. If the {@code owner}
         *            encapsulates the {@code hashResource}, see
         *            {@link Module#getResourceAsStream(String)}, the {@code owner} needs to open
         *            the enclosing package of the {@code hashResource} to the
         *            {@code org.graalvm.truffle} module. It is recommended to use non-encapsulated
         *            resource paths.
         * @param hashResource a {@code '/'}-separated path name that identifies the resource,
         *            similar to {@link Class#getResourceAsStream}. The {@code hashResource} may
         *            contain {@code <os>} and {@code <arch>} wildcards, which are replaced by the
         *            {@link #getOS() current operating system} and the {@link #getCPUArchitecture()
         *            current CPU architecture}, respectively.
         * @return the {@code hashResource} content
         * @throws IOException in case of IO error
         * @since 23.1
         */
        public String readVersionHash(Module owner, String hashResource) throws IOException {
            String resourceName = substituteVariables(hashResource);
            List<String> content = readResourceAsStrings(owner, resourceName);
            if (content.size() != 1) {
                throw CompilerDirectives.shouldNotReachHere("Expected a single line with hash. The " + hashResource + " content " + content);
            }
            return content.get(0);
        }

        /**
         * Unpacks file listed in the {@code fileListResource} to the {@code into} folder.
         *
         * @param owner refers to the module that contains packed resources. If the {@code owner}
         *            encapsulates resources, see {@link Module#getResourceAsStream(String)}, the
         *            {@code owner} needs to open the resources enclosing package to the
         *            {@code org.graalvm.truffle} module. It is recommended to use non-encapsulated
         *            resource paths.
         * @param fileListResource a {@code '/'}-separated path name that identifies the resource
         *            containing the file list. The {@code fileListResource} may contain
         *            {@code <os>} and {@code <arch>} wildcards, which are replaced by the
         *            {@link #getOS() current operating system} and the {@link #getCPUArchitecture()
         *            current CPU architecture}, respectively.
         * @param relativizeTo a list of paths used to relativize the file list entries in the
         *            {@code into} folder. In other words, the file list entries are resolved using
         *            the {@code into} directory after removing the {@code relativizeTo} paths.
         *            Similar to the {@fileListResource}, the {@relativizeTo} paths can utilize
         *            wildcards such as {@code <os>} and {@code <arch>}.
         * @throws IOException in case of IO error
         * @since 23.1
         */
        public void unpackFiles(Path into, Module owner, String fileListResource, List<String> relativizeTo) throws IOException {
            List<Path> roots = new ArrayList<>();
            for (String strPath : relativizeTo) {
                Path root = Paths.get(substituteVariables(strPath));
                if (root.isAbsolute()) {
                    throw new IllegalArgumentException("RelativizeTo must contain only relative paths, but the absolute path " + root + " was given.");
                }
                roots.add(root);
            }
            String resourceName = substituteVariables(fileListResource);
            List<String> fileList = readResourceAsStrings(owner, resourceName);
            for (String resourcePath : fileList) {
                Path target = into.resolve(relativize(Paths.get(resourcePath), roots));
                copyResource(target, owner, resourcePath);
            }
        }

        private String substituteVariables(String resourceName) {
            return resourceName.replace("<os>", getOS().toString()).replace("<arch>", getCPUArchitecture().toString());
        }

        private static Path relativize(Path path, List<Path> relativizeTo) {
            for (Path root : relativizeTo) {
                if (path.startsWith(root)) {
                    return root.relativize(path);
                }
            }
            return path;
        }

        private static InputStream findResource(Module module, String resourceName) throws IOException {
            InputStream stream = module.getResourceAsStream(resourceName);
            if (stream == null) {
                throw new NoSuchFileException(resourceName);
            }
            return stream;
        }

        private static List<String> readResourceAsStrings(Module module, String resourceName) throws IOException {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(findResource(module, resourceName), StandardCharsets.UTF_8))) {
                List<String> content = new ArrayList<>();
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    if (!line.isEmpty()) {
                        content.add(line);
                    }
                }
                return content;
            }
        }

        private static void copyResource(Path target, Module owner, String relativeResourcePath) throws IOException {

            Path parent = target.getParent();
            if (parent == null) {
                throw CompilerDirectives.shouldNotReachHere("RelativeResourcePath must be non-empty.");
            }
            Files.createDirectories(parent);
            try (BufferedInputStream in = new BufferedInputStream(findResource(owner, relativeResourcePath.replace(File.separatorChar, '/')))) {
                Files.copy(in, target);
            }
        }
    }

    /**
     * The annotation used to lookup {@link InternalResource} by an id. In addition to being
     * annotated with this annotation, the implementation of an internal resource must also be
     * registered using the {@link TruffleLanguage.Registration#internalResources()} to be assigned
     * to language or instrument.
     *
     * @since 23.1
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Id {

        /**
         * An internal resource identifier that is a valid path component and unique per language.
         *
         * @since 23.1
         */
        String value();
    }
}
