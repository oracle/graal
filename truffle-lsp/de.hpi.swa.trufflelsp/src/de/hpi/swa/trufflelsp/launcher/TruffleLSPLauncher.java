package de.hpi.swa.trufflelsp.launcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;

import de.hpi.swa.trufflelsp.LanguageSpecificHacks;
import de.hpi.swa.trufflelsp.TruffleAdapter;
import de.hpi.swa.trufflelsp.exceptions.LSPIOException;
import de.hpi.swa.trufflelsp.filesystem.LSPFileSystem;
import de.hpi.swa.trufflelsp.server.LSPServer;

public class TruffleLSPLauncher extends AbstractLanguageLauncher {

    public static void main(String[] args) {
        singleton().launch(args);
    }

    // TODO(ds) get rid of the singleton
    private static TruffleLSPLauncher singleton;

    public static TruffleLSPLauncher singleton() {
        if (singleton == null) {
            singleton = new TruffleLSPLauncher();
        }
        return singleton;
    }

    private static final int DEFAULT_PORT = 8123;
    private static final HostAndPort DEFAULT_ADDRESS = new HostAndPort(null, DEFAULT_PORT);
    private TruffleAdapter truffleAdapter;

    public void initialize(TruffleInstrument.Env env, PrintWriter info, PrintWriter err) {
        truffleAdapter.initialize(env, info, err);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();

        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            switch (arg) {
                default:
                    unrecognized.add(arg);
            }
        }

        unrecognized.add("--polyglot");
        return unrecognized;
    }

    @Override
    protected void launch(Builder contextBuilder) {
        String userDirString = System.getProperty("user.home");
        Path userDir = null;
        if (userDirString != null) {
            userDir = Paths.get(userDirString);
        }

        this.truffleAdapter = new TruffleAdapter(contextBuilder);
        contextBuilder.fileSystem(LSPFileSystem.newFullIOFileSystem(userDir, truffleAdapter));

        try (Context context = contextBuilder.build()) {
            HostAndPort hostAndPort = DEFAULT_ADDRESS;// options.get(Lsp);
            InetSocketAddress socketAddress;
            try {
                socketAddress = hostAndPort.createSocket(false);// options.get(Remote));
                PrintWriter err = new PrintWriter(System.err);
                PrintWriter info = new PrintWriter(System.out);
                ServerSocket serverSocket = new ServerSocket(socketAddress.getPort(), 50, socketAddress.getAddress());
                LanguageSpecificHacks.enableLanguageSpecificHacks = true;// options.get(LanguageSpecificHacksOption).booleanValue();
                LSPServer languageServer = LSPServer.create(truffleAdapter, info, err);
                languageServer.start(serverSocket);
            } catch (IOException e) {
                String message = String.format("[Truffle LSP] Starting server on %s failed: %s", hostAndPort.getHostPort(false/* options.get(Remote) */), e.getLocalizedMessage());
                throw new LSPIOException(message, e);
                // TODO(ds) do not throw
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected String getLanguageId() {
        // TODO(ds) Actually we are no language...
        return "TruffleLSP";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {

    }

    @Override
    protected void collectArguments(Set<String> options) {

    }

    @Override
    protected void printVersion(Engine engine) {
        System.out.println(String.format("%s (GraalVM %s)", getLanguageId(), engine.getVersion()));
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
