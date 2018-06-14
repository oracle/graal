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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.posix.headers.Uio.iovec;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/socket.h.
 */
@CContext(PosixDirectives.class)
public class Socket {

    /* Types of sockets. */

    /** Sequenced, reliable, connection-based byte streams. */
    @CConstant
    public static native int SOCK_STREAM();

    /**
     * Connectionless, unreliable datagrams of fixed maximum length.
     */
    @CConstant
    public static native int SOCK_DGRAM();

    /** Raw protocol interface. */
    @CConstant
    public static native int SOCK_RAW();

    /** Reliably-delivered messages. */
    @CConstant
    public static native int SOCK_RDM();

    /** Sequenced, reliable, connection-based, datagrams of fixed maximum length. */
    @CConstant
    public static native int SOCK_SEQPACKET();

    // [not present on old Linux systems]
    // /** Datagram Congestion Control Protocol. */
    // @CConstant
    // public static native int SOCK_DCCP();

    /**
     * Linux specific way of getting packets at the dev level. For writing rarp and other similar
     * things on the user level.
     */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SOCK_PACKET();

    // [not present on old Linux systems]
    // /*
    // * Flags to be ORed into the type parameter of socket and socketpair and used for the flags
    // * parameter of paccept.
    // */
    //
    // /** Atomically set close-on-exec flag for the new descriptor(s). */
    // @CConstant
    // public static native int SOCK_CLOEXEC();
    //
    // /** Atomically mark descriptor(s) as non-blocking. */
    // @CConstant
    // public static native int SOCK_NONBLOCK();

    /* Protocol families. */
    /** Unspecified. */
    @CConstant
    public static native int PF_UNSPEC();

    /** Local to host (pipes and file-domain). */
    @CConstant
    public static native int PF_LOCAL();

    /** POSIX name for PF_LOCAL. */
    @CConstant
    public static native int PF_UNIX();

    /** Another non-standard name for PF_LOCAL. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_FILE();

    /** IP protocol family. */
    @CConstant
    public static native int PF_INET();

    /** Amateur Radio AX.25. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_AX25();

    /** Novell Internet Protocol. */
    @CConstant
    public static native int PF_IPX();

    /** Appletalk DDP. */
    @CConstant
    public static native int PF_APPLETALK();

    /** Amateur radio NetROM. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_NETROM();

    /** Multiprotocol bridge. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_BRIDGE();

    /** ATM PVCs. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_ATMPVC();

    /** Reserved for X.25 project. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_X25();

    /** IP version 6. */
    @CConstant
    public static native int PF_INET6();

    /** Amateur Radio X.25 PLP. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_ROSE();

    /** Reserved for DECnet project. */
    @CConstant
    public static native int PF_DECnet();

    /** Reserved for 802.2LLC project. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_NETBEUI();

    /** Security callback pseudo AF. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_SECURITY();

    /** PF_KEY key management API. */
    @CConstant
    public static native int PF_KEY();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_NETLINK();

    /** Alias to emulate 4.4BSD. */
    @CConstant
    public static native int PF_ROUTE();

    /** Packet family. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_PACKET();

    /** Ash. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_ASH();

    /** Acorn Econet. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_ECONET();

    /** ATM SVCs. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_ATMSVC();

    // [not present on old Linux systems]
    // /** RDS sockets. */
    // @CConstant
    // public static native int PF_RDS();

    /** Linux SNA Project */
    @CConstant
    public static native int PF_SNA();

    /** IRDA sockets. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_IRDA();

    /** PPPoX sockets. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_PPPOX();

    /** Wanpipe API sockets. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_WANPIPE();

    // [not present on old Linux systems]
    // /** Linux LLC. */
    // @CConstant
    // public static native int PF_LLC();
    //
    // /** Controller Area Network. */
    // @CConstant
    // public static native int PF_CAN();
    //
    // /** TIPC sockets. */
    // @CConstant
    // public static native int PF_TIPC();

