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

import org.graalvm.visualizer.source.impl.actions.RemoveSourceRootAction;
import org.graalvm.visualizer.source.impl.ui.NewGroupAction;
import org.graalvm.visualizer.source.impl.ui.NewSourceRootAction;
import org.netbeans.api.java.classpath.ClassPath;
import org.openide.actions.RenameAction;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@NbBundle.Messages({
        "NAME_SourceRepository=Sources"
})
public class SourceRepositoryNode extends AbstractNode {
    private final SourceRepositoryImpl repository;

    public SourceRepositoryNode(SourceRepositoryImpl repository) {
        this(repository, false);
    }

    public SourceRepositoryNode(SourceRepositoryImpl repository, boolean displayDefault) {
        super(new Ch(repository, displayDefault));
        this.repository = repository;
        setDisplayName(Bundle.NAME_SourceRepository());
    }

    @Override
    public Action[] getActions(boolean context) {
        return new Action[]{
                new NewGroupAction(repository),
                new NewSourceRootAction(repository, null)
        };
    }

    static class Ch extends Children.Keys implements ChangeListener, PropertyChangeListener {
        private final SourceRepositoryImpl repository;
        private final boolean displayDefaultGroup;
        private ChangeListener weakChangeL;
        private PropertyChangeListener weakPropL;

        public Ch(SourceRepositoryImpl repository, boolean displayDefaultGroup) {
            this.repository = repository;
            this.displayDefaultGroup = displayDefaultGroup;
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
            if (weakChangeL != null) {
                repository.removeChangeListener(weakChangeL);
            }
            if (weakPropL != null) {
                ClassPath defCP = repository.getDefaultGroup().getSourcePath();
                defCP.removePropertyChangeListener(weakPropL);
            }
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            refreshKeys();
            repository.addChangeListener(weakChangeL = WeakListeners.change(this, repository));
            ClassPath defCP = repository.getDefaultGroup().getSourcePath();
            defCP.addPropertyChangeListener(weakPropL =
                    WeakListeners.propertyChange(this, ClassPath.PROP_ENTRIES, defCP));
        }

        private void refreshKeys() {
            List keys = new ArrayList();
            if (!displayDefaultGroup) {
                keys.addAll(repository.getDefaultGroup().getFileRoots());
            }
            List<FileGroup> groups = new ArrayList<>(repository.getGroups());
            if (displayDefaultGroup) {
                groups.add(repository.getDefaultGroup());
            }
            groups.sort((g1, g2) -> g1.getDisplayName().compareTo(g2.getDisplayName()));
            keys.addAll(groups);
            setKeys(keys);
        }

        @Override
        protected Node[] createNodes(Object key) {
            if (key instanceof FileRoot) {
                FileRoot r = (FileRoot) key;
                Node n = createFileRootNode(r, repository);
                if (n != null) {
                    return new Node[]{n};
                }
            } else if (key instanceof FileGroup) {
                FileGroup g = (FileGroup) key;
                GCh children = new GCh(g, repository);
                Lookup lkp = Lookups.fixed(g);
                GN node = new GN(repository, children, lkp, g);
                return new Node[]{node};
            }
            return new Node[0];
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            refreshKeys();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            refreshKeys();
        }
    }

    static Node createFileRootNode(FileRoot r, SourceRepositoryImpl repository) {
        URL url = r.getLocation();
        FileObject f = URLMapper.findFileObject(url);
        if (f != null) {
            Lookup fl = f.getLookup();
            Lookup lkp = new ProxyLookup(fl, Lookups.fixed(r, repository));
            Node original = fl.lookup(Node.class);
            if (original == null) {
                DataObject d = fl.lookup(DataObject.class);
                if (d == null) {
                    return null;
                }
                original = d.getNodeDelegate();
            }
            FilterNode.Children fch = new FilterNode.Children(original);
            return new RN(original, fch, lkp, r);
        }

        return null;
    }

    static final class GCh extends Children.Keys implements ChangeListener {
        private final FileGroup group;
        private final SourceRepositoryImpl repository;
        private ChangeListener weakChangeL;

        public GCh(FileGroup group, SourceRepositoryImpl repository) {
            this.group = group;
            this.repository = repository;
        }

        @Override
        protected void removeNotify() {
            if (weakChangeL != null) {
                group.removeChangeListener(weakChangeL);
            }
            super.removeNotify();
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            refreshKeys();
            group.addChangeListener(weakChangeL = WeakListeners.change(this, group));
        }

        private void refreshKeys() {
            List<FileRoot> roots = group.getFileRoots();
            setKeys(roots.toArray(new FileRoot[roots.size()]));
        }

        @Override
        protected Node[] createNodes(Object key) {
            Node n = createFileRootNode((FileRoot) key, repository);
            return n != null ? new Node[]{n} : new Node[0];
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            refreshKeys();
        }
    }

    static final class GN extends AbstractNode implements PropertyChangeListener {
        private final SourceRepositoryImpl repository;
        private final FileGroup group;

        public GN(SourceRepositoryImpl repository, Children children, Lookup lookup, FileGroup group) {
            super(children, lookup);
            this.repository = repository;
            this.group = group;
            setIconBaseWithExtension("org/graalvm/visualizer/source/resources/defaultFolder.gif"); // NOI18N
            super.setName(group.getDisplayName());
            group.addPropertyChangeListener(WeakListeners.propertyChange(this, group));
        }

        @Override
        public void setName(String s) {
            super.setName(s);
            group.setDisplayName(s);
        }

        @Override
        public void destroy() throws IOException {
            super.destroy();
            repository.deleteGroup(group);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (PROP_DISPLAY_NAME.equals(evt.getPropertyName())) {
                super.setDisplayName(group.getDisplayName());
            }
        }

        @Override
        public Action[] getActions(boolean context) {
            return new Action[]{
                    SystemAction.get(RenameAction.class),
                    SystemAction.get(RemoveSourceRootAction.class),
                    new NewSourceRootAction(repository, group)
            };
        }

        @Override
        public boolean canDestroy() {
            return true;
        }

        @Override
        public boolean canRename() {
            return true;
        }

    }

    static final class RN extends AbstractNode implements PropertyChangeListener {
        FileRoot data;

        public RN(Node original, Children children, Lookup lookup, FileRoot r) {
            super(children, lookup);
            this.data = r;
            super.setDisplayName(r.getDisplayName());
            r.addPropertyChangeListener(WeakListeners.propertyChange(this, r));
            setIconBaseWithExtension("org/graalvm/visualizer/source/resources/packageRoot.gif"); // NOI18N
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String prop = evt.getPropertyName();
            if (PROP_DISPLAY_NAME.equals(prop)) {
                super.setDisplayName(data.getDisplayName());
            }
        }

        @Override
        public Action[] getActions(boolean context) {
            return new Action[]{
                    SystemAction.get(RenameAction.class),
                    SystemAction.get(RemoveSourceRootAction.class),
            };
        }

        @Override
        public boolean canDestroy() {
            return true;
        }

        @Override
        public boolean canRename() {
            return true;
        }

        @Override
        public void setName(String s) {
            super.setName(s);
            data.setDisplayName(s);
        }

        @Override
        public void destroy() throws IOException {
            super.destroy();
            data.discard();
        }

    }
}
