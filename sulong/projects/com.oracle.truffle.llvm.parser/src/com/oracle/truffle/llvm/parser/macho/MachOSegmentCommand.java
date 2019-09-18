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

public final class MachOSegmentCommand extends MachOLoadCommand {
    public static final int SEGNAME_SIZE = 16;
    public static final int SECTNAME_SIZE = 16;

    private final String segName;
    private final long vmAddr;
    private final long vmSize;
    private final long fileOff;
    private final long fileSize;
    private final int maxProt;
    private final int initProt;
    private final int nSects;
    private final int flags;

    private final MachOSection[] sections;
    private final int cmdOffset;

    private MachOSegmentCommand(int cmd, int cmdSize, String segName, long vmAddr, long vmSize, long fileOff, long fileSize, int maxProt, int initProt, int nSects, int flags,
                    MachOSection[] sections, int cmdOffset) {
        super(cmd, cmdSize);
        this.segName = segName;
        this.vmAddr = vmAddr;
        this.vmSize = vmSize;
        this.fileOff = fileOff;
        this.fileSize = fileSize;
        this.maxProt = maxProt;
        this.initProt = initProt;
        this.nSects = nSects;
        this.flags = flags;

        this.sections = sections;
        this.cmdOffset = cmdOffset;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + getSegName() + ">";
    }

    public int getOffset() {
        return cmdOffset;
    }

    public MachOSection getSection(String sectName) {
        for (MachOSection sec : sections) {
            if (sec.getSectName().equals(sectName)) {
                return sec;
            }
        }
        return null;
    }

    public String getSegName() {
        return segName;
    }

    public long getVmAddr() {
        return vmAddr;
    }

    public long getVmSize() {
        return vmSize;
    }

    public long getFileOff() {
        return fileOff;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getMaxProt() {
        return maxProt;
    }

    public int getInitProt() {
        return initProt;
    }

    public int getnSects() {
        return nSects;
    }

    public int getFlags() {
        return flags;
    }

    public static MachOSegmentCommand create(MachOReader reader) {
        if (reader.is64Bit()) {
            return readSegmentCmd64(reader);
        } else {
            return readSegmentCmd32(reader);
        }
    }

    private static MachOSegmentCommand readSegmentCmd64(MachOReader buffer) {
        int cmdOffset = buffer.getPosition();

        int cmd = buffer.getInt();
        int cmdSize = buffer.getInt();
        String segName = getString(buffer, MachOSegmentCommand.SEGNAME_SIZE);
        long vmAddr = buffer.getLong();
        long vmSize = buffer.getLong();
        long fileOff = buffer.getLong();
        long fileSize = buffer.getLong();
        int maxProt = buffer.getInt();
        int initProt = buffer.getInt();
        int nSects = buffer.getInt();
        int flags = buffer.getInt();

        MachOSection[] sections = new MachOSection[nSects];
        for (int i = 0; i < nSects; i++) {
            sections[i] = MachOSection.readSection64(buffer);
        }

        return new MachOSegmentCommand(cmd, cmdSize, segName, vmAddr, vmSize, fileOff, fileSize, maxProt, initProt, nSects, flags, sections, cmdOffset);
    }

    private static MachOSegmentCommand readSegmentCmd32(MachOReader buffer) {
        int cmdOffset = buffer.getPosition();

        int cmd = buffer.getInt();
        int cmdSize = buffer.getInt();
        String segName = getString(buffer, MachOSegmentCommand.SEGNAME_SIZE);
        long vmAddr = Integer.toUnsignedLong(buffer.getInt());
        long vmSize = Integer.toUnsignedLong(buffer.getInt());
        long fileOff = Integer.toUnsignedLong(buffer.getInt());
        long fileSize = Integer.toUnsignedLong(buffer.getInt());
        int maxProt = buffer.getInt();
        int initProt = buffer.getInt();
        int nSects = buffer.getInt();
        int flags = buffer.getInt();

        MachOSection[] sections = new MachOSection[nSects];
        for (int i = 0; i < nSects; i++) {
            sections[i] = MachOSection.readSection32(buffer);
        }

        return new MachOSegmentCommand(cmd, cmdSize, segName, vmAddr, vmSize, fileOff, fileSize, maxProt, initProt, nSects, flags, sections, cmdOffset);
    }

    public static final class MachOSection {
        private final String sectName;
        private final String segName;
        private final long addr;
        private final long size;
        private final int offset;
        private final int align;
        private final int relOff;
        private final int nReloc;
        private final int flags;
        private final int reserved1;
        private final int reserved2;
        private final int reserved3;

        private final int cmdOffset;

        private MachOSection(String sectName, String segName, long addr, long size, int offset, int align, int relOff, int nReloc, int flags, int reserved1, int reserved2, int reserved3,
                        int cmdOffset) {
            this.sectName = sectName;
            this.segName = segName;
            this.addr = addr;
            this.size = size;
            this.offset = offset;
            this.align = align;
            this.relOff = relOff;
            this.nReloc = nReloc;
            this.flags = flags;
            this.reserved1 = reserved1;
            this.reserved2 = reserved2;
            this.reserved3 = reserved3;

            this.cmdOffset = cmdOffset;
        }

        public String getSectName() {
            return sectName;
        }

        public String getSegName() {
            return segName;
        }

        public long getAddr() {
            return addr;
        }

        public long getSize() {
            return size;
        }

        public int getOffset() {
            return offset;
        }

        public int getAlign() {
            return align;
        }

        public int getRelOff() {
            return relOff;
        }

        public int getnReloc() {
            return nReloc;
        }

        public int getFlags() {
            return flags;
        }

        public int getReserved1() {
            return reserved1;
        }

        public int getReserved2() {
            return reserved2;
        }

        public int getReserved3() {
            return reserved3;
        }

        public int getCmdOffset() {
            return cmdOffset;
        }

        private static MachOSection readSection32(MachOReader buffer) {
            int cmdOffset = buffer.getPosition();

            String sectName = getString(buffer, MachOSegmentCommand.SECTNAME_SIZE);
            String segName = getString(buffer, MachOSegmentCommand.SEGNAME_SIZE);
            long addr = Integer.toUnsignedLong(buffer.getInt());
            long size = Integer.toUnsignedLong(buffer.getInt());
            int offset = buffer.getInt();
            int align = buffer.getInt();
            int relOff = buffer.getInt();
            int nReloc = buffer.getInt();
            int flags = buffer.getInt();
            int reserved1 = buffer.getInt();
            int reserved2 = buffer.getInt();

            return new MachOSection(sectName, segName, addr, size, offset, align, relOff, nReloc, flags, reserved1, reserved2, 0, cmdOffset);
        }

        private static MachOSection readSection64(MachOReader buffer) {
            int cmdOffset = buffer.getPosition();

            String sectName = getString(buffer, MachOSegmentCommand.SECTNAME_SIZE);
            String segName = getString(buffer, MachOSegmentCommand.SEGNAME_SIZE);
            long addr = buffer.getLong();
            long size = buffer.getLong();
            int offset = buffer.getInt();
            int align = buffer.getInt();
            int relOff = buffer.getInt();
            int nReloc = buffer.getInt();
            int flags = buffer.getInt();
            int reserved1 = buffer.getInt();
            int reserved2 = buffer.getInt();
            int reserved3 = buffer.getInt();

            return new MachOSection(sectName, segName, addr, size, offset, align, relOff, nReloc, flags, reserved1, reserved2, reserved3, cmdOffset);
        }
    }

}
