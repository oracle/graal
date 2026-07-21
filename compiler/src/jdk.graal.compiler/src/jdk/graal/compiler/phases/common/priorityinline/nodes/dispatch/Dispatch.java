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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.vm.ci.meta.AbstractJavaProfile;
import jdk.vm.ci.meta.AbstractProfiledItem;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Represents the implementation of the dispatch that the {@link InlineCacheNode} uses. This class
 * abstracts over what the profile kind for the {@link InlineCacheNode} is, how the items of that
 * profile kind are created and accessed, and what the policy is for creating the children of the
 * {@link InlineCacheNode}.
 * <p>
 * The main goal of this class is to create call-tree children of an inline-cache node, which is
 * done in the {@link Dispatch#createChildren(CallTreeNode, InlineCacheNode)} method.
 */
public abstract class Dispatch {

    private static final double MINIMAL_TOTAL_PROBABILITY = 0.001;

    public abstract void addDebugProperties(Map<Object, Object> map);

    public abstract void createChildren(CallTreeNode caller, InlineCacheNode inlineCacheNode);

    public abstract AbstractJavaProfile<?, ?> createProfileForEmpty();

    protected abstract AbstractJavaProfile<?, ?> profile();

    protected abstract AbstractProfiledItem<?>[] getProfiledItems(AbstractJavaProfile<?, ?> javaProfile);

    protected abstract AbstractProfiledItem<?>[] createProfiledItems(int length);

    protected abstract Object getItem(AbstractProfiledItem<?> profiledItem);

    protected abstract AbstractProfiledItem<?> findProfiledItemFor(AbstractProfiledItem<?>[] oldItems, CallTreeNode child);

    protected abstract AbstractJavaProfile<?, ?> createProfile(double notRecordedProbability, AbstractProfiledItem<?>[] profiledItems);

    /**
     * Given a child of the {@link InlineCacheNode} that did not get inlined in a particular
     * inlining round, create the profile item that corresponds to that child node, and use the
     * specified probability.
     */
    protected abstract AbstractProfiledItem<?> createProfiledItemForPostponed(CallTreeNode child, double probability);

    public abstract SpeculationLog.Speculation tryCreateDeoptSpeculation(InlineCacheNode inlineCacheNode, SpeculationLog speculationLog);

    /**
     * Given a list of children that were not inlined in the current inlining round, create a new
     * profile object that corresponds to those postponed children.
     */
    public AbstractJavaProfile<?, ?> createProfileForPostponed(List<CallTreeNode> postponedChildren) {
        AbstractProfiledItem<?>[] oldItems = getProfiledItems(profile());
        AbstractProfiledItem<?>[] newItems = createProfiledItems(postponedChildren.size());
        double[] newProbabilities = new double[newItems.length];
        double totalProbability = 0.0;
        int lastItemPos = 0;
        for (CallTreeNode child : postponedChildren) {
            AbstractProfiledItem<?> oldProfiledItem = findProfiledItemFor(oldItems, child);
            double probability = oldProfiledItem.getProbability();
            totalProbability += probability;
            newProbabilities[lastItemPos] = probability;
            lastItemPos += 1;
        }
        totalProbability += profile().getNotRecordedProbability();
        if (totalProbability <= 0) {
            // Protect against division by 0, See: GR-48420
            totalProbability = MINIMAL_TOTAL_PROBABILITY;
        }
        lastItemPos = 0;
        for (CallTreeNode child : postponedChildren) {
            newItems[lastItemPos] = createProfiledItemForPostponed(child, newProbabilities[lastItemPos] / totalProbability);
            lastItemPos += 1;
        }
        double newNotRecordedProbability = profile().getNotRecordedProbability() / totalProbability;
        Arrays.sort(newItems, (a, b) -> {
            if (a.getProbability() > b.getProbability()) {
                return -1;
            } else if (a.getProbability() < b.getProbability()) {
                return 1;
            } else {
                return 0;
            }
        });
        return createProfile(newNotRecordedProbability, newItems);
    }

    protected AbstractProfiledItem<?> findByItem(AbstractProfiledItem<?>[] oldItems, Object item) {
        for (AbstractProfiledItem<?> profiledItem : oldItems) {
            if (getItem(profiledItem).equals(item)) {
                return profiledItem;
            }
        }
        throw GraalError.shouldNotReachHere("Profiled types: " + Arrays.toString(oldItems) + ", searching: " + item); // ExcludeFromJacocoGeneratedReport
    }

}
