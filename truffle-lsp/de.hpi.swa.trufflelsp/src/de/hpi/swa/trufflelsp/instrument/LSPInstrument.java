package de.hpi.swa.trufflelsp.instrument;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.concurrent.Future;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import de.hpi.swa.trufflelsp.LanguageServerBootstrapper;
import de.hpi.swa.trufflelsp.ContextAwareExecutorWrapperRegistry;
import de.hpi.swa.trufflelsp.TruffleAdapter;
import de.hpi.swa.trufflelsp.VirtualLanguageServerFileProvider;
import de.hpi.swa.trufflelsp.exceptions.LSPIOException;
import de.hpi.swa.trufflelsp.hacks.LanguageSpecificHacks;
import de.hpi.swa.trufflelsp.server.LanguageServerImpl;

@Registration(id = LSPInstrument.ID, name = "Language Server", version = "0.1", services = {VirtualLanguageServerFileProvider.class, ContextAwareExecutorWrapperRegistry.class, LanguageServerBootstrapper.class})
public class LSPInstrument extends TruffleInstrument implements LanguageServerBootstrapper {
    public static final String ID = "lsp";

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

    @com.oracle.truffle.api.Option(help = "Don't use loopback address. (default:false)", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> Remote = new OptionKey<>(false);

    @com.oracle.truffle.api.Option(name = "Languagespecific.hacks", help = "Enable language specific hacks to get features which are not supported by some languages yet. (default:true)", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> LanguageSpecificHacksOption = new OptionKey<>(true);

    private OptionValues options;

    private TruffleAdapter truffleAdapter;

    private PrintWriter err;

    private PrintWriter info;

    @Override
    protected void onCreate(Env env) {
        options = env.getOptions();
        if (options.hasSetOptions()) {
            LanguageSpecificHacks.enableLanguageSpecificHacks = options.get(LanguageSpecificHacksOption).booleanValue();
        }

        info = new PrintWriter(env.out(), true);
        err = new PrintWriter(env.err(), true);

        truffleAdapter = new TruffleAdapter(env);
        env.registerService(truffleAdapter);
        env.registerService(this);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new LSPInstrumentOptionDescriptors();
    }

    public Future<?> startServer() {
        assert truffleAdapter != null;
        assert options != null;
        assert options.hasSetOptions();

        HostAndPort hostAndPort = options.get(Lsp);
        InetSocketAddress socketAddress;
        try {
            socketAddress = hostAndPort.createSocket(options.get(Remote));
            ServerSocket serverSocket = new ServerSocket(socketAddress.getPort(), 50, socketAddress.getAddress());
            LanguageSpecificHacks.enableLanguageSpecificHacks = options.get(LanguageSpecificHacksOption).booleanValue();
            LanguageServerImpl languageServer = LanguageServerImpl.create(truffleAdapter, info, err);
            return languageServer.start(serverSocket);
        } catch (IOException e) {
            String message = String.format("[Graal LSP] Starting server on %s failed: %s", hostAndPort.getHostPort(options.get(Remote)), e.getLocalizedMessage());
            throw new LSPIOException(message, e);
        }
    }

    private static final class HostAndPort {

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

        String getHostPort(boolean remote) {
            String hostName = host;
            if (hostName == null || hostName.isEmpty()) {
                if (inetAddress != null) {
                    hostName = inetAddress.toString();
                } else if (remote) {
                    hostName = "localhost";
                } else {
                    hostName = InetAddress.getLoopbackAddress().toString();
                }
            }
            return hostName + ":" + port;
        }

        InetSocketAddress createSocket(boolean remote) throws UnknownHostException {
            InetAddress ia;
            if (inetAddress == null) {
                if (remote) {
                    ia = InetAddress.getLocalHost();
                } else {
                    ia = InetAddress.getLoopbackAddress();
                }
            } else {
                ia = inetAddress;
            }
            return new InetSocketAddress(ia, port);
        }
    }

}
