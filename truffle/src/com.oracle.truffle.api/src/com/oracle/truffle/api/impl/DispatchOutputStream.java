/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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

    OutputStream getOut() {
        return out;
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

    OutputStreamList getOutList() {
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

    class OutputStreamList {

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
