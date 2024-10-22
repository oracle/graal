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
package org.graalvm.visualizer.shell.list;

import org.graalvm.visualizer.shell.ShellUtils;
import org.openide.filesystems.FileObject;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.BaseUtilities;

import javax.swing.Action;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author sdedic
 */
public class ScriptFolderNode extends FilterNode {

    public ScriptFolderNode(Node original) {
        super(original, new Ch(original));
        disableDelegation(DELEGATE_GET_ACTIONS | DELEGATE_GET_CONTEXT_ACTIONS);
    }

    @Override
    public Action[] getActions(boolean context) {
        FileObject f = getOriginal().getLookup().lookup(FileObject.class);
        Action[] acts = getOriginal().getActions(context);
        List<Action> res = new ArrayList<>(acts.length);
        boolean newFileAction = false;
        for (Action a : acts) {
            if (a != null && a.getClass().getName().contains(".New")) {
                if (!newFileAction) {
                    newFileAction = true;
                    res.add(new NewScriptAction(f));
                }
            } else {
                res.add(a);
            }
        }
        return res.toArray(new Action[res.size()]);
    }

    private static final Node[] NONE = new Node[0];

    private static class RefCh extends WeakReference<Ch> implements Runnable, Consumer<FileObject> {
        private final Reference<FileObject> refFile;

        public RefCh(Ch referent, FileObject file) {
            super(referent, BaseUtilities.activeReferenceQueue());
            refFile = new WeakReference<>(file);
        }

        @Override
        public void run() {
            FileObject f = refFile.get();
            if (f != null) {
                ShellUtils.removeMaterializeCallback(f, this);
            }
        }

        @Override
        public void accept(FileObject t) {
            Ch ch = get();
            if (ch != null) {
                ch.refreshNodes();
            }
        }
    }

    static class Ch extends FilterNode.Children {
        private final Node original;

        public Ch(Node or) {
            super(or);
            this.original = or;
        }

        @Override
        protected Node[] createNodes(Node key) {
            FileObject f = key.getLookup().lookup(FileObject.class);
            if (f == null || ShellUtils.visibleScriptObjects().test(f)) {
                return super.createNodes(key);
            }
            ShellUtils.onScrapMaterialize(f, new RefCh(this, f));
            return NONE;
        }

        private void refreshNodes() {
            setKeys(original.getChildren().getNodes(true));
        }

        @Override
        protected Node copyNode(Node node) {
            FileObject f = node.getLookup().lookup(FileObject.class);
            if (f == null) {
                return super.copyNode(node);
            }
            if (f.isFolder()) {
                return new ScriptFolderNode(node);
            } else {
                return ScriptNode.create(node, LEAF);
            }
        }
    }
}
