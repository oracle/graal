/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.graalvm.visualizer.data.DataElementHandle;

import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * @author sdedic
 */
public class GraphItem implements ResultItem {
    public static final GraphItem ROOT = new GraphItem("<root>", (DataElementHandle) null); // NOI18N

    private final DataElementHandle handle;
    private final String type;
    private String displayName;
    private Reference<FolderElement> resolved = new WeakReference<>(null);

    public GraphItem(String type, DataElementHandle handle) {
        this.type = type;
        this.handle = handle;
    }

    public GraphItem(String type, FolderElement item) {
        this.type = type;
        this.handle = DataElementHandle.create(item.getOwner(), item);
        this.resolved = new WeakReference<>(item);
        this.displayName = item.getName();
    }

    public CompletableFuture<FolderElement> resolve(Feedback fb) {
        return handle.resolve(fb).thenApply(this::setData);
    }

    private FolderElement setData(FolderElement f) {
        resolved = new WeakReference<>(f);
        return f;
    }

    @Override
    public Properties getProperties() {
        FolderElement d = getData();
        if (d instanceof Properties.Provider) {
            return ((Properties.Provider) d).getProperties();
        } else {
            return Properties.immutableEmpty();
        }
    }

    public DataElementHandle getHandle() {
        return handle;
    }

    public String getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.handle);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GraphItem other = (GraphItem) obj;
        return Objects.equals(this.handle, other.handle);
    }

    public FolderElement getData() {
        return resolved.get();
    }
}
