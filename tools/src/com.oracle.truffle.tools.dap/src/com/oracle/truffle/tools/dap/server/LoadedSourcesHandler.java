/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.dap.types.DebugProtocolClient;
import com.oracle.truffle.tools.dap.types.LoadedSourceEvent;

import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.graalvm.collections.Pair;

public final class LoadedSourcesHandler implements LoadSourceListener {

    private final ExecutionContext context;
    private final DebuggerSession debuggerSession;
    private final Map<Source, Integer> sourceIDs = new HashMap<>(100);
    private final List<DAPSourceWrapper> sources = new ArrayList<>(100);
    private final Map<String, Source> sourcesByPath = new HashMap<>(100);
    private final Map<String, Consumer<Source>> toRunOnLoad = new HashMap<>();
    private volatile List<Source> sourcesBacklog = null;
    private final Object sourcesLock = sourceIDs;

    public LoadedSourcesHandler(ExecutionContext context, DebuggerSession debuggerSession) {
        this.context = context;
        this.debuggerSession = debuggerSession;
    }

    @Override
    public void onLoad(LoadSourceEvent event) {
        final Source source = event.getSource();
        if (context.isInspectInternal() || !source.isInternal()) {
            assureLoaded(source);
        }
    }

    public int getScriptId(Source source) {
        synchronized (sourcesLock) {
            Integer id = sourceIDs.get(source);
            if (id != null) {
                return id;
            }
        }
        return 0;
    }

    public Source getSource(int id) {
        synchronized (sourcesLock) {
            if (id > sources.size()) {
                return null;
            }
            return sources.get(id - 1).truffleSource;
        }
    }

    public Source getSource(String path) {
        synchronized (sourcesLock) {
            return sourcesByPath.get(path);
        }
    }

    public List<com.oracle.truffle.tools.dap.types.Source> getLoadedSources() {
        synchronized (sourcesLock) {
            int n = sources.size();
            com.oracle.truffle.tools.dap.types.Source[] arr = new com.oracle.truffle.tools.dap.types.Source[n];
            for (int i = 0; i < n; i++) {
                arr[i] = sources.get(i).dapSource;
            }
            return Collections.unmodifiableList(Arrays.asList(arr));
        }
    }

    public void runOnLoad(String path, Consumer<Source> task) {
        if (path == null) {
            return;
        }
        synchronized (sourcesLock) {
            Source source = getSource(path);
            if (source != null) {
                if (task != null) {
                    task.accept(source);
                }
            } else {
                if (task != null) {
                    toRunOnLoad.put(path, task);
                } else {
                    toRunOnLoad.remove(path);
                }
            }
        }
    }

    public com.oracle.truffle.tools.dap.types.Source assureLoaded(Source sourceLoaded) {
        TruffleContext truffleContext = context.getEnv().getEnteredContext();
        if (truffleContext == null) {
            truffleContext = context.getATruffleContext();
        }
        if (truffleContext == null) {
            synchronized (sourcesLock) {
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

    public com.oracle.truffle.tools.dap.types.Source assureLoaded(Source sourceLoaded, TruffleContext truffleContext) {
        Source sourceResolved = debuggerSession.resolveSource(sourceLoaded);
        Source source = (sourceResolved != null) ? sourceResolved : sourceLoaded;
        int id;
        Consumer<Source> task = null;
        com.oracle.truffle.tools.dap.types.Source dapSource;
        synchronized (sourcesLock) {
            Integer eid = sourceIDs.get(source);
            if (eid != null) {
                return sources.get(eid - 1).dapSource;
            }
            id = sources.size() + 1;
            sourceIDs.put(source, id);
            dapSource = from(source, truffleContext);
            sources.add(new DAPSourceWrapper(dapSource, source));
            String path = dapSource.getPath();
            if (path != null) {
                sourcesByPath.put(path, source);
                task = toRunOnLoad.remove(path);
            }
        }
        DebugProtocolClient client = context.getClient();
        if (client != null) {
            client.loadedSource(LoadedSourceEvent.EventBody.create("new", dapSource));
        }
        if (task != null) {
            task.accept(sourceLoaded);
        }
        return dapSource;
    }

    private com.oracle.truffle.tools.dap.types.Source from(Source source, TruffleContext truffleContext) {
        if (source == null) {
            return null;
        }
        com.oracle.truffle.tools.dap.types.Source src = com.oracle.truffle.tools.dap.types.Source.create().setName(source.getName());
        Pair<String, Boolean> pathSrcRef = getPath(source, truffleContext);
        String path = pathSrcRef.getLeft();
        if (path != null) {
            src.setPath(path);
        }
        if (pathSrcRef.getRight()) {
            assert Thread.holdsLock(sourcesLock);
            src.setSourceReference(sourceIDs.get(source));
        }
        return src;
    }

    private Pair<String, Boolean> getPath(Source source, TruffleContext truffleContext) {
        String path = source.getPath();
        boolean srcRef = path == null;
        TruffleFile tFile = null;
        try {
            if (path == null) {
                URI uri = source.getURI();
                if (uri.isAbsolute()) {
                    tFile = context.getEnv().getTruffleFile(truffleContext, uri);
                }
            } else {
                if (!source.getURI().isAbsolute()) {
                    tFile = context.getEnv().getTruffleFile(truffleContext, path);
                }
            }
        } catch (UnsupportedOperationException | IllegalArgumentException | FileSystemNotFoundException ex) {
            // Unsupported URI/path
        }
        if (tFile != null) {
            try {
                path = tFile.getAbsoluteFile().getPath();
                srcRef = !tFile.isReadable();
            } catch (SecurityException ex) {
                // Can not resolve relative path
            }
        } else if (path != null) {
            try {
                srcRef = !context.getEnv().getTruffleFile(truffleContext, path).isReadable();
            } catch (UnsupportedOperationException | SecurityException | IllegalArgumentException ex) {
                // Can not verify readability
                srcRef = true;
            }
        }
        return Pair.create(path, srcRef);
    }

    void notifyNewTruffleContext(TruffleContext truffleContext) {
        if (sourcesBacklog != null) {
            List<Source> oldSources = null;
            synchronized (sourcesLock) {
                if (sourcesBacklog != null) {
                    oldSources = sourcesBacklog;
                    sourcesBacklog = null;
                }
            }
            if (oldSources != null) {
                for (Source source : oldSources) {
                    assureLoaded(source, truffleContext);
                }
            }
        }
    }

    private static final class DAPSourceWrapper {

        final com.oracle.truffle.tools.dap.types.Source dapSource;
        final Source truffleSource;

        DAPSourceWrapper(com.oracle.truffle.tools.dap.types.Source dapSource, Source truffleSource) {
            this.dapSource = dapSource;
            this.truffleSource = truffleSource;
        }

    }
}
