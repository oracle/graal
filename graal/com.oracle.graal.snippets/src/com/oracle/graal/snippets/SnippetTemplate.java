/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.snippets;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.compiler.loop.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.phases.CanonicalizerPhase.IsImmutablePredicate;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.snippets.nodes.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * A snippet template is a graph created by parsing a snippet method and then
 * specialized by binding constants to some of the snippet arguments and applying
 * transformations such as canonicalization and loop peeling.
 */
public class SnippetTemplate {

    /**
     * Special value denoting a non-specialized argument.
     */
    public static final Object _ = new Object() {
        @Override
        public String toString() {
            return "_";
        }
    };

    /**
     * Creates a snippet template.
     *
     * @param runtime
     * @param method the snippet method to create the initial graph from
     * @param args the arguments used to specialize the graph
     */
    public static SnippetTemplate create(final RiRuntime runtime, final RiResolvedMethod method, final Object... args) {
        return Debug.scope("SnippetSpecialization", method, new Callable<SnippetTemplate>() {
            @Override
            public SnippetTemplate call() throws Exception {
                assert Modifier.isStatic(method.accessFlags()) : "snippet method must be static: " + method;
                RiSignature signature = method.signature();
                assert signature.argumentCount(false) == args.length : "snippet method expects " + signature.argumentCount(false) + " arguments, " + args.length + " given";
                StructuredGraph snippetGraph = (StructuredGraph) method.compilerStorage().get(Graph.class);

                final Map<CiConstant, CiConstant> constantArrays = new IdentityHashMap<>();
                IsImmutablePredicate immutabilityPredicate = new IsImmutablePredicate() {
                    public boolean apply(CiConstant constant) {
                        return constantArrays.containsKey(constant);
                    }
                };

                // Copy snippet graph, replacing parameters with given args in the process
                StructuredGraph snippetCopy = new StructuredGraph(snippetGraph.name, snippetGraph.method());
                IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
                replacements.put(snippetGraph.start(), snippetCopy.start());
                int localCount = 0;
                for (LocalNode local : snippetGraph.getNodes(LocalNode.class)) {
                    int index = local.index();
                    Object arg = args[index];
                    if (arg != _) {
                        CiKind kind = signature.argumentKindAt(index, false);
                        assert kind.isObject() || (arg != null && kind.toBoxedJavaClass() == arg.getClass()) :
                            "arg " + index + " for " + method + " has wrong kind: expected " + kind + ", got " + (arg == null ? "null" : arg.getClass().getSimpleName());
                        CiConstant specializationArg = CiConstant.forBoxed(kind, arg);
                        if (kind.isObject()) {
                            assert arg == null || signature.argumentTypeAt(index, method.holder()).resolve(method.holder()).toJava().isInstance(arg) :
                                String.format("argument %d is of wrong type: expected %s, got %s", index, signature.argumentTypeAt(index, method.holder()).resolve(method.holder()).toJava().getName(), arg.getClass().getName());
                            if (arg != null) {
                                if (arg.getClass().isArray()) {
                                    constantArrays.put(specializationArg, specializationArg);
                                }
                            }
                        }

                        ConstantNode argNode = ConstantNode.forCiConstant(specializationArg, runtime, snippetCopy);
                        replacements.put(local, argNode);
                    }
                    localCount++;
                }
                assert localCount == args.length : "snippet argument count mismatch";
                snippetCopy.addDuplicates(snippetGraph.getNodes(), replacements);

                Debug.dump(snippetCopy, "Before specialization");

                if (!replacements.isEmpty()) {
                    new CanonicalizerPhase(null, runtime, null, 0, immutabilityPredicate).apply(snippetCopy);
                }

                boolean exploded = false;
                do {
                    exploded = false;
                    for (Node node : snippetCopy.getNodes()) {
                        if (node instanceof ExplodeLoopNode) {
                            final ExplodeLoopNode explodeLoop = (ExplodeLoopNode) node;
                            LoopBeginNode loopBegin = explodeLoop.findLoopBegin();
                            ControlFlowGraph cfg = ControlFlowGraph.compute(snippetCopy, true, true, false, false);
                            for (Loop loop : cfg.getLoops()) {
                                if (loop.loopBegin() == loopBegin) {
                                    SuperBlock wholeLoop = LoopTransformUtil.wholeLoop(loop);
                                    Debug.dump(snippetCopy, "Before exploding loop %s", loopBegin);
                                    int peel = 0;
                                    while (!loopBegin.isDeleted()) {
                                        int mark = snippetCopy.getMark();
                                        LoopTransformUtil.peel(loop, wholeLoop);
                                        Debug.dump(snippetCopy, "After peel %d", peel);
                                        new CanonicalizerPhase(null, runtime, null, mark, immutabilityPredicate).apply(snippetCopy);
                                        peel++;
                                    }
                                    Debug.dump(snippetCopy, "After exploding loop %s", loopBegin);
                                    exploded = true;
                                    break;
                                }
                            }

                            FixedNode explodeLoopNext = explodeLoop.next();
                            explodeLoop.clearSuccessors();
                            explodeLoop.replaceAtPredecessors(explodeLoopNext);
                            explodeLoop.replaceAtUsages(null);
                            GraphUtil.killCFG(explodeLoop);
                            break;
                        }
                    }
                } while (exploded);

                // Remove all frame states from inlined snippet graph. Snippets must be atomic (i.e. free
                // of side-effects that prevent deoptimizing to a point before the snippet).
                for (Node node : snippetCopy.getNodes()) {
                    if (node instanceof StateSplit) {
                        StateSplit stateSplit = (StateSplit) node;
                        FrameState frameState = stateSplit.stateAfter();
                        assert !stateSplit.hasSideEffect() : "snippets cannot contain side-effecting node " + node + "\n    " + frameState.toString(Verbosity.Debugger);
                        if (frameState != null) {
                            stateSplit.setStateAfter(null);
                        }
                    }
                }

                new DeadCodeEliminationPhase().apply(snippetCopy);
                return new SnippetTemplate(args, snippetCopy);
            }
        });
    }

