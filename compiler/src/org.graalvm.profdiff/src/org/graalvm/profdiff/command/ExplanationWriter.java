/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.command;

import org.graalvm.profdiff.core.inlining.InliningTreeNode;

import static org.graalvm.profdiff.core.inlining.InliningTreeNode.CallsiteKind.Deleted;
import static org.graalvm.profdiff.core.inlining.InliningTreeNode.CallsiteKind.Devirtualized;
import static org.graalvm.profdiff.core.inlining.InliningTreeNode.CallsiteKind.Direct;
import static org.graalvm.profdiff.core.inlining.InliningTreeNode.CallsiteKind.Indirect;
import static org.graalvm.profdiff.core.inlining.InliningTreeNode.CallsiteKind.Inlined;
import static org.graalvm.profdiff.core.inlining.InliningTreeNode.CallsiteKind.Root;

import org.graalvm.profdiff.diff.EditScript;
import org.graalvm.profdiff.core.Writer;

/**
 * Writes an explanation of the output. Explains only what is printed, i.e., the output depends on
 * the option values.
 */
public class ExplanationWriter {
    /**
     * The destination writer.
     */
    private final Writer writer;

    /**
     * {@code true} if the output consists of only one experiment.
     */
    private final boolean singleExperiment;

    /**
     * {@code true} if only hot methods are displayed.
     */
    private final boolean onlyHotMethods;

    /**
     * Constructs an explanation writer.
     *
     * @param writer the destination writer
     * @param singleExperiment the output consists of only one experiment
     * @param onlyHotMethods only hot methods are displayed
     */
    public ExplanationWriter(Writer writer, boolean singleExperiment, boolean onlyHotMethods) {
        this.writer = writer;
        this.singleExperiment = singleExperiment;
        this.onlyHotMethods = onlyHotMethods;
    }

    /**
     * Prints an explanation to the destination writer.
     */
    public void explain() {
        writer.writeln("How to read the output");
        writer.setPrefixAfterIndent("- ");
        writer.increaseIndent();
        if (onlyHotMethods) {
            writer.writeln("each method with a hot compilation is printed in a separate section below");
        } else {
            writer.writeln("each compiled method is printed in a separate section below");
        }
        if (!singleExperiment && writer.getOptionValues().shouldDiffCompilations()) {
            writer.writeln("hot compilations are paired by their fraction of execution");
        }
        if (writer.getOptionValues().shouldCreateFragments()) {
            writer.writeln("a compilation refers to either a compilation unit or a compilation fragment");
            writer.increaseIndent();
            writer.writeln("a compilation unit is the result of a Graal compilation");
            writer.writeln("a compilation fragment is created from a compilation unit");
            writer.decreaseIndent();
        }
        if (writer.getOptionValues().isOptimizationContextTreeEnabled()) {
            explainOptimizationContextTree();
        } else {
            explainOptimizationTree();
            explainInliningTree();
        }
        writer.decreaseIndent();
        writer.clearPrefixAfterIndent();
    }

