/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.source.Source;

import com.oracle.truffle.tools.chromeinspector.types.Script;

public final class ScriptsHandler implements LoadSourceListener {

    private final Map<Source, Integer> sourceIDs = new HashMap<>(100);
    private final List<Script> scripts = new ArrayList<>(100);
    private final List<LoadScriptListener> listeners = new ArrayList<>();
    private final boolean reportInternal;

    public ScriptsHandler(boolean reportInternal) {
        this.reportInternal = reportInternal;
    }

    public int getScriptId(Source source) {
        synchronized (sourceIDs) {
            Integer id = sourceIDs.get(source);
            if (id != null) {
                return id;
            }
        }
        return -1;
    }

    public Script getScript(int id) {
        synchronized (sourceIDs) {
            return scripts.get(id);
        }
    }

    public List<Script> getScripts() {
        synchronized (sourceIDs) {
            return Collections.unmodifiableList(scripts);
        }
    }

    void addLoadScriptListener(LoadScriptListener listener) {
        List<Script> scriptsToNotify;
        synchronized (sourceIDs) {
            scriptsToNotify = new ArrayList<>(scripts);
            listeners.add(listener);
        }
        for (Script scr : scriptsToNotify) {
            listener.loadedScript(scr);
        }
    }

    void removeLoadScriptListener(LoadScriptListener listener) {
        synchronized (sourceIDs) {
            listeners.remove(listener);
        }
    }

    public int assureLoaded(Source source) {
        Script scr;
        URI uri = source.getURI();
        int id;
        LoadScriptListener[] listenersToNotify;
        synchronized (sourceIDs) {
            Integer eid = sourceIDs.get(source);
            if (eid != null) {
                return eid;
            }
            id = scripts.size();
            String sourceUrl = getNiceStringFromURI(uri);
            scr = new Script(id, sourceUrl, source);
            sourceIDs.put(source, id);
            scripts.add(scr);
            listenersToNotify = listeners.toArray(new LoadScriptListener[listeners.size()]);
        }
        for (LoadScriptListener l : listenersToNotify) {
            l.loadedScript(scr);
        }
        return id;
    }

    /**
     * Create a "nice" String representing the URI. It decodes special characters so that the name
     * looks better in the Chrome Inspector UI. In order to know where query or fragment starts, it
     * stores their indexes in the scheme in the form of &lt;scheme&gt;?&lt;query
     * index&gt;#&lt;fragment index&gt;`.
     */
    public static String getNiceStringFromURI(URI uri) {
        StringBuilder sb = new StringBuilder();
        String scheme = uri.getScheme();
        String query = uri.getQuery();
        String fragment = uri.getFragment();
        int queryIndex = -1;
        int fragmentIndex = -1;

        if (uri.isOpaque()) {
            String ssp = uri.getSchemeSpecificPart();
            if (ssp.startsWith("/") || ssp.startsWith("_")) {
                sb.append('_'); // Assure that the scheme specific part does not start with '/'
            }
            sb.append(ssp);
        } else {
            String host = uri.getHost();
            if (host != null) {
                sb.append("//");
                if (uri.getRawUserInfo() != null) {
                    sb.append(uri.getRawUserInfo());
                    sb.append('@');
                }
                boolean needBrackets = ((host.indexOf(':') >= 0) && !host.startsWith("[") && !host.endsWith("]"));
                if (needBrackets) {
                    sb.append('[');
                }
                sb.append(host);
                if (needBrackets) {
                    sb.append(']');
                }
                if (uri.getPort() != -1) {
                    sb.append(':');
                    sb.append(uri.getPort());
                }
            } else if (uri.getRawAuthority() != null) {
                sb.append("//");
                sb.append(uri.getRawAuthority());
            } else if ("file".equals(scheme)) {
                sb.append("//");
            }
            if (uri.getPath() != null) {
                sb.append(uri.getPath());
            }
            if (query != null) {
                queryIndex = sb.length();
                sb.append('?');
                sb.append(query);
            }
        }
        if (fragment != null) {
            fragmentIndex = sb.length();
            sb.append('#');
            sb.append(fragment);
        }
        String schemeAppend = "";
        if (queryIndex >= 0) {
            schemeAppend += "?" + queryIndex;
        }
        if (fragmentIndex >= 0) {
            schemeAppend += "#" + fragmentIndex;
        }
        if (schemeAppend.isEmpty()) {
            if (scheme != null && scheme.endsWith("`")) {
                scheme += "`";
            }
        } else {
            if (scheme == null) {
                scheme = schemeAppend + "`";
            } else {
                scheme += schemeAppend + "`";
            }
        }
        if (scheme != null) {
            sb.insert(0, scheme);
            sb.insert(scheme.length(), ':');
        } else {
            sb.insert(0, ':');  // empty scheme
        }
        return sb.toString();
    }

