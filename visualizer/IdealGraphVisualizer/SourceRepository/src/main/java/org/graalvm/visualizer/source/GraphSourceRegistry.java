/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.source;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * System-wide registry of initialized {@link GraphSource} objects,
 * so they can be found or computed for a InputGraph. Use {@link GraphSource#getGraphSource(org.graalvm.visualizer.data.InputGraph)}
 * to obtain GraphSource instance.
 */
class GraphSourceRegistry {
    private final FileRegistry fileRegistry;
    private final Map<InputGraph, Reference<GraphSource>> graphSources = new WeakHashMap<>();
    private final Map<String, Lookup> serviceLookups = new HashMap<>();

    GraphSourceRegistry(FileRegistry fileRegistry) {
        this.fileRegistry = fileRegistry;
    }

    private static GraphSourceRegistry INSTANCE;

    public static GraphSourceRegistry getDefault() {
        synchronized (GraphSourceRegistry.class) {
            if (INSTANCE == null) {
                INSTANCE = new GraphSourceRegistry(Lookup.getDefault().lookup(FileRegistry.class));
            }
        }
        return INSTANCE;
    }

    public GraphSource getSource(InputGraph g) {
        Reference<GraphSource> rsrc;
        synchronized (this) {
            rsrc = graphSources.get(g);
            if (rsrc != null) {
                GraphSource s = rsrc.get();
                if (s != null) {
                    return s;
                }
            }
            GraphSource s = new GraphSource(g, fileRegistry);
            graphSources.put(g, new WeakReference<>(s));
            return s;
        }
    }

    Lookup providerLookup(String mime) {
        Lookup lkp;
        synchronized (this) {
            lkp = serviceLookups.get(mime);
            if (lkp != null) {
                return lkp;
            }
        }
        Lookup mimeLkp = MimeLookup.getLookup(mime);
        lkp = new ProxyLookup(mimeLkp, Lookup.getDefault());

        synchronized (this) {
            serviceLookups.put(mime, lkp);
        }
        return lkp;
    }

    synchronized static void _testReset() {
        FileRegistry._testReset();
        INSTANCE = new GraphSourceRegistry(FileRegistry.getInstance());
    }
}
