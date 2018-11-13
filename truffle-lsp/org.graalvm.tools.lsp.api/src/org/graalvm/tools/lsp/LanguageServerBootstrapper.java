package org.graalvm.tools.lsp;

import java.util.concurrent.Future;

public interface LanguageServerBootstrapper {

    public Future<?> startServer();
}
