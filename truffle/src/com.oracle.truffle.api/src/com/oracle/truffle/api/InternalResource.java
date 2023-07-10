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

import java.io.IOException;
import java.nio.file.Path;
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
     * Returns a resource identifier that is a valid path component and unique per language. By
     * default, the canoncial class name is used for the internal resource.
     *
     * @since 23.1
     */
    default String name() {
        return this.getClass().getCanonicalName();
    }

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
         * Returns the processor architecture. The value can be used to resolve an architecture
         * specific files during resource unpacking.
         *
         * @since 23.1
         */
        public String getCPUArchitecture() {
            return LanguageAccessor.ENGINE.getCPUArchitecture();
        }

        /**
         * Returns the operating system. The value can be used to resolve an OS specific files
         * during resource unpacking.
         *
         * @since 23.1
         */
        public String getOSName() {
            return LanguageAccessor.ENGINE.getOSName();
        }
    }
}
