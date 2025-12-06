/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.compilation.view.action;

import at.ssw.visualizer.model.CompilationModel;
import org.netbeans.api.actions.Openable;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

import java.io.File;

/**
 * DataObject that represents a .CFG file. This object is NOT designed for presentation
 * as is usual for NB dataobjects. It just serves to provide Open file from commandline
 * feature, nothing more.
 */
@NbBundle.Messages(
        "LBL_GraalVMC1VisualizerFile=GraalVM C1Visualizer File"
)
@MIMEResolver.ExtensionRegistration(
        displayName = "#LBL_GraalVMC1VisualizerFile",
        mimeType = C1VisualizerDataObject.GRAAL_CFG_MIME_TYPE,
        position = 3232,
        extension = {"cfg"}
)
@DataObject.Registration(
        displayName = "LBL_GraalVMC1VisualizerFile",
        mimeType = C1VisualizerDataObject.GRAAL_CFG_MIME_TYPE,
        position = 400
)
public class C1VisualizerDataObject extends MultiDataObject {
    /**
     * MIME type for CFG dumps
     */
    public static final String GRAAL_CFG_MIME_TYPE = "application/x-c1visualizer"; // NOI18N

    public C1VisualizerDataObject(FileObject fo, MultiFileLoader loader) throws DataObjectExistsException {
        super(fo, loader);
        getCookieSet().assign(Openable.class, new OpenImpl());
    }

    private class OpenImpl implements OpenCookie {
        @Override
        public void open() {
            importPrimaryFile();
        }
    }

    public void importPrimaryFile() {
        FileObject fo = getPrimaryFile();
        File f = FileUtil.toFile(fo);
        if (f == null) {
            return;
        }

        final String fileName = f.getAbsolutePath();
        RequestProcessor.getDefault().post(new Runnable() {
            public void run() {
                CompilationModel model = Lookup.getDefault().lookup(CompilationModel.class);
                String errorMsg = model.parseInputFile(fileName);
                if (errorMsg != null) {
                    NotifyDescriptor d = new NotifyDescriptor.Message("Errors while parsing input:\n" + errorMsg, NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notify(d);
                }
            }
        });
    }
}

