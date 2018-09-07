package de.hpi.swa.trufflelsp.api;

import java.util.concurrent.Future;

public interface LanguageServerBootstrapper {

    public Future<?> startServer();
}
