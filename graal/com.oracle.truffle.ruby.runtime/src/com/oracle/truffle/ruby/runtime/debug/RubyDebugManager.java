/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.debug;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.runtime.core.*;

public final class RubyDebugManager {

    private final Map<SourceLineLocation, CyclicAssumption> lineAssumptions = new HashMap<>();
    private final Map<SourceLineLocation, RubyProc> lineBreakpoints = new HashMap<>();

    private final Map<MethodLocal, CyclicAssumption> localAssumptions = new HashMap<>();
    private final Map<MethodLocal, RubyProc> localBreakpoints = new HashMap<>();

    public void setBreakpoint(SourceLineLocation sourceLine, RubyProc proc) {
        final CyclicAssumption assumption = lineAssumptions.get(sourceLine);

        if (assumption == null) {
            throw new RuntimeException("Breakpoint " + sourceLine + " not found");
        } else {
            lineBreakpoints.put(sourceLine, proc);
            assumption.invalidate();
        }
    }

    public void setBreakpoint(MethodLocal methodLocal, RubyProc proc) {
        final CyclicAssumption assumption = localAssumptions.get(methodLocal);

        if (assumption == null) {
            throw new RuntimeException("Breakpoint " + methodLocal + " not found");
        } else {
            localBreakpoints.put(methodLocal, proc);
            assumption.invalidate();
        }
    }

    public void removeBreakpoint(SourceLineLocation sourceLine) {
        final CyclicAssumption assumption = lineAssumptions.get(sourceLine);

        if (assumption == null) {
            throw new RuntimeException("Breakpoint " + sourceLine + " not found");
        } else {
            lineBreakpoints.remove(sourceLine);
            assumption.invalidate();
        }
    }

    public void removeBreakpoint(MethodLocal methodLocal) {
        final CyclicAssumption assumption = localAssumptions.get(methodLocal);

        if (assumption == null) {
            throw new RuntimeException("Breakpoint " + methodLocal + " not found");
        } else {
            localBreakpoints.remove(methodLocal);
            assumption.invalidate();
        }
    }

    public Assumption getAssumption(SourceLineLocation sourceLine) {
        CyclicAssumption assumption = lineAssumptions.get(sourceLine);

        if (assumption == null) {
            assumption = new CyclicAssumption(sourceLine.toString());
            lineAssumptions.put(sourceLine, assumption);
        }

        return assumption.getAssumption();
    }

    public RubyProc getBreakpoint(SourceLineLocation sourceLine) {
        return lineBreakpoints.get(sourceLine);
    }

    public Assumption getAssumption(MethodLocal methodLocal) {
        CyclicAssumption assumption = localAssumptions.get(methodLocal);

        if (assumption == null) {
            assumption = new CyclicAssumption(methodLocal.toString());
            localAssumptions.put(methodLocal, assumption);
        }

        return assumption.getAssumption();
    }

    public RubyProc getBreakpoint(MethodLocal methodLocal) {
        return localBreakpoints.get(methodLocal);
    }

}
