/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.subsystems;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Manages trace events and calls the user's trace method if one is set.
 * <p>
 * Once tracing has been enabled via {@link #setTraceProc(RubyProc)}, the underlying instrumentation
 * remains in effect, along with performance impact.
 */
public final class TraceManager {

    private RubyContext context;

    private final AssumedValue<RubyProc> traceProc = new AssumedValue<>("trace-proc", null);
    private boolean suspended;

    private String lastFile;
    private int lastLine;

    private final Assumption notTracingAssumption = Truffle.getRuntime().createAssumption("tracing-disabled");

    public TraceManager(RubyContext context) {
        this.context = context;
    }

    /**
     * Produce a trace; it is a runtime error if {@link #hasTraceProc()}{@code == false}.
     */
    @CompilerDirectives.SlowPath
    public void trace(String event, String file, int line, long objectId, RubyBinding binding, String className) {
        // If tracing is suspended, stop here

        if (suspended) {
            return;
        }

        // If the file and line haven't changed since the last trace, stop here

        if (file.equals(lastFile) && line == lastLine) {
            return;
        }

        final RubyClass stringClass = context.getCoreLibrary().getStringClass();

        // Suspend tracing while we run the trace proc

        suspended = true;

        try {
            // Exceptions from within the proc propagate normally

            traceProc.get().call(null, new RubyString(stringClass, event), //
                            new RubyString(stringClass, file), //
                            line, //
                            GeneralConversions.fixnumOrBignum(objectId), //
                            GeneralConversions.instanceOrNil(binding), //
                            GeneralConversions.instanceOrNil(className));
        } finally {
            // Resume tracing

            suspended = false;
        }

        // Remember the last trace event file and line

        lastFile = file;
        lastLine = line;
    }

    /**
     * Is there a "trace proc" in effect?
     */
    public boolean hasTraceProc() {
        return traceProc.get() != null;
    }

    /**
     * Gets the assumption that there has never yet been tracing enabled. Once the assumption is
     * invalidated, tracing is presumed permanently enabled even if {@link #hasTraceProc()} returns
     * {@code false}.
     */
    public Assumption getNotTracingAssumption() {
        return notTracingAssumption;
    }

    public void setTraceProc(RubyProc newTraceProc) {
        if (!context.getConfiguration().getTrace()) {
            throw new RuntimeException("You need the --trace option to use tracing");
        }

        traceProc.set(newTraceProc);
        lastFile = null;
        lastLine = -1;

        notTracingAssumption.invalidate();
    }
}
