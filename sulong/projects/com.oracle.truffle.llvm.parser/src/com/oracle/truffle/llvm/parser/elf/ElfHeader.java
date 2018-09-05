/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.elf;

public final class ElfHeader {

    private final short type;
    private final short machine;
    private final int version;
    private final long entry;
    private final long phoff;
    private final long shoff;
    private final int flags;
    private final short ehsize;
    private final short phentsize;
    private final short phnum;
    private final short shentsize;
    private final short shnum;
    private final short shstrndx;

    private ElfHeader(short type, short machine, int version, long entry, long phoff, long shoff, int flags, short ehsize, short phentsize, short phnum, short shentsize, short shnum,
                    short shstrndx) {
        this.type = type;
        this.machine = machine;
        this.version = version;
        this.entry = entry;
        this.phoff = phoff;
        this.shoff = shoff;
        this.flags = flags;
        this.ehsize = ehsize;
        this.phentsize = phentsize;
        this.phnum = phnum;
        this.shentsize = shentsize;
        this.shnum = shnum;
        this.shstrndx = shstrndx;
    }

    public short getType() {
        return type;
    }

    public short getMachine() {
        return machine;
    }

    public long getEntry() {
        return entry;
    }

    public long getPhoff() {
        return phoff;
    }

    public long getShoff() {
        return shoff;
    }

    public int getFlags() {
        return flags;
    }

    public short getEhsize() {
        return ehsize;
    }

    public short getPhentsize() {
        return phentsize;
    }

    public short getPhnum() {
        return phnum;
    }

    public short getShentsize() {
        return shentsize;
    }

    public short getShnum() {
        return shnum;
    }

    public short getShstrndx() {
        return shstrndx;
    }

    public int getVersion() {
        return version;
    }

    public static ElfHeader create(ElfReader buffer) {
        if (buffer.is64Bit()) {
            return readHeader64(buffer);
        } else {
            return readHeader32(buffer);
        }
    }

    private static ElfHeader readHeader32(ElfReader buffer) {
        short type = buffer.getShort();
        short machine = buffer.getShort();
        int version = buffer.getInt();
        long entry = buffer.getInt();
        long phoff = buffer.getInt();
        long shoff = buffer.getInt();
        int flags = buffer.getInt();
        short ehsize = buffer.getShort();
        short phentsize = buffer.getShort();
        short phnum = buffer.getShort();
        short shentsize = buffer.getShort();
        short shnum = buffer.getShort();
        short shstrndx = buffer.getShort();
        return new ElfHeader(type, machine, version, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx);
    }

    private static ElfHeader readHeader64(ElfReader buffer) {
        short type = buffer.getShort();
        short machine = buffer.getShort();
        int version = buffer.getInt();
        long entry = buffer.getLong();
        long phoff = buffer.getLong();
        long shoff = buffer.getLong();
        int flags = buffer.getInt();
        short ehsize = buffer.getShort();
        short phentsize = buffer.getShort();
        short phnum = buffer.getShort();
        short shentsize = buffer.getShort();
        short shnum = buffer.getShort();
        short shstrndx = buffer.getShort();
        return new ElfHeader(type, machine, version, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx);
    }
}
