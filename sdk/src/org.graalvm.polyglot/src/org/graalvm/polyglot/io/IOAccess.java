/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

public final class IOAccess {

    public static final IOAccess NONE = newBuilder().allowHostFileAccess(false).allowSocketAccess(false).build();

    public static final IOAccess ALL = newBuilder().allowHostFileAccess(true).allowSocketAccess(true).build();

    private final boolean allowHostFileAccess;
    private final boolean allowSocketAccess;
    private final FileSystem fileSystem;

    IOAccess(boolean allowHostFileAccess, boolean allowSocketAccess, FileSystem fileSystem) {
        if (allowHostFileAccess && fileSystem != null) {
            throw new IllegalStateException("The allow host file access and custom filesystem are mutually exclusive.");
        }
        this.allowHostFileAccess = allowHostFileAccess;
        this.allowSocketAccess = allowSocketAccess;
        this.fileSystem = fileSystem;
    }

    FileSystem getFileSystem() {
        return fileSystem;
    }

    boolean hasHostFileAccess() {
        return allowHostFileAccess;
    }

    boolean hasSocketAccess() {
        return allowSocketAccess;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(IOAccess prototype) {
        return new Builder(prototype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowHostFileAccess, allowSocketAccess, fileSystem);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IOAccess) {
            IOAccess other = (IOAccess) obj;
            return allowHostFileAccess == other.allowHostFileAccess &&
                            allowSocketAccess == other.allowSocketAccess &&
                            Objects.equals(fileSystem, other.fileSystem);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("IOAccess[allow host file access: %b, allow socket access: %b, file system %s]", allowHostFileAccess, allowSocketAccess, fileSystem);
    }

    public static final class Builder {
        private boolean allowHostFileAccess;
        private boolean allowSocketAccess;
        private FileSystem customFileSystem;

        Builder() {
        }

        Builder(IOAccess prototype) {
            this.allowHostFileAccess = prototype.allowHostFileAccess;
            this.allowSocketAccess = prototype.allowSocketAccess;
            this.customFileSystem = prototype.fileSystem;
        }

        public Builder allowHostFileAccess(boolean allow) {
            this.allowHostFileAccess = allow;
            return this;
        }

        public Builder allowSocketAccess(boolean allow) {
            this.allowSocketAccess = allow;
            return this;
        }

        public Builder fileSystem(FileSystem fileSystem) {
            this.customFileSystem = Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            return this;
        }

        public IOAccess build() {
            return new IOAccess(allowHostFileAccess, allowSocketAccess, customFileSystem);
        }
    }
}
