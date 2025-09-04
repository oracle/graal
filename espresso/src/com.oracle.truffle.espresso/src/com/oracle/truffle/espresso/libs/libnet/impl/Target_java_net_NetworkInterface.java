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
package com.oracle.truffle.espresso.libs.libnet.impl;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.libs.InformationLeak;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libnet.LibNet;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibNet.class)
public final class Target_java_net_NetworkInterface {
    @Substitution
    public static void init() {
        // nop
    }

    @Substitution
    @TruffleBoundary
    @Throws(SocketException.class)
    public static @JavaType(NetworkInterface[].class) StaticObject getAll(@Inject EspressoContext ctx, @Inject LibsMeta lMeta) {
        if (!ctx.getEnv().isSocketIOAllowed()) {
            StaticObject[] arr = StaticObject.EMPTY_ARRAY;
            return ctx.getAllocator().wrapArrayAs(lMeta.java_net_NetworkInterface.array(), arr);
        }
        // maintain a map between host and guest InetAddresses to avoid redundant conversions.
        ConcurrentHashMap<InetAddress, @JavaType(InetAddress.class) StaticObject> hostGuestAddrMap = new ConcurrentHashMap<>(32);
        Enumeration<NetworkInterface> netIFs = ctx.getInformationLeak().getNetworkInterfaces();
        List<StaticObject> guestNetIFs = new ArrayList<>();
        while (netIFs.hasMoreElements()) {
            NetworkInterface netIF = netIFs.nextElement();
            guestNetIFs.add(convertNetIF(netIF, StaticObject.NULL, hostGuestAddrMap, ctx, lMeta));
        }
        StaticObject[] arr = guestNetIFs.toArray(StaticObject.EMPTY_ARRAY);
        return ctx.getAllocator().wrapArrayAs(lMeta.java_net_NetworkInterface.array(), arr);
    }

