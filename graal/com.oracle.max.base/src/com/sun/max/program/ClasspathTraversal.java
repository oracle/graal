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
package com.sun.max.program;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import com.sun.max.io.*;
import com.sun.max.program.Classpath.*;

/**
 * Provides a facility for processing all the resources reachable on a given {@linkplain Classpath classpath}.
 */
public class ClasspathTraversal {

    /**
     * Handles a standard file resource encountered during the traversal.
     *
     * @param parent the classpath directory entry under which the resource is located
     * @param resource the path of the resource relative to {@code parent}. The
     *            {@linkplain File#separatorChar platform specific} character is used as the path separator in this
     *            value.
     * @return true if the traversal should continue, false if it should terminate
     */
    protected boolean visitFile(File parent, String resource) {
        return true;
    }

    /**
     * Handles an archive entry resource encountered during the traversal.
     *
     * @param archive the classpath .zip or .jar entry in which the resource is located
     * @param resource the archive entry holding the resource
     * @return true if the traversal should continue, false if it should terminate
     */
    protected boolean visitArchiveEntry(ZipFile archive, ZipEntry resource) {
        return true;
    }

    /**
     * Traverses all the resources reachable on a given classpath.
     *
     * @param classpath the classpath to search
     */
    public void run(final Classpath classpath) {
        run(classpath, null);
    }

    /**
     * Traverses all the resources reachable on a given classpath.
     *
     * @param classpath the classpath to search
     * @param resourcePrefixFilter if non-null, then only resources whose name begins with this value are traversed. The
     *            '/' character must be used in this value as the path separator regardless of the
     *            {@linkplain File#separatorChar default} for the underlying platform.
     */
    public void run(final Classpath classpath, String resourcePrefixFilter) {
        for (final Entry entry : classpath.entries()) {
            if (entry.isDirectory()) {
                final String prefix = entry.path() + File.separator;
                final File startFile;
                if (resourcePrefixFilter == null) {
                    startFile = entry.file();
                } else {
                    if (File.separatorChar != '/') {
                        startFile = new File(entry.file(), resourcePrefixFilter.replace('/', File.separatorChar));
                    } else {
                        startFile = new File(entry.file(), resourcePrefixFilter);
                    }
                }

                final FileTraversal fileTraversal = new FileTraversal() {
                    @Override
                    protected void visitFile(File file) {
                        final String path = file.getPath();
                        assert path.startsWith(prefix);
                        final String resource = path.substring(prefix.length());
                        if (!ClasspathTraversal.this.visitFile(entry.file(), resource)) {
                            stop();
                        }
                    }
                };
                fileTraversal.run(startFile);
                if (fileTraversal.wasStopped()) {
                    return;
                }
            } else if (entry.isArchive()) {
                final ZipFile zipFile = entry.zipFile();
                if (zipFile != null) {
                    for (final Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
                        final ZipEntry zipEntry = e.nextElement();
                        if (resourcePrefixFilter == null || zipEntry.getName().startsWith(resourcePrefixFilter)) {
                            if (!visitArchiveEntry(zipFile, zipEntry)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
