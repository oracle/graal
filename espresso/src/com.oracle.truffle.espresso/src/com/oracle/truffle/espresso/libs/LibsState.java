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
package com.oracle.truffle.espresso.libs;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.Inflater;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.jni.StrongHandles;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public class LibsState {
    private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, LibsState.class);

    private final StrongHandles<Inflater> handle2Inflater = new StrongHandles<>();

    private final EspressoContext context;

    public final LibsStateNet net;

    public LibsState(EspressoContext context, LibsMeta lMeta) {
        this.context = context;
        this.net = (context.getEnv().isSocketIOAllowed()) ? new LibsStateNet(context, lMeta) : null;
    }

    public static TruffleLogger getLogger() {
        return logger;
    }

    public long handlifyInflater(Inflater i) {
        return handle2Inflater.handlify(i);
    }

    public void cleanInflater(long handle) {
        handle2Inflater.freeHandle(handle);
    }

    public Inflater getInflater(long handle) {
        Inflater inflater = handle2Inflater.getObject(handle);
        if (inflater == null) {
            throw throwInternalError();
        }
        return inflater;
    }

    @TruffleBoundary
    private static EspressoException throwInternalError() {
        Meta meta = EspressoContext.get(null).getMeta();
        return meta.throwExceptionWithMessage(meta.java_lang_InternalError, "the provided handle doesn't correspond to an Inflater");
    }

    public void checkCreateProcessAllowed() {
        if (!context.getEnv().isCreateProcessAllowed()) {
            throw Throw.throwSecurityException("process creation is not allowed!", context);
        }
    }

    public final class LibsStateNet {
        private final EspressoContext context;
        private final LibsMeta lMeta;

        public LibsStateNet(EspressoContext context, LibsMeta lMeta) {
            this.context = context;
            this.lMeta = lMeta;
        }

        public void checkNetworkEnabled() {
            if (!context.getEnv().isSocketIOAllowed()) {
                throw Throw.throwSecurityException("Networking is disabled", context);
            }
        }

        public @JavaType StaticObject convertInetAddr(InetAddress inetAddr) {
            return convertInetAddr(inetAddr, StaticObject.NULL);
        }

        public @JavaType StaticObject convertInetAddr(InetAddress inetAddr,
                        @JavaType(NetworkInterface.class) StaticObject netIF) {
            @JavaType(InetAddress.class)
            StaticObject guestInetAddr = null;
            if (inetAddr instanceof Inet4Address ipv4Addr) {
                // IPv4 address
                guestInetAddr = convertIpv4Addr(ipv4Addr);
            } else if (inetAddr instanceof Inet6Address ipv6Addr) {
                // IPv6 address
                guestInetAddr = convertIpv6Addr(ipv6Addr, netIF);
            }
            Objects.requireNonNull(guestInetAddr);
            return guestInetAddr;
        }

        /**
         * Converts a Guest InetAddress given as a StaticObject to a Host InetAddress.
         *
         * @param addr the Guest InetAddress
         * @param preferIPv6 whether to map a IPv4 address to a IPv6
         * @return The Host InetAddress
         */
        public InetAddress fromGuestInetAddress(@JavaType(InetAddress.class) StaticObject addr, boolean preferIPv6) {
            InetAddress inetAddress = null;
            StaticObject ipaHolder = lMeta.net.java_net_InetAddress_holder.getObject(addr);
            String hostName = context.getMeta().toHostString(lMeta.net.java_net_InetAddress$InetAddressHolder_hostName.getObject(ipaHolder));
            try {
                if (InterpreterToVM.instanceOf(addr, lMeta.net.java_net_Inet6Address)) {
                    if (!context.getInformationLeak().isIPv6Available()) {
                        throw Throw.throwSocketException("ipv6 unavailable", context);
                    }
                    // ipv6 --> get the ip6 holder to retrieve the Ip6Address and use getByAddress
                    @JavaType(internalName = "Ljava/net/Inet6Address$Inet6AddressHolder;")
                    StaticObject ipv6Holder = lMeta.net.java_net_Inet6Address_holder6.getObject(addr);
                    @JavaType(byte[].class)
                    StaticObject ip6Addr = lMeta.net.java_net_Inet6Address$Inet6AddressHolder_ipaddress.getObject(ipv6Holder);
                    int scopeId = lMeta.net.java_net_Inet6Address$Inet6AddressHolder_scope_id.getInt(ipv6Holder);
                    inetAddress = Inet6Address.getByAddress(hostName, ip6Addr.unwrap(context.getLanguage()), scopeId);
                } else if (InterpreterToVM.instanceOf(addr, lMeta.net.java_net_Inet4Address)) {
                    // ipv4 case: Retrieve int address, convert to bytes and use getByAddress
                    @JavaType(internalName = "Ljava/net/InetAddress$InetAddressHolder;")
                    int address = lMeta.net.java_net_InetAddress$InetAddressHolder_address.getInt(ipaHolder);
                    return getInetAddress(hostName, address, preferIPv6);
                } else {
                    throw JavaSubstitution.shouldNotReachHere();
                }
                return inetAddress;
            } catch (IOException e) {
                throw Throw.throwIOException(e, context);
            }
        }

        /**
         * Creates a InetAddress object based on the arguments. If preferIPv6 is set and IPv6 is
         * available, it maps the int IPv4 address to an IPv6 address.
         */
        private InetAddress getInetAddress(String hostName, int address, boolean preferIPv6) throws IOException {
            byte[] byteAddress;
            if (preferIPv6 && context.getInformationLeak().isIPv6Available()) {
                byteAddress = new byte[16];
                Arrays.fill(byteAddress, (byte) 0);
                byteAddress[10] = (byte) 0xff;
                byteAddress[11] = (byte) 0xff;
                byteAddress[12] = (byte) ((address >> 24) & 0xff);
                byteAddress[13] = (byte) ((address >> 16) & 0xff);
                byteAddress[14] = (byte) ((address >> 8) & 0xff);
                byteAddress[15] = (byte) (address & 0xff);
                return Inet6Address.getByAddress(hostName, byteAddress, -1);
            } else {
                byteAddress = new byte[4];
                byteAddress[0] = (byte) ((address >> 24) & 0xFF);
                byteAddress[1] = (byte) ((address >> 16) & 0xFF);
                byteAddress[2] = (byte) ((address >> 8) & 0xFF);
                byteAddress[3] = (byte) (address & 0xFF);
                return InetAddress.getByAddress(hostName, byteAddress);
            }
        }

        private @JavaType StaticObject convertIpv4Addr(Inet4Address ipv4Addr) {
            /*
             * In the original native code, they call the void constructor and set the address.
             * However, providing more Information (address and hostName) is the better approach.
             */
            @JavaType(Inet4Address.class)
            StaticObject guestInetAddr = lMeta.net.java_net_Inet4Address.allocateInstance(context);
            @JavaType(String.class)
            StaticObject hostName = lMeta.getMeta().toGuestString(ipv4Addr.getHostName());
            @JavaType(byte[].class)
            StaticObject addr = context.getAllocator().wrapArrayAs(lMeta.getMeta()._byte_array, ipv4Addr.getAddress());

            lMeta.net.java_net_Inet4Address_init.invokeDirectSpecial(
                            /* this */ guestInetAddr,
                            /* hostName */ hostName,
                            /* address */ addr);

            return guestInetAddr;
        }

        private @JavaType StaticObject convertIpv6Addr(Inet6Address ipv6Addr,
                        @JavaType(NetworkInterface.class) StaticObject netIF) {
            /*
             * In the original native code, they call the void constructor and set the address.
             * However, providing more Information is a better approach.
             */
            @JavaType(Inet6Address.class)
            StaticObject guestInetAddr = lMeta.net.java_net_Inet6Address.allocateInstance(context);
            @JavaType(String.class)
            StaticObject hostName = lMeta.getMeta().toGuestString(ipv6Addr.getHostName());
            byte[] hostByteAddress = ipv6Addr.getAddress();
            @JavaType(byte[].class)
            StaticObject addr = context.getAllocator().wrapArrayAs(lMeta.getMeta()._byte_array, hostByteAddress);

            lMeta.net.java_net_Inet6Address_init.invokeDirectSpecial(
                            /* this */ guestInetAddr,
                            /* hostName */ hostName,
                            /* address */ addr,
                            /* scopeId */ ipv6Addr.getScopeId());

            // We also need to set the scope_ifName as in the native code (if applicable)
            if (ipv6Addr.getScopeId() != 0 && netIF != StaticObject.NULL) {
                @JavaType(internalName = "Ljava/net/Inet6Address$Inet6AddressHolder;")
                StaticObject ipv6Holder = lMeta.net.java_net_Inet6Address_holder6.getObject(guestInetAddr);
                lMeta.net.java_net_Inet6Address$Inet6AddressHolder_scope_ifname.setObject(ipv6Holder, netIF);
            }
            return guestInetAddr;
        }
    }
}
