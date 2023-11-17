/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.lowerer;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.hightiercodegen.CodeGenTool;
import jdk.graal.compiler.hightiercodegen.Emitter;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Generates code for a {@link CommitAllocationNode} and its associated {@link VirtualObjectNode}s.
 */
public class CommitAllocationLowerer {

    static class Materialization {
        Materialization(VirtualObjectNode object, List<ValueNode> values, boolean containsVirtual) {
            this.object = object;
            this.values = values;
            this.containsVirtual = containsVirtual;
        }

        public final VirtualObjectNode object;
        public final List<ValueNode> values;
        public final boolean containsVirtual;
    }

    /** See the comment inside {@link #lower}. */
    protected void lowerInstance(VirtualInstanceNode node, CodeGenTool codeGenTool) {
        assert codeGenTool.declared(node) : "variable for VirtualInstanceNode must be declared";
        codeGenTool.lowerValue(node);
        codeGenTool.genAssignment();
        codeGenTool.genNewInstance(node.type());
        codeGenTool.getCodeBuffer().emitInsEnd();
    }

    /** See the comment inside {@link #lower}. */
    protected void lowerArray(VirtualArrayNode node, CodeGenTool codeGenTool) {
        assert codeGenTool.declared(node) : "variable for VirtualArrayNode must be declared";
        codeGenTool.lowerValue(node);
        codeGenTool.genAssignment();

        codeGenTool.genNewArray(node.type(), Emitter.of(node.entryCount()));
        codeGenTool.getCodeBuffer().emitInsEnd();
    }

    public static List<Materialization> computeMaterializations(CommitAllocationNode commit) {
        List<Materialization> mats = new ArrayList<>();
        int valindex = 0;
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            List<ValueNode> values = new ArrayList<>();
            VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
            int entryCount = virtual.entryCount();
            boolean containsVirtual = false;
            for (int i = 0; i < entryCount; i++) {
                ValueNode value = commit.getValues().get(valindex++);
                if (value instanceof VirtualObjectNode) {
                    containsVirtual = true;
                }
                values.add(value);
            }
            mats.add(new Materialization(virtual, values, containsVirtual));
        }
        return mats;
    }

    /**
     * Lower the initialization of a single field of a {@link VirtualInstanceNode} in a
     * {@link CommitAllocationNode}.
     */
    protected void lowerFieldAssignment(VirtualInstanceNode instanceNode, ResolvedJavaField field, ValueNode value, CodeGenTool codeGenTool) {
        codeGenTool.genPropertyAccess(Emitter.of(instanceNode), Emitter.of(field));
        codeGenTool.genAssignment();
        codeGenTool.lowerValue(value);
    }

    /**
     * Lower the initialization of a single element of a {@link VirtualArrayNode} in a
     * {@link CommitAllocationNode}.
     */
    protected void lowerArrayElementAssignment(VirtualArrayNode array, int index, ValueNode value, CodeGenTool codeGenTool) {
        codeGenTool.genArrayStore(Emitter.of(index), array, value);
    }

    /**
     * Lowering of the virtual objects is very special in the sense that we need to ignore them from
     * the schedule yet they are not the same as inlined nodes.
     *
     * The logic that handles them is scattered in three different places:
     *
     * (1) The inline logic dictates that virtual objects need variable allocation;
     *
     * (2) StackifierIRWalker#lowerNode does not issue them to NodeLowerer when processing the
     * schedule;
     *
     * (3) The binding is generated here, i.e., at the schedule point of the associated
     * CommitAllocationNode.
     *
     * Note: The same virtual object might be associated with several different CommitAllocationNode
     * in different branches, thus it is safe to share the same variable name.
     *
     * The following is one example:
     *
     * <pre>
     * class Hello {
     *     static int[] holder1 = null;
     *     static int[] holder2 = null;
     *
     *     public static void main(String[] args) {
     *         int[] array = new int[10];
     *         if (args.length > 5) {
     *             holder1 = array;
     *         } else if (args.length < 1) {
     *             holder2 = array;
     *         } else {
     *             System.out.println(array.length);
     *         }
     *     }
     * }
     * </pre>
     *
     * In the code above, the scheduled point for VirtualArrayNode is at the beginning of the
     * method. We need to ignore the VirtualArrayNode there. Instead, we instantiate the array at
     * the two CommitAllocationNodes, i.e., the first two branches just before the leaking of the
     * array.
     */
    public void lower(CommitAllocationNode commit, CodeGenTool codeGenTool) {
        List<Materialization> mats = computeMaterializations(commit);
        for (Materialization materialization : mats) {
            VirtualObjectNode virtual = materialization.object;
            if (virtual instanceof VirtualInstanceNode) {
                lowerInstance((VirtualInstanceNode) virtual, codeGenTool);
            } else {
                lowerArray((VirtualArrayNode) virtual, codeGenTool);
            }
        }

        for (Materialization mat : mats) {
            VirtualObjectNode virtual = mat.object;
            for (int propertyNum = 0; propertyNum < mat.values.size(); propertyNum++) {
                ValueNode value = mat.values.get(propertyNum);
                if (value != null && !JavaConstant.NULL_POINTER.equals(value.asJavaConstant())) {
                    if (virtual instanceof VirtualInstanceNode instanceNode) {
                        ResolvedJavaField field = instanceNode.field(propertyNum);
                        lowerFieldAssignment(instanceNode, field, value, codeGenTool);
                        codeGenTool.genResolvedVarDeclPostfix("Materialize virtual object property assignment");
                    } else {
                        if (value.isJavaConstant()) {
                            JavaConstant javaConstant = value.asJavaConstant();
                            if (javaConstant.isDefaultForKind()) {
                                /*
                                 * Default values are already assigned in the array initialization
                                 * function.
                                 */
                                continue;
                            }
                        }
                        lowerArrayElementAssignment((VirtualArrayNode) virtual, propertyNum, value, codeGenTool);
                        codeGenTool.genResolvedVarDeclPostfix("Materialize virtual array assignment");
                    }
                }
            }
        }
    }
}
