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

import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;

import java.util.HashMap;
import java.util.List;

/*
* This class is used for processing blocks of nodes, while ReentrantBlockIterator is iterating trough blocks of nodes.
* Base purpose of this class is to ensure that for every class, which is initialized at runtime, DynamicHub.ensureInitialized
* method is generated only once. This is achieved by remembering all classes which are initialized in current
* method graph, by saving hashCodes of these classes into EconomicSet.
* */
public class RemovingUnneededClassInitClosure extends ReentrantBlockIterator.BlockIteratorClosure<EconomicSet<JavaConstant>> {

    private static final int WHICH_CLASS_ARGUMENT_INDEX = 0;

    private HashMap<InvokeWithExceptionNode, AbstractBeginNode> nodesToBeRemoved;

    public void removeUnneededNodes(RemovingUnneededClassInitClosure removingUnneededClassInitClosure, Block start) {
        ReentrantBlockIterator.apply(removingUnneededClassInitClosure, start);
    }

    public HashMap<InvokeWithExceptionNode, AbstractBeginNode> getNodesToBeRemoved() {
        return nodesToBeRemoved;
    }

    @Override
    protected EconomicSet<JavaConstant> getInitialState() {
        this.nodesToBeRemoved = new HashMap<>();
        return EconomicSet.create(Equivalence.IDENTITY);
    }

    @Override
    protected EconomicSet<JavaConstant> processBlock(Block block, EconomicSet<JavaConstant> currentState) {
        for (Node node : block.getNodes()) {
            if (node instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) node;
                if (invokeWithExceptionNode.callTarget() != null && invokeWithExceptionNode.callTarget().targetMethod() != null &&
                        invokeWithExceptionNode.callTarget().targetMethod().getName().equals("ensureInitialized") &&
                        invokeWithExceptionNode.callTarget().targetMethod().getDeclaringClass().getName().equals("Ljava/lang/Class;")) {

                    JavaConstant javaConstant = invokeWithExceptionNode.callTarget().arguments().get(WHICH_CLASS_ARGUMENT_INDEX).asJavaConstant();
                    if (javaConstant != null) {
                        if (!currentState.contains(javaConstant)) {
                            currentState.add(javaConstant);
                        } else {
                            nodesToBeRemoved.put(invokeWithExceptionNode, invokeWithExceptionNode.next());
                        }
                    }
                }
            }
        }

        return currentState;
    }

    @Override
    protected EconomicSet<JavaConstant> merge(Block merge, List<EconomicSet<JavaConstant>> states) {
        // Find intersection of all branches which are merging into current merge node.
        EconomicSet<JavaConstant> result = states.get(0);
        for (int i = 1; i < states.size(); i++) {
            result.retainAll(states.get(i));
        }
        return result;
    }

    @Override
    protected EconomicSet<JavaConstant> cloneState(EconomicSet<JavaConstant> oldState) {
        // Clone current state of iteration.
        EconomicSet<JavaConstant> result = EconomicSet.create(Equivalence.IDENTITY);
        if (oldState != null) {
            result.addAll(oldState);
        }
        return result;
    }

    @Override
    protected List<EconomicSet<JavaConstant>> processLoop(Loop<Block> loop, EconomicSet<JavaConstant> initialState) {
        // No special job to be done here. Just continue into loop with processLoop method.
        return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
    }
}
