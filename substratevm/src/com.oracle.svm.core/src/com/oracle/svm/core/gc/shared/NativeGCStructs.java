/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shared;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.code.CodeInfoPointer;

/** Data structures imported from GC-related header files. */
@CContext(NativeGCHeaderFiles.class)
public class NativeGCStructs {
    /* Data structures for frames that are currently on the stack. */

    @CStruct(addStructKeyword = true)
    public interface StackFrame extends PointerBase {
        StackFrame addressOf(long index);

        @CField("stack_pointer")
        void setStackPointer(Pointer value);

        @CField("encoded_reference_map")
        void setEncodedReferenceMap(CCharPointer value);

        @CField("reference_map_index")
        void setReferenceMapIndex(long value);
    }

    @CStruct(addStructKeyword = true)
    public interface StackFrames extends PointerBase {
        @CField
        long count();

        @CField("count")
        void setCount(long value);

        @CFieldAddress
        StackFrame frames();
    }

    @CStruct(addStructKeyword = true)
    public interface StackFramesPerThread extends PointerBase {
        @CField
        long count();

        @CField("count")
        void setCount(long value);

        @CFieldAddress
        StackFramesPointer threads();
    }

    @CPointerTo(StackFrames.class)
    public interface StackFramesPointer extends PointerBase {
        StackFramesPointer addressOf(long index);

        void write(StackFrames value);

        StackFrames read();
    }

    /* Data structures for JIT-compiled code that is currently on the stack. */

    @CStruct(addStructKeyword = true)
    public interface CodeInfos extends PointerBase {
        @CField
        long count();

        @CField("count")
        void setCount(long value);

        @CFieldAddress("code_infos")
        CodeInfoPointer codeInfos();
    }

    @CStruct(addStructKeyword = true)
    public interface CodeInfosPerThread extends PointerBase {
        @CField
        long count();

        @CField("count")
        void setCount(long value);

        @CFieldAddress
        CodeInfosPointer threads();
    }

    @CPointerTo(CodeInfos.class)
    public interface CodeInfosPointer extends PointerBase {
        CodeInfosPointer addressOf(long index);

        void write(CodeInfos value);

        CodeInfos read();
    }
}
