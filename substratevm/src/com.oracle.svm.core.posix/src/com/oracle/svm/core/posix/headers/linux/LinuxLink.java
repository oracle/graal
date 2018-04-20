/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.posix.headers.linux;

//Checkstyle: stop

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.posix.headers.PosixDirectives;

@CContext(PosixDirectives.class)
@Platforms(Platform.LINUX.class)
public class LinuxLink {

    /** Information on a shared object */
    @CStruct(addStructKeyword = true)
    public interface dl_phdr_info extends PointerBase {
        @CField
        PointerBase dlpi_addr();

        @CField
        CCharPointer dlpi_name();

        @CField
        Elf64_Phdr dlpi_phdr();

        @CField
        short dlpi_phnum(); // unsigned two-byte integer
    }

    /** Program header for an ELF section */
    @CStruct
    public interface Elf64_Phdr extends PointerBase {
        /** Virtual address */
        @CField
        PointerBase p_vaddr();

        /** Offset in file */
        @CField
        PointerBase p_offset();

        /** Size in memory */
        @CField
        UnsignedWord p_memsz();
    }

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int dl_iterate_phdr(CFunctionPointer callback, PointerBase data);
    }
}
