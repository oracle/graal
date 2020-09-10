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
package com.oracle.truffle.tools.dap.server;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.dap.types.DebugProtocolClient;
import com.oracle.truffle.tools.dap.types.LoadedSourceEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class LoadedSourcesHandler implements LoadSourceListener {

    private final ExecutionContext context;
    private final DebuggerSession debuggerSession;
    private final Map<Source, Integer> sourceIDs = new HashMap<>(100);
    private final List<Source> sources = new ArrayList<>(100);
    private final Map<String, Consumer<Source>> toRunOnLoad = new HashMap<>();

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
        synchronized (sourceIDs) {
            Integer id = sourceIDs.get(source);
            if (id != null) {
                return id;
            }
        }
        return 0;
    }

    public Source getSource(int id) {
        synchronized (sourceIDs) {
            return sources.get(id - 1);
        }
    }

    public Source getSource(String path) {
        synchronized (sourceIDs) {
            return sources.stream().filter(source -> Objects.equals(path, getPath(source))).findFirst().orElse(null);
        }
    }

    public List<com.oracle.truffle.tools.dap.types.Source> getLoadedSources() {
        synchronized (sourceIDs) {
            return Collections.unmodifiableList(sources.stream().map(this::from).collect(Collectors.toList()));
        }
    }

    public void runOnLoad(String path, Consumer<Source> task) {
        synchronized (sourceIDs) {
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

    public int assureLoaded(Source sourceLoaded) {
        Source sourceResolved = debuggerSession.resolveSource(sourceLoaded);
        Source source = (sourceResolved != null) ? sourceResolved : sourceLoaded;
        int id;
        Consumer<Source> task;
        synchronized (sourceIDs) {
            Integer eid = sourceIDs.get(source);
            if (eid != null) {
                return eid;
            }
            id = sources.size() + 1;
            sourceIDs.put(source, id);
            sources.add(source);
            task = toRunOnLoad.remove(getPath(source));
        }
        if (task != null) {
            task.accept(sourceLoaded);
        }
        DebugProtocolClient client = context.getClient();
        if (client != null) {
            client.loadedSource(LoadedSourceEvent.EventBody.create("new", from(source)));
        }
        return id;
    }

    public com.oracle.truffle.tools.dap.types.Source from(Source source) {
        if (source == null) {
            return null;
        }
        com.oracle.truffle.tools.dap.types.Source src = com.oracle.truffle.tools.dap.types.Source.create().setName(source.getName());
        String path = getPath(source);
        if (path != null) {
            src.setPath(path);
        } else {
            src.setSourceReference(sourceIDs.get(source));
        }
        return src;
    }

    private static String getPath(Source source) {
        String path = source.getPath();
        if (path == null) {
            URI uri = source.getURI();
            if (uri.isAbsolute()) {
                path = uri.getPath();
            }
        }
        return path;
    }
}