    /** Bluetooth sockets. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PF_BLUETOOTH();

    // [not present on old Linux systems]
    // /** IUCV sockets. */
    // @CConstant
    // public static native int PF_IUCV();
    //
    // /** RxRPC sockets. */
    // @CConstant
    // public static native int PF_RXRPC();
    //
    // /** mISDN sockets. */
    // @CConstant
    // public static native int PF_ISDN();
    //
    // /** Phonet sockets. */
    // @CConstant
    // public static native int PF_PHONET();
    //
    // /** IEEE 802.15.4 sockets. */
    // @CConstant
    // public static native int PF_IEEE802154();
    //
    // /** CAIF sockets. */
    // @CConstant
    // public static native int PF_CAIF();
    //
    // /** Algorithm sockets. */
    // @CConstant
    // public static native int PF_ALG();
    //
    // /** NFC sockets. */
    // @CConstant
    // public static native int PF_NFC();

    /** For now.. */
    @CConstant
    public static native int PF_MAX();

    /* Address families. */

    @CConstant
    public static native int AF_UNSPEC();

    @CConstant
    public static native int AF_LOCAL();

    @CConstant
    public static native int AF_UNIX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_FILE();

    @CConstant
    public static native int AF_INET();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_AX25();

    @CConstant
    public static native int AF_IPX();

    @CConstant
    public static native int AF_APPLETALK();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_NETROM();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_BRIDGE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_ATMPVC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_X25();

    @CConstant
    public static native int AF_INET6();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_ROSE();

    @CConstant
    public static native int AF_DECnet();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_NETBEUI();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_SECURITY();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_KEY();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_NETLINK();

    @CConstant
    public static native int AF_ROUTE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_PACKET();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_ASH();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_ECONET();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_ATMSVC();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int AF_RDS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_SNA();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_IRDA();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_PPPOX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_WANPIPE();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int AF_LLC();
    //
    // @CConstant
    // public static native int AF_CAN();
    //
    // @CConstant
    // public static native int AF_TIPC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AF_BLUETOOTH();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int AF_IUCV();
    //
    // @CConstant
    // public static native int AF_RXRPC();
    //
    // @CConstant
    // public static native int AF_ISDN();
    //
    // @CConstant
    // public static native int AF_PHONET();
    //
    // @CConstant
    // public static native int AF_IEEE802154();
    //
    // @CConstant
    // public static native int AF_CAIF();
    //
    // @CConstant
    // public static native int AF_ALG();
    //
    // @CConstant
    // public static native int AF_NFC();

    @CConstant
    public static native int AF_MAX();

    /*
     * Socket level values. Others are defined in the appropriate headers.
     *
     * XXX These definitions also should go into the appropriate headers as far as they are
     * available.
     */

    // @CConstant
    // public static native int SOL_RAW();
    //
    // @CConstant
    // public static native int SOL_DECNET();
    //
    // @CConstant
    // public static native int SOL_X25();
    //
    // @CConstant
    // public static native int SOL_PACKET();
    //
    // /** ATM layer (cell level). */
    // @CConstant
    // public static native int SOL_ATM();
    //
    // /** ATM Adaption Layer (packet level). */
    // @CConstant
    // public static native int SOL_AAL();
    //
    // @CConstant
    // public static native int SOL_IRDA();
    //
    // /** Maximum queue length specifiable by listen. */
    // @CConstant
    // public static native int SOMAXCONN();

    /* @formatter:off */
    /** Structure describing a generic socket address. */
    // struct sockaddr {
    //     sa_family_t sa_family;   /* [XSI] address family */
    //     char        sa_data[14]; /* [XSI] addr value (actually larger) */
    // };
    /* @formatter:on */
    @CStruct(addStructKeyword = true)
    public interface sockaddr extends PointerBase {

        @Platforms(Platform.DARWIN.class)
        // __uint8_t sa_len; /* total length */
        @CField
        @AllowWideningCast
        int sa_len();

        @CField
        @AllowWideningCast
        int sa_family();

