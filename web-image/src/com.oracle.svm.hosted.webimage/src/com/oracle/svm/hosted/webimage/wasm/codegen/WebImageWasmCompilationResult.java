/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.hosted.webimage.codegen.WebImageCompilationResult;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WebImageWasmCompilationResult extends WebImageCompilationResult {

    /**
     * Main Wasm function associated with this compilation.
     * <p>
     * May be {@code null}.
     */
    protected Function function;

    /**
     * Additional Wasm functions that this compilation created.
     * <p>
     * Such extra functions are for example created for the function template generation (see
     * {@link com.oracle.svm.hosted.webimage.wasmgc.WasmGCFunctionTemplateFeature} where a single
     * compilation generates multiple Wasm functions (one for each parameter in each template).
     * <p>
     * Is not used in most cases, which is why it's lazily initialized.
     * <p>
     * May be {@code null}.
     */
    private List<Function> extraFunctions;

    /**
     * The Java types used for this method's parameters.
     * <p>
     * Stores here because extracting the types from {@link JavaMethod#getSignature()} requires
     * non-trivial logic (e.g. receiver type).
     */
    protected ResolvedJavaType[] paramTypes;

    /**
     * The Java type returned from this method.
     */
    protected ResolvedJavaType returnType;

    /**
     * A list of stack slots which contain possibly live objects.
     * <p>
     * Currently, we over-approximate and consider all objects within a method live for the whole
     * method.
     */
    protected final List<StackSlot> liveSlots = new ArrayList<>();

    public WebImageWasmCompilationResult(CompilationIdentifier compilationId, String name) {
        super(compilationId, name);
    }

    public void setFunction(Function function) {
        this.function = function;
    }

    public Function getFunction() {
        return function;
    }

    public void addExtraFunction(Function f) {
        if (extraFunctions == null) {
            extraFunctions = new ArrayList<>();
        }

        extraFunctions.add(f);
    }

    /**
     * Returns {@link #function} and {@link #extraFunctions} in a list without {@code null} values.
     */
    public List<Function> getAllFunctions() {
        int numExtraFunctions = extraFunctions == null ? 0 : extraFunctions.size();
        List<Function> functions = new ArrayList<>(numExtraFunctions + 1);

        if (function != null) {
            functions.add(function);
        }

        if (extraFunctions != null) {
            functions.addAll(extraFunctions);
        }

        return functions;
    }

    public ResolvedJavaType[] getParamTypes() {
        return paramTypes;
    }

    public void setParamTypes(ResolvedJavaType[] paramTypes) {
        this.paramTypes = paramTypes;
    }

    public ResolvedJavaType getReturnType() {
        return returnType;
    }

    public void setReturnType(ResolvedJavaType returnType) {
        this.returnType = Objects.requireNonNull(returnType);
    }

    public void addLiveStackSlot(StackSlot slot) {
        liveSlots.add(slot);
    }

    public List<StackSlot> getLiveSlots() {
        return Collections.unmodifiableList(liveSlots);
    }
}
