/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
    CAVA {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFCavaRelocation.class;
        }
    };

    abstract Class<? extends Enum<? extends RelocationMethod>> relocationTypes();

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
            default:
                throw new IllegalStateException("unknown ELF machine type");
        }
    }

    public short toShort() {
        if (this == NONE) {
            return (short) 0;
        } else if (this == X86_64) {
            return 62;
        } else if (this == CAVA) {
            return (short) 0xcafe;
        } else {
            throw new IllegalStateException("should not reach here");
        }
    }

    public static ELFMachine getSystemNativeValue() {
        return X86_64; // FIXME: query system
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
