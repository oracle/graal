/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.macho;

public class MachOLoadCommand {

    public static final int CMD_ID_SIZE = 4;

    // command identifiers
    public static final int LC_SEGMENT = 0x00000001;
    public static final int LC_SYMTAB = 0x00000002;
    public static final int LC_SYMSEG = 0x00000003;
    public static final int LC_THREAD = 0x00000004;
    public static final int LC_UNIXTHREAD = 0x00000005;
    public static final int LC_LOADFVMLIB = 0x00000006;
    public static final int LC_IDFVMLIB = 0x00000007;
    public static final int LC_IDENT = 0x00000008;
    public static final int LC_FVMFILE = 0x00000009;
    public static final int LC_PREPAGE = 0x0000000A;
    public static final int LC_DYSYMTAB = 0x0000000B;
    public static final int LC_LOAD_DYLIB = 0x0000000C;
    public static final int LC_ID_DYLIB = 0x0000000D;
    public static final int LC_LOAD_DYLINKER = 0x0000000E;
    public static final int LC_ID_DYLINKER = 0x0000000F;
    public static final int LC_PREBOUND_DYLIB = 0x00000010;
    public static final int LC_ROUTINES = 0x00000011;
    public static final int LC_SUB_FRAMEWORK = 0x00000012;
    public static final int LC_SUB_UMBRELLA = 0x00000013;
    public static final int LC_SUB_CLIENT = 0x00000014;
    public static final int LC_SUB_LIBRARY = 0x00000015;
    public static final int LC_TWOLEVEL_HINTS = 0x00000016;
    public static final int LC_PREBIND_CKSUM = 0x00000017;
    public static final int LC_LOAD_WEAK_DYLIB = 0x80000018;
    public static final int LC_SEGMENT_64 = 0x00000019;
    public static final int LC_ROUTINES_64 = 0x0000001A;
    public static final int LC_UUID = 0x0000001B;
    public static final int LC_RPATH = 0x8000001C;
    public static final int LC_CODE_SIGNATURE = 0x0000001D;
    public static final int LC_SEGMENT_SPLIT_INFO = 0x0000001E;
    public static final int LC_REEXPORT_DYLIB = 0x8000001F;
    public static final int LC_LAZY_LOAD_DYLIB = 0x00000020;
    public static final int LC_ENCRYPTION_INFO = 0x00000021;
    public static final int LC_DYLD_INFO = 0x00000022;
    public static final int LC_DYLD_INFO_ONLY = 0x80000022;
    public static final int LC_LOAD_UPWARD_DYLIB = 0x80000023;
    public static final int LC_VERSION_MIN_MACOSX = 0x00000024;
    public static final int LC_VERSION_MIN_IPHONEOS = 0x00000025;
    public static final int LC_FUNCTION_STARTS = 0x00000026;
    public static final int LC_DYLD_ENVIRONMENT = 0x00000027;
    public static final int LC_MAIN = 0x80000028;
    public static final int LC_DATA_IN_CODE = 0x00000029;
    public static final int LC_SOURCE_VERSION = 0x0000002A;
    public static final int LC_DYLIB_CODE_SIGN_DRS = 0x0000002B;
    public static final int LC_ENCRYPTION_INFO_64 = 0x0000002C;
    public static final int LC_LINKER_OPTION = 0x0000002D;
    public static final int LC_LINKER_OPTIMIZATION_HINT = 0x0000002E;
    public static final int LC_VERSION_MIN_TVOS = 0x0000002F;
    public static final int LC_VERSION_MIN_WATCHOS = 0x00000030;

    private final int cmd;
    private final int cmdSize;

    public MachOLoadCommand(int cmd, int cmdSize) {
        this.cmd = cmd;
        this.cmdSize = cmdSize;
    }

    public int getCmd() {
        return cmd;
    }

    public int getCmdSize() {
        return cmdSize;
    }

    protected static String getString(MachOReader buffer, int len) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < len; i++) {
            byte b = buffer.getByte();
            if (b != 0) {
                sb.append((char) b);
            }
        }

        return sb.toString();
    }
}
