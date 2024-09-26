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

package org.graalvm.visualizer.source.impl;

import org.graalvm.visualizer.source.SourcesRoot;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.spi.java.classpath.ClassPathFactory;
import org.netbeans.spi.java.classpath.ClassPathImplementation;
import org.netbeans.spi.java.classpath.PathResourceImplementation;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Represents one or more folders. There's one implicit FileGroup that represents "other" uncategorized sources.
 */
public class FileGroup implements SourcesRoot, PathResourceImplementation {
    static final String PROP_DISPLAY_NAME = "displayName";
    /**
     * Provides additional cleanup of possibly dangling FileGroups from the GlobalPathRegistry
     */
    private static final List<W> registeredPaths = new ArrayList<>();

    private final URI uri;
    private final List<ChangeListener> listeners = new ArrayList<>();
    private final List<FileRoot> roots = new ArrayList<>();
    private final ClassPath cpI;
    private final MutableClassPathImplementation cpImpl;
    private final PropertyChangeSupport supp = new PropertyChangeSupport(this);
    private final SourceRepositoryImpl storage;
    private final Lookup lkp;
    private final W wPathRef;

    private String displayName;
    // @GuardedBy(roots)
    private List<URL> fileRoots = new ArrayList<>();

    public FileGroup(URI uri, String displayName, SourceRepositoryImpl storage) {
        this.uri = uri;
        this.displayName = displayName;
        this.storage = storage;
        this.cpImpl = new MutableClassPathImplementation();
        this.cpI = ClassPathFactory.createClassPath(cpImpl);
        lkp = Lookups.fixed(cpI, storage);
        wPathRef = new W(getSourcePath(), this);
    }

    void addRegisteredPath() {
        synchronized (registeredPaths) {
            registeredPaths.add(wPathRef);
        }
        GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, new ClassPath[]{wPathRef.registeredPath});
    }

    void removeRegisteredPath() {
        removeRegisteredPath(wPathRef);
        wPathRef.clear();
    }

    private static void removeRegisteredPath(W w) {
        synchronized (registeredPaths) {
            registeredPaths.remove(w);
        }
        GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, new ClassPath[]{w.registeredPath});
    }

    public List<FileRoot> getFileRoots() {
        synchronized (roots) {
            return new ArrayList<>(roots);
        }
    }

    public boolean contains(URL u) {
        synchronized (roots) {
            for (FileRoot r : roots) {
                if (r.getLocation().equals(u)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public URL[] getRoots() {
        return fileRoots.toArray(new URL[fileRoots.size()]);

    }

    @Override
    public ClassPathImplementation getContent() {
        return cpImpl;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        supp.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        supp.removePropertyChangeListener(listener);
    }

    private void refreshURLs() {
        List<URL> urls = new ArrayList<>(roots.size());
        for (FileRoot fr : roots) {
            urls.add(fr.getLocation());
        }
        this.fileRoots = urls;
    }

    void addRoot(FileRoot r) {
        synchronized (roots) {
            roots.add(r);
            refreshURLs();
        }
        cpImpl.addResource(r.getLocation());
        supp.firePropertyChange(PROP_ROOTS, null, null);
        fireChange();
    }

    public void removeRoot(FileRoot r) {
        synchronized (roots) {
            if (!roots.remove(r)) {
                return;
            }
            refreshURLs();
        }
        cpImpl.removeResource(r.getLocation());
        supp.firePropertyChange(PROP_ROOTS, null, null);
        fireChange();
    }

    @Override
    public URI getURI() {
        return uri;
    }

    public void setDisplayName(String displayName) {
        String old = this.displayName;
        if (Objects.equals(old, displayName)) {
            return;
        }
        try {
            storage.renameGroup(this, displayName);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        this.displayName = displayName;
        supp.firePropertyChange(PROP_DISPLAY_NAME, old, displayName);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public ClassPath getSourcePath() {
        return cpI;
    }

    @Override
    public Lookup getLookup() {
        return lkp;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    void fireChange() {
        ChangeListener[] ll;
        synchronized (listeners) {
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
    public String toString() {
        return "Group-" + uri + "[" + getDisplayName() + "]";
    }

    private static class MutableClassPathImplementation implements ClassPathImplementation {

        private final List<PathResourceImplementation> res;
        private final PropertyChangeSupport support;

        public MutableClassPathImplementation() {
            res = new ArrayList<PathResourceImplementation>();
            support = new PropertyChangeSupport(this);
        }

        public void addResource(URL url) {
            res.add(ClassPathSupport.createResource(url));
            this.support.firePropertyChange(PROP_RESOURCES, null, null);
        }

        public void removeResource(URL url) {
            for (Iterator<PathResourceImplementation> it = res.iterator(); it.hasNext(); ) {
                PathResourceImplementation r = it.next();
                if (url.equals(r.getRoots()[0])) {
                    it.remove();
                    this.support.firePropertyChange(PROP_RESOURCES, null, null);
                }
            }
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            support.removePropertyChangeListener(listener);
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            support.addPropertyChangeListener(listener);
        }

        @Override
        public List<PathResourceImplementation> getResources() {
            return res;
        }

    }

    private static class W extends WeakReference<FileGroup> implements Runnable {
        private final ClassPath registeredPath;

        public W(ClassPath registeredPath, FileGroup referent) {
            super(referent, Utilities.activeReferenceQueue());
            this.registeredPath = registeredPath;
        }

        /**
         * This method will be probably never called, but in case of some dangling FileGroup its Classpath
         * will be removed from the global registry
         */
        @Override
        public void run() {
            removeRegisteredPath(this);
        }
    }
}
