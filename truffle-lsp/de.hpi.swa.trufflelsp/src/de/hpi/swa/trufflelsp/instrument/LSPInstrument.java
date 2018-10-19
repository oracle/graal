package de.hpi.swa.trufflelsp.instrument;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Future;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapperRegistry;
import de.hpi.swa.trufflelsp.api.LanguageServerBootstrapper;
import de.hpi.swa.trufflelsp.api.VirtualLanguageServerFileProvider;
import de.hpi.swa.trufflelsp.exceptions.LSPIOException;
import de.hpi.swa.trufflelsp.hacks.LanguageSpecificHacks;
import de.hpi.swa.trufflelsp.instrument.LSOptions.HostAndPort;
import de.hpi.swa.trufflelsp.server.LanguageServerImpl;
import de.hpi.swa.trufflelsp.server.TruffleAdapter;

@Registration(id = LSPInstrument.ID, name = "Language Server", version = "0.1", services = {VirtualLanguageServerFileProvider.class, ContextAwareExecutorWrapperRegistry.class,
                LanguageServerBootstrapper.class})
public class LSPInstrument extends TruffleInstrument implements LanguageServerBootstrapper {
    public static final String ID = "lsp";

    private OptionValues options;
    private LanguageServerImpl languageServer;
    private PrintWriter err;
    private PrintWriter info;

    @Override
    protected void onCreate(Env env) {
        System.out.println("Truffle Runtime: " + Truffle.getRuntime().getName());

        options = env.getOptions();
        if (options.hasSetOptions()) {
            LanguageSpecificHacks.enableLanguageSpecificHacks = options.get(LSOptions.LanguageSpecificHacksOption).booleanValue();
        }

        info = new PrintWriter(env.out(), true);
        err = new PrintWriter(env.err(), true);

        TruffleAdapter truffleAdapter = new TruffleAdapter(env);
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
        InetSocketAddress socketAddress;
        try {
            socketAddress = hostAndPort.createSocket(options.get(LSOptions.Remote));
            ServerSocket serverSocket = new ServerSocket(socketAddress.getPort(), 50, socketAddress.getAddress());
            return languageServer.start(serverSocket);
        } catch (IOException e) {
            String message = String.format("[Graal LSP] Starting server on %s failed: %s", hostAndPort.getHostPort(options.get(LSOptions.Remote)), e.getLocalizedMessage());
            throw new LSPIOException(message, e);
        }
    }
}
