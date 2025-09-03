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

import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;

public class BinaryFloatOperationLowerer {

    public static void doop(JSCodeGenTool jsLTools, JSKeyword op, BinaryArithmeticNode<?> node) {
        assert node.stamp(NodeView.DEFAULT) instanceof FloatStamp : node.stamp(NodeView.DEFAULT);
        assert node.getX().stamp(NodeView.DEFAULT) instanceof FloatStamp : node.getX().stamp(NodeView.DEFAULT);
        assert node.getY().stamp(NodeView.DEFAULT) instanceof FloatStamp : node.getY().stamp(NodeView.DEFAULT);
        FloatStamp fs = (FloatStamp) node.stamp(NodeView.DEFAULT);

        if (fs.getBits() == 32 && WebImageOptions.SemanticOptions.ForceSinglePrecision.getValue(node.getOptions())) {
            jsLTools.getCodeBuffer().emitText("Math.fround");
        }
        jsLTools.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        jsLTools.genBinaryOperation(op, node.getX(), node.getY());
        jsLTools.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

}
