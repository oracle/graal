package org.graalvm.tools.lsp.exceptions;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

public class DiagnosticsNotification extends Exception {

    private static final long serialVersionUID = 8517876447166873194L;

    private final Collection<PublishDiagnosticsParams> paramsCollection;

    public static DiagnosticsNotification create(URI uri, Diagnostic diagnostic) {
        PublishDiagnosticsParams params = new PublishDiagnosticsParams(uri.toString(), Arrays.asList(diagnostic));
        return new DiagnosticsNotification(params);
    }

    public DiagnosticsNotification(PublishDiagnosticsParams diagnosticParams) {
        this.paramsCollection = Arrays.asList(diagnosticParams);
    }

    public DiagnosticsNotification(Collection<PublishDiagnosticsParams> paramsCollection) {
        this.paramsCollection = paramsCollection;
    }

    public Collection<PublishDiagnosticsParams> getDiagnosticParamsCollection() {
        return paramsCollection;
    }
}
