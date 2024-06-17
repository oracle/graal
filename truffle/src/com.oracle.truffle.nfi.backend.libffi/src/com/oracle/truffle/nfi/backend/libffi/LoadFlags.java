/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

import java.util.List;

import com.oracle.truffle.api.InternalResource.OS;

abstract class LoadFlags {

    static LoadFlags initLoadFlags(LibFFIContext ctx) {
        switch (OS.getCurrent()) {
            case LINUX:
            case DARWIN:
                return new PosixFlags(ctx);
            case WINDOWS:
                return new WindowsFlags();
            default:
                return new UnknownFlags();
        }
    }

    abstract int parseFlags(List<String> flags);

    private LoadFlags() {
    }

    static final class UnknownFlags extends LoadFlags {

        @Override
        int parseFlags(List<String> flagNames) {
            return 0;
        }
    }

    static final class PosixFlags extends LoadFlags {

        private final LibFFIContext ctx;

        PosixFlags(LibFFIContext ctx) {
            this.ctx = ctx;
        }

        @Override
        int parseFlags(List<String> flagNames) {
            int flags = 0;
            boolean lazyOrNow = false;
            if (flagNames != null) {
                for (String flag : flagNames) {
                    switch (flag) {
                        case "RTLD_GLOBAL":
                            flags |= ctx.RTLD_GLOBAL;
                            break;
                        case "RTLD_LOCAL":
                            flags |= ctx.RTLD_LOCAL;
                            break;
                        case "RTLD_LAZY":
                            flags |= ctx.RTLD_LAZY;
                            lazyOrNow = true;
                            break;
                        case "RTLD_NOW":
                            flags |= ctx.RTLD_NOW;
                            lazyOrNow = true;
                            break;
                        case "ISOLATED_NAMESPACE":
                            if (ctx.ISOLATED_NAMESPACE == 0) {
                                // undefined
                                throw new NFIUnsupportedException("isolated namespace not supported");
                            }
                            flags |= ctx.ISOLATED_NAMESPACE;
                            break;
                    }
                }
            }
            if (!lazyOrNow) {
                // default to 'RTLD_NOW' if neither 'RTLD_LAZY' nor 'RTLD_NOW' was specified
                flags |= ctx.RTLD_NOW;
            }
            return flags;
        }
    }

    static final class WindowsFlags extends LoadFlags {

        @Override
        int parseFlags(List<String> flagNames) {
            int flags = 0;
            if (flagNames != null) {
                for (String flag : flagNames) {
                    // https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-loadlibraryexw
                    switch (flag) {
                        case "LOAD_LIBRARY_SEARCH_APPLICATION_DIR":
                            flags |= 0x00000200;
                            break;
                        case "LOAD_LIBRARY_SEARCH_DEFAULT_DIRS":
                            flags |= 0x00001000;
                            break;
                        case "LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR":
                            flags |= 0x00000100;
                            break;
                        case "LOAD_LIBRARY_SEARCH_SYSTEM32":
                            flags |= 0x00000800;
                            break;
                        case "LOAD_LIBRARY_SEARCH_USER_DIRS":
                            flags |= 0x00000400;
                            break;
                        case "LOAD_WITH_ALTERED_SEARCH_PATH":
                            flags |= 0x00000008;
                            break;
                        case "ISOLATED_NAMESPACE":
                            throw new NFIUnsupportedException("isolated namespace not supported");
                    }
                }
            }
            return flags;
        }
    }
}
