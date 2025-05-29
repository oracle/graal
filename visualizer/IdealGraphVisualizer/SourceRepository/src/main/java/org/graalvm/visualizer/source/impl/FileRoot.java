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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;


public final class FileRoot {
    private static final String PROP_DISPLAY_NAME = "displayName"; // NOI18N
    private final URL rootURL;
    private final PropertyChangeSupport supp = new PropertyChangeSupport(this);
    private final SourceRepositoryImpl storage;

    private String displayName;
    private FileGroup parent;

    FileRoot(SourceRepositoryImpl storage, URL fileURL) {
        this.rootURL = fileURL;
        this.storage = storage;
    }

    public FileGroup getParent() {
        return parent;
    }

    void setParent(FileGroup parent) {
        this.parent = parent;
    }

    public URL getLocation() {
        return rootURL;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        String old = this.displayName;
        if (Objects.equals(old, displayName)) {
            return;
        }
        this.displayName = displayName;
        if (parent != null) {
            storage.setRootName(rootURL, displayName);
        }
        supp.firePropertyChange(PROP_DISPLAY_NAME, old, displayName);
    }

    public void discard() throws IOException {
        storage.removeRoot(this);
    }

    public void moveTo(FileGroup otherGroup) throws IOException {
        if (getParent() == otherGroup) {
            return;
        }
        storage.removeRoot(this);
        storage.addFileRoot(this, otherGroup);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        supp.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        supp.removePropertyChangeListener(listener);
    }
}
