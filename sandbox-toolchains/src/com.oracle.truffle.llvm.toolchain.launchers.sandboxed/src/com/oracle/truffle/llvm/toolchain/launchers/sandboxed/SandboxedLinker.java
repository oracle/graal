/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.llvm.toolchain.launchers.sandboxed;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;

public final class SandboxedLinker extends SandboxedDriver {

    private SandboxedLinker() {
        super("ld.lld");
    }

    public static void main(String[] args) {
        SandboxedLinker linker = new SandboxedLinker();
        ArrayList<String> extendedArgList = new ArrayList<>(args.length + 3);
        Path compilationSysroot = findSysroot(args);
        String configuredSysroot = System.getenv("GRAALOS_SYSROOT");
        Path runtimeRoot = configuredSysroot == null || configuredSysroot.isBlank()
                        ? compilationSysroot
                        : Path.of(configuredSysroot).toAbsolutePath();

        boolean linkStatic = false;
        boolean needRpath = false;
        ArrayDeque<Boolean> linkStaticStack = new ArrayDeque<>();

        int argIdx = 0;
        while (argIdx < args.length) {
            String flag = args[argIdx++];
            switch (flag) {
                /*
                 * Workaround for rustc: It insists on adding this. Replace with libunwind, which
                 * contains the symbols rustc is interested in (e.g. `_Unwind_*`).
                 */
                case "-lgcc_s":
                    extendedArgList.add("-lunwind");
                    needRpath = true;
                    continue;
            }
            extendedArgList.add(flag);
            switch (flag) {
                case "--push-state" -> linkStaticStack.push(linkStatic);
                case "--pop-state" -> {
                    if (!linkStaticStack.isEmpty()) {
                        linkStatic = linkStaticStack.pop();
                    }
                }
                case "-Bstatic" -> linkStatic = true;
                case "--Bstatic" -> linkStatic = true;
                case "-static" -> linkStatic = true;
                case "--static" -> linkStatic = true;
                case "-dn" -> linkStatic = true;
                case "--dn" -> linkStatic = true;
                case "-non_shared" -> linkStatic = true;
                case "--non_shared" -> linkStatic = true;
                case "-Bdynamic" -> linkStatic = false;
                case "--Bdynamic" -> linkStatic = false;
                case "-dy" -> linkStatic = false;
                case "--dy" -> linkStatic = false;
                case "-lc++" -> {
                    if (linkStatic) {
                        /*
                         * The static libc++.a does not automatically pull in the dependencies to
                         * libc++abi.a and libunwind.a. Add them manually here.
                         */
                        extendedArgList.add("-lc++abi");
                        extendedArgList.add("-lunwind");
                    }
                    needRpath = true;
                }
                case "-dynamic-linker" -> {
                    if (argIdx < args.length) {
                        String dynLinker = args[argIdx++];
                        if (runtimeRoot != null && "/lib/ld-musl-x86_64.so.1".equals(dynLinker)) {
                            /*
                             * When targeting musl, clang assumes the binary will be moved to a
                             * machine where musl is installed in the root directory. Replace this
                             * with our toolchain path instead. That way, the binaries can run on
                             * the build host. Under graalhost, the dynamic linker is replaced
                             * anyway.
                             */
                            extendedArgList.add(runtimeRoot.resolve("lib").resolve("ld-musl-x86_64.so.1").toString());
                        } else {
                            /*
                             * Someone is manually specifying a different dynamic linker? Assume
                             * they know what they are doing and keep it.
                             */
                            extendedArgList.add(dynLinker);
                        }
                    }
                }
                case "-lc" -> {
                    // -lc does not need rpath
                }
                default -> {
                    if (flag.startsWith("-l")) {
                        needRpath = true;
                    }
                }
            }
        }

        /*
         * Don't add rpath if we're not actually linking against any dynamic libraries except libc.
         * This fixes a test case in patchelf that assumes no rpath is there.
         */
        if (needRpath && runtimeRoot != null) {
            Path muslLib = runtimeRoot.resolve("lib");
            Path muslLibLinux = muslLib.resolve("linux");
            extendedArgList.add(String.format("-rpath=%s:%s", muslLib, muslLibLinux));
        }

        linker.run(extendedArgList);
    }

    private static Path findSysroot(String[] args) {
        Path sysroot = null;
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.startsWith("--sysroot=")) {
                sysroot = Path.of(arg.substring("--sysroot=".length()));
            } else if (arg.equals("--sysroot") && i < args.length) {
                sysroot = Path.of(args[i++]);
            }
        }
        return sysroot;
    }
}