    /**
     * Parse the "nice" String created by {@link #getNiceStringFromURI(java.net.URI)} and convert it
     * into the original URI.
     */
    public static URI getURIFromNiceString(String str) throws URISyntaxException {
        String scheme;
        int queryIndex = -1;
        int fragmentIndex = -1;
        int i = str.indexOf(':');
        if (i <= 0) {
            scheme = null;
        } else {
            scheme = str.substring(0, i);
            if (scheme.endsWith("``")) {
                scheme = scheme.substring(0, scheme.length() - 1);
            } else if (scheme.endsWith("`")) {
                int end = scheme.length() - 1;
                int qi = scheme.lastIndexOf('?');
                int fi = scheme.lastIndexOf('#');
                if (fi >= 0) {
                    fragmentIndex = Integer.parseUnsignedInt(scheme.substring(fi + 1, end));
                    end = fi;
                }
                if (qi >= 0) {
                    queryIndex = Integer.parseUnsignedInt(scheme.substring(qi + 1, end));
                    end = qi;
                }
                scheme = scheme.substring(0, end);
            }
            if (scheme.isEmpty()) {
                scheme = null;
            }
        }
        i++; // skip the colon
        int end = str.length();
        String query = null;
        String fragment = null;
        if (fragmentIndex >= 0) {
            fragment = str.substring(i + fragmentIndex + 1);
            end = i + fragmentIndex;
        }
        if (queryIndex >= 0) {
            query = str.substring(i + queryIndex + 1, end);
            end = i + queryIndex;
        }
        String ssp = null;
        if (scheme != null && i < end) {
            char c = str.charAt(i);
            if (c == '_') { // Scheme Specific Part
                ssp = str.substring(i + 1, end);
            } else if (c != '/') { // Scheme Specific Part
                ssp = str.substring(i, end);
            }
        }
        if (ssp != null) {
            return new URI(scheme, ssp, fragment);
        } else {
            String authority = null;
            URI authorityDecoder = null;
            String path = null;
            if (i < end) { // Hierarchical
                int p1 = i;
                char c1 = str.charAt(i);
                if ((i + 1) < end) {
                    char c2 = str.charAt(i + 1);

                    if (c1 == '/' && c2 == '/') { // Authority
                        p1 = str.indexOf('/', i + 2);
                        if (p1 > end || p1 < 0) {
                            p1 = end;
                        }
                        authority = str.substring(i + 2, p1);
                        if (authority.isEmpty()) {
                            authority = null;
                        } else {
                            // Need to decode the encoded authority:
                            authorityDecoder = new URI("a://" + authority);
                        }
                    }
                }
                path = str.substring(p1, end);
                if (path.isEmpty()) {
                    path = null;
                }
            }
            if (authorityDecoder == null) {
                return new URI(scheme, null, path, query, fragment);
            } else {
                return new URI(scheme, authorityDecoder.getUserInfo(), authorityDecoder.getHost(), authorityDecoder.getPort(), path, query, fragment);
            }
        }
    }

    @Override
    public void onLoad(LoadSourceEvent event) {
        Source source = event.getSource();
        if (reportInternal || !source.isInternal()) {
            assureLoaded(source);
        }
    }

    static boolean compareURLs(String url1, String url2) {
        String surl1 = stripScheme(url1);
        String surl2 = stripScheme(url2);
        // Either equals,
        // or equals while ignoring the initial slash (workaround for Chromium bug #851853)
        return surl1.equals(surl2) || surl1.startsWith("/") && surl1.substring(1).equals(surl2);
    }

    private static String stripScheme(String url) {
        // we can strip the scheme part iff it's "file"
        if (url.startsWith("file://")) {
            return url.substring("file://".length());
        }
        return url;
    }

    interface LoadScriptListener {

        void loadedScript(Script script);
    }

}
