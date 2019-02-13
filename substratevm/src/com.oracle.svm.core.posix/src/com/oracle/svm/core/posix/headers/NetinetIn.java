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
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
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
public class NetinetIn {

    /** Private constructor: No instances. */
    private NetinetIn() {
    }

    /** From netinet6/in6.h, which can not be included directly. */
    @CConstant
    public static native int IPV6_V6ONLY();

    @CConstant
    public static native int INADDR_ANY();

    @CConstant
    public static native int IPPROTO_IP();

    @CConstant
    public static native int IPPROTO_IPV6();

    @CConstant
    public static native int IPPROTO_TCP();

    @CConstant
    public static native int IP_TOS();

    @CConstant
    public static native int IP_MULTICAST_IF();

    @CConstant
    public static native int IPV6_MULTICAST_IF();

    @CConstant
    public static native int IP_MULTICAST_LOOP();

    @CConstant
    public static native int IPV6_MULTICAST_LOOP();

    @CConstant
    public static native int IPV6_MULTICAST_HOPS();

    @CConstant
    public static native int IPV6_TCLASS();

    /** From linux/in.h, which exists only on Linux. */
    @Platforms({LINUX.class})
    @CConstant
    public static native int IP_MULTICAST_ALL();

    @CConstant
    public static native int IP_MULTICAST_TTL();

    // @formatter:off
    // sys/_types/_sa_family_t.h:typedef __uint8_t      sa_family_t;
    // sys/_types/_in_port_t.h:typedef  __uint16_t      in_port_t;
    /*
     * Socket address, internet style.
     */
    // struct sockaddr_in {
    //     __uint8_t      sin_len;
    //     sa_family_t    sin_family;
    //     in_port_t      sin_port;
    //     struct in_addr sin_addr;
    //     char           sin_zero[8];
    // };
    // @formatter:on
    @CStruct(addStructKeyword = true)
    public interface sockaddr_in extends PointerBase {

        @CField
        @AllowWideningCast
        int sin_family();

        @CField
        @AllowNarrowingCast
        void set_sin_family(int value);

        @CField
        @AllowWideningCast
        int sin_port();

        @CField
        @AllowNarrowingCast
        void set_sin_port(int value);

        @CFieldAddress
        in_addr sin_addr();
    }

    // @formatter:off
    // sys/_types/_in_addr_t.h:typedef __uint32_t  in_addr_t;  /* base type for internet address */
    /*
     * Internet address (a structure for historical reasons)
     */
    // struct in_addr {
    //     in_addr_t s_addr;
    // };
    // @formatter:on
    @CStruct(addStructKeyword = true)
    public interface in_addr extends PointerBase {

        @CField
        int s_addr();

        @CField
        void set_s_addr(int value);
    }

    /*
     * Declarations from netinet6/in6.h, which can not be #include'd directly.
     */

    // @formatter:off
    /*
     * IPv6 address
     */
    // struct in6_addr {
    //     union {
    //         __uint8_t   __u6_addr8[16];
    //         __uint16_t  __u6_addr16[8];
    //         __uint32_t  __u6_addr32[4];
    //     } __u6_addr;            /* 128-bit IP6 address */
    // };
    // #define s6_addr   __u6_addr.__u6_addr8
    // @formatter:on
    @CStruct(addStructKeyword = true)
    public interface in6_addr extends PointerBase {

        @CFieldAddress
        CCharPointer s6_addr();
    }

    // @formatter:off
    // sys/_types/_sa_family_t.h:typedef __uint8_t      sa_family_t;
    // sys/_types/_in_port_t.h:typedef  __uint16_t      in_port_t;
    /*
     * Socket address for IPv6
     */
    // struct sockaddr_in6 {
    //     __uint8_t       sin6_len;       /* length of this struct(sa_family_t) */
    //     sa_family_t     sin6_family;    /* AF_INET6 (sa_family_t) */
    //     in_port_t       sin6_port;      /* Transport layer port # (in_port_t) */
    //     __uint32_t      sin6_flowinfo;  /* IP6 flow information */
    //     struct in6_addr sin6_addr;      /* IP6 address */
    //     __uint32_t      sin6_scope_id;  /* scope zone index */
    //    };
    // @formatter:on
    @CStruct(addStructKeyword = true)
    public interface sockaddr_in6 extends PointerBase {

        @CField
        @AllowWideningCast
        int sin6_family();

        @CField
        @AllowNarrowingCast
        void set_sin6_family(int value);