        /** Address data. */
        @CFieldAddress
        CCharPointer sa_data();
    }

    /**
     * Structure large enough to hold any socket address (with the historical exception of AF_UNIX).
     * We reserve 128 bytes.
     */
    @CStruct(addStructKeyword = true)
    public interface sockaddr_storage extends PointerBase {
        @CField
        @AllowWideningCast
        short ss_family();
    }

    /** A struct sockaddr**. */
    @CPointerTo(sockaddr.class)
    public interface sockaddrPointer extends PointerBase {

        sockaddr read();

        void write(sockaddr value);
    }

    /* Bits in the FLAGS argument to `send', `recv', et al. */

    /** Process out-of-band data. */
    @CConstant
    public static native int MSG_OOB();

    /** Peek at incoming messages. */
    @CConstant
    public static native int MSG_PEEK();

    /** Don't use local routing. */
    @CConstant
    public static native int MSG_DONTROUTE();

    /** DECnet uses a different name. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_TRYHARD();

    /** Control data lost before delivery. */
    @CConstant
    public static native int MSG_CTRUNC();

    /** Supply or ask second address. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_PROXY();

    @CConstant
    public static native int MSG_TRUNC();

    /** Nonblocking IO. */
    @CConstant
    public static native int MSG_DONTWAIT();

    /** End of record. */
    @CConstant
    public static native int MSG_EOR();

    /** Wait for a full request. */
    @CConstant
    public static native int MSG_WAITALL();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_FIN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_SYN();

    /** Confirm path validity. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_CONFIRM();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_RST();

    /** Fetch message from error queue. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_ERRQUEUE();

    /** Do not generate SIGPIPE. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_NOSIGNAL();

    /** Sender will send more. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MSG_MORE();

    // [not present on old Linux systems]
    // /** Wait for at least one packet to return. */
    // @CConstant
    // public static native int MSG_WAITFORONE();
    //
    // /** Send data in TCP SYN. */
    // @CConstant
    // public static native int MSG_FASTOPEN();
    //
    // /** Set close_on_exit for file descriptor received through SCM_RIGHTS. */
    // @CConstant
    // public static native int MSG_CMSG_CLOEXEC();

    /*
     * Note: socklen_t is "u_int32" on Linux and "int32" on Darwin, so I am using "long" to cover
     * the values, but allowing widening on reads and narrowing on writes.
     */

    /** Structure describing messages sent by `sendmsg' and received by `recvmsg'. */
    @CStruct(addStructKeyword = true)
    public interface msghdr extends PointerBase {
        /** Address to send to/receive from. */
        @CField
        PointerBase msg_name();

        @CField
        void msg_name(PointerBase value);

        /** Length of address data. */
        @CField
        @AllowWideningCast
        long /* socklen_t */ msg_namelen();

        @CField
        @AllowNarrowingCast
        void msg_namelen(long /* socklen_t */ value);

        /** Vector of data to send/receive into. */
        @CField
        iovec msg_iov();

        @CField
        void msg_iov(iovec value);

        /** Number of elements in the vector. */
        @CField
        @AllowWideningCast
        long /* socklen_t */ msg_iovlen();

        @CField
        @AllowNarrowingCast
        void msg_iovlen(long /* socklen_t */ value);

        /** Ancillary data (eg BSD filedesc passing). */
        @CField
        PointerBase msg_control();

        @CField
        void msg_control(PointerBase value);

        /** Ancillary data buffer length. */
        @CField
        @AllowWideningCast
        long /* socklen_t */ msg_controllen();

        @CField
        @AllowNarrowingCast
        void msg_controllen(long /* socklen_t */ value);

        /** Flags on received message. */
        @CField
        int msg_flags();

        @CField
        void msg_flags(int value);
    }

    /** Structure used for storage of ancillary data object information. */
    @CStruct(addStructKeyword = true)
    public interface cmsghdr extends PointerBase {
        /** Length of data in cmsg_data plus length of cmsghdr structure. */
        @CField
        @AllowWideningCast
        long /* socklen_t */ cmsg_len();

