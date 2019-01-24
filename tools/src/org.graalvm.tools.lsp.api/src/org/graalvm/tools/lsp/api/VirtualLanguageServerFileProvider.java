package org.graalvm.tools.lsp.api;

import java.nio.file.Path;

/**
 * This service interface provides callbacks for a custom file system. As we execute source code to
 * collect run-time information, the executed code needs to access the state of (potentially)
 * unsaved files in the LSP source code editor instead of the state on disk. Therefore, a custom
 * file system needs to check for every file access if there is an edited version in the source code
 * editor.
 *
 */
public interface VirtualLanguageServerFileProvider {

    /**
     * @param path A path to a file in the file system.
     * @return The source code of the file as seen/edited by the user in the source code editor.
     */
    String getSourceText(Path path);

    /**
     * @param path A path to a file in the file system.
     * @return true if the file has been marked as "opened" in our LSP server.
     */
    boolean isVirtualFile(Path path);
}
