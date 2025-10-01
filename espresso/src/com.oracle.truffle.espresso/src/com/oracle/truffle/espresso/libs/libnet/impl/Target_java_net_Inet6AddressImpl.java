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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libnet.LibNet;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibNet.class)
public final class Target_java_net_Inet6AddressImpl {
    @Substitution(hasReceiver = true)
    @Throws(UnknownHostException.class)
    @SuppressWarnings("unused")
    public static @JavaType(String.class) StaticObject getLocalHostName(@JavaType(internalName = "Ljava/net/Inet6AddressImpl;") StaticObject self,
                    @Inject Meta meta) {
        try {
            return meta.toGuestString(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            return meta.toGuestString("localhost");
        }
    }

    @Substitution(hasReceiver = true)
    @Throws(UnknownHostException.class)
    @SuppressWarnings("unused")
    @TruffleBoundary
    public static @JavaType(InetAddress[].class) StaticObject lookupAllHostAddr(@JavaType(internalName = "Ljava/net/Inet6AddressImpl;") StaticObject self,
                    @JavaType(String.class) StaticObject hostname, int characteristics,
                    @Inject LibsMeta lMeta, @Inject LibsState libsState, @Inject EspressoContext context, @Inject TruffleIO io) {
        Meta meta = context.getMeta();
        if (hostname == StaticObject.NULL) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NullPointerException, "host argument is null");
        }
        try {
            InetAddress[] allIps = InetAddress.getAllByName(meta.toHostString(hostname));
            // filter and reorder based on characteristics
            boolean ipv4First = (characteristics & (1 << io.inetAddressResolverLookupPolicySync.IPV4_FIRST)) != 0;
            boolean ipv6First = (characteristics & (1 << io.inetAddressResolverLookupPolicySync.IPV6_FIRST)) != 0;
            boolean ipv6 = (characteristics & (1 << io.inetAddressResolverLookupPolicySync.IPV6)) != 0;
            boolean ipv4 = (characteristics & (1 << io.inetAddressResolverLookupPolicySync.IPV4)) != 0;
            if (!ipv6 && !ipv4) {
                ipv4 = true;
                ipv6 = true;
            }
            StaticObject[] orderedArray;
            if (ipv4First == ipv6First) {
                // Just return in system order.
                orderedArray = new StaticObject[allIps.length];
                for (int i = 0; i < allIps.length; i++) {
                    InetAddress ip = allIps[i];
                    if (ip instanceof Inet4Address && ipv4) {
                        orderedArray[i] = (libsState.net.convertInetAddr(ip));
                    } else if (ip instanceof Inet6Address && ipv6) {
                        orderedArray[i] = (libsState.net.convertInetAddr(ip));
                    }
                }
            } else {
                // reorder the array before returning
                orderedArray = orderArray(allIps, ipv4First, ipv6First, ipv4, ipv6, libsState);
            }
            return context.getAllocator().wrapArrayAs(lMeta.net.java_net_InetAddress.array(), orderedArray);
        } catch (UnknownHostException e) {
            throw Throw.throwUnknownHostException(e.getMessage(), context);
        }
    }

    private static StaticObject[] orderArray(InetAddress[] allIps, boolean ipv4First, boolean ipv6First, boolean ipv4, boolean ipv6, LibsState libsState) {
        // pre-conditon from call-site
        assert ipv4First != ipv6First;
        /*
         * We do a double pass over the allIps array. In the first pass, we just calculate the
         * number of ipv4 or ipv6 addresses respectively. In the second pass, we use those counts as
         * indices into the final array to determine the starting position for the respective
         * category based on the desired ordering.
         */
        int ipv4Count = 0;
        int ipv6Count = 0;
        // first pass
        for (InetAddress ip : allIps) {
            if (ip instanceof Inet4Address && ipv4) {
                ipv4Count += 1;
            } else if (ip instanceof Inet6Address && ipv6) {
                ipv6Count += 1;
            }
        }
        // determine starting positions based on the desired ordering.
        StaticObject[] orderedArray = new StaticObject[ipv4Count + ipv6Count];
        int ipv4Index = 0;
        int ipv6Index = 0;
        if (ipv4First && !ipv6First) {
            ipv6Index = ipv4Count;
        } else if (!ipv4First && ipv6First) {
            ipv4Index = ipv6Count;
        }
        // second pass
        for (InetAddress ip : allIps) {
            if (ip instanceof Inet4Address && ipv4) {
                orderedArray[ipv4Index] = libsState.net.convertInetAddr(ip);
                ipv4Index++;
            } else if (ip instanceof Inet6Address && ipv6) {
                orderedArray[ipv6Index] = libsState.net.convertInetAddr(ip);
                ipv6Index++;
            }
        }
        return orderedArray;
    }
}
