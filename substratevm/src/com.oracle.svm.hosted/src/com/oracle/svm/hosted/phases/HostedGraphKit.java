/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.nodes.SubstrateMethodCallTargetNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HostedGraphKit extends SubstrateGraphKit {

    public HostedGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, method, providers, providers.getWordTypes(), providers.getGraphBuilderPlugins(), null);
    }

    @Override
    protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci) {
        ResolvedJavaMethod method = graph.method();
        if (method instanceof HostedMethod) {
            return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp, ((HostedMethod) method).getProfilingInfo(), bci);
        } else {
            return super.createMethodCallTarget(invokeKind, targetMethod, args, returnStamp, bci);
        }
    }
}
