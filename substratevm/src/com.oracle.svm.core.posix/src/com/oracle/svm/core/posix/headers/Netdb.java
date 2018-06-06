/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

// Allow methods with non-standard names: Checkstyle: stop

/*
 * The definitions I need, manually translated from the C header file /usr/include/netdb.h.
 */

@Platforms({DARWIN.class, LINUX.class})
@CContext(PosixDirectives.class)
public final class Netdb {

    /** Private constructor: No instances. */
    private Netdb() {
    }

    /*
     * Constants for getnameinfo()
     */

    @CConstant
    public static native int NI_MAXHOST();

    @CConstant
    public static native int NI_NAMEREQD();

    /*
     * Constants for addrinfo.ai_flags.
     */

    @CConstant
    public static native int AI_CANONNAME();

    // @formatter:off
    // i386/_types.h:typedef __uint32_t       __darwin_socklen_t; /* socklen_t (duh) */
    // sys/_types/_socklen_t.h:typedef    __darwin_socklen_t  socklen_t;
    // int getnameinfo(const struct sockaddr *sa,
    //                 socklen_t              salen,
    //                 char                  *host,
    //                 socklen_t              hostlen,
    //                 char                  *serv,
    //                 socklen_t              servlen,
    //                 int                    flags);
    @CFunction
    public static native int getnameinfo(Socket.sockaddr sa,
                                         int             salen,
                                         CCharPointer    host,
                                         int             hostlen,
                                         CCharPointer    serv,
                                         int             servlen,
                                         int             flags);
    // @formatter:on

    // @formatter:off
    // struct addrinfo {
    //     int       ai_flags;           /* AI_PASSIVE, AI_CANONNAME, AI_NUMERICHOST */
    //     int       ai_family;          /* PF_xxx */
    //     int       ai_socktype;        /* SOCK_xxx */
    //     int       ai_protocol;        /* 0 or IPPROTO_xxx for IPv4 and IPv6 */
    //     socklen_t ai_addrlen;         /* length of ai_addr */
    //     char     *ai_canonname;       /* canonical name for hostname */
    //     struct    sockaddr *ai_addr;  /* binary address */
    //     struct    addrinfo *ai_next;  /* next structure in linked list */
    // };
    // @formatter:on
    @CStruct(addStructKeyword = true)
    public interface addrinfo extends PointerBase {

        @CField
        int ai_flags();

        @CField
        void set_ai_flags(int value);

        @CField
        int ai_family();

        @CField
        void set_ai_family(int value);

        @CField
        int ai_socktype();

        @CField
        int ai_protocol();

        @CField
        int ai_addrlen();

        @CField
        CCharPointer ai_canonname();

        @CField
        Socket.sockaddr ai_addr();

        @CField
        Netdb.addrinfo ai_next();

        @CField
        void set_ai_next(Netdb.addrinfo value);
    }

    // HINT: Think of "addrinfoPointer" as an array of addrinfo instances.
    // That is not "struct addrinfo *" but as "struct addrinfo*[]",
    // so when I call read(i) it returns the ith "struct addrinfo* in the array,
    // even though often there is only one element, at index 0.
    // HINT: These "array" types might want to be able to get the address of
    // the ith element, in which case I would declare a
    // addrinfoPointer addressOf(int index)
    // method to return a pointer to a particular addrinfo from the array.
    @CPointerTo(nameOfCType = "struct addrinfo *")
    public interface addrinfoPointer extends PointerBase {

        Netdb.addrinfo read();

        Netdb.addrinfo read(int i);
    }

    // @formatter:off
    // int getaddrinfo(const char             *hostname,
    //                 const char             *servname,
    //                 const struct addrinfo  *hints,
    //                 struct addrinfo       **res);
    @CFunction
    public static native int getaddrinfo(CCharPointer          hostname,
                                         CCharPointer          servname,
                                         Netdb.addrinfo        hints,
                                         Netdb.addrinfoPointer res);
    // @formatter:on

    // void freeaddrinfo(struct addrinfo *ai);
    @CFunction
    public static native void freeaddrinfo(Netdb.addrinfo ai);
}

// Checkstyle: resume
