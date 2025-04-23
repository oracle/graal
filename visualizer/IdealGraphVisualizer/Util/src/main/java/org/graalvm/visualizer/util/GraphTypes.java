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

package org.graalvm.visualizer.util;

import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileUIUtils;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates category nodes for individual Graph types.
 * Uses registrations in System FileSystem, in /IGV/GraphTypes/&lt;graph-type&gt;
 *
 * @author sdedic
 */
@NbBundle.Messages("NAME_GraphTypesParent=Graph types")
@ServiceProvider(service = GraphTypes.class)
public class GraphTypes {
    private final AbstractNode rootNode;
    private final Map<String, Node> typeNodes = new HashMap<>();
    private final FileObject registrationRoot;
    private final AbstractNode unknownNode = new AbstractNode(Children.LEAF);

    // @GuardedBy(this)
    private List<String> types = null;

    @NbBundle.Messages({
            "DefaultGraphType=Unknown Graph"
    })
    public GraphTypes() {
        registrationRoot = FileUtil.getConfigFile("IGV/GraphTypes");
        rootNode = new AbstractNode(new Ch());
        unknownNode.setIconBaseWithExtension("org/graalvm/visualizer/util/resources/graph_default.png");
        unknownNode.setName("<default>"); // NOI18N
        unknownNode.setDisplayName(Bundle.DefaultGraphType());
    }

    public List<String> getOrderedTypes() {
        List<String> tt = types;
        if (tt != null) {
            return tt;
        }
        synchronized (this) {
            if (types == null) {
                refreshTypes();
            }
            return types;
        }
    }

    public Comparator<String> typeOrderComparator() {
        // create a new instance with a snapshot of types
        return new Comparator<String>() {
            private List<String> typeOrder = GraphTypes.this.getOrderedTypes();

            @Override
            public int compare(String o1, String o2) {
                int a = typeOrder.indexOf(o1);
                int b = typeOrder.indexOf(o2);

                if (a == b) {
                    if (a == -1) {
                        // order not defined explicitly, defined by the type names.
                        return o1.compareToIgnoreCase(o2);
                    } else {
                        // both found at the same index
                        return 0;
                    }
                }
                if (b == -1) {
                    b = Integer.MAX_VALUE;
                }
                return a - b;
            }
        };
    }

    public Node getCategoryNode() {
        return rootNode;
    }

    private static class TypeNode extends AbstractNode {
        private final FileObject typeFile;

        public TypeNode(FileObject typeFile, Children children) {
            super(children);
            this.typeFile = typeFile;
        }

        @Override
        public Image getOpenedIcon(int type) {
            try {
                return FileUIUtils.getImageDecorator(typeFile.getFileSystem()).annotateIcon(null, type, Collections.singleton(typeFile));
            } catch (FileStateInvalidException ex) {
                return super.getOpenedIcon(type);
            }
        }

        @Override
        public Image getIcon(int type) {
            try {
                return FileUIUtils.getImageDecorator(typeFile.getFileSystem()).annotateIcon(null, type, Collections.singleton(typeFile));
            } catch (FileStateInvalidException ex) {
                return super.getIcon(type);
            }
        }
    }

    public synchronized Node getTypeNode(String type) {
        Node n = typeNodes.get(type);
        if (n != null) {
            return n;
        }
        FileObject t = registrationRoot.getFileObject(type);
        if (t == null) {
            return unknownNode;
        }
        n = createTypeNode(t);
        typeNodes.put(type, n);
        return n;
    }

    private synchronized Node createTypeNode(FileObject t) {
        String type = t.getName();
        Node n = typeNodes.get(type);
        if (n != null) {
            return n;
        }
        String dispName;
        try {
            dispName = t.getFileSystem().getDecorator().annotateName(type, Collections.singleton(t));
        } catch (FileStateInvalidException ex) {
            dispName = type;
        }
        AbstractNode an = new TypeNode(t, Children.LEAF);
        an.setName(type);
        an.setDisplayName(dispName);
        typeNodes.put(type, an);
        return an;
    }

    FileObject[] refreshTypes() {
        FileObject[] contents = registrationRoot.getChildren();
        List<String> types = new ArrayList<>(contents.length);
        for (FileObject fo : contents) {
            types.add(fo.getName());
        }
        synchronized (this) {
            this.types = Collections.unmodifiableList(types);
        }
        return contents;
    }

    private final class Ch extends Children.Keys<FileObject> implements FileChangeListener {

        public Ch() {
            registrationRoot.addFileChangeListener(WeakListeners.create(FileChangeListener.class, this, registrationRoot));
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            refreshFiles();
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
        }

        @Override
        public void fileChanged(FileEvent fe) {
            refreshFiles();
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            refreshFiles();
        }

        @Override
        public void fileRenamed(FileRenameEvent fre) {
            refreshFiles();
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fae) {
            refreshFiles();
        }

        void refreshFiles() {
            setKeys(refreshTypes());
        }


        @Override
        protected Node[] createNodes(FileObject t) {
            Node n = createTypeNode(t);
            return n == null ? null : new Node[]{n};
        }
    }
}
