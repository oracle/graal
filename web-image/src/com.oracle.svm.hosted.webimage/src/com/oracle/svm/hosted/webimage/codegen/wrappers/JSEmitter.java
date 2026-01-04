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
package com.oracle.svm.hosted.webimage.codegen.wrappers;

import java.util.Objects;
import java.util.function.Consumer;

import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSNodeLowerer;
import com.oracle.svm.hosted.webimage.codegen.long64.Long64Lowerer;
import com.oracle.svm.hosted.webimage.js.JSStaticMethodDefinition;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.object.ConstantIdentityMapping;
import com.oracle.svm.webimage.object.ObjectInspector;

import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class JSEmitter implements IEmitter {

    private final Consumer<JSCodeGenTool> lowerer;

    protected JSEmitter(Consumer<JSCodeGenTool> lowerer) {
        this.lowerer = lowerer;
    }

    @Override
    public void lower(CodeGenTool codeGenTool) {
        lowerer.accept((JSCodeGenTool) codeGenTool);
    }

    /**
     * Wraps given emitter in int paladin.
     * <p>
     * Produces {@code ((inner) | 0)}
     */
    public static JSEmitter intPaladin(IEmitter inner) {
        return new JSEmitter((t) -> {
            t.genNumericalPaladinIntLeft();
            inner.lower(t);
            t.genNumericalPaladinIntRight();
        });
    }

    public static JSEmitter of(Consumer<JSCodeGenTool> lowerer) {
        return new JSEmitter(lowerer);
    }

    public static JSEmitter of(Long l) {
        Objects.requireNonNull(l);
        return new JSEmitter((t) -> Long64Lowerer.lowerSubstitutedDeclaration(t, l));
    }

    public static JSEmitter of(PrimitiveConstant c) {
        Objects.requireNonNull(c);
        return new JSEmitter((t) -> WebImageJSNodeLowerer.lowerConstant(c, t));
    }

    public static JSEmitter of(ConstantIdentityMapping.IdentityNode node) {
        Objects.requireNonNull(node);
        return new JSEmitter((t) -> t.getCodeBuffer().emitText(node.requestName()));
    }

    public static IEmitter of(ObjectInspector.ValueType v) {
        Objects.requireNonNull(v);
        return JSEmitter.of(v.getConstant());
    }

    public static IEmitter of(ObjectInspector.MethodPointerType ptr) {
        return JSEmitter.of(ptr.getIndex());
    }

    public static JSEmitter of(JSStaticMethodDefinition methodDefinition) {
        return new JSEmitter(methodDefinition::emitReference);
    }

    /**
     * @return Emitter to a complete reference of the given method.
     */
    public static JSEmitter ofMethodReference(ResolvedJavaMethod m) {
        Objects.requireNonNull(m);
        return new JSEmitter((t) -> {
            if (m.isStatic()) {
                t.genStaticMethodReference(m);
            } else {
                t.genPrototypeMethodReference(m);
            }
        });
    }
}
