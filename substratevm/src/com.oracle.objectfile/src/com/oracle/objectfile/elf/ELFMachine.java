/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.objectfile.elf;

import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.RelocationMethod;
import com.oracle.objectfile.elf.ELFRelocationSection.ELFRelocationMethod;

/**
 * ELF machine type (incomplete). Each machine type also defines its set of relocation types.
 */
public enum ELFMachine/* implements Integral */ {
    NONE {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFDummyRelocation.class;
        }
    },
    X86_64 {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFX86_64Relocation.class;
        }
    },
    AArch64 {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFAArch64Relocation.class;
        }
    },
    CAVA {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFCavaRelocation.class;
        }
    };

    abstract Class<? extends Enum<? extends RelocationMethod>> relocationTypes();

    public static ELFMachine from(String s) {
        switch (s.toLowerCase()) {
            case "amd64":
            case "x86_64":
                return X86_64;
            case "arm64":
            case "aarch64":
                return AArch64;
        }
        throw new IllegalStateException("unknown CPU type: " + s);
    }

    public static ELFRelocationMethod getRelocation(ELFMachine m, RelocationKind k, int sizeInBytes) {
        switch (m) {
            case X86_64:
                switch (k) {
                    case DIRECT:
                        switch (sizeInBytes) {
                            case 8:
                                return ELFX86_64Relocation.R_64;
                            case 4:
                                return ELFX86_64Relocation.R_32;
                            case 2:
                                return ELFX86_64Relocation.R_16;
                            case 1:
                                return ELFX86_64Relocation.R_8;
                            default:
                                return ELFX86_64Relocation.R_NONE;
                        }
                    case PC_RELATIVE:
                        switch (sizeInBytes) {
                            case 8:
                                return ELFX86_64Relocation.R_PC64;
                            case 4:
                                return ELFX86_64Relocation.R_PC32;
                            case 2:
                                return ELFX86_64Relocation.R_PC16;
                            case 1:
                                return ELFX86_64Relocation.R_PC8;
                            default:
                                return ELFX86_64Relocation.R_NONE;
                        }
                    case PROGRAM_BASE:
                        switch (sizeInBytes) {
                            case 8:
                                return ELFX86_64Relocation.R_RELATIVE64;
                            case 4:
                                return ELFX86_64Relocation.R_RELATIVE;
                            default:
                                return ELFX86_64Relocation.R_NONE;
                        }
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("cannot map unknown relocation kind to an ELF x86-64 relocation type");
                }
            case AArch64:
                switch (k) {
                    case DIRECT:
                        switch (sizeInBytes) {
                            case 8:
                                return ELFAArch64Relocation.R_AARCH64_ABS64;
                            case 4:
                                return ELFAArch64Relocation.R_AARCH64_ABS32;
                            case 2:
                                return ELFAArch64Relocation.R_AARCH64_ABS16;
                            case 1:
                            default:
                                return ELFAArch64Relocation.R_AARCH64_NONE;
                        }
                    case AARCH64_R_MOVW_UABS_G0:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G0;
                    case AARCH64_R_MOVW_UABS_G0_NC:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G0_NC;
                    case AARCH64_R_MOVW_UABS_G1:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G1;
                    case AARCH64_R_MOVW_UABS_G1_NC:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G1_NC;
                    case AARCH64_R_MOVW_UABS_G2:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G2;
                    case AARCH64_R_MOVW_UABS_G2_NC:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G2_NC;
                    case AARCH64_R_MOVW_UABS_G3:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G3;
                    case AARCH64_R_AARCH64_ADR_PREL_PG_HI21:
                        return ELFAArch64Relocation.R_AARCH64_ADR_PREL_PG_HI21;
                    case AARCH64_R_AARCH64_LDST128_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST128_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_ADD_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_ADD_ABS_LO12_NC;
                    case AARCH64_R_GOT_LD_PREL19:
                        return ELFAArch64Relocation.R_AARCH64_GOT_LD_PREL19;
                    case AARCH64_R_LD_PREL_LO19:
                        return ELFAArch64Relocation.R_AARCH64_LD_PREL_LO19;
                    case AARCH64_R_AARCH64_LDST64_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST64_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_LDST32_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST32_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_LDST16_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST16_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_LDST8_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST8_ABS_LO12_NC;
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("cannot map unknown relocation kind to an ELF aarch64 relocation type: " + k);

                }
            case CAVA:
                switch (k) {
                    case DIRECT_LO:
                        switch (sizeInBytes) {
                            case 2:
                                return ELFCavaRelocation.R_CAVA_OFFSET;
                            default:
                                throw new RuntimeException(Integer.toString(sizeInBytes));
                        }
                    case DIRECT_HI:
                        switch (sizeInBytes) {
                            case 2:
                                return ELFCavaRelocation.R_CAVA_HIGH;
                            default:
                                throw new RuntimeException(Integer.toString(sizeInBytes));
                        }
                    default:
                        throw new RuntimeException(k.toString());
                }
            default:
            case NONE:
                return ELFDummyRelocation.R_NONE;
        }
    }

    // TODO: use explicit enum values
    public static ELFMachine from(int m) {
        switch (m) {
            case 0:
                return NONE;
            case 62:
                return X86_64;
            case 0xB7:
                return AArch64;
            default:
                throw new IllegalStateException("unknown ELF machine type");
        }
    }

    public short toShort() {
        if (this == NONE) {
            return (short) 0;
        } else if (this == AArch64) {
            return 0xB7;
        } else if (this == X86_64) {
            return 62;
        } else if (this == AArch64) {
            return (short) 0xB7;
        } else if (this == CAVA) {
            return (short) 0xcafe;
        } else {
            throw new IllegalStateException("should not reach here");
        }
    }

    public static ELFMachine getSystemNativeValue() {
        if (System.getProperty("os.arch").equals("aarch64")) {
            return AArch64;
        } else {
            return X86_64;
        }
    }
}

