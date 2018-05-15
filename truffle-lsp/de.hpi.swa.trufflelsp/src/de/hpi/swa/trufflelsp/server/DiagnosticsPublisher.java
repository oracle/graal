package de.hpi.swa.trufflelsp.server;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

public interface DiagnosticsPublisher {

    public void addDiagnostics(List<Diagnostic> diagnostics);
}
