/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.libs.libnio.impl;

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.FDAccess;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.InformationLeak;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;
import com.oracle.truffle.espresso.substitutions.VersionFilter;

@EspressoSubstitutions(type = "Lsun/nio/ch/Net;", group = LibNio.class)
public final class Target_sun_nio_ch_Net {
    private static final short POLLIN = 0x1;
    private static final short POLLOUT = 0x4;
    private static final short POLLERR = 0x8;
    private static final short POLLHUP = 0x10;
    private static final short POLLNVAL = 0x20;
    private static final short POLLCONN = 0x40;

    @Substitution
    public static void initIDs() {
        // nop
    }

    @Substitution
    public static boolean isIPv6Available0(@Inject InformationLeak iL) {
        return iL.isIPv6Available0();

    }

    @Substitution
    public static short pollinValue(@Inject InformationLeak iL) {
        iL.checkNetworkEnabled();
        return POLLIN;
    }

    @Substitution
    public static short polloutValue(@Inject InformationLeak iL) {
        iL.checkNetworkEnabled();
        return POLLOUT;
    }

    @Substitution
    public static short pollerrValue(@Inject InformationLeak iL) {
        iL.checkNetworkEnabled();
        return POLLERR;
    }

    @Substitution
    public static short pollhupValue(@Inject InformationLeak iL) {
        iL.checkNetworkEnabled();
        return POLLHUP;
    }

    @Substitution
    public static short pollnvalValue(@Inject InformationLeak iL) {
        iL.checkNetworkEnabled();
        return POLLNVAL;
    }

    @Substitution
    public static short pollconnValue(@Inject InformationLeak iL) {
        iL.checkNetworkEnabled();
        return POLLCONN;
    }

    @Substitution
    public static int isExclusiveBindAvailable(@Inject InformationLeak iL) {
        return iL.isExclusvieBindAvailable();
    }

    @Substitution
    public static boolean isReusePortAvailable0(@Inject InformationLeak iL) {
        return iL.isReusePortAvailable0();
    }

    @Substitution
    @SuppressWarnings("unused")
    public static int socket0(boolean preferIPv6, boolean stream, boolean reuse,
                    boolean fastLoopback,
                    @Inject TruffleIO io) {
        return io.openSocket(preferIPv6, stream, reuse);
    }

