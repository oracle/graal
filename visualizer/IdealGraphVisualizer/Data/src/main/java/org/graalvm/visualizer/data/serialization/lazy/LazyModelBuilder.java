/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.visualizer.data.serialization.BinaryMap;

import jdk.graal.compiler.graphio.parsing.DocumentFactory;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.NameTranslator;
import jdk.graal.compiler.graphio.parsing.ParseMonitor;
import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * Base class for other model builders in this package.
 */
public class LazyModelBuilder extends ModelBuilder {

    public LazyModelBuilder(GraphDocument rootDocument, ParseMonitor monitor) {
        super(rootDocument, monitor);
    }

    public LazyModelBuilder(DocumentFactory factory, ParseMonitor monitor) {
        super(factory, monitor);
    }

    @Override
    public NameTranslator prepareNameTranslator() {
        BinaryMap versions = BinaryMap.versions();
        Properties.Entity versionHolder = folder() != null ? (Properties.Entity) folder() : getEntity();
        final String PREFIX = "version.";
        for (Property<?> p : versionHolder.getProperties()) {
            if (p.getName().startsWith(PREFIX)) {
                versions.request(p.getName().substring(PREFIX.length()), p.getValue().toString());
            }
        }
        return versions;
    }

    @Override
    public void reportLoadingError(String logMessage) {
        super.reportLoadingError(logMessage);
        FolderElement parent = graph();
        Folder folder = folder();
        if (parent == null) {
            parent = folder;
            folder = null;
        }
        ReaderErrors.addError(parent, folder, logMessage);
    }
}
