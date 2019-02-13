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
package com.oracle.svm.core.posix.headers.darwin;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.PosixDirectives;

/* { Do not format quoted code: @formatter:off */
/* { Allow non-standard names: Checkstyle: stop */

@Platforms(Platform.DARWIN.class)
@CContext(PosixDirectives.class)
public class DarwinNetinet6In6_var {

    @CStruct(addStructKeyword = true)
    // struct in6_ifreq {
    public interface in6_ifreq extends PointerBase {

        //  char    ifr_name[IFNAMSIZ];
        @CFieldAddress
        CCharPointer ifr_name();

        //  union {
        //      struct  sockaddr_in6 ifru_addr;
        @CFieldAddress("ifr_ifru.ifru_addr")
        NetinetIn.sockaddr_in6 ifru_addr();

        //      struct  sockaddr_in6 ifru_dstaddr;
        //      int ifru_flags;
        //      int ifru_metric;
        //      int ifru_intval;
        //      caddr_t ifru_data;
        //      struct in6_addrlifetime ifru_lifetime;
        //      struct in6_ifstat ifru_stat;
        //      struct icmp6_ifstat ifru_icmp6stat;
        //      u_int32_t ifru_scope_id[SCOPE6_ID_MAX];
        //  } ifr_ifru;
        // };
    }

    // #define SIOCGIFNETMASK_IN6  _IOWR('i', 37, struct in6_ifreq)
    @CConstant
    public static native long SIOCGIFNETMASK_IN6();
}

/* } Allow non-standard names: Checkstyle: resume */
/* } Do not format quoted code: @formatter:on */
