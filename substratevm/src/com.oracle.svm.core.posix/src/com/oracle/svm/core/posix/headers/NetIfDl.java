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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

@Platforms({DARWIN.class})
@CContext(PosixDirectives.class)
public class NetIfDl {
    /* { Do not format quoted code: @formatter:off */
    /* { Allow non-standard names: Checkstyle: stop */

    //    /*
    //     * Structure of a Link-Level sockaddr:
    //     */
    //    struct sockaddr_dl {
    //        u_char  sdl_len;    /* Total length of sockaddr */
    //        u_char  sdl_family; /* AF_LINK */
    //        u_short sdl_index;  /* if != 0, system given index for interface */
    //        u_char  sdl_type;   /* interface type */
    //        u_char  sdl_nlen;   /* interface name length, no trailing 0 reqd. */
    //        u_char  sdl_alen;   /* link level address length */
    //        u_char  sdl_slen;   /* link layer selector length */
    //        char    sdl_data[12];   /* minimum work area, can be larger;
    //                       contains both if name and ll address */
    //    #ifndef __APPLE__
    //        /* For TokenRing */
    //        u_short sdl_rcf;    /* source routing control */
    //        u_short sdl_route[16];  /* source routing information */
    //    #endif
    //    };
    @CStruct(addStructKeyword = true)
    public interface sockaddr_dl extends PointerBase {

        @CField
        @AllowWideningCast
        int sdl_alen();

        @CField
        @AllowWideningCast
        int sdl_nlen();

        @CFieldAddress
        CCharPointer sdl_data();
    }

    /* } Allow non-standard names: Checkstyle: resume */
    /* } Do not format quoted code: @formatter:on */
}
