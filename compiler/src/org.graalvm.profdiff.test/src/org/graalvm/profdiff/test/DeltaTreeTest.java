/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.matching.tree.DeltaTree;
import org.graalvm.profdiff.matching.tree.DeltaTreeNode;
import org.graalvm.profdiff.matching.tree.EditScript;
import org.junit.Assert;
import org.junit.Test;

public class DeltaTreeTest {
    private static class MockTreeNode extends TreeNode<MockTreeNode> {
        protected MockTreeNode() {
            super(null);
        }
    }

    @Test
    public void editScriptConversion() {
        EditScript<MockTreeNode> editScript = new EditScript<>();
        editScript.delete(new MockTreeNode(), 1);
        editScript.relabel(new MockTreeNode(), new MockTreeNode(), 1);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 2);
        editScript.insert(new MockTreeNode(), 2);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 2);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 1);
        editScript.insert(new MockTreeNode(), 1);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 0);
        EditScript<MockTreeNode> convertedEditScript = DeltaTree.fromEditScript(editScript).asEditScript();
        Assert.assertEquals(editScript.getDeltaNodes().toList(), convertedEditScript.getDeltaNodes().toList());
    }

    @Test
    public void pruneIdentities() {
        DeltaTreeNode<MockTreeNode> root = new DeltaTreeNode<>(0, true, new MockTreeNode(), new MockTreeNode());
        root.addChild(true, new MockTreeNode(), new MockTreeNode());
        DeltaTreeNode<MockTreeNode> identity = root.addChild(true, new MockTreeNode(), new MockTreeNode());
        identity.addChild(true, new MockTreeNode(), new MockTreeNode());
        DeltaTreeNode<MockTreeNode> insertion = identity.addChild(false, null, new MockTreeNode());
        DeltaTreeNode<MockTreeNode> deletion = identity.addChild(false, new MockTreeNode(), null);
        DeltaTreeNode<MockTreeNode> relabeling = identity.addChild(false, new MockTreeNode(), new MockTreeNode());
        DeltaTree<MockTreeNode> deltaTree = new DeltaTree<>(root);
        deltaTree.pruneIdentities();
        List<DeltaTreeNode<MockTreeNode>> expectedPreorder = List.of(root, identity, insertion, deletion, relabeling);
        List<DeltaTreeNode<MockTreeNode>> actualPreorder = new ArrayList<>();
        deltaTree.forEach(actualPreorder::add);
        Assert.assertEquals(expectedPreorder, actualPreorder);
    }
}
