package de.hpi.swa.trufflelsp;

import java.util.concurrent.Future;

public interface LanguageServerBootstrapper {

    public Future<?> startServer();
}