        @CField
        @AllowNarrowingCast
        void cmsg_len(long value);

        /** Originating protocol. */
        @CField
        int cmsg_level();

        @CField
        void cmsg_level(int value);

        /** Protocol specific type. */
        @CField
        int cmsg_type();

        @CField
        void cmsg_type(int value);

        /* followed by unsigned char cmsg_data[]; */
    }

    /* Socket level message types. This must match the definitions in <linux/socket.h>. */

    /** Transfer file descriptors. */
    @CConstant
    public static native int SCM_RIGHTS();

    /** Credentials passing. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SCM_CREDENTIALS();

    /** User visible structure for SCM_CREDENTIALS message */
    @CStruct(addStructKeyword = true)
    @Platforms(Platform.LINUX.class)
    public interface ucred extends PointerBase {
        /** PID of sending process. */
        @CField
        int pid();

        /** UID of sending process. */
        @CField
        int uid();

        /** GID of sending process. */
        @CField
        int gid();
    }

    /* Socket-level I/O control calls. */

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int FIOSETOWN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SIOCSPGRP();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int FIOGETOWN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SIOCGPGRP();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SIOCATMARK();

    /** Get stamp (timeval) */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SIOCGSTAMP();

    @CConstant
    public static native long SIOCGIFCONF();

    @CConstant
    public static native long SIOCGIFFLAGS();

    @CConstant
    public static native long SIOCGIFBRDADDR();

    @CConstant
    public static native long SIOCGIFNETMASK();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SIOCGIFINDEX();

    // /** Get stamp (timespec) */
    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SIOCGSTAMPNS();

    /** For setsockopt(2) */
    @CConstant
    public static native int SOL_SOCKET();

    // @CConstant
    // public static native int SO_DEBUG();

    @CConstant
    public static native int SO_REUSEADDR();

    @CConstant
    public static native int SO_TYPE();

    @CConstant
    public static native int SO_ERROR();

    @CConstant
    public static native int SO_DONTROUTE();

    @CConstant
    public static native int SO_BROADCAST();

    @CConstant
    public static native int SO_SNDBUF();

    @CConstant
    public static native int SO_RCVBUF();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_SNDBUFFORCE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_RCVBUFFORCE();

    @CConstant
    public static native int SO_KEEPALIVE();

    @CConstant
    public static native int SO_OOBINLINE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_NO_CHECK();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_PRIORITY();

    @CConstant
    public static native int SO_LINGER();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_BSDCOMPAT();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int SO_REUSEPORT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_PASSCRED();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_PEERCRED();

    @CConstant
    public static native int SO_RCVLOWAT();

    @CConstant
    public static native int SO_SNDLOWAT();

    @CConstant
    public static native int SO_RCVTIMEO();

    @CConstant
    public static native int SO_SNDTIMEO();

    /* Security levels - as per NRL IPv6 - don't actually do anything */

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_SECURITY_AUTHENTICATION();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_SECURITY_ENCRYPTION_TRANSPORT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_SECURITY_ENCRYPTION_NETWORK();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_BINDTODEVICE();

    /* Socket filtering */

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_ATTACH_FILTER();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_DETACH_FILTER();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int SO_GET_FILTER();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_PEERNAME();

    @CConstant
    public static native int SO_TIMESTAMP();

    @CConstant
    public static native int SCM_TIMESTAMP();

    // @CConstant
    // public static native int SO_ACCEPTCONN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_PEERSEC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SO_PASSSEC();

    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SO_TIMESTAMPNS();

    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SCM_TIMESTAMPNS();

    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SO_MARK();

    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SO_TIMESTAMPING();

    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SCM_TIMESTAMPING();

