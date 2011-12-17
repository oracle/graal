/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ide;

import java.io.*;
import java.util.*;

import com.sun.max.program.*;
import com.sun.max.program.Classpath.Entry;

/**
 * Software project-dependent configuration derived from the
 * {@linkplain Classpath#fromSystem() system class path}.
 */
public final class JavaProject {

    /**
     * System property name specifying the Maxine workspace directory.
     */
    public static final String MAX_WORKSPACE_PROPERTY = "max.workspace";

    /**
     * Determines if a given directory is Maxine workspace directory.
     *
     * @param dir a directory to test
     * @return {@code true} if {@code dir} is a directory containing a file named "projects.properties"
     */
    public static boolean isWorkspace(File dir) {
        return new File(dir, "projects.properties").exists();
    }

    private JavaProject() {
    }

    public static final String SOURCE_DIRECTORY_NAME = "src";

    public static final String TEST_SOURCE_DIRECTORY_NAME = "test";

    /**
     * Gets the paths on which all the class files referenced by a Java project can be found.
     *
     * @param projClass a class denoting a project (i.e. any class in the project)
     * @param includeDependencies  if true, the returned path includes the location of the
     *                             class files produced by each of the projects that the current
     *                             project depends upon
     */
    public static Classpath getClassPath(Class projClass, boolean includeDependencies) {
        String classfile = projClass.getName().replace('.', '/') + ".class";
        ArrayList<Entry> classPathEntries = new ArrayList<Entry>();
        Entry projEntry = null;
        for (Entry entry : Classpath.fromSystem().entries()) {
            if (entry.contains(classfile)) {
                projEntry = entry;
                classPathEntries.add(entry);
                break;
            }
        }
        if (classPathEntries.isEmpty()) {
            throw new JavaProjectNotFoundException("Could not find path to Java project classes");
        }
        if (includeDependencies) {
            for (Entry entry : Classpath.fromSystem().entries()) {
                if (entry != projEntry) {
                    classPathEntries.add(entry);
                }
            }
        }
        return new Classpath(classPathEntries);
    }

    static class WorkspaceFinder extends ClasspathTraversal {

        File workspace;
        File project;


        boolean deriveWorkspace(File start) {
            File dir = start;
            File child = null;
            while (dir != null) {
                if (isWorkspace(dir)) {
                    workspace = dir;
                    project = child;
                    return true;
                }
                child = dir;
                dir = dir.getParentFile();
            }
            return false;
        }

        @Override
        protected boolean visitFile(File parent, String resource) {
            String classFile = JavaProject.class.getName().replace('.', File.separatorChar) + ".class";
            if (resource.equals(classFile)) {
                if (deriveWorkspace(parent)) {
                    return false;
                }
            }
            return true;
        }
        @Override
        protected boolean visitArchiveEntry(java.util.zip.ZipFile archive, java.util.zip.ZipEntry resource) {
            String classFile = JavaProject.class.getName().replace('.', File.separatorChar) + ".class";
            if (resource.equals(classFile)) {
                File archiveFile = new File(archive.getName());
                if (deriveWorkspace(archiveFile)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Gets Maxine workspace directory (i.e. the parent of all the {@linkplain #WORKSPACE_PROJECTS representative project directories}).
     * This can be specified explicitly with the {@value JavaProject#MAX_WORKSPACE_PROPERTY}
     * or is derived from the {@linkplain Classpath#fromSystem() system class path}.
     *
     * @return the Maxine workspace directory
     */
    public static File findWorkspaceDirectory() {
        final String prop = System.getProperty(JavaProject.MAX_WORKSPACE_PROPERTY);
        if (prop != null) {
            File dir = new File(prop);
            ProgramError.check(isWorkspace(dir), prop + " is not a Maxine workspace directory");
            return dir;
        }
        WorkspaceFinder finder = new WorkspaceFinder();
        finder.run(Classpath.fromSystem());
        ProgramError.check(finder.workspace != null, "failed to find the Maxine workspace directory");
        return finder.workspace;
    }

    /**
     * Gets the paths on which all the Java source files for a Java project can be found.
     *
     * @param projClass a class denoting a project (i.e. any class in the project)
     * @param includeDependencies  if true, the returned path includes the location of the
     *                             Java source files for each of the projects that the current
     *                             project depends upon
     */
    public static Classpath getSourcePath(Class projClass, boolean includeDependencies) {
        final Classpath classPath = getClassPath(projClass, includeDependencies);
        final List<String> sourcePath = new LinkedList<String>();
        for (Entry entry : classPath.entries()) {
            WorkspaceFinder finder = new WorkspaceFinder();
            finder.deriveWorkspace(entry.file());
            final File projectDirectory = finder.project;
            if (projectDirectory != null) {
                final File srcDirectory = new File(projectDirectory, SOURCE_DIRECTORY_NAME);
                if (srcDirectory.exists() && srcDirectory.isDirectory()) {
                    sourcePath.add(srcDirectory.getPath());
                }

                final File testDirectory = new File(projectDirectory, TEST_SOURCE_DIRECTORY_NAME);
                if (testDirectory.exists() && testDirectory.isDirectory()) {
                    sourcePath.add(testDirectory.getPath());
                }
                if (!includeDependencies) {
                    break;
                }
            }
        }
        if (sourcePath.isEmpty()) {
            throw new JavaProjectNotFoundException("Could not find path to Java project sources");
        }
        return new Classpath(sourcePath.toArray(new String[sourcePath.size()]));
    }

    /**
     * Find the primary source directory for a project.
     *
     * @param projClass a class denoting a project (i.e. any class in the project)
     */
    public static File findSourceDirectory(Class projClass) {
        return getSourcePath(projClass, false).entries().get(0).file();
    }
}
