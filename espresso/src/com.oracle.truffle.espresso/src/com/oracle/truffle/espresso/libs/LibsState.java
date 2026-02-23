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

import static com.oracle.truffle.espresso.io.TruffleIO.INVALID_FD;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.jni.StrongHandles;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.Management;

/**
 * Class for maintaining state and providing utility in EspressoLibs mode. See
 * {@link com.oracle.truffle.espresso.ffi.EspressoLibsNativeAccess}
 */
public class LibsState {
    private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, LibsState.class);

    /**
     * Generates handles for a TruffleSelector to access the host Selector.
     */
    private final StrongHandles<Selector> truffleSelectorHandles = new StrongHandles<>();

    /**
     * Mapping from guestHandle and channelFD to SelectionKey for the TruffleSelector.
     */
    private final ConcurrentHashMap<Long, SelectionKey> truffleSelectorSelectionKeys = new ConcurrentHashMap<>();

    /**
     * Mapping from SelectionKey to id,fd pair for the TruffleSelector.
     */
    private final ConcurrentHashMap<SelectionKey, Long> truffleSelectorIdFd = new ConcurrentHashMap<>();

    private final EspressoContext context;

    public final LibsStateNet net;

    public LibsState(EspressoContext context, LibsMeta lMeta) {
        this.context = context;
        this.net = (context.getEnv().isSocketIOAllowed()) ? new LibsStateNet(context, lMeta) : null;
    }

    public static TruffleLogger getLogger() {
        return logger;
    }

    @TruffleBoundary
    private EspressoException throwInternalError(String msg) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, msg);
    }

    public void checkCreateProcessAllowed() {
        if (!context.getEnv().isCreateProcessAllowed()) {
            throw Throw.throwSecurityException("process creation is not allowed!", context);
        }
    }

    public void checkManagement() {
        if (!context.getEspressoEnv().EnableManagement) {
            throw Throw.throwSecurityException("Management is disabled", context);
        }
    }

    @TruffleBoundary
    public Management checkAndGetManagement() {
        checkManagement();
        return getManagement();
    }

    @TruffleBoundary
    public Management getManagement() {
        Management management = context.getVM().getManagement();
        if (management == null) {
            // management is only null if Management is disabled
            throw EspressoError.shouldNotReachHere();
        }
        return management;
    }

    /**
     * Opens a host Selector for the guest TruffleSelector and generates a handle for it.
     *
     * @return the handle
     */
    public long handlifyTruffleSelector() {
        try {
            return truffleSelectorHandles.handlify(Selector.open());
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Used for retrieving the TruffleSelector's host selector associated with the given id.
     */
    @TruffleBoundary
    public Selector selectorGetHostSelector(int selectorId) {
        Selector selector = truffleSelectorHandles.getObject(selectorId);
        checkSelector(selector);
        return selector;
    }

    public void checkSelector(Selector selector) {
        if (selector == null || !selector.isOpen()) {
            throw throwInternalError("The selector associated with the given id is invalid!");
        }
    }

    /**
     * Checks the validity of the {@code ops} for the given {@code selectableChannel}.
     *
     * @param selectableChannel the selectableChannel which defines the valid operations
     * @param ops the int ops to check their validity. Should be one of the flags defined in
     *            {@link SelectionKey} e.g. {@link SelectionKey#OP_READ}
     */
    public void checkValidOps(SelectableChannel selectableChannel, int ops) {
        if ((~selectableChannel.validOps() & ops) != 0) {
            throw Throw.throwIOException("operations associated with SelectionKey are not valid", context);
        }
    }

    @TruffleBoundary
    public void selectorPutSelectionKey(int id, int fd, SelectionKey selKey) {
        long key = ((long) id << 32) | (fd & 0xFFFF_FFFFL);
        SelectionKey previousSelKey = truffleSelectorSelectionKeys.put(key, selKey);
        Long previousIdFd = truffleSelectorIdFd.put(selKey, key);
        // sanity check: SelectionKey <==> (SelectorId,ChannelFD)
        assert previousSelKey == null || previousSelKey == selKey;
        assert previousIdFd == null || previousIdFd == key;
    }

    @TruffleBoundary
    public SelectionKey selectorGetSelectionKey(int id, int fd) {
        long key = ((long) id << 32) | (fd & 0xFFFF_FFFFL);
        return truffleSelectorSelectionKeys.get(key);
    }

    /**
     * retrieves the fd associated with the SelectionKey for the TruffleSelector.
     *
     * @param key the SelectionKey
     * @return the fd associated with the key or {@link TruffleIO#INVALID_FD} if not found.
     */
    @TruffleBoundary
    public int selectorSelectionKeyGetFd(SelectionKey key) {
        Long idFd = truffleSelectorIdFd.get(key);
        if (idFd == null) {
            return INVALID_FD;
        }
        // extract the fd (the lower 32 bits)
        return (int) idFd.longValue();
    }

    /**
     * Register fd with the selector or sets the interest ops of the corresponding SelectionKey for
     * the TruffleSelector.
     */
    public void selectorRegisterEvents(Selector selector, int selectorId, int fd, int ops, TruffleIO io) {
        // this is synchronized from outside per guest SelectionKey
        SelectionKey key = selectorGetSelectionKey(selectorId, fd);
        if (key != null) {
            checkValidOps(key.channel(), ops);
            try {
                if (key.interestOps() != ops) {
                    key.interestOps(ops);
                }
            } catch (CancelledKeyException e) {
                throw JavaSubstitution.shouldNotReachHere("CancelledKeyException! This should not happen as canceling and registering a host selection key is synchronized!");
            }
        } else {
            key = io.register(fd, selector, ops);
            selectorPutSelectionKey(selectorId, fd, key);
        }
    }

    /**
     * Performs the select operation.
     *
     * @param selector to call select on.
     * @param timeout how we should select: {@code timeout == -1} blocks indefinitely,
     *            {@code timeout == 0} returns immediately and {@code timeout > 0} blocks for the
     *            given amount of time.
     * @return number of events that were selected
     */
    public int doSelect(Selector selector, long timeout) {
        try {
            if (timeout == 0) {
                return selector.selectNow();
            } else if (timeout == -1) {
                return selector.select();
            } else if (timeout > 0) {
                return selector.select(timeout);
            }
            throw Throw.throwIOException("timeout should be >= -1", context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    @TruffleBoundary
    public void selectorDeregister(int id, int fd) {
        // this is synchronized from outside per guest SelectionKey
        long key = ((long) id << 32) | (fd & 0xFFFF_FFFFL);
        SelectionKey selKey = truffleSelectorSelectionKeys.remove(key);
        if (selKey != null) {
            truffleSelectorIdFd.remove(selKey);
            selKey.cancel();
        }
    }

    public void freeSelector(int selectorId) {
        truffleSelectorHandles.freeHandle(selectorId);
        cleanAllSelectorEntries(selectorId);
    }

    private void cleanAllSelectorEntries(int selectorId) {
        /*
         * The ConcurrentHashMap gives us a weakly consistent iterator, meaning we might or might
         * not see updates to the map from other threads. Even though this method is synchronized on
         * the guest Selector (this), we cannot guarantee that other threads will not act on entries
         * with keySelectorId == selectorId, as sun.nio.ch.TruffleSelector.setEventOps only
         * synchronizes per SelectionKey. Below, we argue why such updates will be properly freed to
         * avoid memory leaks.
         *
         * The only way another thread would add an entry to truffleSelectorSelectionKeys with
         * keySelectorId == selectorId while we are cleaning is if a new channel gets registered
         * with the relevant Selector via
         * com.oracle.truffle.espresso.libs.libnio.impl.Target_sun_nio_ch_TruffleSelector.register.
         * However, such a registration would go through sun.nio.ch.SelectorImpl.register, which
         * checks both before and after the native registration whether the selector was closed by
         * reading its volatile closed field. If the selector was closed, the registration will be
         * natively freed again. Additionally, when reaching this method, we know the closed field
         * was atomically set to true previously in java.nio.channels.spi.AbstractSelector.close.
         * Putting it all together, we can guarantee that if another thread adds an entry with
         * keySelectorId == selectorId to truffleSelectorSelectionKeys while we are cleaning up
         * below, it will eventually read the closed field, which must have been set to true. Thus,
         * the thread will trigger a native deregistration where we will free the relevant entry in
         * com.oracle.truffle.espresso.libs.LibsState.selectorDeregister
         */

        Iterator<Entry<Long, SelectionKey>> it = truffleSelectorSelectionKeys.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Long, SelectionKey> entry = it.next();
            long key = entry.getKey();

            // Correctly extract the high 32 bits
            int keySelectorId = (int) (key >>> 32);

            // If selectorId matches, yield all resources of the entry
            if (keySelectorId == selectorId) {
                SelectionKey sk = entry.getValue();
                it.remove();
                if (sk != null) {
                    sk.cancel();
                    truffleSelectorIdFd.remove(sk);
                }
            }
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
