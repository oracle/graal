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

import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * @author sdedic
 */
public class ManagedSessionImpl extends GraphDocument implements Lookup.Provider {
    private final InstanceContent ic = new InstanceContent();
    private final Lookup lkp = new AbstractLookup(ic);

    /**
     * A file that has been saved
     */
    private FileObject savedAs;

    public ManagedSessionImpl(Object id, Properties initialValues) {
        this(id, (FileObject) null);
        initialValues.iterator().forEachRemaining((p) -> {
            if (!KnownPropertyNames.PROPNAME_NAME.equals(p.getName())) {
                getProperties().setProperty(p.getName(), p.getValue());
            }
        });
    }

    public ManagedSessionImpl(FileObject file) {
        this(null, file);
    }

    private ManagedSessionImpl(Object id, FileObject file) {
        setDocumentId(id);
        this.savedAs = file;
        if (file != null) {
            ic.add(file);
        } else {
            // make modified from the start.
            setModified(true);
        }
    }

    void setSaveAs(FileObject f) {
        FileObject prev = this.savedAs;
        this.savedAs = f;
        if (prev != null) {
            ic.remove(prev);
        }
        if (f != null) {
            ic.add(f);
        }
        boolean wasModified = isModified();
        setModified(false);
        if (!wasModified) {
            getChangedEvent().fire();
        }
    }

    @Override
    public Lookup getLookup() {
        return lkp;
    }
}