    SnippetTemplate(Object[] args, StructuredGraph graph) {
        Object[] flattenedArgs = flatten(args);
        this.graph = graph;
        this.parameters = new Object[flattenedArgs.length];

        // Find the constant nodes corresponding to the flattened positional parameters
        for (ConstantNode node : graph.getNodes().filter(ConstantNode.class)) {
            if (node.kind().isObject()) {
                CiConstant constant = node.asConstant();
                if (constant.kind.isObject() && !constant.isNull()) {
                    for (int i = 0; i < flattenedArgs.length; i++) {
                        if (flattenedArgs[i] == constant.asObject()) {
                            parameters[i] = node;
                        }
                    }
                }
            }
        }

        // Find the local nodes corresponding to the flattened positional parameters
        int localIndex = 0;
        for (int i = 0; i < flattenedArgs.length; i++) {
            if (flattenedArgs[i] == _) {
                for (LocalNode local : graph.getNodes(LocalNode.class)) {
                    if (local.index() == localIndex) {
                        assert parameters[i] == null;
                        parameters[i] = local;
                    }
                }
                localIndex++;
            } else {
                Object param = parameters[i];
                if (param == null) {
                    parameters[i] = flattenedArgs[i];
                } else {
                    assert param instanceof ConstantNode;
                    assert ((ConstantNode) param).kind().isObject();
                }
            }
        }

        nodes = new ArrayList<>(graph.getNodeCount());
        ReturnNode retNode = null;
        StartNode entryPointNode = graph.start();
        for (Node node : graph.getNodes()) {
            if (node == entryPointNode || node == entryPointNode.stateAfter()) {
                // Do nothing.
            } else {
                nodes.add(node);
                if (node instanceof ReturnNode) {
                    retNode = (ReturnNode) node;
                }
            }
        }
        this.returnNode = retNode;
    }

    /**
     * The graph built from the snippet method.
     */
    public final StructuredGraph graph;

    /**
     * The flattened positional parameters of this snippet.
     * A {@link LocalNode} element is bound to a {@link ValueNode} to be supplied during
     * instantiation, a {@link ConstantNode} is replaced by an object constant and any
     * other element denotes an input fixed during specialization.
     */
    public final Object[] parameters;

