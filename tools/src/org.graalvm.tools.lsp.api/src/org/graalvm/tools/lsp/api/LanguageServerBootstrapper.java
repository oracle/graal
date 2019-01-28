package org.graalvm.tools.lsp.api;

import java.util.concurrent.Future;

/**
 * This service interface is used to provide a callback for the launcher, to be able to explicitly
 * start the language server after having set-up a custom file system etc.
 *
 */
public interface LanguageServerBootstrapper {

    /**
     * Gives the kick-off signal, to start the actual LSP language server.
     *
     * @return a {@link Future} to await the shutdown of the server.
     */
    Future<?> startServer();
}
