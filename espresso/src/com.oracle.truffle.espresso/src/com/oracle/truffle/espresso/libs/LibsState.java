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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Inflater;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
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

    public final LibsStateNet net;

    public LibsState(EspressoContext context, LibsMeta lMeta) {
        this.net = (context.getLanguage().enableNetworking()) ? new LibsStateNet(context, lMeta) : null;
    }

    public TruffleLogger getLogger() {
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

    public final class LibsStateNet {
        private final EspressoContext context;
        private final LibsMeta lMeta;
        // used for guestHandle to hostSelector
        private final StrongHandles<Selector> handle2Selector = new StrongHandles<>();
        // mapping from guestHandle and channelFD to SelectionKey and the reversed one
        private final ConcurrentHashMap<Long, SelectionKey> hostSelectionKeys = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<SelectionKey, Integer> selectionKeysToFd = new ConcurrentHashMap<>();

        public LibsStateNet(EspressoContext context, LibsMeta lMeta) {
            this.context = context;
            this.lMeta = lMeta;
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
         * @return The Host InetAddress
         */
        public InetAddress fromGuestInetAddress(@JavaType(InetAddress.class) StaticObject addr) {
            InetAddress inetAddress = null;
            try {
                if (InterpreterToVM.instanceOf(addr, lMeta.net.java_net_Inet4Address)) {
                    // ipv4 case retrieve int address, convert to bytes and use getByAddress
                    @JavaType(internalName = "Ljava/net/InetAddress$InetAddressHolder;")
                    StaticObject ipaHolder = lMeta.net.java_net_InetAddress_holder.getObject(addr);
                    int address = lMeta.net.java_net_InetAddress$InetAddressHolder_address.getInt(ipaHolder);
                    byte[] byteAddress = new byte[]{
                                    (byte) ((address >> 24) & 0xFF),
                                    (byte) ((address >> 16) & 0xFF),
                                    (byte) ((address >> 8) & 0xFF),
                                    (byte) (address & 0xFF)
                    };
                    inetAddress = InetAddress.getByAddress(byteAddress);

                } else if (InterpreterToVM.instanceOf(addr, lMeta.net.java_net_Inet6Address)) {
                    // ipv6 --> get the ip6 holder to retrieve the Ip6Address and use getByAddress
                    @JavaType(internalName = "Ljava/net/Inet6Address$Inet6AddressHolder;")
                    StaticObject ipv6Holder = lMeta.net.java_net_Inet6Address_holder6.getObject(addr);
                    @JavaType(byte[].class)
                    StaticObject ip6Addr = lMeta.net.java_net_Inet6Address$Inet6AddressHolder_ipaddress.getObject(ipv6Holder);
                    inetAddress = InetAddress.getByAddress(ip6Addr.unwrap(context.getLanguage()));
                } else {
                    throw JavaSubstitution.shouldNotReachHere();
                }
                return inetAddress;
            } catch (IOException e) {
                throw Throw.throwIOException(e, context);
            }
        }

        private @JavaType StaticObject convertIpv4Addr(Inet4Address ipv4Addr) {
            // in the original Native code the call the void constructor and set the address. To me
            // providing full Information (Address and HostName) is the better approach.
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
            // in the original Native code the call the void constructor and set the address. To me
            // providing more Information is the better approach.
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

            // we also need to set the scope_ifName as in the native code (if applicable)
            if (ipv6Addr.getScopeId() != 0 && netIF != StaticObject.NULL) {
                @JavaType(internalName = "Ljava/net/Inet6Address$Inet6AddressHolder;")
                StaticObject ipv6Holder = lMeta.net.java_net_Inet6Address_holder6.getObject(guestInetAddr);
                lMeta.net.java_net_Inet6Address$Inet6AddressHolder_scope_ifname.setObject(ipv6Holder, netIF);
            }
            return guestInetAddr;
        }

        public void setFDVal(@JavaType(FileDescriptor.class) StaticObject fd, int fdVal) {
            context.getTruffleIO().java_io_FileDescriptor_fd.setInt(fd, fdVal);
        }

        public void checkValidOps(SelectableChannel selectableChannel, int ops) {
            if ((~selectableChannel.validOps() & ops) != 0) {
                throw Throw.throwIOException("operations associated with SelectionKey are not valid", context);
            }
        }

        @TruffleBoundary
        public void putSelectionKey(int id, int fd, SelectionKey selKey) {
            long key = ((long) id << 32) | (fd & 0xFFFFFFFFL);
            SelectionKey previousSelKey = hostSelectionKeys.put(key, selKey);
            Integer previousFd = selectionKeysToFd.put(selKey, fd);
            // sanity check: SelectionKey <==> (SelectorId,ChannelFD)
            assert previousSelKey == null || previousSelKey == selKey;
            assert previousFd == null || previousFd == fd;
        }

        @TruffleBoundary
        public SelectionKey getSelectionKey(int id, int fd) {
            long key = ((long) id << 32) | (fd & 0xFFFFFFFFL);
            return hostSelectionKeys.get(key);
        }

        @TruffleBoundary
        public int getFdOfSelectionKey(SelectionKey key) {
            return selectionKeysToFd.get(key);
        }

        @TruffleBoundary
        public void removeSelectionKey(int id, int fd) {
            long key = ((long) id << 32) | (fd & 0xFFFFFFFFL);
            SelectionKey selKey = hostSelectionKeys.remove(key);
            selectionKeysToFd.remove(selKey);
        }

        public long handlifySelector() {
            try {
                return handle2Selector.handlify(Selector.open());
            } catch (IOException e) {
                throw Throw.throwIOException(e, context);
            }
        }

        public Selector getSelector(int selectorId) {
            Selector selector = handle2Selector.getObject(selectorId);
            if (selector == null) {
                // Breaks the invariant that all ids are associated with a selector.
                throw JavaSubstitution.shouldNotReachHere();
            }
            return selector;
        }

        /**
         * If the fd is already registered with the selector it updates the interestOps and
         * otherwise registers the fd with the selector.
         */
        public SelectionKey setInterestOpsOrRegister(int selectorId, int fd, int ops, TruffleIO io) {
            Selector selector = getSelector(selectorId);
            SelectionKey key = getSelectionKey(selectorId, fd);
            if (key != null) {
                checkValidOps(key.channel(), ops);
                key.interestOps(ops);
            } else {
                key = io.register(fd, selector, ops);
                putSelectionKey(selectorId, fd, key);
            }
            return key;
        }

        public void freeSelector(int selectorId) {
            handle2Selector.freeHandle(selectorId);
        }
    }
}