        @CField
        @AllowWideningCast
        int sin6_port();

        @CField
        @AllowNarrowingCast
        void set_sin6_port(int value);

        @CField
        int sin6_flowinfo();

        @CField
        void set_sin6_flowinfo(int value);

        @CFieldAddress
        in6_addr sin6_addr();

        @CField
        int sin6_scope_id();

        @CField
        void set_sin6_scope_id(int value);
    }

    @CConstant
    public static native int LITTLE_ENDIAN();

    @CConstant
    public static native int BIG_ENDIAN();

    @CConstant
    public static native int BYTE_ORDER();

    // These are macros in C, rather than functions,
    // so I implemented them as methods.
    // TODO: These methods are used only occasionally from the SubstrateVM Java code,
    // so I am not so worried about whether this is the most efficient way to write them.

    /** Host to Network Short: Converts a host-byte-order short to a network-byte-order short. */
    public static int htons(int hostshort) {
        // An expansion of the htons macro on little-endian machines
        // takes a short with 0xA1B0 and turns it into 0xB0A1.
        final int result;
        if (BYTE_ORDER() == LITTLE_ENDIAN()) {
            result = NetinetIn.swap_uint16_t(hostshort);
        } else {
            result = NetinetIn.int_to_uint16_t(hostshort);
        }
        return result;
    }

    /** Host to Network Short: Converts a host-byte-order short to a network-byte-order short. */
    public static int ntohs(int hostshort) {
        // An expansion of the ntohs macro on little-endian machines
        // takes a short with 0xA1B0 and turns it into 0xB0A1.
        final int result;
        if (BYTE_ORDER() == LITTLE_ENDIAN()) {
            result = NetinetIn.swap_uint16_t(hostshort);
        } else {
            result = NetinetIn.int_to_uint16_t(hostshort);
        }
        return result;
    }

    /** Host to Network Long: Converts a host-byte-order int to a network-byte-order int. */
    public static int htonl(int hostlong) {
        // An expansion of the htonl macro on little-endian machines
        // takes an int with 0xA3B2C1D0 and turns it into 0xD0C1B2A3.
        final int result;
        if (BYTE_ORDER() == LITTLE_ENDIAN()) {
            result = NetinetIn.swap_uint32_t(hostlong);
        } else {
            result = hostlong;
        }
        return result;
    }

    public static int ntohl(int netlong) {
        // An expansion of the ntohl macro on little-endian machines,
        // takes an int with 0xD3C2B1A0 and turns it into 0xA0B1C2D3.
        final int result;
        if (BYTE_ORDER() == LITTLE_ENDIAN()) {
            result = NetinetIn.swap_uint32_t(netlong);
        } else {
            result = netlong;
        }
        return result;
    }

    // This traffics in int rather than short, but it contains the values to the range of uint16_t.
    public static int swap_uint16_t(int value) {
        // @formatter:off
        return NetinetIn.int_to_uint16_t(NetinetIn.insertByte(NetinetIn.extractByte(value, 1), 0) |
                               NetinetIn.insertByte(NetinetIn.extractByte(value, 0), 1));

        // @formatter:on
    }

    // This traffics in int rather than short, but it contains the values to the range of uint16_t.
    public static int int_to_uint16_t(int value) {
        return (value & 0xFFFF);
    }

    public static int swap_uint32_t(int value) {
        // @formatter:off
        return NetinetIn.insertByte(NetinetIn.extractByte(value, 3), 0) |
               NetinetIn.insertByte(NetinetIn.extractByte(value, 2), 1) |
               NetinetIn.insertByte(NetinetIn.extractByte(value, 1), 2) |
               NetinetIn.insertByte(NetinetIn.extractByte(value, 0), 3);

        // @formatter:on
    }

    /** Extract a byte from an int. Bytes are numbered from 0 from the low-order. */
    public static int extractByte(int value, int whichByte) {
        assert ((0 <= whichByte) && (whichByte <= 3)) : "Which byte not in [0..3]";
        return ((value >>> (whichByte * 8)) & 0xFF);
    }

    /** Insert a byte into an int. */
    public static int insertByte(int value, int whichByte) {
        assert ((0 <= whichByte) && (whichByte <= 3)) : "Which byte not in [0..3]";
        assert ((0 <= value) && (value <= 255)) : "Value not in [0..255]";
        return (value << (whichByte * 8));
    }
}

// Checkstyle: resume
