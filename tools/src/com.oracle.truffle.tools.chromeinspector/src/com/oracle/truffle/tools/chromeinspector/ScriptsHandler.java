/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

import com.oracle.truffle.tools.chromeinspector.types.Script;
import org.graalvm.collections.EconomicSet;

public final class ScriptsHandler implements LoadSourceListener {

    private final Map<Source, Integer> sourceIDs = new HashMap<>(100);
    private final List<Script> scripts = new ArrayList<>(100);
    private volatile List<Source> sourcesBacklog = null;
    private final Map<String, Integer> uniqueSourceNames = new HashMap<>();
    private final List<LoadScriptListener> listeners = new ArrayList<>();
    private final boolean reportInternal;
    private final TruffleInstrument.Env env;
    private volatile DebuggerSession debuggerSession;
    private final EventBinding<ContextsListener> contextsBinding;
    private final EconomicSet<TruffleContext> contexts = EconomicSet.create();

    ScriptsHandler(TruffleInstrument.Env env, boolean reportInternal) {
        this.env = env;
        this.reportInternal = reportInternal;
        this.contextsBinding = env.getInstrumenter().attachContextsListener(new ContextTracker(), true);
    }

    void setDebuggerSession(DebuggerSession debuggerSession) {
        this.debuggerSession = debuggerSession;
    }

    private TruffleContext getATruffleContext() {
        synchronized (contexts) {
            if (contexts.isEmpty()) {
                return null;
            } else {
                return contexts.iterator().next();
            }
        }
    }

    DebuggerSession getDebuggerSession() {
        return this.debuggerSession;
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

    public Script assureLoaded(Source sourceLoaded) {
        TruffleContext truffleContext = env.getEnteredContext();
        if (truffleContext == null) {
            truffleContext = getATruffleContext();
        }
        if (truffleContext == null) {
            synchronized (sourceIDs) {
                if (sourcesBacklog == null) {
                    sourcesBacklog = new ArrayList<>();
                }
                sourcesBacklog.add(sourceLoaded);
            }
            return null;
        } else {
            return assureLoaded(sourceLoaded, truffleContext);
        }
    }

    private Script assureLoaded(Source sourceLoaded, TruffleContext truffleContext) {
        DebuggerSession ds = debuggerSession;
        Source sourceResolved = sourceLoaded;
        if (ds != null) {
            sourceResolved = ds.resolveSource(sourceLoaded);
        }
        Source source = (sourceResolved != null) ? sourceResolved : sourceLoaded;
        Script scr;
        LoadScriptListener[] listenersToNotify;
        synchronized (sourceIDs) {
            Integer eid = sourceIDs.get(source);
            if (eid != null) {
                scr = scripts.get(eid);
                assert scr != null : sourceLoaded;
                return scr;
            }
            int id = scripts.size();
            String sourceUrl = getSourceURL(source, truffleContext);
            scr = new Script(id, sourceUrl, source, sourceLoaded);
            sourceIDs.put(source, id);
            sourceIDs.put(sourceLoaded, id);
            scripts.add(scr);
            listenersToNotify = listeners.toArray(new LoadScriptListener[listeners.size()]);
        }
        for (LoadScriptListener l : listenersToNotify) {
            l.loadedScript(scr);
        }
        return scr;
    }

    public String getSourceURL(Source source) {
        return getSourceURL(source, null);
    }

    private String getSourceURL(Source source, TruffleContext truffleContext) {
        URL url = source.getURL();
        if (url != null) {
            return url.toExternalForm();
        }
        String path = source.getPath();
        if (path != null) {
            if (source.getURI().isAbsolute()) {
                return source.getURI().toString();
            } else {
                try {
                    return env.getTruffleFile(truffleContext, path).getAbsoluteFile().toUri().toString();
                } catch (SecurityException ex) {
                    if (File.separatorChar == '/') {
                        return path;
                    } else {
                        return path.replace(File.separatorChar, '/');
                    }
                }
            }
        }
        String name = source.getName();
        if (name != null) {
            String uniqueName;
            synchronized (uniqueSourceNames) {
                int count = uniqueSourceNames.getOrDefault(name, 0);
                count++;
                if (count == 1) {
                    uniqueName = name;
                } else {
                    do {
                        uniqueName = count + "/" + name;
                    } while (uniqueSourceNames.containsKey(uniqueName) && (count++) > 0);
                }
                uniqueSourceNames.put(name, count);
            }
            return uniqueName;
        }
        return source.getURI().toString();
    }

    @Override
    public void onLoad(LoadSourceEvent event) {
        Source source = event.getSource();
        if (reportInternal || !source.isInternal()) {
            assureLoaded(source);
        }
    }

    void dispose() {
        contextsBinding.dispose();
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

    private final class ContextTracker implements ContextsListener {

        @Override
        public void onContextCreated(TruffleContext context) {
            synchronized (contexts) {
                contexts.add(context);
            }
            if (sourcesBacklog != null) {
                List<Source> oldSources = null;
                synchronized (sourceIDs) {
                    if (sourcesBacklog != null) {
                        oldSources = sourcesBacklog;
                        sourcesBacklog = null;
                    }
                }
                if (oldSources != null) {
                    for (Source source : oldSources) {
                        assureLoaded(source, context);
                    }
                }
            }
        }

        @Override
        public void onContextClosed(TruffleContext context) {
            synchronized (contexts) {
                contexts.remove(context);
            }
        }

        @Override
        public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
        }
    }
}
