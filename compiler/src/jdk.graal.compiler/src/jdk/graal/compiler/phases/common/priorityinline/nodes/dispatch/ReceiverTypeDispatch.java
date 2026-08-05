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

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.vm.ci.meta.AbstractJavaProfile;
import jdk.vm.ci.meta.AbstractProfiledItem;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Performs the dispatch based on the receiver type profile.
 * <p>
 * The dispatch infos are generated based on the receiver types. Only the receiver types for which a
 * method can be resolved will be placed into the dispatch-info list.
 * <p>
 * This policy can still create method-based checks if the InliningProvider allows it. In this case,
 * a method-based check is created if two receiver types share the same implementation method.
 */
public final class ReceiverTypeDispatch extends Dispatch {
    private final JavaTypeProfile profile;

    public ReceiverTypeDispatch(JavaTypeProfile profile) {
        this.profile = profile;
    }

    @Override
    public void addDebugProperties(Map<Object, Object> map) {
        map.put("profile", profile);
    }

    @Override
    public void createChildren(CallTreeNode caller, InlineCacheNode inlineCacheNode) {
        ArrayList<DispatchInfo> dispatches = new ArrayList<>();
        InliningProvider inliningProvider = inlineCacheNode.callTree().inliningProvider();
        Invoke invoke = inlineCacheNode.invoke();
        ResolvedJavaMethod targetMethod = invoke.getTargetMethod();
        ResolvedJavaType receiverType = invoke.getReceiverType();

        double adjustedNotRecorded = profile.getNotRecordedProbability();

        // Generate the inline cache node based on the receiver-type profiles.
        for (JavaTypeProfile.ProfiledType profiledType : profile.getTypes()) {
            double typeProbability = profiledType.getProbability();
            ResolvedJavaMethod directTarget = profiledType.getType().resolveConcreteMethod(targetMethod, invoke.getContextType());

            if (directTarget == null) {
                // Fall into virtual call category
                adjustedNotRecorded += typeProbability;
                continue;
            }

            // If possible, combine multiple types that dispatch to the same method.
            if (!inlineCacheNode.callTree().hasDirectedReceiverTypeFilters() &&
                            inliningProvider.useMethodChecks(inlineCacheNode.getOptions()) &&
                            inliningProvider.isMethodForDevirtualizationInTable(inlineCacheNode.originalTargetMethod(), targetMethod, directTarget, receiverType)) {
                DispatchInfo existingInfo = DispatchInfo.match(dispatches, directTarget, inliningProvider);
                if (existingInfo != null) {
                    existingInfo.needsMethodDispatch = true;
                    existingInfo.probability += typeProbability;
                    continue;
                }
            }

            DispatchInfo newInfo = new DispatchInfo();
            newInfo.probability = typeProbability;
            newInfo.dispatchedType = profiledType.getType();
            newInfo.dispatchedMethod = directTarget;
            newInfo.needsMethodDispatch = false;
            dispatches.add(newInfo);
        }

        dispatches.sort(null);

        inlineCacheNode.createChildrenFromDispatches(caller, dispatches, adjustedNotRecorded);
    }

    @Override
    public SpeculationLog.Speculation tryCreateDeoptSpeculation(InlineCacheNode inlineCacheNode, SpeculationLog speculationLog) {
        SpeculationLog.Speculation speculation = SpeculationLog.NO_SPECULATION;
        SpeculationLog.SpeculationReason speculationReason = InliningUtil.createSpeculation(inlineCacheNode.invoke(), profile);
        if (speculationLog.maySpeculate(speculationReason)) {
            speculation = speculationLog.speculate(speculationReason);
        }
        return speculation;
    }

    @Override
    public AbstractJavaProfile<?, ?> createProfileForEmpty() {
        return new JavaTypeProfile(profile.getNullSeen(), profile.getNotRecordedProbability(), new JavaTypeProfile.ProfiledType[0]);
    }

    @Override
    protected AbstractJavaProfile<?, ?> profile() {
        return profile;
    }

    @Override
    protected AbstractProfiledItem<?>[] getProfiledItems(AbstractJavaProfile<?, ?> javaProfile) {
        return ((JavaTypeProfile) javaProfile).getTypes();
    }

    @Override
    protected AbstractProfiledItem<?>[] createProfiledItems(int length) {
        return new JavaTypeProfile.ProfiledType[length];
    }

    @Override
    protected Object getItem(AbstractProfiledItem<?> profiledItem) {
        return ((JavaTypeProfile.ProfiledType) profiledItem).getType();
    }

    @Override
    protected AbstractProfiledItem<?> findProfiledItemFor(AbstractProfiledItem<?>[] oldItems, CallTreeNode child) {
        ResolvedJavaType dispatchedType = child instanceof CutoffNode ? ((CutoffNode) child).getOriginalDispatchedType() : ((SubgraphNode) child).getOriginalDispatchedType();
        return findByItem(oldItems, dispatchedType);
    }

    @Override
    protected AbstractJavaProfile<?, ?> createProfile(double notRecordedProbability, AbstractProfiledItem<?>[] profiledItems) {
        return new JavaTypeProfile(profile.getNullSeen(), notRecordedProbability, (JavaTypeProfile.ProfiledType[]) profiledItems);
    }

    @Override
    protected AbstractProfiledItem<?> createProfiledItemForPostponed(CallTreeNode child, double probability) {
        ResolvedJavaType dispatchedType = child instanceof CutoffNode ? ((CutoffNode) child).getOriginalDispatchedType() : ((SubgraphNode) child).getOriginalDispatchedType();
        return new JavaTypeProfile.ProfiledType(dispatchedType, probability);
    }
}
