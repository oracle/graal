/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers.darwin;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.posix.headers.PosixDirectives;

// Checkstyle: stop

@CContext(PosixDirectives.class)
public class DarwinVirtualMemory {

    @CStruct("vm_region_basic_info_data_64_t")
    public interface vm_region_basic_info_data_64_t extends PointerBase {
        @CField
        int protection();
    }

    @CConstant
    public static native int VM_PROT_READ();

    @CConstant
    public static native int VM_PROT_WRITE();

    @CConstant
    public static native int VM_REGION_BASIC_INFO_64();

    @CConstant
    public static native int VM_REGION_SUBMAP_INFO_COUNT_64();

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native WordPointer mach_task_self();

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int vm_copy(WordPointer targetTask, WordBase sourceAddress, UnsignedWord count, WordBase destAddress);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int mach_vm_region(WordPointer task, WordPointer address, WordPointer size, int flavor, vm_region_basic_info_data_64_t info, CIntPointer infoCnt,
                    WordPointer object_name);
}
