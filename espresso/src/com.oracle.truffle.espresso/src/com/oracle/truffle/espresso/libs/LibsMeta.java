/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.ALL;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.DiffVersionLoadHelper;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class LibsMeta implements ContextAccess {
    private final EspressoContext context;
    private final Meta meta;
    // Checkstyle: stop field name check
    // libzip
    public final ObjectKlass java_util_zip_CRC32;
    public final Field HIDDEN_CRC32;
    public final ObjectKlass java_util_zip_Inflater;
    public final Field java_util_zip_Inflater_inputConsumed;
    public final Field java_util_zip_Inflater_outputConsumed;
    public final ObjectKlass java_util_zip_DataFormatException;
    // Checkstyle: resume field name check

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public Meta getMeta() {
        return meta;
    }

    public LibsMeta(EspressoContext ctx) {
        this.context = ctx;
        this.meta = context.getMeta();

        // libzip
        java_util_zip_CRC32 = knownKlass(EspressoSymbols.Types.java_util_zip_CRC32);
        HIDDEN_CRC32 = diff().field(ALL, EspressoSymbols.Names.HIDDEN_CRC32, EspressoSymbols.Types._int).maybeHiddenfield(java_util_zip_CRC32);
        java_util_zip_Inflater = knownKlass(EspressoSymbols.Types.java_util_zip_Inflater);
        java_util_zip_DataFormatException = knownKlass(EspressoSymbols.Types.java_util_zip_DataFormatException);
        java_util_zip_Inflater_inputConsumed = java_util_zip_Inflater.requireDeclaredField(EspressoSymbols.Names.inputConsumed, EspressoSymbols.Types._int);
        java_util_zip_Inflater_outputConsumed = java_util_zip_Inflater.requireDeclaredField(EspressoSymbols.Names.outputConsumed, EspressoSymbols.Types._int);
    }

    public ObjectKlass knownKlass(Symbol<Type> type) {
        return meta.knownKlass(type);
    }

    private DiffVersionLoadHelper diff() {
        return new DiffVersionLoadHelper(meta);
    }

    public final class LibNetMeta {
        // Checkstyle: stop field name check
        public final ObjectKlass java_net_InetAddress;
        public final Field java_net_InetAddress_holder;
        public final ObjectKlass java_net_InetAddress$InetAddressHolder;
        public final Field java_net_InetAddress$InetAddressHolder_address;

        public final ObjectKlass java_net_InterfaceAddress;
        public final Method java_net_InterfaceAddress_init;
        public final Field java_net_InterfaceAddress_address;
        public final Field java_net_InterfaceAddress_broadcast;
        public final Field java_net_InterfaceAddress_maskLength;

        public final ObjectKlass java_net_Inet4Address;
        public final Method java_net_Inet4Address_init;

        public final ObjectKlass java_net_Inet6Address;
        public final Method java_net_Inet6Address_init;
        public final Field java_net_Inet6Address_holder6;

        public final ObjectKlass java_net_Inet6Address$Inet6AddressHolder;
        public final Field java_net_Inet6Address$Inet6AddressHolder_scope_ifname;
        public final Field java_net_Inet6Address$Inet6AddressHolder_ipaddress;

        public final Method java_net_NetworkInterface_init;
        public final Field java_net_NetworkInterface_displayName;
        public final Field java_net_NetworkInterface_virtual;
        public final Field java_net_NetworkInterface_bindings;
        public final Field java_net_NetworkInterface_parent;
        public final Field java_net_NetworkInterface_childs;

        public final ObjectKlass java_net_InetSocketAddress;
        public final Method java_net_InetSocketAddress_init;

        // Checkstyle: resume field name check

        private LibNetMeta() {
            java_net_InetAddress = knownKlass(EspressoSymbols.Types.java_net_InetAddress);
            java_net_InetAddress_holder = java_net_InetAddress.requireDeclaredField(EspressoSymbols.Names.holder, EspressoSymbols.Types.java_net_InetAddress$InetAddressHolder);
            java_net_InetAddress$InetAddressHolder = knownKlass(EspressoSymbols.Types.java_net_InetAddress$InetAddressHolder);
            java_net_InetAddress$InetAddressHolder_address = java_net_InetAddress$InetAddressHolder.requireDeclaredField(EspressoSymbols.Names.address, EspressoSymbols.Types._int);

            java_net_Inet4Address = knownKlass(EspressoSymbols.Types.java_net_Inet4Address);
            java_net_Inet4Address_init = java_net_Inet4Address.lookupDeclaredMethod(EspressoSymbols.Names._init_, EspressoSymbols.Signatures.java_net_Inet4Address_init_signature);

            java_net_Inet6Address = knownKlass(EspressoSymbols.Types.java_net_Inet6Address);
            java_net_Inet6Address_init = java_net_Inet6Address.lookupDeclaredMethod(EspressoSymbols.Names._init_, EspressoSymbols.Signatures.java_net_Inet6Address_init_signature);
            java_net_Inet6Address_holder6 = java_net_Inet6Address.requireDeclaredField(EspressoSymbols.Names.holder6, EspressoSymbols.Types.java_net_Inet6Address$Inet6AddressHolder);
            java_net_Inet6Address$Inet6AddressHolder = knownKlass(EspressoSymbols.Types.java_net_Inet6Address$Inet6AddressHolder);
            java_net_Inet6Address$Inet6AddressHolder_scope_ifname = java_net_Inet6Address$Inet6AddressHolder.requireDeclaredField(EspressoSymbols.Names.scope_ifname,
                            EspressoSymbols.Types.java_net_NetworkInterface);
            java_net_Inet6Address$Inet6AddressHolder_ipaddress = java_net_Inet6Address$Inet6AddressHolder.requireDeclaredField(EspressoSymbols.Names.ipaddress, EspressoSymbols.Types._byte_array);

            java_net_InterfaceAddress = knownKlass(EspressoSymbols.Types.java_net_InterfaceAddress);
            java_net_InterfaceAddress_init = java_net_InterfaceAddress.lookupDeclaredMethod(EspressoSymbols.Names._init_, EspressoSymbols.Signatures._void);
            java_net_InterfaceAddress_address = java_net_InterfaceAddress.requireDeclaredField(EspressoSymbols.Names.address, EspressoSymbols.Types.java_net_InetAddress);
            java_net_InterfaceAddress_broadcast = java_net_InterfaceAddress.requireDeclaredField(EspressoSymbols.Names.broadcast, EspressoSymbols.Types.java_net_Inet4Address);
            java_net_InterfaceAddress_maskLength = java_net_InterfaceAddress.requireDeclaredField(EspressoSymbols.Names.maskLength, EspressoSymbols.Types._short);

            java_net_NetworkInterface_init = java_net_NetworkInterface.lookupDeclaredMethod(EspressoSymbols.Names._init_, EspressoSymbols.Signatures.java_net_NetworkInterface_init_signature);
            java_net_NetworkInterface_displayName = java_net_NetworkInterface.requireDeclaredField(EspressoSymbols.Names.displayName, EspressoSymbols.Types.java_lang_String);
            java_net_NetworkInterface_virtual = java_net_NetworkInterface.requireDeclaredField(EspressoSymbols.Names.virtual, EspressoSymbols.Types._boolean);
            java_net_NetworkInterface_bindings = java_net_NetworkInterface.requireDeclaredField(EspressoSymbols.Names.bindings, EspressoSymbols.Types.java_net_InterfaceAddress_array);
            java_net_NetworkInterface_parent = java_net_NetworkInterface.requireDeclaredField(EspressoSymbols.Names.parent, EspressoSymbols.Types.java_net_NetworkInterface);
            java_net_NetworkInterface_childs = java_net_NetworkInterface.requireDeclaredField(EspressoSymbols.Names.childs, EspressoSymbols.Types.java_net_NetworkInterface_array);

            java_net_InetSocketAddress = knownKlass(EspressoSymbols.Types.java_net_InetSocketAddress);
            java_net_InetSocketAddress_init = java_net_InetSocketAddress.lookupDeclaredMethod(EspressoSymbols.Names._init_, EspressoSymbols.Signatures.java_net_InetSocketAddress_init_signature);
        }

    }
}
