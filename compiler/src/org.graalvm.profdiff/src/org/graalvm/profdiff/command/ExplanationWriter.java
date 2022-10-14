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
package org.graalvm.profdiff.command;

import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.matching.tree.EditScript;
import org.graalvm.profdiff.util.Writer;

/**
 * Writes an explanation of the output. Explains only what is printed, i.e., the output depends on
 * the current {@link VerbosityLevel}.
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
        VerbosityLevel verbosityLevel = writer.getVerbosityLevel();
        writer.writeln("How to read the output");
        writer.setPrefixAfterIndent("- ");
        writer.increaseIndent();
        if (onlyHotMethods) {
            writer.writeln("each method with a hot compilation is printed in a separate section below");
        } else {
            writer.writeln("each compiled method is printed in a separate section below");
        }
        if (!singleExperiment && verbosityLevel.shouldDiffCompilations()) {
            writer.writeln("hot compilation units are paired by their fraction of execution");
        }
        explainOptimizationTree();
        explainInliningTree();
        writer.writeln("this message is adapted to the " + verbosityLevel.toString().toLowerCase() + " verbosity level");
        writer.decreaseIndent();
        writer.setPrefixAfterIndent("");
    }

    private void explainOptimizationTree() {
        VerbosityLevel verbosityLevel = writer.getVerbosityLevel();
        if (!verbosityLevel.shouldPrintOptimizationTree() && !verbosityLevel.shouldDiffCompilations()) {
            return;
        }
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
        if (!singleExperiment && verbosityLevel.shouldDiffCompilations()) {
            writer.writeln("the optimization trees of two paired compilation units are diffed");
            writer.writeln("a list of operations to transform the first optimization tree to the second with minimal cost is computed");
            writer.writeln("the operations include subtree deletion and subtree insertion");
            writer.writeln("the diff is printed in the form of a tree");
            if (verbosityLevel.shouldShowOnlyDiff()) {
                writer.writeln("only the different parts of the trees are displayed at this verbosity level");
            }
            writer.writeln("each node's prefix shows the type of operation it took part in, for example");
            writer.increaseIndent();
            writer.writeln("`" + EditScript.IDENTITY_PREFIX + "SomePhase` - the node is present in both compilations");
            writer.writeln("`" + EditScript.DELETE_PREFIX + "SomePhase` - the node is present in the first compilation but absent in the second");
            writer.writeln("`" + EditScript.INSERT_PREFIX + "SomePhase` - the node is absent in the frist compilation but present in the second");
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }

    private void explainInliningTree() {
        VerbosityLevel verbosityLevel = writer.getVerbosityLevel();
        if (!verbosityLevel.shouldPrintOptimizationTree() && !verbosityLevel.shouldDiffCompilations()) {
            return;
        }
        writer.writeln("the inlining tree is a tree of methods that were inlined or considered for inlining in a compilation unit");
        writer.increaseIndent();
        writer.writeln("the root of the tree is the compiled root method");
        writer.writeln("the children of a node are the methods that are called from the node at a particular bci");
        writer.writeln("if a node is not inlined, it is displayed with the prefix `" + InliningTreeNode.NOT_INLINED_PREFIX + "`");
        if (verbosityLevel.shouldSortInliningTree()) {
            writer.writeln("the children of each node are sorted lexicographically by (bci, name)");
        } else {
            writer.writeln("the children of a node are in the order they appeared in the log");
        }
        if (!singleExperiment && verbosityLevel.shouldDiffCompilations()) {
            writer.writeln("the inlining trees of two paired compilation units are diffed");
            writer.increaseIndent();
            writer.writeln("the tree also includes negative decisions, i.e., a callsite may be considered for inlining but ultimately not inlined");
            writer.writeln("a list of operations to transform the first inlining tree to the second with minimal cost is computed");
            writer.writeln("the operations include subtree deletion, subtree insertion and relabeling (changing one node to another node)");
            writer.writeln("the diff is printed in the form of a tree");
            if (verbosityLevel.shouldShowOnlyDiff()) {
                writer.writeln("only the different parts of the trees are displayed at this verbosity level");
            }
            writer.writeln("each node's prefix shows the type of operation it took part in, for example");
            writer.increaseIndent();
            writer.writeln("`" + EditScript.RELABEL_PREFIX +
                            "(inlined -> not inlined) someMethod()` - the callsite was inlined in the first compilation unit but not inlined in the second compilation");
            writer.writeln("`" + EditScript.IDENTITY_PREFIX + "someMethod()` - the callsite was inlined in both compilations");
            writer.writeln("`" + EditScript.IDENTITY_PREFIX + InliningTreeNode.NOT_INLINED_PREFIX + "someMethod()` - the callsite was considered and not inlined in both compilations");
            writer.writeln("`" + EditScript.DELETE_PREFIX + "someMethod()` - the callsite was inlined in the first compilation unit but not considered at all in the second compilation");
            writer.writeln("`" + EditScript.INSERT_PREFIX + "someMethod()` - the callsite was not considered at all in the first compilation unit but inlined in the second compilation");
            writer.writeln("`" + EditScript.DELETE_PREFIX + InliningTreeNode.NOT_INLINED_PREFIX +
                            "someMethod()` - the callsite was not inlined in the first compilation unit and not considered at all in the second compilation");
            writer.writeln("`" + EditScript.INSERT_PREFIX + InliningTreeNode.NOT_INLINED_PREFIX +
                            "(not inlined) someMethod()` - the callsite was not considered at all in the first compilation unit and not inlined in the second compilation");
            writer.decreaseIndent();
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }
}
