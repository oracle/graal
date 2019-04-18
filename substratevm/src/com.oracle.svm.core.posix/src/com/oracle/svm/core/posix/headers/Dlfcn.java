/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform.DARWIN_AND_JNI;
import org.graalvm.nativeimage.impl.InternalPlatform.LINUX_AND_JNI;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file dlfcn.h.
 */
@Platforms({DARWIN_AND_JNI.class, LINUX_AND_JNI.class})
@CContext(PosixDirectives.class)
@CLibrary("dl")
public class Dlfcn {

    /* The MODE argument to `dlopen' contains one of the following: */

    /** Lazy function call binding. */
    @CConstant
    public static native int RTLD_LAZY();

    /** Immediate function call binding. */
    @CConstant
    public static native int RTLD_NOW();

    /** Do not load the object. */
    @CConstant
    public static native int RTLD_NOLOAD();

    /**
     * If the following bit is set in the MODE argument to `dlopen', the symbols of the loaded
     * object and its dependencies are made visible as if the object were linked directly into the
     * program.
     */
    @CConstant
    public static native int RTLD_GLOBAL();

    /**
     * Unix98 demands the following flag which is the inverse to RTLD_GLOBAL. The implementation
     * does this by default and so we can define the value to zero.
     */
    @CConstant
    public static native int RTLD_LOCAL();

    /** Do not delete object when closed. */
    @CConstant
    public static native int RTLD_NODELETE();

    /**
     * If the first argument of `dlsym' or `dlvsym' is set to RTLD_NEXT the run-time address of the
     * symbol called NAME in the next shared object is returned. The "next" relation is defined by
     * the order the shared objects were loaded.
     */
    @CConstant
    public static native PointerBase RTLD_NEXT();

    /**
     * If the first argument to `dlsym' or `dlvsym' is set to RTLD_DEFAULT the run-time address of
     * the symbol called NAME in the global scope is returned.
     */
    @CConstant
    public static native PointerBase RTLD_DEFAULT();

    /**
     * Open the shared object FILE and map it in; return a handle that can be passed to `dlsym' to
     * get symbol values from it.
     */
    @CFunction
    public static native PointerBase dlopen(CCharPointer file, int mode);

    /**
     * Unmap and close a shared object opened by `dlopen'. The handle cannot be used again after
     * calling `dlclose'.
     */
    @CFunction
    public static native int dlclose(PointerBase handle);

    /**
     * Find the run-time address in the shared object HANDLE refers to of the symbol called NAME.
     */
    @CFunction
    public static native <T extends PointerBase> T dlsym(PointerBase handle, CCharPointer name);

    /** Like `dlopen', but request object to be allocated in a new namespace. */
    @CFunction
    public static native <T extends PointerBase> T dlmopen(long nsid, CCharPointer file, int mode);

    /**
     * Find the run-time address in the shared object HANDLE refers to of the symbol called NAME
     * with VERSION.
     */
    @CFunction
    public static native <T extends PointerBase> T dlvsym(PointerBase handle, CCharPointer name, CCharPointer version);

    /**
     * When any of the above functions fails, call this function to return a string describing the
     * error. Each call resets the error string so that a following call returns null.
     */
    @CFunction
    public static native CCharPointer dlerror();

    /** Structure containing information about object searched using `dladdr'. */
    @CStruct
    public interface Dl_info extends PointerBase {
        /** File name of defining object. */
        @CField
        CCharPointer dli_fname();

        /** Load address of that object. */
        @CField
        Pointer dli_fbase();

        /** Name of nearest symbol. */
        @CField
        CCharPointer dli_sname();

        /** Exact value of nearest symbol. */
        @CField
        Pointer dli_saddr();
    }

    /**
     * Fill in *INFO with the following information about ADDRESS. Returns 0 iff no shared object's
     * segments contain that address.
     */
    @CFunction
    public static native int dladdr(WordBase address, Dl_info info);
}
