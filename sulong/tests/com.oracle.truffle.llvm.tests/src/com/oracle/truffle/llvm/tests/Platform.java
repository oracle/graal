/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 * Copyright (c) 2020, Arm Limited.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests;

public class Platform {

    public enum Architecture {
        AMD64,
        AArch64;

        private static Architecture findArchitecture() {
            final String name = System.getProperty("os.arch");
            if (name.equals("amd64") || name.equals("x86_64")) {
                return AMD64;
            }
            if (name.equals("aarch64")) {
                return AArch64;
            }
            throw new IllegalArgumentException("unknown architecture: " + name);
        }

        private static final Architecture architecture = findArchitecture();

        public static Architecture getArchitecture() {
            return architecture;
        }
    }

    public enum OS {
        Linux,
        Solaris,
        Darwin;

        private static OS findOS() {
            final String name = System.getProperty("os.name");
            if (name.equals("Linux")) {
                return Linux;
            }
            if (name.equals("SunOS")) {
                return Solaris;
            }
            if (name.equals("Mac OS X") || name.equals("Darwin")) {
                return Darwin;
            }
            throw new IllegalArgumentException("unknown OS: " + name);
        }

        private static final OS os = findOS();

        public static OS getOS() {
            return os;
        }
    }

    public static Architecture getArchitecture() {
        return Architecture.getArchitecture();
    }

    public static OS getOS() {
        return OS.getOS();
    }

    public static boolean isAMD64() {
        return Architecture.getArchitecture() == Architecture.AMD64;
    }

    public static boolean isAArch64() {
        return Architecture.getArchitecture() == Architecture.AArch64;
    }

    public static boolean isLinux() {
        return OS.getOS() == OS.Linux;
    }

    public static boolean isSolaris() {
        return OS.getOS() == OS.Solaris;
    }

    public static boolean isDarwin() {
        return OS.getOS() == OS.Darwin;
    }
}
