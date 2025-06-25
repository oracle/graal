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

package com.oracle.svm.hosted.webimage.wasm.ast.id;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LabeledBlock;
import com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasmgc.ast.FunctionType;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Defines {@link WasmId}s that are used in the Wasm backends.
 */
public class WebImageWasmIds {

    public static class MethodName extends WasmId.Func {

        final ResolvedJavaMethod method;

        protected MethodName(ResolvedJavaMethod method) {
            this.method = method;
        }

        @Override
        public String doResolve(ResolverContext ctxt) {
            return ctxt.namingConvention.identForType(method.getDeclaringClass()) + "_" + ctxt.namingConvention.identForMethod(method);
        }

        @Override
        public String toInnerString() {
            return method.format("%H.%n(%P)%R");
        }
    }

    /**
     * Used for Wasm functions that are not compiled from Java.
     * <p>
     * The content of these functions has to be built manually.
     */
    public static class InternalFunction extends WasmId.Func {

        public final String name;

        protected InternalFunction(String name) {
            this.name = name;
        }

        @Override
        public String doResolve(ResolverContext ctxt) {
            /*
             * TODO GR-41720 there is currently nothing to ensure that these struct names are
             * unique.
             */
            return "func." + name;
        }

        @Override
        public String toInnerString() {
            return name;
        }
    }

    public static class LoopLabel extends WasmId.Label {
        final HIRBlock loopHeader;

        protected LoopLabel(HIRBlock loopHeader) {
            this.loopHeader = loopHeader;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return ResolverContext.getLoopLabel(loopHeader);
        }

        @Override
        public String toInnerString() {
            return String.valueOf(loopHeader.getId());
        }
    }

    public static class BlockLabel extends WasmId.Label {

        final LabeledBlock labeledBlock;

        protected BlockLabel(LabeledBlock labeledBlock) {
            this.labeledBlock = labeledBlock;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return labeledBlock.getLabel();
        }

        @Override
        public String toInnerString() {
            return labeledBlock.getLabel();
        }
    }

    public static class SwitchLabel extends WasmId.Label {

        // TODO GR-41720 Move state into ResolverContext
        static AtomicInteger num = new AtomicInteger(0);

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "switch_" + num.getAndIncrement();
        }
    }

    /**
     * Simple named label. Used for manually constructing more complex Wasm control flow (loops and
     * breaking out of blocks).
     */
    public static class InternalLabel extends WasmId.Label {
        public final String name;

        public InternalLabel(String name) {
            this.name = name;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            /*
             * TODO GR-41720 there is currently nothing to ensure that these label names are unique.
             */
            return "label." + name;
        }
    }

    /**
     * Id for local variables that materialize nodes.
     */
    public static class NodeVariable extends WasmId.Local {

        final ResolvedVar var;

        protected NodeVariable(ResolvedVar var, WasmValType variableType) {
            super(variableType);
            this.var = var;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            assert var.born() : var;
            return var.getName();
        }

        @Override
        public String toInnerString() {
            return var.getName();
        }
    }

    /**
     * Id for function parameters.
     */
    public static class Param extends WasmId.Local {

        public final int idx;

        protected Param(int idx, WasmValType variableType) {
            super(variableType);
            this.idx = idx;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "p" + idx;
        }

        @Override
        public String toInnerString() {
            return String.valueOf(idx);
        }
    }

    /**
     * Temporary local variable.
     */
    public static class TempLocal extends WasmId.Local {

        // TODO GR-41720 Move state into ResolverContext
        static AtomicInteger num = new AtomicInteger(0);

        protected TempLocal(WasmValType variableType) {
            super(variableType);
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "TEMP_" + num.getAndIncrement();
        }
    }

    /**
     * Function type backed by {@link FunctionTypeDescriptor}.
     * <p>
     * From this function type,
     * {@link com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmElementCreator} can directly
     * create a {@link FunctionType} type definition. This makes it easier to create function type
     * ids on the fly without having to manually create the type definition later.
     */
    public static class DescriptorFuncType extends WasmId.FuncType {
        private final FunctionTypeDescriptor descriptor;

        // TODO GR-41720 Move state into ResolverContext
        static AtomicInteger num = new AtomicInteger(0);

        public DescriptorFuncType(FunctionTypeDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "func." + num.getAndIncrement();
        }

        public FunctionType createTypeDefinition(Object comment) {
            return descriptor.toTypeDefinition(this, comment);
        }

        public TypeUse getTypeUse() {
            return descriptor.typeUse();
        }
    }
}
