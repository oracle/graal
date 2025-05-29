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

package org.graalvm.visualizer.data.serialization.lazy;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jdk.graal.compiler.graphio.parsing.model.ChangedEventProvider;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;

/**
 *
 */
final class GroupCompleter extends BaseCompleter<List<? extends FolderElement>, LazyGroup> {
    private final StreamIndex streamIndex;

    /**
     * First expansion of the group. During first expansion, some statistics are gathered
     */
    private boolean firstExpand = true;

    /**
     * Loaded data
     */
    private volatile Reference<List<? extends FolderElement>> refData = new WeakReference<>(null);

    public GroupCompleter(Env env, StreamIndex index, StreamEntry groupEntry) {
        super(env, groupEntry);
        this.streamIndex = index;
    }

    public void removeData(FolderElement fe) {
        List<? extends FolderElement> d = refData.get();
        if (d != null) {
            d.remove(fe);
        }
    }

    @Override
    protected List<? extends FolderElement> createEmpty() {
        return new ArrayList<>();
    }

    @Override
    protected List<? extends FolderElement> filter(List<? extends FolderElement> data) {
        Set<String> names = getModel().getExcludedNames();
        if (names.isEmpty()) {
            return data;
        }
        for (Iterator<? extends FolderElement> it = data.iterator(); it.hasNext(); ) {
            FolderElement el = it.next();
            if (names.contains(el.getName())) {
                it.remove();
            }
        }
        return data;
    }

    @Override
    protected List<? extends FolderElement> hookData(List<? extends FolderElement> data) {
        ChangedListener l;
        Object keepalive = future();
        if (keepalive instanceof ChangedListener) {
            l = (ChangedListener) keepalive;
        } else {
            l = new ChangedListener() {
                final Object dataHook = keepalive;

                @Override
                public void changed(Object source) {
                }
            };
        }
        if (data != null) {
            for (FolderElement f : data) {
                if (f instanceof ChangedEventProvider) {
                    // just keep a backreference to the whole list
                    ((ChangedEventProvider) f).getChangedEvent().addListener(l);
                }
            }
        }
        refData = new WeakReference<>(data);
        return data;
    }

    /**
     * Signals partial data update to the group being completed.
     */
    @Override
    protected void setPartialData(List<? extends FolderElement> partialData) {
        super.setPartialData(partialData);
        toComplete.dataReceived();
    }

    @Override
    protected List<? extends FolderElement> load(ReadableByteChannel channel, int majorVersion, int minorVersion, Feedback feedback) throws IOException {
        BinarySource bs = new BinarySource(channel, majorVersion, minorVersion, entry.getStart());
        SingleGroupBuilder builder = new SingleGroupBuilder(
                toComplete, env(), bs,
                streamIndex, entry,
                feedback,
                firstExpand,
                this::setPartialData);
        firstExpand = false;
        new BinaryReader(bs, builder).parse();
        List<? extends FolderElement> els = builder.getItems();
        return els;
    }
}