enum ELFDummyRelocation implements ELFRelocationMethod {
    R_NONE;

    @Override
    public RelocationKind getKind() {
        return RelocationKind.UNKNOWN;
    }

    @Override
    public boolean canUseExplicitAddend() {
        return true;
    }

    @Override
    public boolean canUseImplicitAddend() {
        return true;
    }

    @Override
    public long toLong() {
        return ordinal();
    }

    @Override
    public int getRelocatedByteSize() {
        throw new UnsupportedOperationException();
    }
}

enum ELFX86_64Relocation implements ELFRelocationMethod {
    // These are all named R_X86_64_... in elf.h,
    // but we just use R_... to keep it short.
    // We need the "R_" because some begin with digits.
    R_NONE,
    R_64 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 8;
        }
    },
    R_PC32 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.PC_RELATIVE;
        }

        @Override
        public int getRelocatedByteSize() {
            return 4;
        }
    },
    R_GOT32,
    R_PLT32,
    R_COPY,
    R_GLOB_DAT,
    R_JUMP_SLOT,
    R_RELATIVE {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.PROGRAM_BASE;
        }

        @Override
        public int getRelocatedByteSize() {
            return 8;
        }

    },
    R_GOTPCREL,
    R_32 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 4;
        }
    },
    R_32S,
    R_16 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 2;
        }
    },
    R_PC16 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.PC_RELATIVE;
        }

        @Override
        public int getRelocatedByteSize() {
            return 2;
        }
    },
    R_8 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 1;
        }
    },
    R_PC8 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.PC_RELATIVE;
        }

        @Override
        public int getRelocatedByteSize() {
            return 1;
        }
    },
    R_DTPMOD64,
    R_DTPOFF64,
    R_TPOFF64,
    R_TLSGD,
    R_TLSLD,
    R_DTPOFF32,
    R_GOTTPOFF,
    R_TPOFF32,
    R_PC64 {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.PC_RELATIVE;
        }

        @Override
        public int getRelocatedByteSize() {
            return 8;
        }
    },
    R_GOTOFF64,
    R_GOTPC32,
    R_GOT64,
    R_GOTPCREL64,
    R_GOTPC64,
    R_GOTPLT64,
    R_PLTOFF64,
    R_SIZE32,
    R_SIZE64,
    R_GOTPC32_TLSDESC,
    R_TLSDESC_CALL,
    R_TLSDESC,
    R_IRELATIVE,
    R_RELATIVE64,
    R_COUNT;

    static {
        // spot check
        assert R_COUNT.ordinal() == 39;
    }

    @Override
    public RelocationKind getKind() {
        return RelocationKind.UNKNOWN;
    }

    /*
     * x86-64 relocs always use explicit addends.
     */
    @Override
    public boolean canUseExplicitAddend() {
        return true;
    }

    @Override
    public boolean canUseImplicitAddend() {
        return false;
    }

    @Override
    public long toLong() {
        return ordinal();
    }

    @Override
    public int getRelocatedByteSize() {
        throw new UnsupportedOperationException(); // better safe than sorry
    }
}

