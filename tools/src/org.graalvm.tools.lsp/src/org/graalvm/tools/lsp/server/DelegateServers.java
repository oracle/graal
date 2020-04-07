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
package org.graalvm.tools.lsp.server;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import org.graalvm.tools.lsp.server.types.CodeLensOptions;
import org.graalvm.tools.lsp.server.types.CompletionOptions;
import org.graalvm.tools.lsp.server.types.DocumentLinkOptions;
import org.graalvm.tools.lsp.server.types.ExecuteCommandOptions;
import org.graalvm.tools.lsp.server.types.LanguageServer.DelegateServer;
import org.graalvm.tools.lsp.server.types.LanguageServer.LoggerProxy;
import org.graalvm.tools.lsp.server.types.RenameOptions;
import org.graalvm.tools.lsp.server.types.ServerCapabilities;
import org.graalvm.tools.lsp.server.types.TextDocumentSyncKind;
import org.graalvm.tools.lsp.server.types.TextDocumentSyncOptions;

/**
 * Merge of communication with delegate language servers.
 */
public final class DelegateServers {

    private final TruffleAdapter truffleAdapter;
    private final DelegateServer[] delegateServers;
    private final LoggerProxy logger;

    public DelegateServers(TruffleAdapter truffleAdapter, List<DelegateServer> delegateServers, LoggerProxy logger) {
        this.truffleAdapter = truffleAdapter;
        this.delegateServers = delegateServers.toArray(new DelegateServer[delegateServers.size()]);
        this.logger = logger;
    }

    public void submitAll(ExecutorService executors) {
        for (DelegateServer ds : delegateServers) {
            executors.submit(ds);
        }
    }

    public void sendMessageToDelegates(byte[] buffer, Object id, String method, JSONObject params) {
        String language = findContextLanguage(params);
        for (DelegateServer ds : delegateServers) {
            if (languageMatch(language, ds.getLanguageId()) && supportsMethod(method, params, ds.getCapabilities())) {
                try {
                    ds.sendMessage(buffer, id, method);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Delegate server " + ds.getAddress() + ": " + e.getMessage(), e);
                }
            }
        }
    }

    private String findContextLanguage(JSONObject params) {
        JSONObject textDocument = params != null ? params.optJSONObject("textDocument") : null;
        Object uri = textDocument != null ? textDocument.opt("uri") : null;
        return uri instanceof String ? truffleAdapter.getLanguageId(URI.create((String) uri)) : null;
    }

    private static boolean languageMatch(String languageId1, String languageId2) {
        if (languageId1 == null || languageId2 == null) {
            return true;
        }
        return languageId1.equals(languageId2);
    }

    public Object mergeResults(Object id, Object result) {
        Object allResults = result;
        for (DelegateServer ds : delegateServers) {
            try {
                JSONObject message = ds.awaitMessage(id);
                if (message != null && logger.isLoggable(Level.FINER)) {
                    String format = "[Trace - %s] Received response from %s: %s";
                    logger.log(Level.FINER, String.format(format, Instant.now().toString(), ds.toString(), message.toString()));
                }
                allResults = mergeResults(allResults, message);
            } catch (InterruptedException iex) {
            }
        }
        return allResults;
    }

    public void close() {
        for (DelegateServer ds : delegateServers) {
            ds.close();
        }
    }

    private static Object mergeResults(Object allResults, JSONObject message2) {
        if (message2 != null && message2.has("result")) {
            Object result2 = message2.get("result");
            if (allResults == null) {
                return result2;
            } else {
                mergeJSONInto("result", allResults, result2);
            }
        }
        return allResults;
    }

    private static Object mergeJSONInto(String propertyName, Object j1, Object j2) {
        if (j1 instanceof JSONObject && j2 instanceof JSONObject) {
            JSONObject jo1 = (JSONObject) j1;
            JSONObject jo2 = (JSONObject) j2;
            for (String key2 : jo2.keySet()) {
                if (!jo1.has(key2)) {
                    jo1.put(key2, jo2.get(key2));
                } else {
                    jo1.put(key2, mergeJSONInto(key2, jo1.get(key2), jo2.get(key2)));
                }
            }
        } else if (j1 instanceof JSONArray) {
            JSONArray ja1 = (JSONArray) j1;
            if (j2 instanceof JSONArray) {
                JSONArray ja2 = (JSONArray) j2;
                if (ja1.isEmpty()) {
                    for (Object value : ja2) {
                        ja1.put(value);
                    }
                } else if (!ja2.isEmpty()) {
                    Set<String> labels = getLabels(ja1);
                    for (Object value : ja2) {
                        addIfNoSuchLabel(ja1, labels, value);
                    }
                }
            } else if (!ja1.isEmpty()) {
                Set<String> labels = getLabels(ja1);
                addIfNoSuchLabel(ja1, labels, j2);
            } else {
                ja1.put(j2);
            }
        } else if (j2 instanceof JSONArray) {
            JSONArray ja2 = (JSONArray) j2;
            if (!ja2.isEmpty()) {
                Set<String> labels = getLabels(ja2);
                addIfNoSuchLabel(ja2, labels, j1);
            } else {
                ja2.put(j1);
            }
        } else {
            if (isTrue(j1) && isFalse(j2) || isFalse(j1) && isTrue(j2)) {
                // One says true, the other says false. We merge it into true:
                return Boolean.TRUE;
            }
            if (j1 instanceof Number && j2 instanceof Number) {
                if ("textDocumentSync".equals(propertyName)) {
                    int i1 = ((Number) j1).intValue();
                    int i2 = ((Number) j2).intValue();
                    if (i1 == 0 || i2 == 0) {
                        return 0;
                    }
                    if (i1 == 1 || i2 == 1) {
                        return 1;
                    }
                }
            }
        }
        return j1;
    }