    @Substitution
    @Throws(SocketException.class)
    @SuppressWarnings("unused")
    public static @JavaType(NetworkInterface.class) StaticObject getByName0(@JavaType(String.class) StaticObject name) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(SocketException.class)
    @SuppressWarnings("unused")
    public static @JavaType(NetworkInterface.class) StaticObject getByIndex0(int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(SocketException.class)
    @SuppressWarnings("unused")
    public static boolean boundInetAddress0(@JavaType(InetAddress.class) StaticObject addr) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(SocketException.class)
    public static @JavaType(NetworkInterface.class) StaticObject getByInetAddress0(@JavaType(InetAddress.class) StaticObject addr,
                    @Inject LibsMeta lMeta,
                    @Inject EspressoContext ctx,
                    @Inject LibsState libsState) {
        libsState.net.checkNetworkEnabled();
        InetAddress inetAddress = libsState.net.fromGuestInetAddress(addr, false);
        try {
            NetworkInterface netIF = NetworkInterface.getByInetAddress(inetAddress);
            return convertNetIF(netIF, ctx, lMeta);
        } catch (SocketException e) {
            throw Throw.throwSocketException(e, ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    @Throws(SocketException.class)
    public static boolean isUp0(@JavaType(String.class) StaticObject name, int ind, @Inject EspressoContext ctx) {
        ctx.getLibsState().net.checkNetworkEnabled();
        try {
            return getNetIF(name, ind, ctx).isUp();
        } catch (SocketException e) {
            throw Throw.throwSocketException(e, ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    @Throws(SocketException.class)
    public static boolean isLoopback0(@JavaType(String.class) StaticObject name, int ind, @Inject EspressoContext ctx) {
        ctx.getLibsState().net.checkNetworkEnabled();
        try {
            return getNetIF(name, ind, ctx).isLoopback();
        } catch (SocketException e) {
            throw Throw.throwSocketException(e, ctx);
        }
    }

    @Substitution
    @Throws(SocketException.class)
    @SuppressWarnings("unused")
    public static boolean supportsMulticast0(@JavaType(String.class) StaticObject name, int ind) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(SocketException.class)
    @SuppressWarnings("unused")
    public static boolean isP2P0(@JavaType(String.class) StaticObject name, int ind) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(SocketException.class)
    public static @JavaType(byte[].class) StaticObject getMacAddr0(@SuppressWarnings("unused") @JavaType(byte[].class) StaticObject inAddr, @JavaType(String.class) StaticObject name, int ind,
                    @Inject EspressoContext ctx,
                    @Inject InformationLeak iL) {
        // The inAddr is not used. That's also the case in the native code.
        ctx.getLibsState().net.checkNetworkEnabled();
        byte[] macAddrHost = iL.getMacAddress(getNetIF(name, ind, ctx));
        if (macAddrHost == null) {
            return StaticObject.NULL;
        }
        return ctx.getAllocator().wrapArrayAs(ctx.getMeta()._byte_array, macAddrHost);
    }

    @Substitution
    @Throws(SocketException.class)
    @SuppressWarnings("unused")
    public static int getMTU0(@JavaType(String.class) StaticObject name, int ind) {
        throw JavaSubstitution.unimplemented();
    }

    private static @JavaType(NetworkInterface.class) StaticObject convertNetIF(NetworkInterface netIF,
                    EspressoContext ctx, LibsMeta lMeta) {
        return convertNetIF(netIF, StaticObject.NULL, new ConcurrentHashMap<>(8), ctx, lMeta);
    }

    private static @JavaType(NetworkInterface.class) StaticObject convertNetIF(NetworkInterface netIF,
                    @JavaType(NetworkInterface.class) StaticObject parent,
                    ConcurrentHashMap<InetAddress, @JavaType(InetAddress.class) StaticObject> hostGuestAddrMap,
                    EspressoContext ctx, LibsMeta lMeta) {
        @JavaType(NetworkInterface.class)
        StaticObject guestNetIF = lMeta.java_net_NetworkInterface.allocateInstance(ctx);
        // convert constructor parameters
        @JavaType(InetAddress[].class)
        StaticObject guestInetAddrs = convertInetAddrs(netIF.getInetAddresses(), guestNetIF, hostGuestAddrMap, ctx, lMeta);
        @JavaType(InterfaceAddress[].class)
        StaticObject guestIFAddrs = convertIFAddrs(netIF.getInterfaceAddresses(), guestNetIF, hostGuestAddrMap, ctx, lMeta);
        @JavaType(String.class)
        StaticObject name = lMeta.getMeta().toGuestString(netIF.getName());

        // constructor
        lMeta.net.java_net_NetworkInterface_init.invokeDirectSpecial(
                        /* this */ guestNetIF,
                        /* name */ name,
                        /* index */ netIF.getIndex(),
                        /* addr */ guestInetAddrs);

        lMeta.net.java_net_NetworkInterface_displayName.setObject(guestNetIF, name);
        lMeta.net.java_net_NetworkInterface_virtual.setBoolean(guestNetIF, netIF.isVirtual());
        lMeta.net.java_net_NetworkInterface_bindings.setObject(guestNetIF, guestIFAddrs);

        // convert children if there are any
        if (netIF.getSubInterfaces().hasMoreElements()) {
            @JavaType(NetworkInterface[].class)
            StaticObject guestChilds = convertChilds(netIF.getSubInterfaces(), guestNetIF, hostGuestAddrMap, ctx, lMeta);
            lMeta.net.java_net_NetworkInterface_childs.setObject(guestNetIF, guestChilds);
        }

        if (parent != StaticObject.NULL) {
            lMeta.net.java_net_NetworkInterface_parent.setObject(guestNetIF, parent);
        }

        return guestNetIF;
    }

    private static @JavaType(InetAddress[].class) StaticObject convertInetAddrs(Enumeration<InetAddress> inetAddrs,
                    @JavaType(NetworkInterface.class) StaticObject netIF,
                    ConcurrentHashMap<InetAddress, @JavaType(InetAddress.class) StaticObject> hostGuestAddrMap,
                    EspressoContext ctx, LibsMeta lMeta) {
        List<StaticObject> guestInetAddrs = new ArrayList<>();
        while (inetAddrs.hasMoreElements()) {
            InetAddress hostInetAddr = inetAddrs.nextElement();
            @JavaType(InetAddress.class)
            StaticObject guestInetAddr = convertInetAddr(hostInetAddr, netIF, hostGuestAddrMap, ctx);
            guestInetAddrs.add(guestInetAddr);
        }
        StaticObject[] arr = guestInetAddrs.toArray(StaticObject.EMPTY_ARRAY);
        return ctx.getAllocator().wrapArrayAs(lMeta.net.java_net_InetAddress.array(), arr);
    }

    private static @JavaType StaticObject convertInetAddr(InetAddress inetAddr,
                    @JavaType(NetworkInterface.class) StaticObject netIF,
                    ConcurrentHashMap<InetAddress, @JavaType(InetAddress.class) StaticObject> hostGuestAddrMap,
                    EspressoContext ctx) {
        // check cache first
        if (hostGuestAddrMap.containsKey(inetAddr)) {
            return hostGuestAddrMap.get(inetAddr);
        }
        // not in cache so do full conversion and cache it
        @JavaType(InetAddress.class)
        StaticObject guestInetAddr = ctx.getLibsState().net.convertInetAddr(inetAddr, netIF);
        hostGuestAddrMap.put(inetAddr, guestInetAddr);
        return guestInetAddr;
    }

    private static @JavaType(InterfaceAddress[].class) StaticObject convertIFAddrs(List<InterfaceAddress> iFAddrs,
                    @JavaType(NetworkInterface.class) StaticObject netIF,
                    ConcurrentHashMap<InetAddress, @JavaType(InetAddress.class) StaticObject> hostGuestAddrMap,
                    EspressoContext ctx, LibsMeta lMeta) {
        List<StaticObject> guestIFAddrs = new ArrayList<>(iFAddrs.size());
        for (InterfaceAddress iFAddr : iFAddrs) {
            @JavaType(InterfaceAddress.class)
            StaticObject guestIFAddr = convertIFAddr(iFAddr, netIF, hostGuestAddrMap, ctx, lMeta);
            guestIFAddrs.add(guestIFAddr);
        }
        StaticObject[] arr = guestIFAddrs.toArray(StaticObject.EMPTY_ARRAY);
        return ctx.getAllocator().wrapArrayAs(lMeta.net.java_net_InterfaceAddress.array(), arr);
    }

    private static @JavaType(InterfaceAddress.class) StaticObject convertIFAddr(InterfaceAddress iFAddrs,
                    @JavaType(NetworkInterface.class) StaticObject netIF,
                    ConcurrentHashMap<InetAddress, @JavaType(InetAddress.class) StaticObject> hostGuestAddrMap,
                    EspressoContext ctx, LibsMeta lMeta) {
        @JavaType(InterfaceAddress.class)
        StaticObject guestIFAddr = lMeta.net.java_net_InterfaceAddress.allocateInstance(ctx);
        lMeta.net.java_net_InterfaceAddress_init.invokeDirectSpecial(guestIFAddr);

        @JavaType(InetAddress.class)
        StaticObject addr = convertInetAddr(iFAddrs.getAddress(), netIF, hostGuestAddrMap, ctx);
        lMeta.net.java_net_InterfaceAddress_address.setObject(guestIFAddr, addr);

        if (iFAddrs.getBroadcast() != null) {
            @JavaType(InetAddress.class)
            StaticObject broadcastAddr = convertInetAddr(iFAddrs.getBroadcast(), netIF, hostGuestAddrMap, ctx);
            lMeta.net.java_net_InterfaceAddress_broadcast.setObject(guestIFAddr, broadcastAddr);
        }

        lMeta.net.java_net_InterfaceAddress_maskLength.setShort(guestIFAddr, iFAddrs.getNetworkPrefixLength());
        return guestIFAddr;
    }

    private static @JavaType(NetworkInterface[].class) StaticObject convertChilds(Enumeration<NetworkInterface> childs,
                    @JavaType(NetworkInterface.class) StaticObject parent,
                    ConcurrentHashMap<InetAddress, @JavaType(InetAddress.class) StaticObject> hostGuestAddrMap,
                    EspressoContext ctx, LibsMeta lMeta) {
        List<StaticObject> guestChilds = new ArrayList<>();
        while (childs.hasMoreElements()) {
            NetworkInterface child = childs.nextElement();
            @JavaType(NetworkInterface.class)
            StaticObject guestChild = convertNetIF(child, parent, hostGuestAddrMap, ctx, lMeta);
            guestChilds.add(guestChild);
        }
        StaticObject[] arr = guestChilds.toArray(StaticObject.EMPTY_ARRAY);
        return ctx.getAllocator().wrapArrayAs(lMeta.java_net_NetworkInterface.array(), arr);
    }

    private static NetworkInterface getNetIF(@JavaType(String.class) StaticObject name, int ind, EspressoContext ctx) {
        try {
            NetworkInterface netIF = NetworkInterface.getByIndex(ind);
            if (netIF == null) {
                String hoststring = ctx.getMeta().toHostString(name);
                netIF = hoststring == null ? null : NetworkInterface.getByName(hoststring);
            }
            if (netIF == null) {
                Throw.throwSocketException("Didn't found a NetworkInterface associated with the given name and index", ctx);
            }
            return netIF;
        } catch (SocketException e) {
            throw Throw.throwSocketException(e, ctx);
        }
    }
}
