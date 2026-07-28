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
import java.util.concurrent.TimeUnit;

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

    private static final long CLOSE_WAIT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120);

    private static final long CLOSE_WAIT_PROGRESS_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15);

    public DiagnosticsOutputDirectory(OptionValues options) {
        this.options = options;
    }

    private final OptionValues options;

    private String path;

    private int activeOutputScopes;

    private boolean closing;

    /**
     * Opens a scope for code that may write files into this diagnostics directory.
     *
     * Closing this diagnostics directory waits until all open output scopes are closed before
     * archiving and deleting the directory. Closing the returned object marks this scope as
     * complete. If this was the last open scope, it notifies any thread that is waiting for that
     * condition while closing this diagnostics directory.
     *
     * This method returns {@code null} if closing has already started.
     */
    public synchronized DebugCloseable openOutputScope() {
        if (closing || CLOSED.equals(path)) {
            return null;
        }
        activeOutputScopes++;
        return new DebugCloseable() {
            private boolean closed;

            @Override
            public void close() {
                synchronized (DiagnosticsOutputDirectory.this) {
                    if (!closed) {
                        closed = true;
                        activeOutputScopes--;
                        if (activeOutputScopes == 0) {
                            DiagnosticsOutputDirectory.this.notifyAll();
                        }
                    }
                }
            }
        };
    }

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
        if (closing) {
            return null;
        }
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
        closing = true;
        boolean interrupted = false;
        long waitStart = System.nanoTime();
        long deadline = waitStart + CLOSE_WAIT_TIMEOUT_NANOS;
        while (activeOutputScopes != 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                String outDir = CLOSED.equals(path) ? null : path;
                if (outDir == null) {
                    TTY.printf("Warning: timed out waiting for %d Graal diagnostic output scope(s) to close before any diagnostic directory was created%n", activeOutputScopes);
                } else {
                    TTY.printf("Warning: timed out waiting for %d Graal diagnostic output scope(s) to close; leaving %s unarchived%n", activeOutputScopes, outDir);
                }
                return;
            }
            try {
                TimeUnit.NANOSECONDS.timedWait(this, Math.min(remaining, CLOSE_WAIT_PROGRESS_INTERVAL_NANOS));
            } catch (InterruptedException e) {
                interrupted = true;
            }
            if (activeOutputScopes != 0 && System.nanoTime() < deadline) {
                TTY.printf("After waiting %d seconds, %d Graal diagnostic output scope(s) are still open; will wait before archiving until an overall limit of %d seconds%n",
                                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - waitStart),
                                activeOutputScopes,
                                TimeUnit.NANOSECONDS.toSeconds(CLOSE_WAIT_TIMEOUT_NANOS));
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        String outDir = CLOSED.equals(path) ? null : path;
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
