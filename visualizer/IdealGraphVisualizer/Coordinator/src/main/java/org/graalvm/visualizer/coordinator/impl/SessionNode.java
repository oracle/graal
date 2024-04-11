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
package org.graalvm.visualizer.coordinator.impl;

import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.Folder;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import org.graalvm.visualizer.util.ListenerSupport;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;

import javax.swing.*;
import java.util.Collection;

/**
 * @author sdedic
 */
public class SessionNode extends FolderNode implements ChangedListener<GraphDocument>, LookupListener {
    private static final String ICON_PREFIX = "org/graalvm/visualizer/coordinator/images/"; // NOI18N
    private Lookup.Result<FileObject> storageResult;

    private FileObject file;

    public SessionNode(Folder folder) {
        super(folder);

        Lookup.Provider p = (Lookup.Provider) folder;
        storageResult = p.getLookup().lookupResult(FileObject.class);
        updateUI();
        ListenerSupport.addWeakListener(this, ((GraphDocument) folder).getPropertyChangedEvent());
        storageResult.addLookupListener(WeakListeners.create(LookupListener.class, this, storageResult));
    }

    private GraphDocument doc() {
        return (GraphDocument) folder;
    }

    @Override
    public void resultChanged(LookupEvent le) {
        updateUI();
    }

    @Override
    public void changed(GraphDocument source) {
        SwingUtilities.invokeLater(this::updateUI);
    }

    @NbBundle.Messages({
            "# {0} - display name",
            "FMT_ModifiedHtmlDecoration=<html><b>{0}</b></html>"
    })
    private void updateUI() {
        boolean hasFile;
        FileObject f;

        synchronized (this) {
            Collection<? extends FileObject> col = storageResult.allInstances();
            hasFile = !col.isEmpty();
            f = file = hasFile ? col.iterator().next() : null;
        }
        setIconBaseWithExtension(ICON_PREFIX + (hasFile ? "file.png" : "graal.png"));

        String name = doc().getName();
        String label = doc().getProperties().getString(KnownPropertyNames.PROPNAME_USER_LABEL, null);

        String n = SessionManagerImpl.getInstance().getSessionDisplayName(doc());
        setDisplayName(n);
        fireDisplayNameChange(null, null);
    }

    @Override
    public String getHtmlDisplayName() {
        boolean modified = doc().isModified();
        if (modified) {
            return Bundle.FMT_ModifiedHtmlDecoration(super.getDisplayName());
        } else {
            return super.getHtmlDisplayName();
        }
    }
}
