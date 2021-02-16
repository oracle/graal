/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.debug;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Resolve relative URIs to absolute to find a location where to load the content of content-less
 * sources from.
 */
final class DebugSourcesResolver {

    private final Env env;
    private volatile URI[] sourcePath = new URI[0];
    private final Map<Source, Source> resolvedMap = new WeakHashMap<>();

    DebugSourcesResolver(Env env) {
        this.env = env;
    }

    void setSourcePath(Iterable<URI> uris) {
        Collection<URI> collection;
        if (uris instanceof Collection) {
            collection = (Collection<URI>) uris;
        } else {
            List<URI> list = new ArrayList<>();
            for (URI uri : uris) {
                list.add(uri);
            }
            collection = list;
        }
        URI[] array = collection.toArray(new URI[collection.size()]);
        for (int i = 0; i < array.length; i++) {
            if (!array[i].isAbsolute()) {
                try {
                    array[i] = new URI("file://" + array[i].toString());
                } catch (URISyntaxException ex) {
                    throw new IllegalArgumentException("URI " + array[i] + " is not absolute and can not be converted to a file: " + ex.getLocalizedMessage());
                }
            }
        }
        sourcePath = array;
    }

    Source resolve(Source source) {
        if (source.hasCharacters() || source.hasBytes()) {
            return source;
        }
        Source resolved;
        synchronized (resolvedMap) {
            resolved = resolvedMap.getOrDefault(source, source);
            if (resolved == source) { // not resolved
                resolved = doResolve(source);
                resolvedMap.put(source, resolved);
            }
        }
        return resolved;
    }

    private Source doResolve(Source source) {
        URI uri = source.getURI();
        InputStream stream = null;
        if (uri.isAbsolute()) {
            try {
                stream = uri.toURL().openConnection().getInputStream();
            } catch (IOException ioex) {
                return null;
            }
        } else {
            URI[] roots = sourcePath;
            for (URI root : roots) {
                URI resolved = resolve(root, uri);
                try {
                    stream = resolved.toURL().openConnection().getInputStream();
                    uri = resolved;
                    break;
                } catch (IOException ioex) {
                    continue;
                }
            }
        }
        if (stream == null) {
            return null;
        }
        try {
            Source.SourceBuilder builder = null;
            if ("file".equals(uri.getScheme())) {
                TruffleFile file = env.getTruffleFile(uri);
                builder = Source.newBuilder(source.getLanguage(), file);
            } else {
                URL url;
                try {
                    url = uri.toURL();
                    builder = Source.newBuilder(source.getLanguage(), url);
                } catch (MalformedURLException | IllegalArgumentException ex) {
                    // fallback to a general Source
                }
            }
            if (builder == null) {
                String name = uri.getPath() != null ? uri.getPath() : uri.getSchemeSpecificPart();
                builder = Source.newBuilder(source.getLanguage(), new InputStreamReader(stream), name).uri(uri);
            }
            try {
                return builder.cached(false).interactive(source.isInteractive()).internal(source.isInternal()).mimeType(source.getMimeType()).build();
            } catch (IOException | SecurityException e) {
                env.getLogger("").warning(String.format("Failed to resolve %s: %s%s", source.getURI(), e.getLocalizedMessage(), System.lineSeparator()));
                return null;
            }
        } finally {
            try {
                stream.close();
            } catch (IOException ioe) {
            }
        }
    }

    // We can not use URI.resolve(URI), as it does not resolve URIs from ZIP files.
    private static URI resolve(URI base, URI child) {
        String childPath = child.getPath();
        if (childPath == null || childPath.isEmpty()) {
            return base;
        }
        String path = base.getPath();
        try {
            URI resolved;
            if (path != null) {
                if (path.endsWith("/")) {
                    path = path + childPath;
                } else {
                    path = path + "/" + childPath;
                }
                resolved = new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), path, base.getQuery(), base.getFragment());
            } else {
                String ssp = base.getSchemeSpecificPart();
                if (ssp.endsWith("/")) {
                    ssp = ssp + childPath;
                } else {
                    ssp = ssp + "/" + childPath;
                }
                resolved = new URI(base.getScheme(), ssp, base.getFragment());
            }
            return resolved.normalize();
        } catch (URISyntaxException ex) {
            return base;
        }
    }

    SourceSection resolve(SourceSection section) {
        if (section == null) {
            return null;
        }
        Source source = section.getSource();
        Source rSource = resolve(source);
        if (rSource == source || rSource == null) {
            return section;
        }
        try {
            if (!section.isAvailable()) {
                return rSource.createUnavailableSection();
            } else if (section.hasCharIndex()) {
                return rSource.createSection(section.getCharIndex(), section.getCharLength());
            } else if (section.hasColumns()) {
                return rSource.createSection(section.getStartLine(), section.getStartColumn(),
                                section.getEndLine(), section.getEndColumn());
            } else if (section.hasLines()) {
                int startLine = section.getStartLine();
                int endLine = section.getEndLine();
                int startColumn = 0;
                CharSequence firstLine = rSource.getCharacters(startLine);
                int length = firstLine.length();
                while (startColumn < length && Character.isWhitespace(firstLine.charAt(startColumn))) {
                    startColumn++;
                }
                if (startColumn == length) {
                    startColumn = 0;
                }
                return rSource.createSection(startLine, startColumn + 1, endLine, rSource.getLineLength(endLine));
            } else {
                return section;
            }
        } catch (IllegalArgumentException ex) {
            // Thrown from createSection() when the section does not fit into the resolved source.
            return section;
        }
    }

    /**
     * Finds an encapsulating source section, prefer instrumentable nodes and available sections.
     */
    static SourceSection findEncapsulatedSourceSection(Node node) {
        Node n = node;
        while (n != null) {
            if (n instanceof InstrumentableNode && ((InstrumentableNode) n).isInstrumentable()) {
                SourceSection sourceSection = n.getSourceSection();
                if (sourceSection != null && sourceSection.isAvailable()) {
                    return sourceSection;
                }
            }
            n = n.getParent();
        }
        final RootNode rootNode = node.getRootNode();
        return rootNode != null ? rootNode.getSourceSection() : null;
    }
}