    // [not present on old Linux systems]
    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SO_PROTOCOL();
    //
    // @CConstant
    // @Platforms(Platform.LINUX.class)
    // public static native int SO_DOMAIN();
    //
    // @CConstant
    // public static native int SO_RXQ_OVFL();
    //
    // @CConstant
    // public static native int SO_WIFI_STATUS();
    //
    // @CConstant
    // public static native int SCM_WIFI_STATUS();
    //
    // @CConstant
    // public static native int SO_PEEK_OFF();
    //
    // /** Instruct lower device to use last 4-bytes of skb data as FCS */
    // @CConstant
    // public static native int SO_NOFCS();
    //
    // @CConstant
    // public static native int SO_LOCK_FILTER();
    //
    // @CConstant
    // public static native int SO_SELECT_ERR_QUEUE();
    //
    // @CConstant
    // public static native int SO_BUSY_POLL();

    /** Structure used to manipulate the SO_LINGER option. */
    @CStruct(addStructKeyword = true)
    public interface linger extends PointerBase {

        /** Nonzero to linger on close. */
        @CField
        int l_onoff();

        @CField
        void set_l_onoff(int value);

        /** Time to linger. */
        @CField
        int l_linger();

        @CField
        void set_l_linger(int value);
    }

    /* The following constants should be used for the second parameter of `shutdown'. */

    /** No more receptions. */
    @CConstant
    public static native int SHUT_RD();

    /** No more transmissions. */
    @CConstant
    public static native int SHUT_WR();

    /** No more receptions or transmissions. */
    @CConstant
    public static native int SHUT_RDWR();

    // [not present on old Linux systems]
    // /** For `recvmmsg' and `sendmmsg'. */
    // @CStruct(addStructKeyword = true)
    // public interface mmsghdr extends PointerBase {
    // /** Actual message header. */
    // @CFieldAddress
    // msghdr msg_hdr();
    //
    // /** Number of received or sent bytes for the entry. */
    // @CField
    // int msg_len();
    // }

    /**
     * Create a new socket of type TYPE in domain DOMAIN, using protocol PROTOCOL. If PROTOCOL is
     * zero, one is chosen automatically. Returns a file descriptor for the new socket, or -1 for
     * errors.
     */
    @CFunction
    public static native int socket(int domain, int type, int protocol);

    /**
     * Create two new sockets, of type TYPE in domain DOMAIN and using protocol PROTOCOL, which are
     * connected to each other, and put file descriptors for them in FDS[0] and FDS[1]. If PROTOCOL
     * is zero, one will be chosen automatically. Returns 0 on success, -1 for errors.
     */
    @CFunction
    public static native int socketpair(int domain, int type, int protocol, CIntPointer fds);

    /** Give the socket FD the local address ADDR (which is LEN bytes long). */
    @CFunction
    public static native int bind(int fd, sockaddr addr, int len);

    /** Put the local address of FD into *ADDR and its length in *LEN. */
    @CFunction
    public static native int getsockname(int fd, sockaddr addr, CIntPointer len);

    /**
     * Open a connection on socket FD to peer at ADDR (which LEN bytes long). For connectionless
     * socket types, just set the default address to send to and the only address from which to
     * accept transmissions. Return 0 on success, -1 for errors.
     */
    @CFunction
    public static native int connect(int fd, sockaddr addr, int len);

    /**
     * Put the address of the peer connected to socket FD into *ADDR (which is *LEN bytes long), and
     * its actual length into *LEN.
     */
    @CFunction
    public static native int getpeername(int fd, sockaddr addr, CIntPointer len);

    /** Send N bytes of BUF to socket FD. Returns the number sent or -1. */
    @CFunction
    public static native SignedWord send(int fd, PointerBase buf, UnsignedWord n, int flags);

    /** Read N bytes into BUF from socket FD. Returns the number read or -1 for errors. */
    @CFunction
    public static native SignedWord recv(int fd, PointerBase buf, UnsignedWord n, int flags);

    /**
     * Send N bytes of BUF on socket FD to peer at address ADDR (which is ADDR_LEN bytes long).
     * Returns the number sent, or -1 for errors.
     */
    @CFunction
    public static native SignedWord sendto(int fd, PointerBase buf, UnsignedWord n, int flags, sockaddr addr, int addr_len);

