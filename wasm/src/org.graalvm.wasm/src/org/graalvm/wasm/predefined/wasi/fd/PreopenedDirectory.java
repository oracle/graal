/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.predefined.wasi.fd;

import java.util.Objects;

import com.oracle.truffle.api.TruffleFile;

/**
 * Wrapper around a pair (virtual directory, host directory).
 * <p>
 * It is responsible to:
 * <ul>
 * <li>map virtual paths to host paths ({@link #virtualFileToHostFile(TruffleFile)}),</li>
 * <li>check that given paths are children of this directory
 * ({@link #containedVirtualFile(TruffleFile)} and {@link #containedHostFile(TruffleFile)}).</li>
 * </ul>
 * <p>
 * These methods are only called from {@link DirectoryFd}, which is currently the only way to
 * interact with {@link Fd} objects.
 */
final class PreopenedDirectory {

    /**
     * Host path of this pre-opened directory.
     */
    private final TruffleFile hostPath;

    /**
     * The length of the host path used for comparisons.
     */
    private final int hostPathLength;

    /**
     * Virtual path of this pre-opened directory.
     */
    private final TruffleFile virtualPath;

    /**
     * The length of the virtual path used for comparisons.
     */
    private final int virtualPathLength;

    PreopenedDirectory(TruffleFile hostPath, TruffleFile virtualPath) {
        Objects.requireNonNull(hostPath);
        Objects.requireNonNull(virtualPath);
        assert hostPath.isAbsolute() : "hostRoot must be absolute";

        this.hostPath = hostPath.normalize();
        this.hostPathLength = this.hostPath.getPath().length();
        this.virtualPath = virtualPath.normalize();
        this.virtualPathLength = this.virtualPath.getPath().length();
    }

    private static String getRelativePath(TruffleFile virtualFile, TruffleFile rootPath, int rootPathLength) {
        String virtualPath = virtualFile.getPath();
        final int virtualFileLength = virtualPath.length();
        if (virtualFileLength == rootPathLength) {
            return "";
        }
        if (rootPathLength == 1) {
            if (rootPath.isAbsolute()) {
                // root path, no trailing slash
                return virtualPath.substring(1);
            } else if (rootPath.getPath().equals(".")) {
                return virtualPath;
            }
        }
        // remove trailing slash
        return virtualPath.substring(rootPathLength + 1);
    }

    /**
     * Returns the normalized virtual file for the given virtual file if it is contained in this
     * pre-opened directory, or {@code null} otherwise.
     */
    TruffleFile containedVirtualFile(TruffleFile virtualFile) {
        Objects.requireNonNull(virtualFile);

        final TruffleFile result = virtualFile.normalize();

        // Checks that the given path did not resolve to a file outside virtualRoot.
        return result.startsWith(virtualPath) || (!result.isAbsolute() && virtualPathLength == 1 && virtualPath.getPath().equals(".")) ? result : null;
    }

    /**
     * Returns the normalized host file for the given host file if it is contained in this
     * pre-opened directory, or {@code null} otherwise.
     */
    TruffleFile containedHostFile(TruffleFile hostFile) {
        Objects.requireNonNull(hostFile);

        final TruffleFile result = hostPath.resolve(hostFile.getPath()).normalize();

        // Checks that the given path did not resolve to a file outside hostRoot.
        return result.isAbsolute() && result.startsWith(hostPath) ? result : null;
    }

    /**
     * Returns the normalized host file for the given virtual file if it is contained in this
     * pre-opened directory, or {@code null} otherwise.
     */
    TruffleFile virtualFileToHostFile(TruffleFile virtualTruffleFile) {
        Objects.requireNonNull(virtualTruffleFile);

        final TruffleFile resolvedVirtualTruffleFile = containedVirtualFile(virtualTruffleFile);
        if (resolvedVirtualTruffleFile != null) {
            final String relativePathToRoot = getRelativePath(resolvedVirtualTruffleFile, virtualPath, virtualPathLength);
            return containedHostFile(hostPath.resolve(relativePathToRoot));
        }
        return null;
    }

    /**
     * Returns the normalized virtual file for the given host file if it is contained in this
     * pre-opened directory, or {@code null} otherwise.
     */
    TruffleFile hostFileToVirtualFile(TruffleFile hostTruffleFile) {
        Objects.requireNonNull(hostTruffleFile);

        final TruffleFile resolvedHostTruffleFile = containedHostFile(hostTruffleFile);
        if (resolvedHostTruffleFile != null) {
            final String resolvedPathToRoot = getRelativePath(resolvedHostTruffleFile, hostPath, hostPathLength);
            return containedVirtualFile(virtualPath.resolve(resolvedPathToRoot));
        }
        return null;
    }

}
