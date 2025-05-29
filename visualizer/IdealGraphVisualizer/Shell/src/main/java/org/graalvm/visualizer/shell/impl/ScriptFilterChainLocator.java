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
package org.graalvm.visualizer.shell.impl;

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.filter.spi.GraphFilterLocator;
import org.graalvm.visualizer.shell.ShellUtils;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author sdedic
 */
@ServiceProvider(service = GraphFilterLocator.class)
public class ScriptFilterChainLocator implements GraphFilterLocator {
    private LookupBasedProvider globalProvider;
    private final Map<FileObject, Reference<FilterProvider>> fileCache = new WeakHashMap<>();

    public ScriptFilterChainLocator() {
    }

    @Override
    public FilterProvider findChain(Lookup lkp) {
        FileObject f = null;
        FileObject fp = null;
        if (lkp != null) {
            DataShadow sh = null;
            Collection<? extends FileObject> ff = lkp.lookupAll(FileObject.class);
            if (!ff.isEmpty()) {
                Iterator<? extends FileObject> it = ff.iterator();
                f = it.next();
                try {
                    // hook in a DataObject to initialize FO's lookup
                    DataObject d = DataObject.find(f);
                    if (d instanceof DataShadow) {
                        sh = (DataShadow) d;
                    }
                } catch (IOException ex) {
                    // ignore
                }
                if (sh == null) {
                    sh = lkp.lookup(DataShadow.class);
                }
                if (it.hasNext()) {
                    f = null;
                }
            }
            if (sh != null && f != null) {
                fp = sh.getPrimaryFile();
                try {
                    FileObject target = DataShadow.findOriginal(f);
                    if (target != null) {
                        f = target;
                    }
                } catch (IOException ex) {
                }
            } else {
                fp = f;
            }
        }
        FilterProvider ch;
        if (f != null && f.isValid() && f.isData()) {
            if (!(ShellUtils.isScriptMimeType(f.getMIMEType()) &&
                    ShellUtils.isScriptObject(f))) {
                return null;
            }
            synchronized (this) {
                Reference<FilterProvider> ref = fileCache.get(fp);
                ch = ref == null ? null : ref.get();
                if (ch != null) {
                    return ch;
                } else {
                    ch = new FileChainProvider(f);
                    fileCache.put(fp, new WeakReference<>(ch));
                }
            }
            return ch;
        }
        if (lkp != null) {
            return null;
        }
        return globalProvider();
    }

    private synchronized LookupBasedProvider globalProvider() {
        if (globalProvider == null) {
            globalProvider = new LookupBasedProvider(Utilities.actionsGlobalContext(),
                    this);
        }
        return globalProvider;
    }

    static class NullSource implements FilterProvider {
        @Override
        public Filter createFilter(boolean createNew) throws IllegalStateException {
            return null;
        }

        @Override
        public Filter getFilter() {
            return null;
        }

        @Override
        public void addChangeListener(ChangeListener l) {
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
        }
    }

    private static final FilterProvider NULL_SOURCE = new NullSource();

    class LookupBasedProvider implements FilterProvider, LookupListener {
        /**
         * Provider from the currently selected file
         */
        private FilterProvider delegate = NULL_SOURCE;
        private final ScriptFilterChainLocator master;
        private final Lookup context;
        private final Lookup.Result<FileObject> fileResult;
        private final List<ChangeListener> listeners = new ArrayList<>();

        private FileObject currentFile;

        public LookupBasedProvider(Lookup context, ScriptFilterChainLocator master) {
            this.context = context;
            this.fileResult = context.lookupResult(FileObject.class);
            fileResult.addLookupListener(WeakListeners.create(LookupListener.class, this, context));
            this.master = master;
        }

        private FilterProvider updateFile() {
            Collection c = fileResult.allInstances();
            FileObject newFile;

            if (c == null || c.isEmpty()) {
                newFile = null;
            } else {
                newFile = (FileObject) c.iterator().next();
            }

            synchronized (this) {
                if (currentFile == newFile) {
                    return delegate;
                }
                this.currentFile = newFile;
                if (newFile == null) {
                    return delegate = NULL_SOURCE;
                }
            }
            FilterProvider prov = master.findChain(newFile.getLookup());
            if (prov == null || prov == this) {
                prov = NULL_SOURCE;
            }
            synchronized (this) {
                if (currentFile == newFile) {
                    this.delegate = prov;
                }
            }
            fireChangeEvent();
            return prov;
        }

        @Override
        public Filter getFilter() {
            synchronized (this) {
                if (delegate != null) {
                    return delegate.getFilter();
                }
            }
            return createFilter(false);
        }

        @Override
        public Filter createFilter(boolean createNew) throws IllegalStateException {
            FilterProvider cur = this.delegate;
            FilterProvider dele = updateFile();
            Filter result;
            if (dele != cur) {
                fireChangeEvent();
                result = dele == null ? null : dele.createFilter(createNew);
            } else {
                result = null;
            }
            return result;
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            FilterProvider src;
            synchronized (this) {
                src = this.delegate;
            }
            FilterProvider newDele = updateFile();
            if (newDele != src) {
                fireChangeEvent();
            }
        }

        private void fireChangeEvent() {
            ChangeListener[] ll;
            synchronized (this) {
                if (listeners.isEmpty()) {
                    return;
                }
                ll = listeners.toArray(new ChangeListener[listeners.size()]);
            }
            ChangeEvent e = new ChangeEvent(this);
            for (ChangeListener l : ll) {
                l.stateChanged(e);
            }
        }

        @Override
        public void addChangeListener(ChangeListener l) {
            synchronized (this) {
                listeners.add(l);
            }
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
            synchronized (this) {
                listeners.remove(l);
            }
        }
    }
}
