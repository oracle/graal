package de.hpi.swa.trufflelsp.server;

import java.net.URI;

import org.eclipse.lsp4j.Diagnostic;

public interface DiagnosticsPublisher {

    public void addDiagnostics(URI uri, Diagnostic... diagnostics);

    public void reportCollectedDiagnostics(String documentUri);

    public void reportCollectedDiagnostics();
}
