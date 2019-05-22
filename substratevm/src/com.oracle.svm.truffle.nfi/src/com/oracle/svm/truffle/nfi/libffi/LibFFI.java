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
package com.oracle.svm.truffle.nfi.libffi;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

//Checkstyle: stop

@CContext(LibFFIHeaderDirectives.class)
@CLibrary(value = "ffi", requireStatic = true)
public class LibFFI {

    @CPointerTo(ffi_type.class)
    public interface ffi_type_array extends PointerBase {

        ffi_type read(int index);

        void write(int index, ffi_type value);
    }

    @CStruct("ffi_type")
    public interface ffi_type extends PointerBase {

        @CField
        UnsignedWord size();

        @CField
        short alignment();
    }

    @CStruct("ffi_arg")
    public interface ffi_arg extends PointerBase {
    }

    @CStruct("ffi_cif")
    public interface ffi_cif extends PointerBase {

        @CField
        int nargs();

        @CField
        ffi_type_array arg_types();
    }

    @CStruct("ffi_closure")
    public interface ffi_closure extends PointerBase {
    }

    public interface ffi_closure_callback extends CFunctionPointer {
    }

    @CConstant
    public static native int FFI_OK();

    @CConstant
    public static native int FFI_DEFAULT_ABI();

    @CFunction
    public static native int ffi_prep_cif(ffi_cif cif, int abi, UnsignedWord nargs, ffi_type ret, ffi_type_array args);

    @CFunction
    public static native int ffi_prep_cif_var(ffi_cif cif, int abi, UnsignedWord nFixedArgs, UnsignedWord nargs, ffi_type ret, ffi_type_array args);

    @CFunction
    public static native void ffi_call(ffi_cif cif, PointerBase fn, PointerBase rvalue, WordPointer avalue);

    @CFunction
    public static native <T extends WordBase> T ffi_closure_alloc(UnsignedWord size, WordPointer code);

    @CFunction
    public static native void ffi_closure_free(PointerBase closure);

    @CFunction
    public static native int ffi_prep_closure_loc(ffi_closure closure, ffi_cif cif, ffi_closure_callback fn, WordBase user_data, PointerBase code_loc);
}