/**
 * Reference: https://developer.arm.com/docs/ihi0056/latest.
 */
enum ELFAArch64Relocation implements ELFRelocationMethod {
    R_AARCH64_NONE(0),
    R_AARCH64_ABS64(0x101) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 8;
        }
    },
    R_AARCH64_ABS32(0x102) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 4;
        }
    },
    R_AARCH64_ABS16(0x103) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 2;
        }
    },
    R_AARCH64_PREL64(0x104),
    R_AARCH64_PREL32(0x105),
    R_AARCH64_PREL16(0x106),
    R_AARCH64_MOVW_UABS_G0(0x107) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 2;
        }
    },
    R_AARCH64_MOVW_UABS_G0_NC(0x108) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 2;
        }
    },
    R_AARCH64_MOVW_UABS_G1(0x109) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 4;
        }
    },
    R_AARCH64_MOVW_UABS_G1_NC(0x10a) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 4;
        }
    },
    R_AARCH64_MOVW_UABS_G2(0x10b) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 6;
        }
    },
    R_AARCH64_MOVW_UABS_G2_NC(0x10c) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 6;
        }
    },
    R_AARCH64_MOVW_UABS_G3(0x10d) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.DIRECT;
        }

        @Override
        public int getRelocatedByteSize() {
            return 8;
        }
    },
    R_AARCH64_MOVW_SABS_G0(0x10e),
    R_AARCH64_MOVW_SABS_G1(0x10f),
    R_AARCH64_MOVW_SABS_G2(0x110),
    R_AARCH64_LD_PREL_LO19(0x111) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.PC_RELATIVE;
        }

        @Override
        public int getRelocatedByteSize() {
            return 8; // no, 19 bits
        }
    },
    R_AARCH64_ADR_PREL_LO21(0x112),
    R_AARCH64_ADR_PREL_PG_HI21(0x113) {
        @Override
        public RelocationKind getKind() {
            return RelocationKind.PC_RELATIVE;
        }

        @Override
        public int getRelocatedByteSize() {
            return 8; // no, 21 bits
        }
    },
    R_AARCH64_ADR_PREL_PG_HI21_NC(0x114),
    R_AARCH64_ADD_ABS_LO12_NC(0x115),
    R_AARCH64_LDST8_ABS_LO12_NC(0x116),
    R_AARCH64_TSTBR14(0x117),
    R_AARCH64_CONDBR19(0x118),
    R_AARCH64_JUMP26(0x11a),
    R_AARCH64_CALL26(0x11b),
    R_AARCH64_LDST16_ABS_LO12_NC(0x11c),
    R_AARCH64_LDST32_ABS_LO12_NC(0x11d),
    R_AARCH64_LDST64_ABS_LO12_NC(0x11e),
    R_AARCH64_MOVW_PREL_G0(0x11f),
    R_AARCH64_MOVW_PREL_G0_NC(0x120),
    R_AARCH64_MOVW_PREL_G1(0x121),
    R_AARCH64_MOVW_PREL_G1_NC(0x122),
    R_AARCH64_MOVW_PREL_G2(0x123),
    R_AARCH64_MOVW_PREL_G2_NC(0x124),
    R_AARCH64_MOVW_PREL_G3(0x125),
    R_AARCH64_LDST128_ABS_LO12_NC(0x12b),
    R_AARCH64_MOVW_GOTOFF_G0(0x12c),
    R_AARCH64_MOVW_GOTOFF_G0_NC(0x12d),
    R_AARCH64_MOVW_GOTOFF_G1(0x12e),
    R_AARCH64_MOVW_GOTOFF_G1_NC(0x12f),
    R_AARCH64_MOVW_GOTOFF_G2(0x130),
    R_AARCH64_MOVW_GOTOFF_G2_NC(0x131),
    R_AARCH64_MOVW_GOTOFF_G3(0x132),
    R_AARCH64_GOTREL64(0x133),
    R_AARCH64_GOTREL32(0x134),
    R_AARCH64_GOT_LD_PREL19(0x135),
    R_AARCH64_LD64_GOTOFF_LO15(0x136),
    R_AARCH64_ADR_GOT_PAGE(0x137),
    R_AARCH64_LD64_GOT_LO12_NC(0x138),
    R_AARCH64_LD64_GOTPAGE_LO15(0x139),
    R_AARCH64_TLSGD_ADR_PREL21(0x200),
    R_AARCH64_TLSGD_ADR_PAGE21(0x201),
    R_AARCH64_TLSGD_ADD_LO12_NC(0x202),
    R_AARCH64_TLSGD_MOVW_G1(0x203),
    R_AARCH64_TLSGD_MOVW_G0_NC(0x204),
    R_AARCH64_TLSLD_ADR_PREL21(0x205),
    R_AARCH64_TLSLD_ADR_PAGE21(0x206),
    R_AARCH64_TLSLD_ADD_LO12_NC(0x207),
    R_AARCH64_TLSLD_MOVW_G1(0x208),
    R_AARCH64_TLSLD_MOVW_G0_NC(0x209),
    R_AARCH64_TLSLD_LD_PREL19(0x20a),
    R_AARCH64_TLSLD_MOVW_DTPREL_G2(0x20b),
    R_AARCH64_TLSLD_MOVW_DTPREL_G1(0x20c),
    R_AARCH64_TLSLD_MOVW_DTPREL_G1_NC(0x20d),
    R_AARCH64_TLSLD_MOVW_DTPREL_G0(0x20e),
    R_AARCH64_TLSLD_MOVW_DTPREL_G0_NC(0x20f),
    R_AARCH64_TLSLD_ADD_DTPREL_HI12(0x210),
    R_AARCH64_TLSLD_ADD_DTPREL_LO12(0x211),
    R_AARCH64_TLSLD_ADD_DTPREL_LO12_NC(0x212),
    R_AARCH64_TLSLD_LDST8_DTPREL_LO12(0x213),
    R_AARCH64_TLSLD_LDST8_DTPREL_LO12_NC(0x214),
    R_AARCH64_TLSLD_LDST16_DTPREL_LO12(0x215),
    R_AARCH64_TLSLD_LDST16_DTPREL_LO12_NC(0x216),
    R_AARCH64_TLSLD_LDST32_DTPREL_LO12(0x217),
    R_AARCH64_TLSLD_LDST32_DTPREL_LO12_NC(0x218),
    R_AARCH64_TLSLD_LDST64_DTPREL_LO12(0x219),
    R_AARCH64_TLSLD_LDST64_DTPREL_LO12_NC(0x21a),
    R_AARCH64_TLSIE_MOVW_GOTTPREL_G1(0x21b),
    R_AARCH64_TLSIE_MOVW_GOTTPREL_G0_NC(0x21c),
    R_AARCH64_TLSIE_ADR_GOTTPREL_PAGE21(0x21d),
    R_AARCH64_TLSIE_LD64_GOTTPREL_LO12_NC(0x21e),
    R_AARCH64_TLSIE_LD_GOTTPREL_PREL19(0x21f),
    R_AARCH64_TLSLE_MOVW_TPREL_G2(0x220),
    R_AARCH64_TLSLE_MOVW_TPREL_G1(0x221),
    R_AARCH64_TLSLE_MOVW_TPREL_G1_NC(0x222),
    R_AARCH64_TLSLE_MOVW_TPREL_G0(0x223),
    R_AARCH64_TLSLE_MOVW_TPREL_G0_NC(0x224),
    R_AARCH64_TLSLE_ADD_TPREL_HI12(0x225),
    R_AARCH64_TLSLE_ADD_TPREL_LO12(0x226),
    R_AARCH64_TLSLE_ADD_TPREL_LO12_NC(0x227),
    R_AARCH64_TLSLE_LDST8_TPREL_LO12(0x228),
    R_AARCH64_TLSLE_LDST8_TPREL_LO12_NC(0x229),
    R_AARCH64_TLSLE_LDST16_TPREL_LO12(0x22a),
    R_AARCH64_TLSLE_LDST16_TPREL_LO12_NC(0x22b),
    R_AARCH64_TLSLE_LDST32_TPREL_LO12(0x22c),
    R_AARCH64_TLSLE_LDST32_TPREL_LO12_NC(0x22d),
    R_AARCH64_TLSLE_LDST64_TPREL_LO12(0x22e),
    R_AARCH64_TLSLE_LDST64_TPREL_LO12_NC(0x22f),
    R_AARCH64_TLSDESC_LD_PREL19(0x230),
    R_AARCH64_TLSDESC_ADR_PREL21(0x231),
    R_AARCH64_TLSDESC_ADR_PAGE21(0x232),
    R_AARCH64_TLSDESC_LD64_LO12_NC(0x233),
    R_AARCH64_TLSDESC_ADD_LO12_NC(0x234),
    R_AARCH64_TLSDESC_OFF_G1(0x235),
    R_AARCH64_TLSDESC_OFF_G0_NC(0x236),
    R_AARCH64_TLSDESC_LDR(0x237),
    R_AARCH64_TLSDESC_ADD(0x238),
    R_AARCH64_TLSDESC_CALL(0x239),
    R_AARCH64_TLSLE_LDST128_TPREL_LO12(0x23a),
    R_AARCH64_TLSLE_LDST128_TPREL_LO12_NC(0x23b),
    R_AARCH64_TLSLD_LDST128_DTPREL_LO12(0x23c),
    R_AARCH64_TLSLD_LDST128_DTPREL_LO12_NC(0x23d),
    R_AARCH64_COPY(0x400),
    R_AARCH64_GLOB_DAT(0x401),
    R_AARCH64_JUMP_SLOT(0x402),
    R_AARCH64_RELATIVE(0x403),
    R_AARCH64_TLS_DTPREL64(0x404),
    R_AARCH64_TLS_DTPMOD64(0x405),
    R_AARCH64_TLS_TPREL64(0x406),
    R_AARCH64_TLSDESC(0x407),
    R_AARCH64_IRELATIVE(0x408);

    private final long code;

    ELFAArch64Relocation(long code) {
        this.code = code;
    }

    @Override
    public RelocationKind getKind() {
        return RelocationKind.UNKNOWN;
    }

    @Override
    public boolean canUseImplicitAddend() {
        return false;
    }

    @Override
    public boolean canUseExplicitAddend() {
        return true;
    }

    @Override
    public int getRelocatedByteSize() {
        throw new UnsupportedOperationException(); // better safe than sorry
    }

    @Override
    public long toLong() {
        return code;
    }
}

