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
package com.oracle.svm.hosted.nodes;

import java.util.Map;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;

import com.oracle.graal.pointsto.results.StaticAnalysisResults;

import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public final class SubstrateMethodCallTargetNode extends MethodCallTargetNode {
    public static final NodeClass<SubstrateMethodCallTargetNode> TYPE = NodeClass.create(SubstrateMethodCallTargetNode.class);

    protected final StaticAnalysisResults staticAnalysisResults;
    protected final int bci;

    public SubstrateMethodCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, StampPair returnStamp, StaticAnalysisResults staticAnalysisResults, int bci) {
        super(TYPE, invokeKind, targetMethod, arguments, returnStamp, staticAnalysisResults.getTypeProfile(bci));
        this.staticAnalysisResults = staticAnalysisResults;
        this.bci = bci;
    }

    public StaticAnalysisResults getStaticAnalysisResults() {
        return staticAnalysisResults;
    }

    public int getBci() {
        return bci;
    }

    public JavaTypeProfile getTypeProfile() {
        return getProfile();
    }

    public JavaMethodProfile getMethodProfile() {
        return staticAnalysisResults.getMethodProfile(bci);
    }

    public JavaTypeProfile getInvokeResultTypeProfile() {
        return staticAnalysisResults.getInvokeResultTypeProfile(bci);
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {

        map.put("typeProfile", getTypeProfile());
        map.put("methodProfile", getMethodProfile());
        map.put("resultTypeProfile", getInvokeResultTypeProfile());

        return super.getDebugProperties(map);
    }
}
