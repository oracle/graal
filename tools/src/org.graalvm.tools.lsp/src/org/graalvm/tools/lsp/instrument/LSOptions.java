package org.graalvm.tools.lsp.instrument;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import com.oracle.truffle.api.Option;

@Option.Group(LSPInstrument.ID)
public final class LSOptions {

    private LSOptions() {
        // no instances
    }

    @com.oracle.truffle.api.Option(name = "Languagespecific.hacks", help = "Enable language specific hacks to get features which are not supported by some languages yet. (default:true)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> LanguageSpecificHacksOption = new OptionKey<>(true);

    @com.oracle.truffle.api.Option(help = "Enable features for language developers, e.g. hovering code snippets shows AST related information like the node class or tags. (default:false)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> LanguageDeveloperMode = new OptionKey<>(false);

    @com.oracle.truffle.api.Option(help = "Include sources with isInternal()==true in goto-definition, references and symbols search. (default:false)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> IncludeInternalSources = new OptionKey<>(false);

    private static final int DEFAULT_PORT = 8123;
    private static final HostAndPort DEFAULT_ADDRESS = new HostAndPort(null, DEFAULT_PORT);

    static final OptionType<HostAndPort> ADDRESS_OR_BOOLEAN = new OptionType<>("[[host:]port]", DEFAULT_ADDRESS, (address) -> {
        if (address.isEmpty() || address.equals("true")) {
            return DEFAULT_ADDRESS;
        } else {
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
    }, (address) -> address.verify());

    @com.oracle.truffle.api.Option(name = "", help = "Start the Language Server on [[host:]port]. (default: <loopback address>:" + DEFAULT_PORT + ")", category = OptionCategory.USER) //
    static final OptionKey<HostAndPort> Lsp = new OptionKey<>(DEFAULT_ADDRESS, ADDRESS_OR_BOOLEAN);

    @com.oracle.truffle.api.Option(help = "Requested maximum length of the Socket queue of incoming connections. (default: -1)", category = OptionCategory.EXPERT) //
    static final OptionKey<Integer> SocketBacklogSize = new OptionKey<>(-1);

    static final class HostAndPort {

        private final String host;
        private String portStr;
        private int port;
        private InetAddress inetAddress;

        HostAndPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        HostAndPort(String host, String portStr) {
            this.host = host;
            this.portStr = portStr;
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
}
