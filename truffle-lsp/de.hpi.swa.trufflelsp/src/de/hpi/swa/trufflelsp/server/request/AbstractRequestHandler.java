package de.hpi.swa.trufflelsp.server.request;

import java.io.PrintWriter;
import java.net.URI;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class AbstractRequestHandler {

    protected final TruffleInstrument.Env env;
    protected final Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate;
    protected final PrintWriter err;
    protected final ContextAwareExecutorWrapper contextAwareExecutor;

    public AbstractRequestHandler(TruffleInstrument.Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor) {
        this.env = env;
        this.err = new PrintWriter(env.err(), true);
        this.uri2TextDocumentSurrogate = uri2TextDocumentSurrogate;
        this.contextAwareExecutor = contextAwareExecutor;
    }
}
