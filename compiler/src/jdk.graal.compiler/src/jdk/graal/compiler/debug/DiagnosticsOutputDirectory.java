/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.debug;

import java.io.IOException;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.IsolateUtil;

/**
 * Manages a directory into which diagnostics such crash reports and dumps should be written. The
 * directory is archived and deleted when {@link #close()} is called.
 */
public class DiagnosticsOutputDirectory {

    /**
     * Use an illegal file name to denote that {@link #close()} has been called.
     */
    private static final String CLOSED = "\u0000";

    public DiagnosticsOutputDirectory(OptionValues options) {
        this.options = options;
    }

    private final OptionValues options;

    private String path;

    /**
     * Gets the path to the output directory managed by this object, creating if it doesn't exist
     * and has not been deleted.
     *
     * @return the directory or {@code null} if the could not be created or has been deleted
     */
    public String getPath() {
        return getPath(true);
    }

    private synchronized String getPath(boolean createIfNull) {
        if (path == null && createIfNull) {
            path = createPath();
            String dir = PathUtilities.getAbsolutePath(path);
            if (!PathUtilities.exists(dir)) {
                try {
                    PathUtilities.createDirectories(dir);
                } catch (IOException e) {
                    TTY.println("Warning: could not create Graal diagnostic directory " + dir + ": " + e);
                    return null;
                }
            }
        }
        if (CLOSED.equals(path)) {
            TTY.println("Warning: Graal diagnostic directory already closed");
            return null;
        }
        return path;
    }

    /**
     * Gets the path of the directory to be created.
     *
     * Subclasses can override this to determine how the path name is created.
     *
     * @return the path to be created
     */
    protected String createPath() {
        String baseDir;
        try {
            baseDir = DebugOptions.getDumpDirectory(options);
        } catch (IOException e) {
            // Default to current directory if there was a problem creating the
            // directory specified by the DumpPath option.
            baseDir = ".";
        }
        return PathUtilities.getPath(baseDir, "graal_diagnostics_" + GraalServices.getExecutionID() + '@' + IsolateUtil.getIsolateID());
    }

    /**
     * Archives and deletes this directory if it exists.
     */
    public void close() {
        archiveAndDelete();
    }

    /**
     * Archives and deletes the {@linkplain #getPath() output directory} if it exists.
     */
    private synchronized void archiveAndDelete() {
        String outDir = getPath(false);
        if (outDir != null) {
            // Notify other threads calling getPath() that the directory is deleted.
            // This attempts to mitigate other threads writing to the directory
            // while it is being archived and deleted.
            path = CLOSED;
            try {
                String zip = outDir + ".zip";
                PathUtilities.archiveAndDelete(outDir, zip);
            } catch (IOException e) {
                TTY.println(e.getMessage());
                TTY.println("Cause: " + e.getCause());
            }
        }
    }
}
