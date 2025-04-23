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

package org.graalvm.visualizer.filter.profiles.impl;

import java.io.IOException;
import java.util.Collections;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterEnvironment;
import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.filter.profiles.FilterDefinition;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.util.ListenerSupport;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.*;
import org.openide.util.Lookup;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * Bridges the filter implementation to a filter extracted from a file.
 * Synchronizes filter properties with the file (name, contents). Note that
 * default profile's filters are not renamed on disk, just their display name
 * is changed (is stored in fs attributes). That way links to those filters are
 * not broken by rename.
 */
public class FilterBridge implements Filter, FilterDefinition, ChangedListener {
    final FilterProfile ops;
    final FileObject file;
    final FilterProvider source;
    private final ChangedEvent<Filter> event = new ChangedEvent<>(this);
    final ProfileService profiles;

    private Filter lastDelegate;
    private final L l = new L();
    private ChangedListener weakCL;
    private final PL lookup = new PL();
    int hashCode = -1;

    class PL extends ProxyLookup {
        void updateLookup(Lookup lkp) {
            FileObject f = file;
            if (f == null) {
                setLookups(lkp);
            } else {
                this.setLookups(Lookups.fixed(f, FilterBridge.this, ops), lkp);
            }
        }
    }

    public FilterBridge(FileObject f, FilterProfile ops, FilterProvider source, ProfileService srv) {
        this.ops = ops;
        this.file = f;
        this.source = source;
        this.profiles = srv;

        f.addFileChangeListener(WeakListeners.create(FileChangeListener.class, l, f));
        source.addChangeListener(WeakListeners.change(l, source));
    }

    class L extends FileChangeAdapter implements ChangeListener {
        @Override
        public void fileAttributeChanged(FileAttributeEvent fe) {
            super.fileAttributeChanged(fe);
            if ("displayName".equals(fe.getName())) { // NOI18N
                getChangedEvent().fire();
            }
        }

        @Override
        public void fileRenamed(FileRenameEvent fe) {
            super.fileRenamed(fe);
            getChangedEvent().fire();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            updateDelegate();
        }
    }

    @Override
    public Filter getOriginalFilter() {
        FileObject link = file.getLookup().lookup(FileObject.class);
        for (Filter f : profiles.getDefaultProfile().getProfileFilters()) {
            FileObject ff = f.getLookup().lookup(FileObject.class);
            if (ff == link) {
                return f;
            }
        }
        return this;
    }

    @Override
    public void setName(String newName) throws IOException {
        profiles.renameFilter(this, newName);
    }

    private Filter delegate() {
        Filter f = source.getFilter();
        if (f != null) {
            return changeDelegate(f);
        }
        return changeDelegate(Filter.NONE);
    }

    public int hashCode() {
        if (hashCode == -1) {
            return delegate().hashCode();
        }
        return hashCode;
    }

    private Filter updateDelegate() {
        Filter f = source.createFilter(false);
        if (f != null) {
            return changeDelegate(f);
        }
        return changeDelegate(Filter.NONE);
    }

    private Filter changeDelegate(Filter delegate) {
        Filter old;

        synchronized (this) {
            if (delegate == lastDelegate) {
                return delegate;
            }
            old = lastDelegate;
            lastDelegate = delegate;
            if (old != null && weakCL != null) {
                old.getChangedEvent().removeListener(weakCL);
            }
            if (delegate != null) {
                weakCL = ListenerSupport.addWeakListener(this, delegate.getChangedEvent());
            } else {
                weakCL = null;
            }
        }
        if (delegate != null) {
            if (hashCode == -1) {
                this.hashCode = delegate.hashCode();
            }
            lookup.updateLookup(delegate.getLookup());
        }
        if (old != null) {
            event.fire();
        }
        return delegate;
    }

    @Override
    public String getName() {
        return file2Name(file);
    }

    static final String file2Name(FileObject f) {
        if (f != null) {
            try {
                return f.getFileSystem().getDecorator().annotateName(f.getName(), Collections.singleton(f));
            } catch (FileStateInvalidException ex) {

            }
        }
        return Filter.NONE.getName();
    }

    @Override
    public Lookup getLookup() {
        delegate();
        return lookup;
    }

    @Override
    public OpenCookie getEditor() {
        return getLookup().lookup(OpenCookie.class);
    }

    @Override
    public ChangedEvent<Filter> getChangedEvent() {
        return event;
    }

    @Override
    public Properties getProperties() {
        return delegate().getProperties();
    }

    @Override
    public void applyWith(FilterEnvironment env) {
        delegate().applyWith(env);
    }

    @Override
    public boolean cancel(FilterEnvironment d) {
        return delegate().cancel(d);
    }

    @Override
    public void changed(Object source) {
        if (SwingUtilities.isEventDispatchThread()) {
            event.fire();
        } else {
            SwingUtilities.invokeLater(event::fire);
        }
    }

    public String toString() {
        return "FB[" + file.getPath() + ", impl = " + lastDelegate + "]";
    }
}
