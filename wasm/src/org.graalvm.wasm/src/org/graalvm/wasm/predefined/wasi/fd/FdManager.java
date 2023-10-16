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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;

public final class FdManager implements Closeable {

    private final EconomicMap<Integer, Fd> handles;

    public FdManager(TruffleLanguage.Env env) {
        CompilerAsserts.neverPartOfCompilation();
        handles = EconomicMap.create(3);

        put(0, new InputStreamFd(env.in()));
        put(1, new OutputStreamFd(env.out()));
        put(2, new OutputStreamFd(env.err()));

        final String preopenedDirs = WasmOptions.WasiMapDirs.getValue(env.getOptions());
        if (preopenedDirs == null || preopenedDirs.isEmpty()) {
            return;
        }

        int fd = 3;
        for (final String dir : preopenedDirs.split(",")) {
            final String[] parts = dir.split("::", 2);
            if (parts.length > 2) {
                throw WasmException.create(Failure.INVALID_WASI_DIRECTORIES_MAPPING,
                                String.format("Wasi directory map '%s' is not valid. Syntax: --WasiMapDirs <virtual_path>::<host_path>, or --WasiMapDirs <host_path>", dir));
            }
            final String virtualDirPath = parts[0];
            final String hostDirPath = parts.length == 2 ? parts[1] : parts[0];

            final TruffleFile virtualDir;
            final TruffleFile hostDir;
            try {
                virtualDir = env.getPublicTruffleFile(virtualDirPath).normalize();
                // Currently, we follow symbolic links.
                hostDir = env.getPublicTruffleFile(hostDirPath).getCanonicalFile();
            } catch (IOException | SecurityException e) {
                throw WasmException.create(Failure.INVALID_WASI_DIRECTORIES_MAPPING);
            }

            put(fd, new PreopenedDirectoryFd(this, hostDir, virtualDir, virtualDirPath));
            ++fd;
        }
    }

    public synchronized Fd get(int fd) {
        return handles.get(fd);
    }

    /**
     * Registers {@code handle} with a new random free file descriptor number, and returns this
     * number.
     * <p>
     * Expected time complexity: O(2^31 / (2^31 - n)), where n is {@link #size() the number of
     * opened file descriptors}. This is effectively constant for n < 2^30, which should always be
     * the case in practice (Linux for example has a limit of 1024 file descriptors).
     */
    synchronized int put(Fd handle) {
        int fd;
        do {
            fd = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        } while (handles.containsKey(fd));
        put(fd, handle);
        return fd;
    }

    private synchronized void put(int fd, Fd handle) {
        handles.put(fd, handle);
    }

    public synchronized void remove(int fd) {
        handles.removeKey(fd);
    }

    /**
     * Returns the number of opened file descriptors.
     */
    public synchronized int size() {
        return handles.size();
    }

    @Override
    public synchronized void close() throws IOException {
        for (final Fd handle : handles.getValues()) {
            handle.close();
        }
        handles.clear();
    }

}
