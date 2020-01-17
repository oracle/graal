/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.tools.lsp.exceptions;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.graalvm.tools.lsp.server.types.Diagnostic;
import org.graalvm.tools.lsp.server.types.PublishDiagnosticsParams;

/**
 * This is a utility diagnostics exception. When caught, the associated
 * {@link PublishDiagnosticsParams} are sent to the client.
 */
public final class DiagnosticsNotification extends Exception {

    private static final long serialVersionUID = 8517876447166873194L;

    private final Collection<PublishDiagnosticsParams> paramsCollection;

    public static DiagnosticsNotification create(URI uri, Diagnostic diagnostic) {
        PublishDiagnosticsParams params = PublishDiagnosticsParams.create(uri.toString(), Arrays.asList(diagnostic));
        return new DiagnosticsNotification(params);
    }

    public DiagnosticsNotification(PublishDiagnosticsParams diagnosticParams) {
        this.paramsCollection = Arrays.asList(diagnosticParams);
    }

    public DiagnosticsNotification(Collection<PublishDiagnosticsParams> paramsCollection) {
        this.paramsCollection = paramsCollection;
    }

    public DiagnosticsNotification(Map<URI, List<Diagnostic>> paramsMap) {
        this.paramsCollection = new ArrayList<>(paramsMap.size());
        for (Map.Entry<URI, List<Diagnostic>> entry : paramsMap.entrySet()) {
            this.paramsCollection.add(PublishDiagnosticsParams.create(entry.getKey().toString(), entry.getValue()));
        }
    }

    public Collection<PublishDiagnosticsParams> getDiagnosticParamsCollection() {
        return paramsCollection;
    }
}
