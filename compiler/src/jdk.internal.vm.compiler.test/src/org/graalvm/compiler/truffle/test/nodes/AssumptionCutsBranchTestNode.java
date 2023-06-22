/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.nodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo
public class AssumptionCutsBranchTestNode extends AbstractTestNode {

    private final Assumption assumption;
    private int counter;
    @Child private Node childNode;

    public AssumptionCutsBranchTestNode(Assumption assumption) {
        this.assumption = assumption;
        this.counter = 0;
    }

    @Override
    public int execute(VirtualFrame frame) {
        int returnVal = 0;
        if (!assumption.isValid()) {
            // this branch should be cut off, thus the Assertion should not trigger
            CompilerAsserts.neverPartOfCompilation("this branch should be cut off");

            // execute some complicated but otherwise meaningless code
            double sum = 0;

            if (Math.random() < 0.5) {
                int iSum = 0;
                for (int i = 0; i < 100; i++) {
                    if (Math.random() > 0.5) {
                        sum += Math.cos(Math.random());
                    } else {
                        sum += Math.sin(Math.random());
                    }
                    iSum += i * 2;
                }
                AssumptionCutsBranchTestNode node2 = new AssumptionCutsBranchTestNode(assumption);
                node2.adoptChildren();
                childNode = node2.copy();

                if (iSum % 2 != 0) {
                    // this is never executed, but introduces a potential invalidation
                    assumption.invalidate();
                }
            } else {
                sum = Math.random();
            }

            return Math.round(sum) % 100 == 0 && childNode.toString().contains("a") ? 666 : 777;
        } else if (counter % 2 == 0) {
            returnVal++;
        }
        counter++;
        return returnVal % 2 == 0 ? returnVal : 0; // will always return 0
    }

    public Node getChildNode() {
        return childNode;
    }
}
