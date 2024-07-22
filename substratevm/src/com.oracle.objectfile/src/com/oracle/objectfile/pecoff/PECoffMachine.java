/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile.pecoff;

import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.RelocationMethod;
import com.oracle.objectfile.pecoff.PECoff.IMAGE_FILE_HEADER;
import com.oracle.objectfile.pecoff.PECoff.IMAGE_RELOCATION;
import com.oracle.objectfile.pecoff.PECoffRelocationTable.PECoffRelocationMethod;

/**
 * PECoff machine type (incomplete). Each machine type also defines its set of relocation types.
 */
public enum PECoffMachine/* implements Integral */ {
    X86_64 {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return PECoffX86_64Relocation.class;
        }
    };

    abstract Class<? extends Enum<? extends RelocationMethod>> relocationTypes();

    public static PECoffRelocationMethod getRelocation(PECoffMachine m, RelocationKind k) {
        switch (m) {
            case X86_64:
                switch (k) {
                    case DIRECT_8:
                        return PECoffX86_64Relocation.ADDR64;
                    case DIRECT_4:
                        return PECoffX86_64Relocation.ADDR32;
                    case PC_RELATIVE_4:
                        return PECoffX86_64Relocation.REL32;
                    case ADDR32NB_4:
                        return PECoffX86_64Relocation.ADDR32NB;
                    case SECTION_2:
                        return PECoffX86_64Relocation.SECTION;
                    case SECREL_4:
                        return PECoffX86_64Relocation.SECREL;
                    case UNKNOWN:
                    default:
                        throw new IllegalArgumentException("Cannot map unknown relocation kind to an PECoff x86-64 relocation type");
                }
            default:
                throw new IllegalStateException("Unknown PECoff machine type");
        }
    }

    public static PECoffMachine from(int m) {
        switch (m) {
            case IMAGE_FILE_HEADER.IMAGE_FILE_MACHINE_AMD64:
                return X86_64;
            default:
                throw new IllegalStateException("Unknown PECoff machine type");
        }
    }

    public short toShort() {
        if (this == X86_64) {
            return (short) IMAGE_FILE_HEADER.IMAGE_FILE_MACHINE_AMD64;
        } else {
            throw new IllegalStateException("Should not reach here");
        }
    }

    public static PECoffMachine getSystemNativeValue() {
        String arch = System.getProperty("os.arch");
        return switch (arch) {
            case "amd64", "x86_64" -> X86_64;
            default -> throw new IllegalArgumentException("Unsupported PECoff machine type: " + arch);
        };
    }
}

/*-
 *
 * IMAGE_REL_AMD64_ABSOLUTE 0x0000 // Reference is absolute, no relocation is necessary
 * IMAGE_REL_AMD64_ADDR64 0x0001   // 64-bit address (VA).
 * IMAGE_REL_AMD64_ADDR32 0x0002   // 32-bit address (VA).
 * IMAGE_REL_AMD64_ADDR32NB 0x0003 // 32-bit address w/o image base (RVA).
 * IMAGE_REL_AMD64_REL32 0x0004    // 32-bit relative address from byte following reloc
 * IMAGE_REL_AMD64_REL32_1 0x0005  // 32-bit relative address from byte distance 1 from reloc
 * IMAGE_REL_AMD64_REL32_2 0x0006  // 32-bit relative address from byte distance 2 from reloc
 * IMAGE_REL_AMD64_REL32_3 0x0007  // 32-bit relative address from byte distance 3 from reloc
 * IMAGE_REL_AMD64_REL32_4 0x0008  // 32-bit relative address from byte distance 4 from reloc
 * IMAGE_REL_AMD64_REL32_5 0x0009  // 32-bit relative address from byte distance 5 from reloc
 * IMAGE_REL_AMD64_SECTION 0x000A  // Section index
 * IMAGE_REL_AMD64_SECREL 0x000B   // 32 bit offset from base of section containing target
 * IMAGE_REL_AMD64_SECREL7 0x000C  // 7 bit unsigned offset from base of section containing target
 * IMAGE_REL_AMD64_TOKEN 0x000D    // 32 bit metadata token
 * IMAGE_REL_AMD64_SREL32 0x000E   // 32 bit signed span-dependent value emitted into object
 * IMAGE_REL_AMD64_PAIR 0x000F IMAGE_REL_AMD64_SSPAN32 0x0010 // 32 bit signed span-dependent value applied at link time
 */
enum PECoffX86_64Relocation implements PECoffRelocationMethod {
    ADDR64 {
        @Override
        public long toLong() {
            return IMAGE_RELOCATION.IMAGE_REL_AMD64_ADDR64;
        }
    },
    ADDR32 {
        @Override
        public long toLong() {
            return IMAGE_RELOCATION.IMAGE_REL_AMD64_ADDR32;
        }
    },
    ADDR32NB {
        @Override
        public long toLong() {
            return IMAGE_RELOCATION.IMAGE_REL_AMD64_ADDR32NB;
        }
    },
    REL32 {
        @Override
        public long toLong() {
            return IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
        }
    },
    SECTION {
        @Override
        public long toLong() {
            return IMAGE_RELOCATION.IMAGE_REL_AMD64_SECTION;
        }
    },
    SECREL {
        @Override
        public long toLong() {
            return IMAGE_RELOCATION.IMAGE_REL_AMD64_SECREL;
        }
    };
}