    private void explainOptimizationTree() {
        writer.writeln("the optimization tree is a tree of optimization phases and individual performed optimizations");
        writer.increaseIndent();
        writer.writeln("the root of the tree is a virtual root phase that encompasses all phases");
        writer.writeln("the children of an optimization phase are either phases directly invoked inside the phase or individual optimizations");
        writer.writeln("an optimization is described by an optimization name, event name, position, and optionally properties");
        writer.increaseIndent();
        writer.writeln("optimization name is derived from the name of the phase that performed it");
        writer.writeln("event name name is a more specific description of the optimization");
        writer.writeln("position is the byte code position of a significant node related to the transformation");
        writer.increaseIndent();
        writer.writeln("in the presence of inlining, the position is a list of (method, bci) pairs");
        writer.writeln("the first pair is a position of a bytecode instruction in an inlined method");
        writer.writeln("the rest are callsites and their positions, obtained by following predecessors in the inlining tree");
        writer.decreaseIndent();
        writer.writeln("properties are key-value pairs that give additional information");
        writer.decreaseIndent();
        if (!singleExperiment && writer.getOptionValues().shouldDiffCompilations()) {
            writer.writeln("the optimization trees of two paired compilations are diffed");
            writer.writeln("a list of operations to transform the first optimization tree to the second with minimal cost is computed");
            writer.writeln("the operations include subtree deletion and subtree insertion");
            writer.writeln("the diff is printed in the form of a tree");
            if (writer.getOptionValues().shouldPruneIdentities()) {
                writer.writeln("only the different parts of the trees are displayed at this verbosity level");
            }
            writer.writeln("each node's prefix shows the type of operation it took part in, for example");
            writer.increaseIndent();
            writer.writeln("`" + EditScript.IDENTITY_PREFIX + "SomePhase` - the node is present in both compilations");
            writer.writeln("`" + EditScript.DELETE_PREFIX + "SomePhase` - the node is present in the first compilation but absent in the second");
            writer.writeln("`" + EditScript.INSERT_PREFIX + "SomePhase` - the node is absent in the first compilation but present in the second");
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }

    private void explainIndirectCalls() {
        writer.writeln("indirect calls are prefixed with `" + Indirect.label() + "`");
        writer.increaseIndent();
        writer.writeln("initially-indirect calls may be devirtualized and have children of their own (the devirtualized callees)");
        writer.writeln("indirect calls include a receiver-type profile if it is available");
        writer.writeln("each line in the profile is in the format `probability% typeName -> concreteMethodName`");
        writer.writeln("`typeName` is the exact type of the receiver");
        writer.writeln("`concreteMethodName` is the concrete method called for the given receiver type");
        writer.writeln("`probability` is the fraction of calls having this exact receiver type");
        writer.decreaseIndent();
    }

    private void explainCallsiteKinds() {
        writer.writeln("each invoked method is classified into one of the following callsite kinds");
        writer.increaseIndent();
        writer.writeln(Root.prefix() + "- the compiled root method");
        writer.writeln(Inlined.prefix() + "- an inlined method");
        writer.writeln(Direct.prefix() + "- a direct method invocation, which was not inlined and not deleted");
        writer.writeln(Indirect.prefix() + "- an indirect method invocation, which was not inlined and not deleted");
        writer.writeln(Deleted.prefix() + "- a deleted method invocation");
        writer.writeln(Devirtualized.prefix() + "- an indirect method invocation that was devirtualized to at least one direct call and then deleted");
        writer.decreaseIndent();
    }

    private void inliningTreeOperationExamples() {
        writer.writeln("`" + EditScript.IDENTITY_PREFIX + Inlined.prefix() + "someMethod()` - the callsite is inlined in both compilations");
        writer.writeln("`" + EditScript.IDENTITY_PREFIX + Direct.prefix() + "someMethod()` - there is a direct invocation in both compilations");
        writer.writeln("`" + EditScript.IDENTITY_PREFIX + Deleted.prefix() + "someMethod()` - the callsite is deleted in both compilations");
        writer.writeln("`" + EditScript.RELABEL_PREFIX + InliningTreeNode.CallsiteKind.change(Inlined, Direct) +
                        "someMethod()` - the callsite is inlined in the 1st compilation but there is a direct invocation in the 2nd compilation");
        writer.writeln("`" + EditScript.RELABEL_PREFIX + InliningTreeNode.CallsiteKind.change(Direct, Deleted) +
                        "someMethod()` - there is a direct invocation in the 1st compilation but the callsite is deleted in the 2nd compilation");
        writer.writeln("`" + EditScript.DELETE_PREFIX + Inlined.prefix() + "someMethod()` - the callsite is inlined in the 1st compilation but absent in the 2nd compilation");
        writer.writeln("`" + EditScript.INSERT_PREFIX + Direct.prefix() + "someMethod()` - the callsite is absent in the 1st compilation but there is a direct invocation in the 2nd compilation");
        writer.writeln("`" + EditScript.INSERT_PREFIX + Deleted.prefix() + "someMethod()` - the callsite is absent in the 1st compilation and it is deleted in the 2nd compilation");
    }

    private void explainInliningTree() {
        writer.writeln("the inlining tree is a tree of invoked methods in a compilation unit");
        writer.increaseIndent();
        writer.writeln("the root of the tree is the compiled root method");
        writer.writeln("the children of a direct call node are the methods that are invoked from the node at a particular bci");
        writer.writeln("the children of an indirect call may be devirtualized method calls");
        explainCallsiteKinds();
        if (writer.getOptionValues().shouldSortInliningTree()) {
            writer.writeln("the children of each node are sorted lexicographically by (bci, name)");
        } else {
            writer.writeln("the children of a node are in the order they appeared in the log");
        }
        explainIndirectCalls();
        if (!singleExperiment && writer.getOptionValues().shouldDiffCompilations()) {
            writer.writeln("the inlining trees of two paired compilations are diffed");
            writer.increaseIndent();
            writer.writeln("a callsite may be present in one compilation but not present at all in the other compilation");
            writer.writeln("a callsite may be present in the other compilation with a different callsite kind (e.g. inlined vs direct)");
            writer.writeln("a list of operations to transform the first inlining tree to the second with minimal cost is computed");
            writer.writeln("the operations include subtree deletion, subtree insertion and relabelling (changing one callsite kind to another kind)");
            writer.writeln("the diff is printed in the form of a tree");
            if (writer.getOptionValues().shouldPruneIdentities()) {
                writer.writeln("only the different parts of the trees are displayed");
            }
            writer.writeln("each node's prefix shows the type of operation it took part in, for example");
            writer.increaseIndent();
            inliningTreeOperationExamples();
            writer.decreaseIndent();
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }

    private void explainOptimizationContextTree() {
        writer.writeln("the optimization-context tree is a tree of methods and the optimizations performed in them");
        writer.increaseIndent();
        writer.writeln("each node in the tree is either an invoked method or an optimization");
        writer.writeln("the root of the tree is the compilation root method");
        writer.writeln("the children of an inlined method are");
        writer.increaseIndent();
        writer.writeln("the methods that are invoked from the node at a particular bci");
        writer.writeln("and the optimizations performed in an inlined method");
        writer.decreaseIndent();
        writer.writeln("the children of an indirect call may be devirtualized method calls");
        writer.writeln("an optimization is always a leaf node in the tree");
        explainCallsiteKinds();
        explainIndirectCalls();
        if (!singleExperiment && writer.getOptionValues().shouldDiffCompilations()) {
            writer.writeln("the optimization-context trees of two paired compilations are diffed");
            writer.increaseIndent();
            writer.writeln("the tree also includes negative decisions, i.e., a callsite may be considered for inlining but is ultimately not inlined");
            writer.writeln("a list of operations to transform the first inlining tree to the second with minimal cost is computed");
            writer.writeln("the operations include subtree deletion, subtree insertion and relabeling (changing one node to another node)");
            writer.writeln("the diff is printed in the form of a tree");
            if (writer.getOptionValues().shouldPruneIdentities()) {
                writer.writeln("only the different parts of the trees are displayed");
            }
            writer.writeln("each node's prefix shows the type of operation it took part in, for example");
            writer.increaseIndent();
            writer.writeln("`" + EditScript.IDENTITY_PREFIX + "SomeOptimization` - the optimization is present in both compilations");
            writer.writeln("`" + EditScript.DELETE_PREFIX + "SomeOptimization` - the optimization is present in the first compilation but absent in the second");
            writer.writeln("`" + EditScript.INSERT_PREFIX + "SomeOptimization` - the optimization is absent in the first compilation but present in the second");
            inliningTreeOperationExamples();
            writer.decreaseIndent();
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }
}
