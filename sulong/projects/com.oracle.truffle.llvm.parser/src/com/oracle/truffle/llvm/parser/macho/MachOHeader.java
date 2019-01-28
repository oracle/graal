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

public final class MachOHeader {

    private final int cpuType;
    private final int cpuSubType;
    private final int fileType;
    private final int nCmds;
    private final int sizeOfCmds;
    private final int flags;
    private final int reserved;

    public MachOHeader(int cpuType, int cpuSubType, int fileType, int nrOfCmds, int sizeOfCmds, int flags, int reserved) {
        this.cpuType = cpuType;
        this.cpuSubType = cpuSubType;
        this.fileType = fileType;
        this.nCmds = nrOfCmds;
        this.sizeOfCmds = sizeOfCmds;
        this.flags = flags;
        this.reserved = reserved;
    }

    public int getCpuType() {
        return cpuType;
    }

    public int getCpuSubType() {
        return cpuSubType;
    }

    public int getFileType() {
        return fileType;
    }

    public int getNCmds() {
        return nCmds;
    }

    public int getSizeOfCmds() {
        return sizeOfCmds;
    }

    public int getFlags() {
        return flags;
    }

    public int getReserved() {
        return reserved;
    }

    public static MachOHeader create(MachOReader reader) {
        int cpuType = reader.getInt();
        int cpuSubType = reader.getInt();
        int fileType = reader.getInt();
        int nrOfCmds = reader.getInt();
        int sizeOfCmds = reader.getInt();
        int flags = reader.getInt();
        // 0 initialize the unused 'reserved' field
        int reserved = reader.is64Bit() ? reader.getInt() : 0;

        return new MachOHeader(cpuType, cpuSubType, fileType, nrOfCmds, sizeOfCmds, flags, reserved);
    }
}
