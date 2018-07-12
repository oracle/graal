package de.hpi.swa.trufflelsp;

import java.util.concurrent.Future;

public interface LSPServerBootstrapper {

    public Future<?> startServer();
}
