/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link OutputStream} that can be dispatched to other output streams.
 */
public final class DispatchOutputStream extends OutputStream {

    private final OutputStream out;
    @CompilationFinal private volatile OutputStreamList outList;
    @CompilationFinal private volatile Assumption outListUnchanged;

    DispatchOutputStream(OutputStream out) {
        this.out = out;
        outListUnchanged = Truffle.getRuntime().createAssumption("Unchanged list");
    }

    synchronized void attach(OutputStream outConsumer) {
        if (outList == null) {
            outList = new OutputStreamList();
            outListChanged();
        }
        outList.add(outConsumer);
    }

    synchronized void detach(OutputStream outConsumer) {
        if (outList == null) {
            return;
        }
        outList.remove(outConsumer);
        if (outList.isEmpty()) {
            outList = null;
            outListChanged();
        }
    }

    private void outListChanged() {
        Assumption changed = outListUnchanged;
        outListUnchanged = Truffle.getRuntime().createAssumption("Unchanged list");
        changed.invalidate();
    }

    private OutputStreamList getOutList() {
        if (outListUnchanged.isValid()) {
            return outList;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return outList;
        }
    }

    @Override
    public void write(int b) throws IOException {
        OutputStreamList outs = getOutList();
        if (outs != null) {
            outs.writeMulti(b);
        }
        out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        OutputStreamList outs = getOutList();
        if (outs != null) {
            outs.writeMulti(b);
        }
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        OutputStreamList outs = getOutList();
        if (outs != null) {
            outs.writeMulti(b, off, len);
        }
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        OutputStreamList outs = getOutList();
        if (outs != null) {
            outs.flushMulti();
        }
        out.flush();
    }

    @Override
    public void close() throws IOException {
        OutputStreamList outs = getOutList();
        if (outs != null) {
            outs.closeMulti();
        }
        out.close();
    }

    private class OutputStreamList {

        private final List<OutputStream> outs = new CopyOnWriteArrayList<>();
        @CompilationFinal private boolean seenException;
        private Map<OutputStream, String> reportedExceptions;

        void add(OutputStream outConsumer) {
            outs.add(outConsumer);
        }

        void remove(OutputStream outConsumer) {
            outs.remove(outConsumer);
            synchronized (this) {
                if (reportedExceptions != null) {
                    reportedExceptions.remove(outConsumer);
                }
            }
        }

        boolean isEmpty() {
            return outs.isEmpty();
        }

        @TruffleBoundary
        void writeMulti(int b) {
            for (OutputStream os : outs) {
                try {
                    os.write(b);
                } catch (Throwable t) {
                    if (!seenException) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenException = true;
                    }
                    handleException("write(I)", os, t);
                }
            }
        }

        @TruffleBoundary
        void writeMulti(byte[] b) {
            for (OutputStream os : outs) {
                try {
                    os.write(b);
                } catch (Throwable t) {
                    if (!seenException) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenException = true;
                    }
                    handleException("write(B[)", os, t);
                }
            }
        }

        @TruffleBoundary
        void writeMulti(byte[] b, int off, int len) {
            for (OutputStream os : outs) {
                try {
                    os.write(b, off, len);
                } catch (Throwable t) {
                    if (!seenException) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenException = true;
                    }
                    handleException("write(B[II)", os, t);
                }
            }
        }

        @TruffleBoundary
        void flushMulti() {
            for (OutputStream os : outs) {
                try {
                    os.flush();
                } catch (Throwable t) {
                    if (!seenException) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenException = true;
                    }
                    handleException("flush()", os, t);
                }
            }
        }

        @TruffleBoundary
        void closeMulti() {
            for (OutputStream os : outs) {
                try {
                    os.close();
                } catch (Throwable t) {
                    if (!seenException) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenException = true;
                    }
                    handleException("close()", os, t);
                }
            }
            outs.clear();
            synchronized (this) {
                reportedExceptions = null;
            }
        }

        private void handleException(String method, OutputStream os, Throwable t) {
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            // We may propagate IOException as well, but we probably do not want to break the main
            // output just because of an I/O problem of one instrument.

            // Check if we printed already such an exception for this output delegate to prevent
            // from proliferation of error messages
            String description = method + t.getMessage() + t.getClass().getName();
            boolean report;
            synchronized (this) {
                if (reportedExceptions == null) {
                    reportedExceptions = new HashMap<>();
                }
                report = reportedExceptions.put(os, description) == null;
            }
            if (report) {
                String message = String.format("Output operation %s failed for %s.", method, os);
                Exception exception = new Exception(message, t);
                PrintStream stream = new PrintStream(out);
                exception.printStackTrace(stream);
            }
        }
    }
}
