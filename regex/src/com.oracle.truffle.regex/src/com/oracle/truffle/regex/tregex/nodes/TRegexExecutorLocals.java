/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.nodes;

/**
 * Base class for local variables used by an executor node called by a {@link TRegexExecRootNode}.
 */
public abstract class TRegexExecutorLocals {

    private final Object input;
    private final int fromIndex;
    private final int maxIndex;
    private int index;

    public TRegexExecutorLocals(Object input, int fromIndex, int maxIndex, int index) {
        this.input = input;
        this.fromIndex = fromIndex;
        this.maxIndex = maxIndex;
        this.index = index;
    }

    /**
     * The {@code input} argument given to {@link TRegexExecRootNode#execute(Object, int)}.
     *
     * @return the {@code input} argument given to {@link TRegexExecRootNode#execute(Object, int)}.
     */
    public Object getInput() {
        return input;
    }

    /**
     * The {@code fromIndex} argument given to {@link TRegexExecRootNode#execute(Object, int)}.
     *
     * @return the {@code fromIndex} argument given to
     *         {@link TRegexExecRootNode#execute(Object, int)}.
     */
    public int getFromIndex() {
        return fromIndex;
    }

    /**
     * The maximum index as given by the parent {@link TRegexExecRootNode}.
     *
     * @return the maximum index as given by the parent {@link TRegexExecRootNode}.
     */
    public int getMaxIndex() {
        return maxIndex;
    }

    /**
     * The index pointing into {@link #getInput()}.
     *
     * @return the current index of {@link #getInput()} that is being processed.
     */
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void incIndex(int i) {
        this.index += i;
    }
}
