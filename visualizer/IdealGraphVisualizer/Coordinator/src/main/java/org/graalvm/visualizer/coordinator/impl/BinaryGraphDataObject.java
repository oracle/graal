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

import org.netbeans.api.actions.Openable;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * DataObject that represents a .BGV file. This object is NOT designed for presentation
 * as is usual for NB dataobjects. It just serves to provide Open file from commandline
 * feature, nothing more.
 * <p/>
 * Before the object can be shown in the UI, the Coordinator cookies have to be refactored,
 * since Open on BinaryGraphDataObject means actually "import" its data to the current workspace
 * (coordinator's Outline tree).
 * <p/>
 * Warning: Duplicate opens will duplicate items in the Coordinator's view.
 *
 * @author sdedic
 */
@NbBundle.Messages(
        "LBL_GraalVMGraphBinaryFile=GraalVM Graph Binary Dump"
)
@MIMEResolver.ExtensionRegistration(
        displayName = "#LBL_GraalVMGraphBinaryFile",
        mimeType = BinaryGraphDataObject.GRAAL_DUMP_MIME_TYPE,
        position = 3232,
        extension = {"bgv"}
)
@DataObject.Registration(
        displayName = "LBL_GraalVMGraphBinaryFile",
        mimeType = BinaryGraphDataObject.GRAAL_DUMP_MIME_TYPE,
        position = 400
)
public class BinaryGraphDataObject extends MultiDataObject {
    private static final Logger LOG = Logger.getLogger(BinaryGraphDataObject.class.getName());
    /**
     * MIME type for BGV dumps
     */
    public static final String GRAAL_DUMP_MIME_TYPE = "application/x-graalvm-graph-binary"; // NOI18N

    public BinaryGraphDataObject(FileObject fo, MultiFileLoader loader) throws DataObjectExistsException {
        super(fo, loader);
        getCookieSet().assign(Openable.class, new OpenImpl());
    }

    private class OpenImpl implements OpenCookie {
        @Override
        public void open() {
            importPrimaryFile();
        }
    }

    @NbBundle.Messages({
            "# {0} - file name",
            "# {1} - error message",
            "ERR_OpeningFile=Error importing from file {0}: {1}",
    })
    public void importPrimaryFile() {
        FileObject fo = getPrimaryFile();
        File f = FileUtil.toFile(fo);
        if (f == null) {
            return;
        }
        try {
            Thread.dumpStack();
            FileImporter.asyncImportDocument(f.toPath(), true, true, null);
        } catch (IOException ex) {
            Exceptions.printStackTrace(
                    Exceptions.attachLocalizedMessage(ex, Bundle.ERR_OpeningFile(f.toPath(), ex.toString()))
            );
        }
    }
}
