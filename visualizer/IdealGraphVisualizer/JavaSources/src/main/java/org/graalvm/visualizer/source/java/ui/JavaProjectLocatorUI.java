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

package org.graalvm.visualizer.source.java.ui;

import org.graalvm.visualizer.source.FileRegistry;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.spi.LocatorUI;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@NbBundle.Messages({
        "LBL_LocateInJavaProject=Locate in Java Project",
        "Filter_JavaProjects=Java Projects"
})
@ServiceProvider(service = LocatorUI.class, position = 20000)
public class JavaProjectLocatorUI implements LocatorUI {
    @Override
    public String getDisplayName() {
        return Bundle.LBL_LocateInJavaProject();
    }

    @Override
    public Image getIcon() {
        return ImageUtilities.loadImage("org/graalvm/visualizer/source/resources/j2seproject.png");
    }

    @Override
    public boolean accepts(Location l) {
        return l.getMimeType().equals("text/x-java");
    }

    @Override
    public Future<Boolean> resolve(Location l) {
        CompletableFuture cf = new CompletableFuture();
        SwingUtilities.invokeLater(new LocatorRunner(cf, l));
        return cf;
    }

    static FileObject findSourceFile(Project p, String fileName, AtomicReference<FileObject> pathRoot) {
        ClassPath srcPath = ClassPath.getClassPath(p.getProjectDirectory(), ClassPath.SOURCE);
        if (srcPath != null) {
            return srcPath.findResource(fileName);
        }
        Sources src = p.getLookup().lookup(Sources.class);
        if (src == null) {
            return null;
        }

        for (SourceGroup sg : src.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)) {
            FileObject root = sg.getRootFolder();
            ClassPath cp = ClassPath.getClassPath(root, ClassPath.SOURCE);
            FileObject found = cp == null ? null : cp.findResource(fileName);
            if (found != null) {
                if (pathRoot != null) {
                    pathRoot.set(root);
                }
                return found;
            }
        }
        return null;
    }

    private class LocatorRunner extends FileFilter implements Runnable {
        private final CompletableFuture future;
        private final Location loc;

        public LocatorRunner(CompletableFuture future, Location loc) {
            this.future = future;
            this.loc = loc;
        }

        @Override
        public boolean accept(File f) {
            if (f == null) {
                return false;
            }
            FileObject fo = FileUtil.toFileObject(f);
            Project selected = FileOwnerQuery.getOwner(fo);
            // accept just J2SE projects, or projects with sourcepath
            if (selected == null) {
                return fo.isFolder();
            }
            return findSourceFile(selected, loc.getFileName(), null) != null;
        }

        @Override
        public String getDescription() {
            return Bundle.Filter_JavaProjects();
        }

        @Override
        public void run() {
            try {
                JFileChooser projectChooser = ProjectChooser.projectChooser();
                projectChooser.setCurrentDirectory(ProjectChooser.getProjectsFolder());
                projectChooser.setFileFilter(this);
                projectChooser.setMultiSelectionEnabled(true);
                projectChooser.setAcceptAllFileFilterUsed(false);
                projectChooser.setAccessory(new ProjectContentPanel(loc, projectChooser));
                if (projectChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                    // no project was opened, no location resolved.
                    future.complete(false);
                    return;
                }
                ProjectChooser.setProjectsFolder(projectChooser.getCurrentDirectory());
                File[] files = projectChooser.getSelectedFiles();
                Collection<Project> prjs = new ArrayList<>();
                for (int i = 0; i < files.length; i++) {
                    FileObject fob = FileUtil.toFileObject(files[i]);
                    try {
                        Project p = ProjectManager.getDefault().findProject(fob);
                        if (p != null) {
                            prjs.add(p);
                        }
                    } catch (IllegalStateException | IOException ex) {
                        // ignore the project
                    }
                }
                OpenProjects.getDefault().open(prjs.toArray(new Project[prjs.size()]), true, true);
                RequestProcessor.getDefault().post(new Runnable() {
                    public void run() {
                        try {
                            OpenProjects.getDefault().openProjects().get();
                        } catch (InterruptedException | ExecutionException ex) {
                            // ignore, but do not attempt to resolve
                            future.completeExceptionally(ex);
                            return;
                        }
                        FileRegistry.getInstance().attemptResolve(loc.getFile());
                        future.complete(true);
                    }
                });
                // attempt to resolve the file again
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        }
    }
}
