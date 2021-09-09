/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;

import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_closure;
import com.oracle.svm.truffle.nfi.libffi.LibFFIHeaderDirectives;

public class LibFFI {

    @CContext(LibFFIHeaderDirectives.class)
    @CStruct("svm_closure_data")
    public interface ClosureData extends PointerBase {

        @CFieldAddress
        ffi_closure ffiClosure();

        @CField
        LibFFI.NativeClosureHandle nativeClosureHandle();

        @CField("nativeClosureHandle")
        void setNativeClosureHandle(LibFFI.NativeClosureHandle handle);

        @CField
        Isolate isolate();

        @CField("isolate")
        void setIsolate(Isolate isolate);

        @CField
        int envArgIdx();

        @CField("envArgIdx")
        void setEnvArgIdx(int envArgIdx);
    }

    interface NativeClosureHandle extends ComparableWord {
    }

}
