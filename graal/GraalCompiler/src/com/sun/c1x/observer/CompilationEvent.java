/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.observer;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.alloc.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * An event that occurred during compilation. Instances of this class provide information about the event and the state
 * of the compilation when the event was raised. {@link #getCompilation()} and {@link #getMethod()} are guaranteed to
 * always return a non-{@code null} value. Other objects provided by getter methods may be {@code null},
 * depending on the available information when and where the event was triggered.
 *
 * @author Peter Hofer
 */
public class CompilationEvent {

    private final C1XCompilation compilation;
    private final String label;
    private BlockBegin startBlock;

    private BlockMap blockMap;
    private int codeSize = -1;

    private LinearScan allocator;
    private CiTargetMethod targetMethod;
    private boolean hirValid = false;
    private boolean lirValid = false;

    private Interval[] intervals;
    private int intervalsSize;
    private Interval[] intervalsCopy = null;

    public CompilationEvent(C1XCompilation compilation) {
        this(compilation, null);
    }

    private CompilationEvent(C1XCompilation compilation, String label) {
        assert compilation != null;
        this.label = label;
        this.compilation = compilation;
    }

    public CompilationEvent(C1XCompilation compilation, String label, BlockBegin startBlock, boolean hirValid, boolean lirValid) {
        this(compilation, label);
        this.startBlock = startBlock;
        this.hirValid = hirValid;
        this.lirValid = lirValid;
    }

    public CompilationEvent(C1XCompilation compilation, String label, BlockBegin startBlock, boolean hirValid, boolean lirValid, CiTargetMethod targetMethod) {
        this(compilation, label, startBlock, hirValid, lirValid);
        this.targetMethod = targetMethod;
    }

    public CompilationEvent(C1XCompilation compilation, String label, BlockMap blockMap, int codeSize) {
        this(compilation, label);
        this.blockMap = blockMap;
        this.codeSize = codeSize;
    }

    public CompilationEvent(C1XCompilation compilation, String label, LinearScan allocator, Interval[] intervals, int intervalsSize) {
        this(compilation, label);
        this.allocator = allocator;
        this.intervals = intervals;
        this.intervalsSize = intervalsSize;
    }

    public C1XCompilation getCompilation() {
        return compilation;
    }

    public String getLabel() {
        return label;
    }

    public RiMethod getMethod() {
        return compilation.method;
    }

    public BlockMap getBlockMap() {
        return blockMap;
    }

    public BlockBegin getStartBlock() {
        return startBlock;
    }

    public LinearScan getAllocator() {
        return allocator;
    }

    public CiTargetMethod getTargetMethod() {
        return targetMethod;
    }

    public boolean isHIRValid() {
        return hirValid;
    }

    public boolean isLIRValid() {
        return lirValid;
    }

    public Interval[] getIntervals() {
        if (intervalsCopy == null && intervals != null) {
            // deferred copy of the valid range of the intervals array
            intervalsCopy = Arrays.copyOf(intervals, intervalsSize);
        }
        return intervalsCopy;
    }

    public int getCodeSize() {
        return codeSize;
    }
}
