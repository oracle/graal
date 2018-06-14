package de.hpi.swa.trufflelsp.filesystem;

import java.nio.file.Path;

public interface VirtualLSPFileProvider {

    public String getSourceText(Path path);

// public Path translatePath(Path uri);

    public boolean isVirtualFile(Path path);
}