    @Substitution
    @Throws(IOException.class)
    @TruffleBoundary
    public static int accept(@JavaType(FileDescriptor.class) StaticObject fd, @JavaType(FileDescriptor.class) StaticObject newfd, @JavaType(InetSocketAddress[].class) StaticObject isaa,
                    @Inject TruffleIO io, @Inject LibsState libsState, @Inject LibsMeta lMeta, @Inject EspressoContext ctx) {
        // accept connection & populate fd.
        SocketAddress[] clientSaArr = new SocketAddress[1];
        int retCode = io.accept(fd, FDAccess.forFileDescriptor(), newfd, clientSaArr, libsState);
        if (retCode < 0) {
            return retCode;
        }

        if (clientSaArr[0] instanceof InetSocketAddress clientIsa) {
            // convert to guest-object and populate argument-array
            @JavaType(InetAddress.class)
            StaticObject guestInetAddress = libsState.net.convertInetAddr(clientIsa.getAddress());

            @JavaType(InetSocketAddress.class)
            StaticObject guestSocket = lMeta.net.java_net_InetSocketAddress.allocateInstance(ctx);
            // constructor call for guest-object-conversion
            lMeta.net.java_net_InetSocketAddress_init.invokeDirectSpecial(guestSocket, guestInetAddress,
                            clientIsa.getPort());

            @JavaType(InetSocketAddress.class)
            StaticObject[] argArray = isaa.unwrap(ctx.getLanguage());
            // avoid check for non-emptiness since at every call site an array of length 1 is
            // provided!
            argArray[0] = guestSocket;
        } else {
            // remote Address is UnixDomainSocketAddress and IPC is not implemented.
            throw JavaSubstitution.unimplemented();
        }
        // return 1 as in the native Method
        return 1;
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    @TruffleBoundary
    public static int connect0(boolean preferIPv6, @JavaType(FileDescriptor.class) StaticObject fd,
                    @JavaType(InetAddress.class) StaticObject remote,
                    int remotePort, @Inject TruffleIO io, @Inject LibsState libsState) {
        InetAddress remoteAddress = libsState.net.fromGuestInetAddress(remote);
        SocketAddress remoteSocket = new InetSocketAddress(remoteAddress, remotePort);
        return io.connect(fd, FDAccess.forFileDescriptor(), remoteSocket) ? 1 : io.ioStatusSync.UNAVAILABLE;
    }

    @Substitution
    @Throws(IOException.class)
    @TruffleBoundary
    public static void shutdown(@JavaType(FileDescriptor.class) StaticObject fd, int how,
                    @Inject TruffleIO io) {
        io.shutdownSocketChannel(fd, FDAccess.forFileDescriptor(), how);
    }

    @Substitution(languageFilter = VersionFilter.Java25OrLater.class)
    public static boolean shouldShutdownWriteBeforeClose0() {
        // returns false on linux.
        return false;
    }

    @Substitution
    @Throws(IOException.class)
    public static void bind0(@JavaType(FileDescriptor.class) StaticObject fd, boolean preferIPv6,
                    boolean useExclBind, @JavaType(InetAddress.class) StaticObject addr,
                    int port,
                    @Inject TruffleIO io, @Inject LibsState libsState) {
        io.bind(fd, FDAccess.forFileDescriptor(), preferIPv6, useExclBind, addr, port, libsState);
    }

    @Substitution
    @Throws(IOException.class)
    public static void listen(@JavaType(FileDescriptor.class) StaticObject fd, int backlog,
                    @Inject TruffleIO io) {
        io.listenTCP(fd, FDAccess.forFileDescriptor(), backlog);
    }

    @Substitution
    @Throws(IOException.class)
    public static @JavaType(InetAddress.class) StaticObject localInetAddress(@JavaType(FileDescriptor.class) StaticObject fd, @Inject TruffleIO io) {
        return io.getLocalAddress(fd, FDAccess.forFileDescriptor());
    }

    @Substitution
    @Throws(IOException.class)
    public static int localPort(@JavaType(FileDescriptor.class) StaticObject fd, @Inject TruffleIO io) {
        return io.getPort(fd, FDAccess.forFileDescriptor());
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings({"unused", "unchecked"})
    @TruffleBoundary
    public static void setIntOption0(@JavaType(FileDescriptor.class) StaticObject fd, boolean mayNeedConversion,
                    int level, int opt, int arg, boolean isIPv6,
                    @Inject EspressoContext ctx, @Inject TruffleIO io) {
        // We set the option over the public NetworkChannel API, thus the low-level Plattform
        // specific arguments like mayNeedConversion and isIpv6 aren't needed

        // recover SocketOption and do Type-Conversion
        SocketOption<?> socketOption = getSocketOption(level, opt, ctx);
        Class<?> type = socketOption.type();
        if (type == Integer.class) {
            SocketOption<Integer> intSocketOption = (SocketOption<Integer>) socketOption;
            io.setSocketOption(fd, FDAccess.forFileDescriptor(), intSocketOption, arg);
        } else if (type == Boolean.class) {
            SocketOption<Boolean> boolSocketOption = (SocketOption<Boolean>) socketOption;
            io.setSocketOption(fd, FDAccess.forFileDescriptor(), boolSocketOption, arg == 1 ? Boolean.TRUE : Boolean.FALSE);
        } else {
            // SocketOptions are Integer or Boolean
            throw JavaSubstitution.shouldNotReachHere();
        }
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings({"unused", "unchecked"})
    @TruffleBoundary
    public static int getIntOption0(@JavaType(FileDescriptor.class) StaticObject fd, boolean mayNeedConversion,
                    int level, int opt,
                    @Inject EspressoContext ctx, @Inject TruffleIO io) {
        // We get the option over the public NetworkChannel API, thus the low-level Plattform
        // mayNeedConversion and isIpv6 aren't needed

        // recover SocketOption and do Type-Conversion
        SocketOption<?> socketOption = getSocketOption(level, opt, ctx);
        Class<?> type = socketOption.type();
        if (type == Integer.class) {
            SocketOption<Integer> intSocketOption = (SocketOption<Integer>) socketOption;
            return io.getSocketOption(fd, FDAccess.forFileDescriptor(), intSocketOption);
        } else if (type == Boolean.class) {
            SocketOption<Boolean> boolSocketOption = (SocketOption<Boolean>) socketOption;
            return io.getSocketOption(fd, FDAccess.forFileDescriptor(), boolSocketOption) ? 1 : 0;
        } else {
            // SocketOptions are Integer or Boolean
            throw JavaSubstitution.shouldNotReachHere();
        }
    }

    @Substitution
    @Throws(IOException.class)
    public static int available(@JavaType(FileDescriptor.class) StaticObject fd, @Inject TruffleIO io) {
        return io.available(fd, FDAccess.forFileDescriptor());
    }

    private static SocketOption<?> getSocketOption(int level, int opt, EspressoContext ctx) {
        switch (level) {
            case 0:
                switch (opt) {
                    case 1:
                        return StandardSocketOptions.IP_TOS;
                    case 32:
                        return StandardSocketOptions.IP_MULTICAST_IF;
                    case 33:
                        return StandardSocketOptions.IP_MULTICAST_TTL;
                    case 34:
                        return StandardSocketOptions.IP_MULTICAST_LOOP;
                }
                break;
            case 1:
                switch (opt) {
                    case 2:
                        return StandardSocketOptions.SO_REUSEADDR;
                    case 6:
                        return StandardSocketOptions.SO_BROADCAST;
                    case 7:
                        return StandardSocketOptions.SO_SNDBUF;
                    case 8:
                        return StandardSocketOptions.SO_RCVBUF;
                    case 9:
                        return StandardSocketOptions.SO_KEEPALIVE;
                    case 10:
                        // Would be a ExtendedSocketOption.SO_OOBINLINE, however
                        // ExtendedOption are package private so we cannot easily access them. For
                        // Set and GetOption of Net Extended Option aren't accessed over the native
                        // world anyway thus we shouldn't reach here
                        throw JavaSubstitution.unimplemented();
                    case 13:
                        return StandardSocketOptions.SO_LINGER;
                    case 15:
                        return StandardSocketOptions.SO_REUSEPORT;
                }
                break;
            case 41:
                switch (opt) {
                    case 17:
                        return StandardSocketOptions.IP_MULTICAST_IF;
                    case 18:
                        return StandardSocketOptions.IP_MULTICAST_TTL;
                    case 19:
                        return StandardSocketOptions.IP_MULTICAST_LOOP;
                    case 67:
                        return StandardSocketOptions.IP_TOS;
                }
                break;
        }
        if (level == 6 && opt == 1) {
            return StandardSocketOptions.TCP_NODELAY;
        }
        throw Throw.throwUnsupported("Unsupported SocketOption: level = " + level + ", opt = " + opt, ctx);
    }
}
