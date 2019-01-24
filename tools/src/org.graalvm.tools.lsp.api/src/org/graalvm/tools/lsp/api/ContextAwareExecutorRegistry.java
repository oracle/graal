package org.graalvm.tools.lsp.api;

/**
 * This service interface provides a method to register a {@link ContextAwareExecutor} instance. It
 * is used bridge Truffle API and Graal SDK to provide a callback for the LSP language server to
 * execute tasks by a Polyglot-Context-entered Thread.
 *
 */
public interface ContextAwareExecutorRegistry {

    void register(ContextAwareExecutor executor);
}
