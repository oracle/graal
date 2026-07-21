/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.nodes.dispatch;

import java.util.ArrayList;
import java.util.Map;

import jdk.graal.compiler.debug.GraalError;

import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;

import jdk.vm.ci.meta.AbstractJavaProfile;
import jdk.vm.ci.meta.AbstractProfiledItem;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Performs the dispatch based on the profile of dispatched methods.
 * <p>
 * The polymorphic dispatch switch is generated based on the concrete methods. This policy generates
 * method-based checks, so the InliningProvider must allow them.
 */
public final class MethodDispatch extends Dispatch {
    private final JavaMethodProfile profile;

    public MethodDispatch(JavaMethodProfile profile) {
        this.profile = profile;
    }

    @Override
    public void addDebugProperties(Map<Object, Object> map) {
        map.put("method-profile", profile);
        StringBuilder sb = new StringBuilder();
        for (JavaMethodProfile.ProfiledMethod method : profile.getMethods()) {
            sb.append(method.getMethod().format("%H.%n")).append(" -> ").append(method.getProbability()).append(System.lineSeparator());
        }
        sb.append("Not recorded: ").append(profile.getNotRecordedProbability());
        map.put("method-profile-detailed", sb.toString());
    }

    @Override
    public void createChildren(CallTreeNode caller, InlineCacheNode inlineCacheNode) {
        assert inlineCacheNode.callTree().inliningProvider().useMethodChecks(inlineCacheNode.getOptions()) : "Method-based checks disallowed, cannot generated dispatch";
        ArrayList<DispatchInfo> dispatches = new ArrayList<>();
        double adjustedNotRecorded = profile.getNotRecordedProbability();

        // If sampling method profiles are available, we prefer to use them instead of the type
        // profiles to generate the inline cache node.
        for (JavaMethodProfile.ProfiledMethod profiledMethod : profile.getMethods()) {
            ResolvedJavaMethod method = profiledMethod.getMethod();
            double methodProbability = profiledMethod.getProbability();

            DispatchInfo newInfo = new DispatchInfo();
            newInfo.probability = methodProbability;
            newInfo.dispatchedMethod = method;
            newInfo.needsMethodDispatch = true;
            newInfo.dispatchedType = null;
            dispatches.add(newInfo);
        }

        inlineCacheNode.createChildrenFromDispatches(caller, dispatches, adjustedNotRecorded);
    }

    @Override
    public SpeculationLog.Speculation tryCreateDeoptSpeculation(InlineCacheNode inlineCacheNode, SpeculationLog speculationLog) {
        throw GraalError.shouldNotReachHere("Method-profile-based deopt speculation during polymorphic inlining is not supported."); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public AbstractJavaProfile<?, ?> createProfileForEmpty() {
        return new JavaMethodProfile(profile.getNotRecordedProbability(), new JavaMethodProfile.ProfiledMethod[0]);
    }

    @Override
    protected AbstractJavaProfile<?, ?> profile() {
        return profile;
    }

    @Override
    protected AbstractProfiledItem<?>[] getProfiledItems(AbstractJavaProfile<?, ?> javaProfile) {
        return ((JavaMethodProfile) javaProfile).getMethods();
    }

    @Override
    protected AbstractProfiledItem<?>[] createProfiledItems(int length) {
        return new JavaMethodProfile.ProfiledMethod[length];
    }

    @Override
    protected Object getItem(AbstractProfiledItem<?> profiledItem) {
        return ((JavaMethodProfile.ProfiledMethod) profiledItem).getMethod();
    }

    @Override
    protected AbstractProfiledItem<?> findProfiledItemFor(AbstractProfiledItem<?>[] oldItems, CallTreeNode child) {
        ResolvedJavaMethod resolvedJavaMethod = child.targetMethod();
        return findByItem(oldItems, resolvedJavaMethod);
    }

    @Override
    protected AbstractJavaProfile<?, ?> createProfile(double notRecordedProbability, AbstractProfiledItem<?>[] profiledItems) {
        return new JavaMethodProfile(notRecordedProbability, (JavaMethodProfile.ProfiledMethod[]) profiledItems);
    }

    @Override
    protected AbstractProfiledItem<?> createProfiledItemForPostponed(CallTreeNode child, double probability) {
        assert child instanceof CutoffNode || child instanceof SubgraphNode : "Child must be a cutoff or a subgraph: " + child;
        ResolvedJavaMethod dispatchedMethod = child.targetMethod();
        return new JavaMethodProfile.ProfiledMethod(dispatchedMethod, probability);
    }
}
