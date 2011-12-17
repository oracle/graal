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
package com.sun.max.io;

import java.io.*;

/**
 * Provides a facility for walking a file system hierarchy similar to that provided by the Unix find(1) facility.
 */
public class FileTraversal {

    private boolean stopped;

    /**
     * Handles a standard file resource encountered during the traversal.
     *
     * @param file a file resource for which {@link File#isFile()} returns {@code true}
     */
    protected void visitFile(File file) {
    }

    /**
     * Handles a directory encountered during the traversal.
     *
     * @param directory a file resource for which {@link File#isDirectory()} returns {@code true}
     * @return true if the traversal should process the file system hierarchy rooted at {@code directory}, false if it
     *         should be skipped
     */
    protected boolean visitDirectory(File directory) {
        return true;
    }

    /**
     * Handles a file resource encountered during the traversal that is neither a standard file or directory.
     *
     * @param other a file resource for which neither {@link File#isFile()} nor {@link File#isDirectory()} returns
     *            {@code true}
     */
    protected void visitOther(File other) {
    }

    /**
     * Stops the traversal after the current file resource has been processed. This can be called from within an
     * overriding implementation of {@link #visitFile(File)}, {@link #visitDirectory(File)} or
     * {@link #visitOther(File)} to prematurely terminate a traversal.
     */
    protected final void stop() {
        stopped = true;
    }

    /**
     * Traverses the file hierarchy rooted at a given file. The {@linkplain #wasStopped() stopped} status of this
     * traversal object is reset to {@code false} before the traversal begins.
     *
     * @param file the file or directory at which to start the traversal
     */
    public void run(File file) {
        stopped = false;
        visit(file);
    }

    /**
     * Determines if the traversal was stopped before every file in the file hierarchy was traversed.
     */
    public boolean wasStopped() {
        return stopped;
    }

    private boolean visit(File entry) {
        File subdirectoryToTraverse = null;
        if (entry.isFile()) {
            visitFile(entry);
        } else if (entry.isDirectory()) {
            if (visitDirectory(entry)) {
                subdirectoryToTraverse = entry;
            }
        } else {
            visitOther(entry);
        }
        if (stopped) {
            return false;
        }
        if (subdirectoryToTraverse != null) {
            traverse(subdirectoryToTraverse);
            if (stopped) {
                return false;
            }
        }
        return true;
    }

    private void traverse(File directory) {
        final File[] entries = directory.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (!visit(entry)) {
                    return;
                }
            }
        }
    }
}
