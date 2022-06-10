/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow.context.bytecode;

import java.util.Arrays;

import com.oracle.graal.pointsto.flow.context.AnalysisContext;

import jdk.vm.ci.code.BytecodePosition;

/**
 * It effectively contains a context chain, i.e. a list of {@linkplain BytecodePosition}
 * representing allocation sites. The context chain has a maximum depth of {@linkplain #getLength()}
 * which is fixed upon creation.
 */
public final class BytecodeAnalysisContext extends AnalysisContext {

    public static final BytecodePosition[] emptyLabelList = new BytecodePosition[0];

    /**
     * The chain of {@link BytecodePosition} objects representing allocation sites.
     * <p>
     * Note: the size of {@code labels} is the {@code depth} of the context space.
     */
    protected final BytecodePosition[] labels;

    protected BytecodeAnalysisContext(BytecodePosition[] labelList) {
        this.labels = labelList;
    }

    public int getLength() {
        return labels.length;
    }

    public BytecodePosition[] labels() {
        return labels;
    }

    @Override
    protected boolean valueEquals(AnalysisContext obj) {
        if (obj instanceof BytecodeAnalysisContext) {
            BytecodeAnalysisContext that = (BytecodeAnalysisContext) obj;
            return Arrays.equals(this.labels, that.labels);
        }
        return false;
    }

    @Override
    protected int valueHashCode() {
        int result = 42;
        for (BytecodePosition l : labels) {
            result = result ^ l.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String separator = " ";
        for (BytecodePosition bytecode : labels) {
            result.append(separator).append(bytecode);
            separator = "\t";
        }
        return result.toString();
    }

}