enum ELFCavaRelocation implements ELFRelocationMethod {
    R_CAVA_16(0),
    R_CAVA_24(1),
    R_CAVA_32(2),
    R_CAVA_64(3),
    R_CAVA_AOP(4),
    R_CAVA_BOP(5),
    R_CAVA_COP(6),
    R_CAVA_QOP(7),
    R_CAVA_SOP(8),
    R_CAVA_TOP(9),
    R_CAVA_A(10),
    R_CAVA_B(11),
    R_CAVA_C(12),
    R_CAVA_CALL(13),
    R_CAVA_D(14),
    R_CAVA_GOTO(15),
    R_CAVA_HIGH(16),
    R_CAVA_HIGHER(17),
    R_CAVA_HIGHEST(18),
    R_CAVA_OFFSET(19),
    R_CAVA_SA5(20),
    R_CAVA_SA6(21),
    R_CAVA_SCALE(22),
    R_CAVA_SCMP5(23),
    R_CAVA_SIMM16(24),
    R_CAVA_TARGET(25),
    R_CAVA_UCMP5(26),
    R_CAVA_UCST5(27),
    R_CAVA_UIMM10(28),
    R_CAVA_UIMM16(29),
    R_CAVA_UIMM16A(30),
    R_CAVA_UIMM16B(31),
    R_CAVA_UIMM16C(32),
    R_CAVA_UIMM16D(33),
    R_CAVA_UIMM5(34),
    R_CAVA_VTABLE_INHERIT(35),
    R_CAVA_VTABLE_ENTRY(36);

    private final int id;

    @Override
    public long toLong() {
        return id;
    }

    @Override
    public RelocationKind getKind() {
        return RelocationKind.UNKNOWN;
    }

    @Override
    public boolean canUseImplicitAddend() {
        /*
         * after some tests it seems that the implicit addend is always used, even if there is an
         * explicit one so we must be careful with what we leave there. In particular, don't leave
         * some dead beef or old cafe!
         */
        return true;
    }

    @Override
    public boolean canUseExplicitAddend() {
        return true;
    }

    @Override
    public int getRelocatedByteSize() {
        return -1;
    }

    ELFCavaRelocation(int id) {
        this.id = id;
    }
}
