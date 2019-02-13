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
package com.oracle.svm.core.posix.headers.linux;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.core.posix.headers.PosixDirectives;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file dlfcn.h.
 */
@CContext(PosixDirectives.class)
@CLibrary("dl")
@Platforms(Platform.LINUX.class)
public class LinuxDlfcn extends Dlfcn {

    /** Mask of binding time value. */
    @CConstant
    public static native int RTLD_BINDING_MASK();

    /** Use deep binding. */
    @CConstant
    public static native int RTLD_DEEPBIND();

    /* Special namespace ID values. */

    /** Initial namespace. */
    @CConstant
    public static native int LM_ID_BASE();

    /** For dlmopen: request new namespace. */
    @CConstant
    public static native int LM_ID_NEWLM();

    /** Same as `dladdr', but additionally sets *EXTRA_INFO according to FLAGS. */
    @CFunction
    public static native int dladdr1(PointerBase address, Dl_info info, PointerBase extra_info, int flags);

    /**
     * These are the possible values for the FLAGS argument to `dladdr1'. This indicates what extra
     * information is stored at *EXTRA_INFO. It may also be zero, in which case the EXTRA_INFO
     * argument is not used.
     */

    /** Matching symbol table entry (const ElfNN_Sym *). */
    @CConstant
    public static native int RTLD_DL_SYMENT();

    /** The object containing the address (struct link_map *). */
    @CConstant
    public static native int RTLD_DL_LINKMAP();

    /**
     * Get information about the shared object HANDLE refers to. REQUEST is from among the values
     * below, and determines the use of ARG.
     *
     * On success, returns zero. On failure, returns -1 and records an error message to be fetched
     * with `dlerror'.
     */
    @CFunction
    public static native int dlinfo(PointerBase handle, int request, PointerBase arg);

    /* These are the possible values for the REQUEST argument to `dlinfo'. */

    /** Treat ARG as `lmid_t *'; store namespace ID for HANDLE there. */
    @CConstant
    public static native int RTLD_DI_LMID();

    /** Treat ARG as `struct link_map **'; store the `struct link_map *' for HANDLE there. */
    @CConstant
    public static native int RTLD_DI_LINKMAP();

    /** Unsupported, defined by Solaris. */
    @CConstant
    public static native int RTLD_DI_CONFIGADDR();

    /**
     * Treat ARG as `Dl_serinfo *' (see below), and fill in to describe the directories that will be
     * searched for dependencies of this object. RTLD_DI_SERINFOSIZE fills in just the `dls_cnt' and
     * `dls_size' entries to indicate the size of the buffer that must be passed to RTLD_DI_SERINFO
     * to fill in the full information.
     */
    @CConstant
    public static native int RTLD_DI_SERINFO();

    @CConstant
    public static native int RTLD_DI_SERINFOSIZE();

    /**
     * Treat ARG as `CCharPointer ', and store there the directory name used to expand $ORIGIN in
     * this shared object's dependency file names.
     */
    @CConstant
    public static native int RTLD_DI_ORIGIN();

    /** Unsupported, defined by Solaris. */
    @CConstant
    public static native int RTLD_DI_PROFILENAME();

    /** Unsupported, defined by Solaris. */
    @CConstant
    public static native int RTLD_DI_PROFILEOUT();

    /**
     * Treat ARG as `size_t *', and store there the TLS module ID of this object's PT_TLS segment,
     * as used in TLS relocations; store zero if this object does not define a PT_TLS segment.
     */
    @CConstant
    public static native int RTLD_DI_TLS_MODID();

    /**
     * Treat ARG as ` PointerBase *', and store there a pointer to the calling thread's TLS block
     * corresponding to this object's PT_TLS segment. Store a null pointer if this object does not
     * define a PT_TLS segment, or if the calling thread has not allocated a block for it.
     */
    @CConstant
    public static native int RTLD_DI_TLS_DATA();

    @CConstant
    public static native int RTLD_DI_MAX();

    /**
     * This is the type of elements in `Dl_serinfo', below. The `dls_name' member points to space in
     * the buffer passed to `dlinfo'.
     */
    @CStruct
    public interface Dl_serpath extends PointerBase {
        /** Name of library search path directory. */
        @CField
        CCharPointer dls_name();

        /** Indicates where this directory came from. */
        @CField
                        /* unsigned */int dls_flags();
    }

    /**
     * This is the structure that must be passed (by reference) to `dlinfo' for the RTLD_DI_SERINFO
     * and RTLD_DI_SERINFOSIZE requests.
     */
    @CStruct
    public interface Dl_serinfo extends PointerBase {
        /** Size in bytes of the whole buffer. */
        @CField
        UnsignedWord dls_size();

        /** Number of elements in `dls_serpath'. */
        @CField
                        /* unsigned */int dls_cnt();

        /** Actually longer, dls_cnt elements. */
        @CFieldAddress
        Dl_serpath dls_serpath();
    }
}