    private static boolean isTrue(Object jsonObj) {
        return Boolean.TRUE.equals(jsonObj) || jsonObj instanceof String && "true".equalsIgnoreCase((String) jsonObj);
    }

    private static boolean isFalse(Object jsonObj) {
        return Boolean.FALSE.equals(jsonObj) || jsonObj instanceof String && "false".equalsIgnoreCase((String) jsonObj);
    }

    private static String getLabel(Object value) {
        if (value instanceof JSONObject) {
            JSONObject jv = (JSONObject) value;
            return jv.optString("label");
        } else {
            return null;
        }
    }

    private static Set<String> getLabels(JSONArray ja) {
        Set<String> labels = null;
        for (Object value : ja) {
            String label = getLabel(value);
            if (label != null) {
                if (labels == null) {
                    labels = new HashSet<>();
                }
                labels.add(label);
            }
        }
        return labels != null ? labels : Collections.emptySet();
    }

    private static void addIfNoSuchLabel(JSONArray ja, Set<String> labels, Object value) {
        String label = getLabel(value);
        if (label == null || !labels.contains(label)) {
            ja.put(value);
        }
    }

    static boolean supportsMethod(String method, JSONObject params, ServerCapabilities capabilities) {
        Object capability;
        switch (method) {
            case "textDocument/codeAction":
                capability = capabilities.getCodeActionProvider();
                break;
            case "textDocument/codeLens":
                capability = capabilities.getCodeLensProvider();
                break;
            case "codeLens/resolve":
                CodeLensOptions clp = capabilities.getCodeLensProvider();
                capability = (clp != null) ? clp.getResolveProvider() : null;
                break;
            case "textDocument/colorPresentation":
                capability = capabilities.getColorProvider();
                break;
            case "textDocument/completion":
                capability = capabilities.getCompletionProvider();
                break;
            case "completionItem/resolve":
                CompletionOptions co = capabilities.getCompletionProvider();
                capability = (co != null) ? co.getResolveProvider() : null;
                break;
            case "textDocument/declaration":
                capability = capabilities.getDeclarationProvider();
                break;
            case "textDocument/definition":
                capability = capabilities.getDefinitionProvider();
                break;
            case "textDocument/formatting":
                capability = capabilities.getDocumentFormattingProvider();
                break;
            case "textDocument/documentHighlight":
                capability = capabilities.getDocumentHighlightProvider();
                break;
            case "textDocument/documentLink":
                capability = capabilities.getDocumentLinkProvider();
                break;
            case "documentLink/resolve":
                DocumentLinkOptions dlo = capabilities.getDocumentLinkProvider();
                capability = (dlo != null) ? dlo.getResolveProvider() : null;
                break;
            case "textDocument/onTypeFormatting":
                capability = capabilities.getDocumentOnTypeFormattingProvider();
                break;
            case "textDocument/rangeFormatting":
                capability = capabilities.getDocumentRangeFormattingProvider();
                break;
            case "textDocument/documentSymbol":
                capability = capabilities.getDocumentSymbolProvider();
                break;
            case "workspace/executeCommand":
                ExecuteCommandOptions eco = capabilities.getExecuteCommandProvider();
                return eco.getCommands().contains(params.getString("command"));
            case "textDocument/foldingRange":
                capability = capabilities.getFoldingRangeProvider();
                break;
            case "textDocument/hover":
                capability = capabilities.getHoverProvider();
                break;
            case "textDocument/implementation":
                capability = capabilities.getImplementationProvider();
                break;
            case "textDocument/references":
                capability = capabilities.getReferencesProvider();
                break;
            case "textDocument/rename":
                capability = capabilities.getRenameProvider();
                break;
            case "textDocument/prepareRename":
                Object renameProvider = capabilities.getRenameProvider();
                capability = (renameProvider != null) ? ((RenameOptions) renameProvider).getPrepareProvider() : null;
                break;
            case "textDocument/signatureHelp":
                capability = capabilities.getSignatureHelpProvider();
                break;
            case "textDocument/didChange":
                capability = capabilities.getTextDocumentSync();
                if (capability instanceof TextDocumentSyncOptions) {
                    capability = ((TextDocumentSyncOptions) capability).getChange();
                }
                capability = (capability == TextDocumentSyncKind.Full || capability == TextDocumentSyncKind.Incremental) ? true : null;
                break;
            case "textDocument/typeDefinition":
                capability = capabilities.getTypeDefinitionProvider();
                break;
            case "workspace/symbol":
                capability = capabilities.getWorkspaceSymbolProvider();
                break;
            default:
                return true;
        }
        return capability != null && !Boolean.FALSE.equals(capability);
    }

}
