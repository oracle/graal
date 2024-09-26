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

package org.graalvm.visualizer.source.impl.ui;

import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.impl.FileGroup;
import org.graalvm.visualizer.source.impl.SourceRepositoryImpl;
import org.graalvm.visualizer.source.spi.LocatorUI;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.filesystems.FileChooserBuilder;
import org.openide.filesystems.FileChooserBuilder.SelectionApprover;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.awt.Image;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 *
 */
@NbBundle.Messages({
        "LBL_LocateFolder=Add root of sources",
        "Filter_FileHolder=Folder containing the file"
})
@ServiceProvider(service = LocatorUI.class, position = 99000)
public class BasicLocatorUI implements LocatorUI {
    @Override
    public String getDisplayName() {
        return Bundle.LBL_LocateFolder();
    }

    @Override
    public Image getIcon() {
        return ImageUtilities.loadImage("org/graalvm/visualizer/source/resources/srcFolder.png");
    }

    @Override
    public boolean accepts(Location l) {
        // accept all locations
        return true;
    }

    @Override
    public Future<Boolean> resolve(Location l) {
        CompletableFuture cf = new CompletableFuture();
        SwingUtilities.invokeLater(new LocatorRunner(cf, l));
        return cf;
    }

    private class LocatorRunner extends FileFilter implements Runnable, SelectionApprover {
        private final CompletableFuture future;
        private final Location loc;
        private final SourceRepositoryImpl repository = SourceRepositoryImpl.getInstance();

        public LocatorRunner(CompletableFuture future, Location loc) {
            this.future = future;
            this.loc = loc;
        }

        @Override
        public boolean accept(File f) {
            if (f == null) {
                return false;
            }
            if (f.isDirectory()) {
                return true;
            }
            FileObject rootDir = FileUtil.toFileObject(f);
            return rootDir.getFileObject(loc.getFileName()) != null;
        }

        @Override
        public String getDescription() {
            return Bundle.Filter_FileHolder();
        }

        private RootContentPanel contentPanel;

        @Override
        public void run() {
            try {
                File dir = ProjectChooser.getProjectsFolder();
                if (dir == null) {
                    dir = new File(System.getProperty("user.home"));
                }
                SourceRepositoryImpl impl = SourceRepositoryImpl.getInstance();
                FileObject fDir = FileUtil.toFileObject(dir);
                JFileChooser choose = FileChooserBuilder.create(fDir.getFileSystem()).
                        setDefaultWorkingDirectory(dir).
                        setSelectionApprover(this).
                        createFileChooser();
                choose.setFileFilter(this);
                choose.setMultiSelectionEnabled(false);
                choose.setAcceptAllFileFilterUsed(false);
                contentPanel = new RootContentPanel(loc, choose, impl);
                choose.setAccessory(contentPanel);
                contentPanel.setFileGroup(repository.getDefaultGroup());
                if (choose.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                    // no project was opened, no location resolved.
                    future.complete(false);
                    return;
                }
                ProjectChooser.setProjectsFolder(choose.getCurrentDirectory());
                File f = choose.getSelectedFile();
                if (f == null) {
                    future.complete(false);
                    return;
                }
                FileObject theFile = FileUtil.toFileObject(f);
                String desc = contentPanel.getDescription();
                FileGroup parent = contentPanel.getParentGroup();
                if (parent == null) {
                    parent = repository.getDefaultGroup();
                }
                repository.addLocation(theFile, desc, parent);
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        }

        @Override
        public boolean approve(File[] selection) {
            if (selection == null || selection.length != 1) {
                return false;
            }
            FileObject rootDir = FileUtil.toFileObject(selection[0]);
            return rootDir.getFileObject(loc.getFileName()) != null;
        }
    }
}