    /**
     * Read N bytes into BUF through socket FD. If ADDR is not NULL, fill in *ADDR_LEN bytes of it
     * with tha address of the sender, and store the actual size of the address in *ADDR_LEN.
     * Returns the number of bytes read or -1 for errors.
     */
    @CFunction
    public static native SignedWord recvfrom(int fd, PointerBase buf, UnsignedWord n, int flags, sockaddr addr, CIntPointer addr_len);

    /**
     * Send a message described MESSAGE on socket FD. Returns the number of bytes sent, or -1 for
     * errors.
     */
    @CFunction
    public static native SignedWord sendmsg(int fd, msghdr message, int flags);

    // [not present on old Linux systems]
    // /**
    // * Send a VLEN messages as described by VMESSAGES to socket FD. Returns the number of
    // datagrams
    // * successfully written or -1 for errors.
    // */
    // @CFunction
    // public static native int sendmmsg(int fd, mmsghdr vmessages, /* unsigned */int vlen, int
    // flags);

    /**
     * Receive a message as described by MESSAGE from socket FD. Returns the number of bytes read or
     * -1 for errors.
     */
    @CFunction
    public static native SignedWord recvmsg(int fd, msghdr message, int flags);

    // [not present on old Linux systems]
    // /**
    // * Receive up to VLEN messages as described by VMESSAGES from socket FD. Returns the number of
    // * bytes read or -1 for errors.
    // */
    // @CFunction
    // public static native int recvmmsg(int fd, mmsghdr vmessages, /* unsigned */int vlen, int
    // flags,
    // timespec tmo);

    /**
     * Put the current value for socket FD's option OPTNAME at protocol level LEVEL into OPTVAL
     * (which is *OPTLEN bytes long), and set *OPTLEN to the value's actual length. Returns 0 on
     * success, -1 for errors.
     */
    @CFunction
    public static native int getsockopt(int fd, int level, int optname, PointerBase optval, CIntPointer optlen);

    /**
     * Set socket FD's option OPTNAME at protocol level LEVEL to *OPTVAL (which is OPTLEN bytes
     * long). Returns 0 on success, -1 for errors.
     */
    @CFunction
    public static native int setsockopt(int fd, int level, int optname, PointerBase optval, int optlen);

    /**
     * Prepare to accept connections on socket FD. N connection requests will be queued before
     * further requests are refused. Returns 0 on success, -1 for errors.
     */
    @CFunction
    public static native int listen(int fd, int n);

    /**
     * Await a connection on socket FD. When a connection arrives, open a new socket to communicate
     * with it, set *ADDR (which is *ADDR_LEN bytes long) to the address of the connecting peer and
     * *ADDR_LEN to the address's actual length, and return the new socket's descriptor, or -1 for
     * errors.
     */
    @CFunction
    public static native int accept(int fd, sockaddr addr, CIntPointer addr_len);

    /** Similar to 'accept' but takes an additional parameter to specify flags. */
    @CFunction
    public static native int accept4(int fd, sockaddr addr, CIntPointer addr_len, int flags);

    /**
     * Shut down all or part of the connection open on socket FD. HOW determines what to shut down:
     * SHUT_RD = No more receptions; SHUT_WR = No more transmissions; SHUT_RDWR = No more receptions
     * or transmissions. Returns 0 on success, -1 for errors.
     */
    @CFunction
    public static native int shutdown(int fd, int how);

    /** Determine whether socket is at a out-of-band mark. */
    @CFunction
    public static native int sockatmark(int fd);

    /**
     * FDTYPE is S_IFSOCK or another S_IF* macro defined in <sys/stat.h>; returns 1 if FD is open on
     * an object of the indicated type, 0 if not, or -1 for errors (setting errno).
     */
    @CFunction
    public static native int isfdtype(int fd, int fdtype);
}
