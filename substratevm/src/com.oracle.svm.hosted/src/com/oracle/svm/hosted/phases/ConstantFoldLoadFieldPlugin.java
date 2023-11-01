/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.util.ConstantFoldUtil;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

public final class ConstantFoldLoadFieldPlugin implements NodePlugin {
    private final ParsingReason reason;

    public ConstantFoldLoadFieldPlugin(ParsingReason reason) {
        this.reason = reason;
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
        if (receiver.isConstant()) {
            JavaConstant asJavaConstant = receiver.asJavaConstant();
            return tryConstantFold(b, field, asJavaConstant);
        }
        return false;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField staticField) {
        return tryConstantFold(b, staticField, null);
    }

    private boolean tryConstantFold(GraphBuilderContext b, ResolvedJavaField field, JavaConstant receiver) {
        ConstantNode result;
        try {
            result = ConstantFoldUtil.tryConstantFold(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(), field, receiver, b.getOptions(),
                            b.getGraph().currentNodeSourcePosition());
        } catch (UnsupportedFeatureException e) {
            if (reason.duringAnalysis()) {
                AnalysisMetaAccess metaAccess = (AnalysisMetaAccess) b.getMetaAccess();
                ObjectScanner.unsupportedFeatureDuringFieldFolding(metaAccess.getUniverse().getBigbang(), (AnalysisField) field, receiver, e, (AnalysisMethod) b.getMethod(), b.bci());
                // kill control flow, the image build fails anyway
                b.add(new LoweredDeadEndNode());
                return true;
            } else {
                throw e;
            }
        }

        if (result != null) {
            assert result.asJavaConstant() != null;
            result = b.getGraph().unique(result);
            b.push(field.getJavaKind(), result);
            return true;
        }
        return false;
    }
}
