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
import java.util.ArrayDeque;
import java.util.Deque;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.Throw;
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

@EspressoSubstitutions(type = "Ljava/net/Inet6AddressImpl;", group = LibNet.class)
public final class Target_java_net_Inet6AddressImpl {
    @Substitution(hasReceiver = true)
    @Throws(UnknownHostException.class)
    @SuppressWarnings("unused")
    public static @JavaType(String.class) StaticObject getLocalHostName(@JavaType(internalName = "Ljava/net/Inet6AddressImpl;") StaticObject self,
                    @Inject LibsMeta lMeta, @Inject EspressoContext context) {
        try {
            return lMeta.getMeta().toGuestString(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            throw Throw.throwUnknownHostException(e.getMessage(), context);
        }
    }

    @Substitution(hasReceiver = true)
    @Throws(UnknownHostException.class)
    @SuppressWarnings("unused")
    @TruffleBoundary
    public static @JavaType(InetAddress[].class) StaticObject lookupAllHostAddr(@JavaType(internalName = "Ljava/net/Inet6AddressImpl;") StaticObject self,
                    @JavaType(String.class) StaticObject hostname, int characteristics,
                    @Inject LibsMeta lMeta, @Inject LibsState libsState, @Inject EspressoContext context) {
        Meta meta = context.getMeta();
        if (hostname == StaticObject.NULL) {
            throw lMeta.getMeta().throwExceptionWithMessage(meta.java_lang_NullPointerException, "host argument is null");
        }
        try {
            InetAddress[] allIps = InetAddress.getAllByName(meta.toHostString(hostname));
            // filter and reorder based on characteristics
            boolean ipv4First = (characteristics & (1 << 2)) != 0;
            boolean ipv6First = (characteristics & (1 << 3)) != 0;
            boolean ipv6 = (characteristics & (1 << 1)) != 0;
            boolean ipv4 = (characteristics & (1 << 0)) != 0;
            Deque<StaticObject> filteredIps = new ArrayDeque<>(allIps.length);

            if (!ipv6 && !ipv4) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "characteristics");
            }

            for (InetAddress ip : allIps) {
                if (ip instanceof Inet4Address && ipv4) {
                    if (ipv4First) {
                        filteredIps.addFirst(libsState.net.convertInetAddr(ip));
                    } else {
                        filteredIps.addLast(libsState.net.convertInetAddr(ip));
                    }
                } else if (ip instanceof Inet6Address && ipv6) {
                    if (ipv6First) {
                        filteredIps.addFirst(libsState.net.convertInetAddr(ip));
                    } else {
                        filteredIps.addLast(libsState.net.convertInetAddr(ip));
                    }
                }
            }
            StaticObject[] arr = filteredIps.toArray(StaticObject.EMPTY_ARRAY);
            return context.getAllocator().wrapArrayAs(lMeta.net.java_net_InetAddress.array(), arr);
        } catch (UnknownHostException e) {
            throw Throw.throwUnknownHostException(e.getMessage(), context);
        }
    }
}
