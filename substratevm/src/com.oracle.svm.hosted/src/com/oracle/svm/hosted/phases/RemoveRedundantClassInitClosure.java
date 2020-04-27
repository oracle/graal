/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.hosted.meta.HostedMethod;
import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;

import java.util.List;

/*
* This class is used for processing one block of nodes, while ReentrantBlockIterator is iterating trough blocks of nodes.
* Base purpose of this class is to ensure that for every class, which is initialized at runtime, DynamicHub.ensureInitialized
* method is generated only once.
* It's called from RemoveRedundantClassInitPhase.
* */
public class RemoveRedundantClassInitClosure extends ReentrantBlockIterator.BlockIteratorClosure<EconomicSet<JavaConstant>> {

    private static final int WHICH_ARGUMENT_INDEX_IS_CLASS = 0;

    private EconomicMap<InvokeWithExceptionNode, AbstractBeginNode> nodesToBeRemoved;
    private EconomicMap<InvokeWithExceptionNode, AbstractBeginNode> nodesToRemain;

    private final HostedMethod classInitMethod;

    public RemoveRedundantClassInitClosure(HostedMethod classInitMethod) {
        this.classInitMethod = classInitMethod;
    }

    public void removeRedundantNodes(RemoveRedundantClassInitClosure removeRedundantClassInitClosure, Block start) {
        ReentrantBlockIterator.apply(removeRedundantClassInitClosure, start);
    }

    public EconomicMap<InvokeWithExceptionNode, AbstractBeginNode> getNodesToBeRemoved() {
        return nodesToBeRemoved;
    }

    public EconomicMap<InvokeWithExceptionNode, AbstractBeginNode> getNodesToRemain() {
        return nodesToRemain;
    }

    @Override
    protected EconomicSet<JavaConstant> getInitialState() {
        this.nodesToBeRemoved = EconomicMap.create(Equivalence.IDENTITY);
        this.nodesToRemain = EconomicMap.create(Equivalence.IDENTITY);

        return EconomicSet.create(Equivalence.IDENTITY);
    }

    // processBlock method detects redundant initialization nodes base od their java constant values,
    // and store them into nodesToBeRemoved EconomicMap. Opposite of nodesToBeRemoved is nodesToRemain EconomicMap.
    @Override
    protected EconomicSet<JavaConstant> processBlock(Block block, EconomicSet<JavaConstant> currentState) {
        for (Node node : block.getNodes()) {
            if (node instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) node;
                if (invokeWithExceptionNode.callTarget() != null && invokeWithExceptionNode.callTarget().targetMethod() != null &&
                        classInitMethod == invokeWithExceptionNode.callTarget().targetMethod()) {

                    ValueNode valueNode = invokeWithExceptionNode.callTarget().arguments().get(WHICH_ARGUMENT_INDEX_IS_CLASS);
                    if (valueNode.isJavaConstant()) {
                        JavaConstant javaConstant = valueNode.asJavaConstant();
                        if (!currentState.contains(javaConstant)) {
                            currentState.add(javaConstant);
                            nodesToRemain.put(invokeWithExceptionNode, invokeWithExceptionNode.next());
                        } else {
                            nodesToRemain.removeKey(invokeWithExceptionNode);
                            nodesToBeRemoved.put(invokeWithExceptionNode, invokeWithExceptionNode.next());
                        }
                    }
                }
            }
        }

        return currentState;
    }

    // One-time initialization is achieved by remembering (in EconomicSet) all classes which are initialized in one branch, then in merge nodes,
    // we need only to find intersection between all these set. Result of intersection is new set with all class which are surely initialized.
    @Override
    protected EconomicSet<JavaConstant> merge(Block merge, List<EconomicSet<JavaConstant>> states) {
        EconomicSet<JavaConstant> result = states.get(0);
        for (int i = 1; i < states.size(); i++) {
            result.retainAll(states.get(i));
        }
        return result;
    }

    // Clone state simply copies current state of set.
    @Override
    protected EconomicSet<JavaConstant> cloneState(EconomicSet<JavaConstant> oldState) {
        EconomicSet<JavaConstant> result = EconomicSet.create(Equivalence.IDENTITY);
        if (oldState != null) {
            result.addAll(oldState);
        }
        return result;
    }

    // No special job to be done here. Continue with loop processing.
    @Override
    protected List<EconomicSet<JavaConstant>> processLoop(Loop<Block> loop, EconomicSet<JavaConstant> initialState) {
        return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
    }
}
