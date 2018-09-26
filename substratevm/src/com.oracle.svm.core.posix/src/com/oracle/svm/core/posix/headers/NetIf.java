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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

// Allow methods with non-standard names: Checkstyle: stop

/** The definitions I need, manually translated from the C header file. */
@Platforms({DARWIN.class, LINUX.class})
@CContext(PosixDirectives.class)
public class NetIf {

    @CConstant
    public static native int IFF_LOOPBACK();

    // #define IF_NAMESIZE 16
    @CConstant
    public static native int IF_NAMESIZE();

    // #define IFNAMSIZ IF_NAMESIZE
    @CConstant
    public static native int IFNAMSIZ();

    // #define IFHWADDRLEN 6;
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int IFHWADDRLEN();

    @CConstant
    public static native int IFF_BROADCAST();

    /* { Do not reformat commented out C code: @formatter:off */
    @CContext(PosixDirectives.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    @CStruct(addStructKeyword = true)
    public interface ifreq extends PointerBase {
        // /*
        //  * Interface request structure used for socket
        //  * ioctl's.  All interface ioctl's must have parameter
        //  * definitions which begin with ifr_name.  The
        //  * remainder may be interface specific.
        //  */
        // struct ifreq {
        //         union
        //         {
        //                 char    ifrn_name[IFNAMSIZ];            /* if name, e.g. "en0" */
        //         } ifr_ifrn;
        //
        //         union {
        //                 struct  sockaddr ifru_addr;
        //                 struct  sockaddr ifru_dstaddr;
        //                 struct  sockaddr ifru_broadaddr;
        //                 struct  sockaddr ifru_netmask;
        //                 struct  sockaddr ifru_hwaddr;
        //                 short   ifru_flags;
        //                 int     ifru_ivalue;
        //                 int     ifru_mtu;
        //                 struct  ifmap ifru_map;
        //                 char    ifru_slave[IFNAMSIZ];   /* Just fits the size */
        //                 char    ifru_newname[IFNAMSIZ];
        //                 void *  ifru_data;
        //                 struct  if_settings ifru_settings;
        //         } ifr_ifru;
        // };

        /*
         * The definition of `struct ifreq` is slightly different between Linux and Darwin but they both
         * have identically-named macros to access the fields that exist on both platforms.
         *
         * `ifr_ifrn` and `ifr_ifru` are inline unnamed unions, but there are C macros to access them.
         */
        /* { Do not reformat commented out C code: @formatter:off */
        // #define ifr_name        ifr_ifrn.ifrn_name      /* interface name       */
        // #define ifr_hwaddr      ifr_ifru.ifru_hwaddr    /* MAC address          */
        // #define ifr_addr        ifr_ifru.ifru_addr      /* address              */
        // #define ifr_dstaddr     ifr_ifru.ifru_dstaddr   /* other end of p-p lnk */
        // #define ifr_broadaddr   ifr_ifru.ifru_broadaddr /* broadcast address    */
        // #define ifr_netmask     ifr_ifru.ifru_netmask   /* interface net mask   */
        // #define ifr_flags       ifr_ifru.ifru_flags     /* flags                */
        // #define ifr_metric      ifr_ifru.ifru_ivalue    /* metric               */
        // #define ifr_mtu         ifr_ifru.ifru_mtu       /* mtu                  */
        // #define ifr_map         ifr_ifru.ifru_map       /* device map           */
        // #define ifr_slave       ifr_ifru.ifru_slave     /* slave device         */
        // #define ifr_data        ifr_ifru.ifru_data      /* for use by interface */
        // #define ifr_ifindex     ifr_ifru.ifru_ivalue    /* interface index      */
        // #define ifr_bandwidth   ifr_ifru.ifru_ivalue    /* link bandwidth       */
        // #define ifr_qlen        ifr_ifru.ifru_ivalue    /* Queue length         */
        // #define ifr_newname     ifr_ifru.ifru_newname   /* New name             */
        // #define ifr_settings    ifr_ifru.ifru_settings  /* Device/proto settings*/
        /* } Do not reformat commented out C code: @formatter:on */

        /*
         * Since I can only deal in addresses, rather than values, I use CFieldAddress instead of
         * CField for the return types of these access methods.
         */
        @CFieldAddress
        CCharPointer ifr_name();

        @CFieldAddress
        Socket.sockaddr ifr_addr();

        @CFieldAddress
        Socket.sockaddr ifr_broadaddr();

        @CField
        @AllowWideningCast
        short ifr_flags();

        @Platforms(Platform.LINUX.class)
        @CField
        int ifr_ifindex();

        NetIf.ifreq addressOf(int index);

        @Platforms(Platform.LINUX.class)
        @CFieldAddress
        Socket.sockaddr ifr_hwaddr();

    }
    /* } Do not reformat commented out C code: @formatter:on */

    /* { Do not reformat commented out C code: @formatter:off */
    @CContext(PosixDirectives.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    @CStruct(addStructKeyword = true)
    public interface ifconf extends PointerBase {
        // /*
        //  * Structure used in SIOCGIFCONF request.
        //  * Used to retrieve interface configuration
        //  * for machine (useful for programs which
        //  * must know all networks accessible).
        //  */
        // struct  ifconf {
        //     int ifc_len;        /* size of associated buffer */
        //     union {
        //         caddr_t ifcu_buf;
        //         struct  ifreq *ifcu_req;
        //     } ifc_ifcu;
        // };
        // #define ifc_buf ifc_ifcu.ifcu_buf   /* buffer address */
        // #define ifc_req ifc_ifcu.ifcu_req   /* array of structures returned */
        /* } Do not reformat commented out C code: @formatter:on */

        @CField
        int ifc_len();

        /* Use the C macro names to get the addresses of the fields of the union. */

        @CField
        void ifc_buf(CCharPointer value);

        @CField
        NetIf.ifreq ifc_req();
    }

    @CFunction
    public static native int if_nametoindex(CCharPointer ifname);

}
// Checkstyle: resume
