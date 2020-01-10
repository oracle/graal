/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.instrument;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import com.oracle.truffle.api.Option;

@Option.Group(LSPInstrument.ID)
public final class LSOptions {

    private LSOptions() {
        // no instances
    }

    @Option(help = "Enable features for language developers, e.g. hovering code snippets shows AST related information like the node class or tags. (default:false)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> DeveloperMode = new OptionKey<>(false);

    @Option(help = "Include internal sources in goto-definition, references and symbols search. (default:false)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> Internal = new OptionKey<>(false);

    private static final int DEFAULT_PORT = 8123;
    private static final HostAndPort DEFAULT_ADDRESS = new HostAndPort(null, DEFAULT_PORT);

    static final OptionType<HostAndPort> ADDRESS_OR_BOOLEAN = new OptionType<>("[[host:]port]", (address) -> {
        if (address.isEmpty() || address.equals("true")) {
            return DEFAULT_ADDRESS;
        } else {
            return HostAndPort.parse(address);
        }
    }, (Consumer<HostAndPort>) (address) -> address.verify());

    static final OptionType<List<LanguageAndAddress>> DELEGATES = new OptionType<>("[languageId@][[host:]port],...", (addresses) -> {
        if (addresses.isEmpty()) {
            return Collections.emptyList();
        }
        String[] array = addresses.split(",");
        List<LanguageAndAddress> hostPorts = new ArrayList<>(array.length);
        for (String address : array) {
            hostPorts.add(LanguageAndAddress.parse(address));
        }
        return hostPorts;
    }, (Consumer<List<LanguageAndAddress>>) (addresses) -> addresses.forEach((address) -> address.verify()));

    @Option(name = "", help = "Start the Language Server on [[host:]port]. (default: <loopback address>:" + DEFAULT_PORT + ")", category = OptionCategory.USER) //
    static final OptionKey<HostAndPort> Lsp = new OptionKey<>(DEFAULT_ADDRESS, ADDRESS_OR_BOOLEAN);

    @Option(help = "Requested maximum length of the Socket queue of incoming connections. (default: -1)", category = OptionCategory.EXPERT) //
    static final OptionKey<Integer> SocketBacklogSize = new OptionKey<>(-1);

    @Option(help = "Delegate language servers", category = OptionCategory.USER) //
    static final OptionKey<List<LanguageAndAddress>> Delegates = new OptionKey<>(Collections.emptyList(), DELEGATES);

    static final class HostAndPort {

        private final String host;
        private String portStr;
        private int port;
        private InetAddress inetAddress;

        private HostAndPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private HostAndPort(String host, String portStr) {
            this.host = host;
            this.portStr = portStr;
        }

        static HostAndPort parse(String address) {
            int colon = address.indexOf(':');
            String port;
            String host;
            if (colon >= 0) {
                port = address.substring(colon + 1);
                host = address.substring(0, colon);
            } else {
                port = address;
                host = null;
            }
            return new HostAndPort(host, port);
        }

        void verify() {
            // Check port:
            if (port == 0) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Port is not a number: " + portStr);
                }
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid port number: " + port);
            }
            // Check host:
            if (host != null && !host.isEmpty()) {
                try {
                    inetAddress = InetAddress.getByName(host);
                } catch (UnknownHostException ex) {
                    throw new IllegalArgumentException(ex.getLocalizedMessage(), ex);
                }
            }
        }

        String getHostPort() {
            String hostName = host;
            if (hostName == null || hostName.isEmpty()) {
                if (inetAddress != null) {
                    hostName = inetAddress.toString();
                } else {
                    hostName = InetAddress.getLoopbackAddress().toString();
                }
            }
            return hostName + ":" + port;
        }

        InetSocketAddress createSocket() {
            InetAddress ia;
            if (inetAddress == null) {
                ia = InetAddress.getLoopbackAddress();
            } else {
                ia = inetAddress;
            }
            return new InetSocketAddress(ia, port);
        }
    }

    static final class LanguageAndAddress {

        private final String languageId;
        private final HostAndPort address;

        private LanguageAndAddress(String languageId, HostAndPort address) {
            this.languageId = languageId;
            this.address = address;
        }

        static LanguageAndAddress parse(String la) {
            int at = la.indexOf('@');
            if (at < 0) {
                return new LanguageAndAddress(null, HostAndPort.parse(la));
            } else {
                return new LanguageAndAddress(la.substring(0, at), HostAndPort.parse(la.substring(at + 1)));
            }
        }

        void verify() {
            if (languageId != null && languageId.isEmpty()) {
                throw new IllegalArgumentException("Unknown empty language specified.");
            }
            address.verify();
        }

        String getLanguageId() {
            return languageId;
        }

        HostAndPort getAddress() {
            return address;
        }
    }
}
