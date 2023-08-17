/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.util.Objects;

import org.graalvm.polyglot.Context;

/**
 * Represents an IO access configuration of a polyglot context. The IO access configuration
 * determines how a guest language can access the host IO. The access to host IO can be
 * {@link IOAccess#NONE disabled}, {@link IOAccess#ALL enabled}, or virtualized using a custom
 * {@link FileSystem file system}.
 *
 * @since 23.0
 */
public final class IOAccess {

    /**
     * Provides guest language no access to IO. Guest language only has read-only access to the
     * language's home files. The {@code NONE} is a default value for {@link Context}s created
     * without {@link org.graalvm.polyglot.Context.Builder#allowAllAccess(boolean) all access}. If
     * you are using this preset, the newly added privileges will default to {@code false} for this
     * preset.
     *
     * <p>
     * Equivalent of using the following builder configuration:
     *
     * <pre>
     * <code>
     * IOAccess.newBuilder().build();
     * </code>
     * </pre>
     *
     * @since 23.0
     */
    public static final IOAccess NONE = newBuilder().name("IOAccess.NONE").build();

    /**
     * Provides guest language full access to host IO. Guest language have full access to host file
     * system and host sockets. The {@code ALL} is a default value for {@link Context}s created with
     * {@link org.graalvm.polyglot.Context.Builder#allowAllAccess(boolean) all access} set to
     * {@code true}. If you are using this preset, the newly added privileges will default to
     * {@code true} for this preset.
     *
     * <p>
     * Equivalent of using the following builder configuration:
     *
     * <pre>
     * <code>
     * IOAccess.newBuilder()
     *           .allowHostFileAccess(true)
     *           .allowHostSocketAccess(true)
     *           .build();
     * </code>
     * </pre>
     *
     * @since 23.0
     */
    public static final IOAccess ALL = newBuilder().name("IOAccess.ALL").allowHostFileAccess(true).allowHostSocketAccess(true).build();

    private final String name;
    private final boolean allowHostFileAccess;
    private final boolean allowHostSocketAccess;
    private final FileSystem fileSystem;

    IOAccess(String name, boolean allowHostFileAccess, boolean allowHostSocketAccess, FileSystem fileSystem) {
        if (allowHostFileAccess && fileSystem != null) {
            throw new IllegalArgumentException("The method IOAccess.Builder.allowHostFileAccess(boolean) and the method IOAccess.Builder.fileSystem(FileSystem) are mutually exclusive.");
        }
        this.name = name;
        this.allowHostFileAccess = allowHostFileAccess;
        this.allowHostSocketAccess = allowHostSocketAccess;
        this.fileSystem = fileSystem;
    }

    boolean hasHostFileAccess() {
        return allowHostFileAccess;
    }

    boolean hasHostSocketAccess() {
        return allowHostSocketAccess;
    }

    FileSystem getFileSystem() {
        return fileSystem;
    }

    /**
     * Creates a new builder that allows to create a custom IO access configuration. The builder
     * configuration needs to be completed using the {@link Builder#build() method}.
     *
     * @since 23.0
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates a new builder that allows to create a custom IO access configuration based of a
     * preset configuration. The preset configuration is copied and used as a template for the
     * returned builder. The builder configuration needs to be completed using the
     * {@link Builder#build() method}.
     *
     * @since 23.0
     */
    public static Builder newBuilder(IOAccess prototype) {
        return new Builder(prototype);
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public int hashCode() {
        return Objects.hash(allowHostFileAccess, allowHostSocketAccess, fileSystem);
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IOAccess) {
            IOAccess other = (IOAccess) obj;
            return allowHostFileAccess == other.allowHostFileAccess &&
                            allowHostSocketAccess == other.allowHostSocketAccess &&
                            Objects.equals(fileSystem, other.fileSystem);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public String toString() {
        if (name != null) {
            return name;
        } else {
            return String.format("IOAccess[allowHostFileAccess=%b, allowHostSocketAccess=%b, fileSystem=%s]", allowHostFileAccess, allowHostSocketAccess, fileSystem);
        }
    }

    /**
     * Builder to create a custom {@link IOAccess IO access configuration}.
     *
     * @since 23.0
     */
    public static final class Builder {

        private String name;
        private boolean allowHostFileAccess;
        private boolean allowHostSocketAccess;
        private FileSystem customFileSystem;

        Builder() {
        }

        Builder(IOAccess prototype) {
            this.allowHostFileAccess = prototype.allowHostFileAccess;
            this.allowHostSocketAccess = prototype.allowHostSocketAccess;
            this.customFileSystem = prototype.fileSystem;
        }

        Builder name(String givenName) {
            this.name = givenName;
            return this;
        }

        /**
         * If {@code true}, it allows the guest language unrestricted access to files on the host
         * system.
         *
         * @since 23.0
         */
        public Builder allowHostFileAccess(boolean allow) {
            this.allowHostFileAccess = allow;
            return this;
        }

        /**
         * If {@code true}, it allows the guest language unrestricted access to host system sockets.
         *
         * @since 23.0
         */
        public Builder allowHostSocketAccess(boolean allow) {
            this.allowHostSocketAccess = allow;
            return this;
        }

        /**
         * Sets a new {@link FileSystem}. Access to the file system in the guest language will be
         * virtualized using the {@code fileSystem}. A file system can restrict access to files or
         * fully virtualize file system operations. An example of virtualization is a <a href=
         * "https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.test/src/com/oracle/truffle/api/test/polyglot/MemoryFileSystem.java">memory-based
         * file system</a>.
         *
         * @param fileSystem the file system to use in the guest language
         * @return the {@link Builder}
         * @since 23.0
         */
        public Builder fileSystem(FileSystem fileSystem) {
            this.customFileSystem = Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            return this;
        }

        /**
         * Creates an instance of the custom IO access configuration.
         *
         * @throws IllegalArgumentException if host file access is enabled and a custom file system
         *             is set.
         * @since 23.0
         */
        public IOAccess build() {
            return new IOAccess(name, allowHostFileAccess, allowHostSocketAccess, customFileSystem);
        }
    }
}
