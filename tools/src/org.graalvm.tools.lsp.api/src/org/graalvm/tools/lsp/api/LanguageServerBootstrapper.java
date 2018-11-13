package org.graalvm.tools.lsp.api;

import java.util.concurrent.Future;

public interface LanguageServerBootstrapper {

    public Future<?> startServer();
}
