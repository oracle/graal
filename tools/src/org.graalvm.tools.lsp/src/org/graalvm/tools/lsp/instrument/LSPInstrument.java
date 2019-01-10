package org.graalvm.tools.lsp.instrument;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Future;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.tools.lsp.api.ContextAwareExecutorRegistry;
import org.graalvm.tools.lsp.api.LanguageServerBootstrapper;
import org.graalvm.tools.lsp.api.VirtualLanguageServerFileProvider;
import org.graalvm.tools.lsp.exceptions.LSPIOException;
import org.graalvm.tools.lsp.hacks.LanguageSpecificHacks;
import org.graalvm.tools.lsp.instrument.LSOptions.HostAndPort;
import org.graalvm.tools.lsp.server.LanguageServerImpl;
import org.graalvm.tools.lsp.server.TruffleAdapter;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = LSPInstrument.ID, name = "Language Server", version = "0.1", services = {VirtualLanguageServerFileProvider.class, ContextAwareExecutorRegistry.class,
                LanguageServerBootstrapper.class})
public class LSPInstrument extends TruffleInstrument implements LanguageServerBootstrapper {
    public static final String ID = "lsp";

    private OptionValues options;
    private LanguageServerImpl languageServer;

    @Override
    protected void onCreate(Env env) {
        options = env.getOptions();
        if (options.hasSetOptions()) {
            LanguageSpecificHacks.enableLanguageSpecificHacks = options.get(LSOptions.LanguageSpecificHacksOption).booleanValue();
        }

        TruffleAdapter truffleAdapter = new TruffleAdapter(env);
        PrintWriter info = new PrintWriter(env.out(), true);
        PrintWriter err = new PrintWriter(env.err(), true);
        languageServer = LanguageServerImpl.create(truffleAdapter, info, err);

        env.registerService(truffleAdapter);
        env.registerService(this);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new LSOptionsOptionDescriptors();
    }

    public Future<?> startServer() {
        assert languageServer != null;
        assert options != null;
        assert options.hasSetOptions();

        HostAndPort hostAndPort = options.get(LSOptions.Lsp);
        try {
            InetSocketAddress socketAddress = hostAndPort.createSocket();
            int port = socketAddress.getPort();
            Integer backlog = options.get(LSOptions.SocketBacklogSize);
            InetAddress address = socketAddress.getAddress();
            ServerSocket serverSocket = new ServerSocket(port, backlog, address);
            return languageServer.start(serverSocket);
        } catch (IOException e) {
            String message = String.format("[Graal LSP] Starting server on %s failed: %s", hostAndPort.getHostPort(), e.getLocalizedMessage());
            throw new LSPIOException(message, e);
        }
    }
}
