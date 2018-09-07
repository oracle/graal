package de.hpi.swa.trufflelsp.api;

import java.nio.file.Path;

public interface VirtualLanguageServerFileProvider {

    public String getSourceText(Path path);

    public boolean isVirtualFile(Path path);
}