    /**
     * The return node (if any) of the snippet.
     */
    public final ReturnNode returnNode;

    /**
     * The nodes to be inlined when this specialization is instantiated.
     */
    public final ArrayList<Node> nodes;

    private IdentityHashMap<Node, Node> replacements(StructuredGraph replaceeGraph, RiRuntime runtime, Object... args) {
        Object[] flattenedArgs = flatten(args);
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        assert parameters.length >= flattenedArgs.length;
        for (int i = 0; i < flattenedArgs.length; i++) {
            Object param = parameters[i];
            Object arg = flattenedArgs[i];
            if (arg == null) {
                assert !(param instanceof ValueNode) : param;
            } else if (arg instanceof ValueNode) {
                assert param instanceof LocalNode;
                replacements.put((LocalNode) param, (ValueNode) arg);
            } else if (param instanceof ConstantNode) {
                replacements.put((ConstantNode) param, ConstantNode.forObject(arg, runtime, replaceeGraph));
            } else {
                if (!param.equals(arg)) {
                    System.exit(1);
                }
                assert param.equals(arg) : param + " != " + arg;
            }
        }
        return replacements;
    }

    /**
     * Replaces a given node with this specialized snippet.
     *
     * @param runtime
     * @param replacee the node that will be replaced
     * @param anchor the control flow replacee
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    public void instantiate(RiRuntime runtime,
                    Node replacee,
                    FixedWithNextNode anchor, Object... args) {

        // Inline the snippet nodes, replacing parameters with the given args in the process
        String name = graph.name == null ? "{copy}" : graph.name + "{copy}";
        StructuredGraph graphCopy = new StructuredGraph(name, graph.method());
        StartNode entryPointNode = graph.start();
        FixedNode firstCFGNode = entryPointNode.next();
        StructuredGraph replaceeGraph = (StructuredGraph) replacee.graph();
        IdentityHashMap<Node, Node> replacements = replacements(replaceeGraph, runtime, args);
        Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, replacements);
        Debug.dump(replaceeGraph, "After inlining snippet %s", graphCopy.method());

        // Re-wire the control flow graph around the replacee
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        anchor.replaceAtPredecessors(firstCFGNodeDuplicate);
        FixedNode next = anchor.next();
        anchor.setNext(null);

        // Replace all usages of the replacee with the value returned by the snippet
        Node returnValue = null;
        if (returnNode != null) {
            if (returnNode.result() instanceof LocalNode) {
                returnValue = replacements.get(returnNode.result());
            } else {
                returnValue = duplicates.get(returnNode.result());
            }
            assert returnValue != null || replacee.usages().isEmpty();
            replacee.replaceAtUsages(returnValue);

            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.clearInputs();
            returnDuplicate.replaceAndDelete(next);
        }

        // Remove the replacee from its graph
        replacee.clearInputs();
        replacee.replaceAtUsages(null);
        if (replacee instanceof FixedNode) {
            GraphUtil.killCFG((FixedNode) replacee);
        } else {
            replacee.safeDelete();
        }

        Debug.dump(replaceeGraph, "After lowering %s with %s", replacee, this);
    }

    /**
     * Flattens a list of objects by replacing any array in {@code args} with its elements.
     */
    private static Object[] flatten(Object... args) {
        List<Object> list = new ArrayList<>(args.length * 2);
        for (Object o : args) {
            if (o instanceof Object[]) {
                list.addAll(Arrays.asList((Object[]) o));
            } else {
                list.add(o);
            }
        }
        return list.toArray(new Object[list.size()]);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(graph.toString()).append('(');
        for (int i = 0; i < parameters.length; i++) {
            Object param = parameters[i];
            if (param instanceof ConstantNode) {
                buf.append(((ConstantNode) param).asConstant().asObject());
            } else if (param instanceof LocalNode) {
                buf.append('_');
            } else {
                buf.append(param);
            }
            if (i != parameters.length - 1) {
                buf.append(", ");
            }
        }
        return buf.append(')').toString();
    }
}
