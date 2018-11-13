package org.graalvm.tools.lsp;

import java.nio.file.Path;

public interface VirtualLanguageServerFileProvider {

    public String getSourceText(Path path);

    public boolean isVirtualFile(Path path);
}
