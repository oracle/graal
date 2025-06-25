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
package com.oracle.svm.hosted.webimage.codegen.arithm;

import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.MATH_IMUL;

import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.wrappers.JSEmitter;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.webimage.hightiercodegen.Emitter;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * Lowering logic for binary integer operations with at most 32-bits. The {@code long} datatype is
 * handled in {@link com.oracle.svm.hosted.webimage.codegen.long64.Long64Lowerer}.
 */
public class BinaryIntOperationLowerer {

    public static void binaryOp(ValueNode node, ValueNode lOp, ValueNode rOp, JSCodeGenTool jsLTools, JSKeyword operatorSymbol, boolean intPaladin) {
        assert node.getStackKind() == JavaKind.Int : node.getStackKind();
        assert lOp.getStackKind() == JavaKind.Int : lOp.getStackKind();
        assert rOp.getStackKind() == JavaKind.Int : rOp.getStackKind();
        if (node instanceof MulNode) {
            /*
             * The JavaScript function 'Math.imul' is used because multiplying two 32-bit integers
             * might give a result that needs more than 53 bits and might not be storable with a
             * 64-bit float. Doing (a * b) | 0 can give wrong results if that happens. With
             * Math.imul(a, b) this does not happen.
             */
            MATH_IMUL.emitCall(jsLTools, Emitter.of(lOp), Emitter.of(rOp));
        } else {
            JSEmitter emitter = JSEmitter.of((t) -> t.genBinaryOperation(operatorSymbol, lOp, rOp));

            if (intPaladin) {
                emitter = JSEmitter.intPaladin(emitter);
            }

            emitter.lower(jsLTools);
        }
    }
}
