/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.parser;

import java.math.*;
import java.util.*;
import java.util.regex.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.call.*;
import com.oracle.truffle.ruby.nodes.cast.*;
import com.oracle.truffle.ruby.nodes.constants.*;
import com.oracle.truffle.ruby.nodes.control.*;
import com.oracle.truffle.ruby.nodes.core.*;
import com.oracle.truffle.ruby.nodes.literal.*;
import com.oracle.truffle.ruby.nodes.literal.array.*;
import com.oracle.truffle.ruby.nodes.methods.*;
import com.oracle.truffle.ruby.nodes.methods.locals.*;
import com.oracle.truffle.ruby.nodes.objects.*;
import com.oracle.truffle.ruby.nodes.objects.instancevariables.*;
import com.oracle.truffle.ruby.nodes.yield.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.range.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * A JRuby parser node visitor which translates JRuby AST nodes into our Ruby nodes, implementing a
 * Ruby parser. Therefore there is some namespace contention here! We make all references to JRuby
 * explicit. This is the only place though - it doesn't leak out elsewhere.
 */
public class Translator implements org.jrubyparser.NodeVisitor {

    protected final Translator parent;

    protected final RubyContext context;
    protected final TranslatorEnvironment environment;
    protected final Source source;
    protected final RubyNodeInstrumenter instrumenter;

    private boolean translatingForStatement = false;

    private static final Map<Class, String> nodeDefinedNames = new HashMap<>();

    static {
        nodeDefinedNames.put(org.jrubyparser.ast.SelfNode.class, "self");
        nodeDefinedNames.put(org.jrubyparser.ast.NilNode.class, "nil");
        nodeDefinedNames.put(org.jrubyparser.ast.TrueNode.class, "true");
        nodeDefinedNames.put(org.jrubyparser.ast.FalseNode.class, "false");
        nodeDefinedNames.put(org.jrubyparser.ast.LocalAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.DAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.GlobalAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.InstAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.ClassVarAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.OpAsgnAndNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.OpAsgnOrNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.OpAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.OpElementAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.MultipleAsgnNode.class, "assignment");
        nodeDefinedNames.put(org.jrubyparser.ast.GlobalVarNode.class, "global-variable");
        nodeDefinedNames.put(org.jrubyparser.ast.StrNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.DStrNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.FixnumNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.BignumNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.FloatNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.RegexpNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.DRegexpNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.ArrayNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.HashNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.SymbolNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.DotNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.NotNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.AndNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.OrNode.class, "expression");
        nodeDefinedNames.put(org.jrubyparser.ast.LocalVarNode.class, "local-variable");
        nodeDefinedNames.put(org.jrubyparser.ast.DVarNode.class, "local-variable");
    }

    /**
     * Global variables which in common usage have frame local semantics.
     */
    public static final Set<String> FRAME_LOCAL_GLOBAL_VARIABLES = new HashSet<>(Arrays.asList("$_"));

    public Translator(RubyContext context, Translator parent, TranslatorEnvironment environment, Source source) {
        this.context = context;
        this.parent = parent;
        this.environment = environment;
        this.source = source;
        this.instrumenter = environment.getNodeInstrumenter();
    }

    @Override
    public Object visitAliasNode(org.jrubyparser.ast.AliasNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final org.jrubyparser.ast.LiteralNode oldName = (org.jrubyparser.ast.LiteralNode) node.getOldName();
        final org.jrubyparser.ast.LiteralNode newName = (org.jrubyparser.ast.LiteralNode) node.getNewName();

        final ClassNode classNode = new ClassNode(context, sourceSection, new SelfNode(context, sourceSection));

        return new AliasNode(context, sourceSection, classNode, newName.getName(), oldName.getName());
    }

    @Override
    public Object visitAndNode(org.jrubyparser.ast.AndNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode x;

        if (node.getFirst() == null) {
            x = new NilNode(context, sourceSection);
        } else {
            x = (RubyNode) node.getFirst().accept(this);
        }

        RubyNode y;

        if (node.getSecond() == null) {
            y = new NilNode(context, sourceSection);
        } else {
            y = (RubyNode) node.getSecond().accept(this);
        }

        return AndNodeFactory.create(context, sourceSection, x, y);
    }

    @Override
    public Object visitArgsCatNode(org.jrubyparser.ast.ArgsCatNode node) {
        final List<org.jrubyparser.ast.Node> nodes = new ArrayList<>();
        collectArgsCatNodes(nodes, node);

        final List<RubyNode> translatedNodes = new ArrayList<>();

        for (org.jrubyparser.ast.Node catNode : nodes) {
            translatedNodes.add((RubyNode) catNode.accept(this));
        }

        return new ArrayConcatNode(context, translate(node.getPosition()), translatedNodes.toArray(new RubyNode[translatedNodes.size()]));
    }

    // ArgsCatNodes can be nested - this collects them into a flat list of children
    private void collectArgsCatNodes(List<org.jrubyparser.ast.Node> nodes, org.jrubyparser.ast.ArgsCatNode node) {
        if (node.getFirst() instanceof org.jrubyparser.ast.ArgsCatNode) {
            collectArgsCatNodes(nodes, (org.jrubyparser.ast.ArgsCatNode) node.getFirst());
        } else {
            nodes.add(node.getFirst());
        }

        if (node.getSecond() instanceof org.jrubyparser.ast.ArgsCatNode) {
            collectArgsCatNodes(nodes, (org.jrubyparser.ast.ArgsCatNode) node.getSecond());
        } else {
            nodes.add(node.getSecond());
        }
    }

    @Override
    public Object visitArgsNode(org.jrubyparser.ast.ArgsNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitArgsPushNode(org.jrubyparser.ast.ArgsPushNode node) {
        return new ArrayPushNode(context, translate(node.getPosition()), (RubyNode) node.getFirstNode().accept(this), (RubyNode) node.getSecondNode().accept(this));
    }

    @Override
    public Object visitArrayNode(org.jrubyparser.ast.ArrayNode node) {
        final List<org.jrubyparser.ast.Node> values = node.childNodes();

        final RubyNode[] translatedValues = new RubyNode[values.size()];

        for (int n = 0; n < values.size(); n++) {
            translatedValues[n] = (RubyNode) values.get(n).accept(this);
        }

        return new UninitialisedArrayLiteralNode(context, translate(node.getPosition()), translatedValues);
    }

    @Override
    public Object visitAttrAssignNode(org.jrubyparser.ast.AttrAssignNode node) {
        return visitAttrAssignNodeExtraArgument(node, null);
    }

    /**
     * See translateDummyAssignment to understand what this is for.
     */
    public RubyNode visitAttrAssignNodeExtraArgument(org.jrubyparser.ast.AttrAssignNode node, RubyNode extraArgument) {
        final org.jrubyparser.ast.CallNode callNode = new org.jrubyparser.ast.CallNode(node.getPosition(), node.getReceiver(), node.getName(), node.getArgs());
        return visitCallNodeExtraArgument(callNode, extraArgument);
    }

    @Override
    public Object visitBackRefNode(org.jrubyparser.ast.BackRefNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitBeginNode(org.jrubyparser.ast.BeginNode node) {
        return node.getBody().accept(this);
    }

    @Override
    public Object visitBignumNode(org.jrubyparser.ast.BignumNode node) {
        return new BignumLiteralNode(context, translate(node.getPosition()), node.getValue());
    }

    @Override
    public Object visitBlockArg18Node(org.jrubyparser.ast.BlockArg18Node node) {
        return unimplemented(node);
    }

    @Override
    public Object visitBlockArgNode(org.jrubyparser.ast.BlockArgNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitBlockNode(org.jrubyparser.ast.BlockNode node) {
        final List<org.jrubyparser.ast.Node> children = node.childNodes();

        final List<RubyNode> translatedChildren = new ArrayList<>();

        for (int n = 0; n < children.size(); n++) {
            final RubyNode translatedChild = (RubyNode) children.get(n).accept(this);

            if (!(translatedChild instanceof DeadNode)) {
                translatedChildren.add(translatedChild);
            }
        }

        if (translatedChildren.size() == 1) {
            return translatedChildren.get(0);
        } else {
            return new SequenceNode(context, translate(node.getPosition()), translatedChildren.toArray(new RubyNode[translatedChildren.size()]));
        }
    }

    @Override
    public Object visitBlockPassNode(org.jrubyparser.ast.BlockPassNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitBreakNode(org.jrubyparser.ast.BreakNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode resultNode;

        if (node.getValueNode() == null) {
            resultNode = new NilNode(context, sourceSection);
        } else {
            resultNode = (RubyNode) node.getValueNode().accept(this);
        }

        return new BreakNode(context, sourceSection, resultNode);
    }

    @Override
    public Object visitCallNode(org.jrubyparser.ast.CallNode node) {
        return visitCallNodeExtraArgument(node, null);
    }

    /**
     * See translateDummyAssignment to understand what this is for.
     */
    public RubyNode visitCallNodeExtraArgument(org.jrubyparser.ast.CallNode node, RubyNode extraArgument) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode receiverTranslated = (RubyNode) node.getReceiver().accept(this);

        org.jrubyparser.ast.Node args = node.getArgs();
        org.jrubyparser.ast.Node block = node.getIter();

        if (block == null && args instanceof org.jrubyparser.ast.IterNode) {
            final org.jrubyparser.ast.Node temp = args;
            args = block;
            block = temp;
        }

        final ArgumentsAndBlockTranslation argumentsAndBlock = translateArgumentsAndBlock(sourceSection, block, args, extraArgument);

        RubyNode translated = new CallNode(context, sourceSection, node.getName(), receiverTranslated, argumentsAndBlock.getBlock(), argumentsAndBlock.isSplatted(), argumentsAndBlock.getArguments());

        return instrumenter.instrumentAsCall(translated, node.getName());
    }

    protected class ArgumentsAndBlockTranslation {

        private final RubyNode block;
        private final RubyNode[] arguments;
        private final boolean isSplatted;

        public ArgumentsAndBlockTranslation(RubyNode block, RubyNode[] arguments, boolean isSplatted) {
            super();
            this.block = block;
            this.arguments = arguments;
            this.isSplatted = isSplatted;
        }

        public RubyNode getBlock() {
            return block;
        }

        public RubyNode[] getArguments() {
            return arguments;
        }

        public boolean isSplatted() {
            return isSplatted;
        }

    }

    protected ArgumentsAndBlockTranslation translateArgumentsAndBlock(SourceSection sourceSection, org.jrubyparser.ast.Node iterNode, org.jrubyparser.ast.Node argsNode, RubyNode extraArgument) {
        assert !(argsNode instanceof org.jrubyparser.ast.IterNode);

        final List<org.jrubyparser.ast.Node> arguments = new ArrayList<>();
        org.jrubyparser.ast.Node blockPassNode = null;

        boolean isSplatted = false;

        if (argsNode instanceof org.jrubyparser.ast.ListNode) {
            arguments.addAll(((org.jrubyparser.ast.ListNode) argsNode).childNodes());
        } else if (argsNode instanceof org.jrubyparser.ast.BlockPassNode) {
            final org.jrubyparser.ast.BlockPassNode blockPass = (org.jrubyparser.ast.BlockPassNode) argsNode;

            final org.jrubyparser.ast.Node blockPassArgs = blockPass.getArgs();

            if (blockPassArgs instanceof org.jrubyparser.ast.ListNode) {
                arguments.addAll(((org.jrubyparser.ast.ListNode) blockPassArgs).childNodes());
            } else if (blockPassArgs instanceof org.jrubyparser.ast.ArgsCatNode) {
                arguments.add(blockPassArgs);
            } else if (blockPassArgs != null) {
                throw new UnsupportedOperationException("Don't know how to block pass " + blockPassArgs);
            }

            blockPassNode = blockPass.getBody();
        } else if (argsNode instanceof org.jrubyparser.ast.SplatNode) {
            isSplatted = true;
            arguments.add(argsNode);
        } else if (argsNode instanceof org.jrubyparser.ast.ArgsCatNode) {
            isSplatted = true;
            arguments.add(argsNode);
        } else if (argsNode != null) {
            isSplatted = true;
            arguments.add(argsNode);
        }

        RubyNode blockTranslated;

        if (blockPassNode != null && iterNode != null) {
            throw new UnsupportedOperationException("Don't know how to pass both an block and a block-pass argument");
        } else if (iterNode != null) {
            blockTranslated = (BlockDefinitionNode) iterNode.accept(this);
        } else if (blockPassNode != null) {
            blockTranslated = ProcCastNodeFactory.create(context, sourceSection, (RubyNode) blockPassNode.accept(this));
        } else {
            blockTranslated = null;
        }

        final List<RubyNode> argumentsTranslated = new ArrayList<>();

        for (org.jrubyparser.ast.Node argument : arguments) {
            argumentsTranslated.add((RubyNode) argument.accept(this));
        }

        if (extraArgument != null) {
            argumentsTranslated.add(extraArgument);
        }

        final RubyNode[] argumentsTranslatedArray = argumentsTranslated.toArray(new RubyNode[argumentsTranslated.size()]);

        return new ArgumentsAndBlockTranslation(blockTranslated, argumentsTranslatedArray, isSplatted);
    }

    @Override
    public Object visitCaseNode(org.jrubyparser.ast.CaseNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode elseNode;

        if (node.getElse() != null) {
            elseNode = (RubyNode) node.getElse().accept(this);
        } else {
            elseNode = new NilNode(context, sourceSection);
        }

        /*
         * There are two sorts of case - one compares a list of expressions against a value, the
         * other just checks a list of expressions for truth.
         */

        if (node.getCase() != null) {
            // Evaluate the case expression and store it in a local

            final String tempName = environment.allocateLocalTemp();

            final RubyNode readTemp = environment.findLocalVarNode(tempName, sourceSection);

            final RubyNode assignTemp = ((ReadNode) readTemp).makeWriteNode((RubyNode) node.getCase().accept(this));

            /*
             * Build an if expression from the whens and else. Work backwards because the first if
             * contains all the others in its else clause.
             */

            for (int n = node.getCases().size() - 1; n >= 0; n--) {
                final org.jrubyparser.ast.WhenNode when = (org.jrubyparser.ast.WhenNode) node.getCases().get(n);

                // Make a condition from the one or more expressions combined in an or expression

                final List<org.jrubyparser.ast.Node> expressions;

                if (when.getExpression() instanceof org.jrubyparser.ast.ListNode) {
                    expressions = ((org.jrubyparser.ast.ListNode) when.getExpression()).childNodes();
                } else {
                    expressions = Arrays.asList(when.getExpression());
                }

                final List<RubyNode> comparisons = new ArrayList<>();

                for (org.jrubyparser.ast.Node expressionNode : expressions) {
                    final RubyNode rubyExpression = (RubyNode) expressionNode.accept(this);

                    final CallNode comparison = new CallNode(context, sourceSection, "===", rubyExpression, null, false, new RubyNode[]{environment.findLocalVarNode(tempName, sourceSection)});

                    comparisons.add(comparison);
                }

                RubyNode conditionNode = comparisons.get(comparisons.size() - 1);

                // As with the if nodes, we work backwards to make it left associative

                for (int i = comparisons.size() - 2; i >= 0; i--) {
                    conditionNode = OrNodeFactory.create(context, sourceSection, comparisons.get(i), conditionNode);
                }

                // Create the if node

                final BooleanCastNode conditionCastNode = BooleanCastNodeFactory.create(context, sourceSection, conditionNode);

                RubyNode thenNode;

                if (when.getBody() == null) {
                    thenNode = new NilNode(context, sourceSection);
                } else {
                    thenNode = (RubyNode) when.getBody().accept(this);
                }

                final IfNode ifNode = new IfNode(context, sourceSection, conditionCastNode, thenNode, elseNode);

                // This if becomes the else for the next if

                elseNode = ifNode;
            }

            final RubyNode ifNode = elseNode;

            // A top-level block assigns the temp then runs the if

            return new SequenceNode(context, sourceSection, assignTemp, ifNode);
        } else {
            for (int n = node.getCases().size() - 1; n >= 0; n--) {
                final org.jrubyparser.ast.WhenNode when = (org.jrubyparser.ast.WhenNode) node.getCases().get(n);

                // Make a condition from the one or more expressions combined in an or expression

                final List<org.jrubyparser.ast.Node> expressions;

                if (when.getExpression() instanceof org.jrubyparser.ast.ListNode) {
                    expressions = ((org.jrubyparser.ast.ListNode) when.getExpression()).childNodes();
                } else {
                    expressions = Arrays.asList(when.getExpression());
                }

                final List<RubyNode> tests = new ArrayList<>();

                for (org.jrubyparser.ast.Node expressionNode : expressions) {
                    final RubyNode rubyExpression = (RubyNode) expressionNode.accept(this);
                    tests.add(rubyExpression);
                }

                RubyNode conditionNode = tests.get(tests.size() - 1);

                // As with the if nodes, we work backwards to make it left associative

                for (int i = tests.size() - 2; i >= 0; i--) {
                    conditionNode = OrNodeFactory.create(context, sourceSection, tests.get(i), conditionNode);
                }

                // Create the if node

                final BooleanCastNode conditionCastNode = BooleanCastNodeFactory.create(context, sourceSection, conditionNode);

                final RubyNode thenNode = (RubyNode) when.getBody().accept(this);

                final IfNode ifNode = new IfNode(context, sourceSection, conditionCastNode, thenNode, elseNode);

                // This if becomes the else for the next if

                elseNode = ifNode;
            }

            return elseNode;
        }
    }

    @Override
    public Object visitClassNode(org.jrubyparser.ast.ClassNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final String name = node.getCPath().getName();

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParser(), environment.getParser().allocateReturnID(), true, true,
                        new UniqueMethodIdentifier());
        final ModuleTranslator classTranslator = new ModuleTranslator(context, this, newEnvironment, source);

        final MethodDefinitionNode definitionMethod = classTranslator.compileClassNode(node.getPosition(), node.getCPath().getName(), node.getBody());

        /*
         * See my note in visitDefnNode about where the class gets defined - the same applies here.
         */

        RubyNode superClass;

        if (node.getSuper() != null) {
            superClass = (RubyNode) node.getSuper().accept(this);
        } else {
            superClass = new ObjectLiteralNode(context, sourceSection, context.getCoreLibrary().getObjectClass());
        }

        final DefineOrGetClassNode defineOrGetClass = new DefineOrGetClassNode(context, sourceSection, name, getModuleToDefineModulesIn(sourceSection), superClass);

        return new OpenModuleNode(context, sourceSection, defineOrGetClass, definitionMethod);
    }

    protected RubyNode getModuleToDefineModulesIn(SourceSection sourceSection) {
        return new ClassNode(context, sourceSection, new SelfNode(context, sourceSection));
    }

    @Override
    public Object visitClassVarAsgnNode(org.jrubyparser.ast.ClassVarAsgnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode receiver = new ClassNode(context, sourceSection, new SelfNode(context, sourceSection));

        final RubyNode rhs = (RubyNode) node.getValue().accept(this);

        return new WriteClassVariableNode(context, sourceSection, node.getName(), receiver, rhs);
    }

    @Override
    public Object visitClassVarDeclNode(org.jrubyparser.ast.ClassVarDeclNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitClassVarNode(org.jrubyparser.ast.ClassVarNode node) {
        final SourceSection sourceSection = translate(node.getPosition());
        return new ReadClassVariableNode(context, sourceSection, node.getName(), new SelfNode(context, sourceSection));
    }

    @Override
    public Object visitColon2Node(org.jrubyparser.ast.Colon2Node node) {
        final RubyNode lhs = (RubyNode) node.getLeftNode().accept(this);

        return new UninitializedReadConstantNode(context, translate(node.getPosition()), node.getName(), lhs);
    }

    @Override
    public Object visitColon3Node(org.jrubyparser.ast.Colon3Node node) {
        // Colon3 means the root namespace, as in ::Foo

        final SourceSection sourceSection = translate(node.getPosition());

        final ObjectLiteralNode root = new ObjectLiteralNode(context, sourceSection, context.getCoreLibrary().getMainObject());

        return new UninitializedReadConstantNode(context, sourceSection, node.getName(), root);
    }

    @Override
    public Object visitConstDeclNode(org.jrubyparser.ast.ConstDeclNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final ClassNode classNode = new ClassNode(context, sourceSection, new SelfNode(context, sourceSection));

        return new WriteConstantNode(context, sourceSection, node.getName(), classNode, (RubyNode) node.getValue().accept(this));
    }

    @Override
    public Object visitConstNode(org.jrubyparser.ast.ConstNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        return new UninitializedReadConstantNode(context, sourceSection, node.getName(), new SelfNode(context, sourceSection));
    }

    @Override
    public Object visitDAsgnNode(org.jrubyparser.ast.DAsgnNode node) {
        return new org.jrubyparser.ast.LocalAsgnNode(node.getPosition(), node.getName(), node.getDepth(), node.getValue()).accept(this);
    }

    @Override
    public Object visitDRegxNode(org.jrubyparser.ast.DRegexpNode node) {
        SourceSection sourceSection = translate(node.getPosition());

        final RubyNode stringNode = translateInterpolatedString(sourceSection, node.childNodes());

        return StringToRegexpNodeFactory.create(context, sourceSection, stringNode);
    }

    @Override
    public Object visitDStrNode(org.jrubyparser.ast.DStrNode node) {
        return translateInterpolatedString(translate(node.getPosition()), node.childNodes());
    }

    @Override
    public Object visitDSymbolNode(org.jrubyparser.ast.DSymbolNode node) {
        SourceSection sourceSection = translate(node.getPosition());

        final RubyNode stringNode = translateInterpolatedString(sourceSection, node.childNodes());

        return StringToSymbolNodeFactory.create(context, sourceSection, stringNode);
    }

    private RubyNode translateInterpolatedString(SourceSection sourceSection, List<org.jrubyparser.ast.Node> childNodes) {
        final List<RubyNode> children = new ArrayList<>();

        for (org.jrubyparser.ast.Node child : childNodes) {
            children.add((RubyNode) child.accept(this));
        }

        return new InterpolatedStringNode(context, sourceSection, children.toArray(new RubyNode[children.size()]));
    }

    @Override
    public Object visitDVarNode(org.jrubyparser.ast.DVarNode node) {
        RubyNode readNode = environment.findLocalVarNode(node.getName(), translate(node.getPosition()));

        if (readNode == null) {
            context.implementationMessage("can't find variable %s at %s, using noop", node.getName(), node.getPosition());
            readNode = new NilNode(context, translate(node.getPosition()));
        }

        return readNode;
    }

    @Override
    public Object visitDXStrNode(org.jrubyparser.ast.DXStrNode node) {
        SourceSection sourceSection = translate(node.getPosition());

        final RubyNode string = translateInterpolatedString(sourceSection, node.childNodes());

        return new SystemNode(context, sourceSection, string);
    }

    @Override
    public Object visitDefinedNode(org.jrubyparser.ast.DefinedNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        org.jrubyparser.ast.Node expressionNode = node.getExpression();

        while (expressionNode instanceof org.jrubyparser.ast.NewlineNode) {
            expressionNode = ((org.jrubyparser.ast.NewlineNode) expressionNode).getNextNode();
        }

        final String name = nodeDefinedNames.get(expressionNode.getClass());

        if (name != null) {
            final StringLiteralNode literal = new StringLiteralNode(context, sourceSection, name);
            return literal;
        }

        return new DefinedNode(context, sourceSection, (RubyNode) node.getExpression().accept(this));
    }

    @Override
    public Object visitDefnNode(org.jrubyparser.ast.DefnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());
        final ClassNode classNode = new ClassNode(context, sourceSection, new SelfNode(context, sourceSection));
        return translateMethodDefinition(sourceSection, classNode, node.getName(), node.getArgs(), node.getBody());
    }

    @Override
    public Object visitDefsNode(org.jrubyparser.ast.DefsNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode objectNode = (RubyNode) node.getReceiver().accept(this);

        final SingletonClassNode singletonClassNode = new SingletonClassNode(context, sourceSection, objectNode);

        return translateMethodDefinition(sourceSection, singletonClassNode, node.getName(), node.getArgs(), node.getBody());
    }

    private RubyNode translateMethodDefinition(SourceSection sourceSection, RubyNode classNode, String methodName, org.jrubyparser.ast.ArgsNode argsNode, org.jrubyparser.ast.Node bodyNode) {
        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParser(), environment.getParser().allocateReturnID(), true, true,
                        new UniqueMethodIdentifier());

        // ownScopeForAssignments is the same for the defined method as the current one.

        final MethodTranslator methodCompiler = new MethodTranslator(context, this, newEnvironment, false, source);

        final MethodDefinitionNode functionExprNode = methodCompiler.compileFunctionNode(sourceSection, methodName, argsNode, bodyNode);

        /*
         * In the top-level, methods are defined in the class of the main object. This is
         * counter-intuitive - I would have expected them to be defined in the singleton class.
         * Apparently this is a design decision to make top-level methods sort of global.
         * 
         * http://stackoverflow.com/questions/1761148/where-are-methods-defined-at-the-ruby-top-level
         */

        return new AddMethodNode(context, sourceSection, classNode, functionExprNode);
    }

    @Override
    public Object visitDotNode(org.jrubyparser.ast.DotNode node) {
        final RubyNode begin = (RubyNode) node.getBegin().accept(this);
        final RubyNode end = (RubyNode) node.getEnd().accept(this);
        SourceSection sourceSection = translate(node.getPosition());

        if (begin instanceof FixnumLiteralNode && end instanceof FixnumLiteralNode) {
            final int beginValue = ((FixnumLiteralNode) begin).getValue();
            final int endValue = ((FixnumLiteralNode) end).getValue();

            return new ObjectLiteralNode(context, sourceSection, new FixnumRange(context.getCoreLibrary().getRangeClass(), beginValue, endValue, node.isExclusive()));
        }
        // See RangeNode for why there is a node specifically for creating this one type
        return RangeLiteralNodeFactory.create(context, sourceSection, node.isExclusive(), begin, end);
    }

    @Override
    public Object visitEncodingNode(org.jrubyparser.ast.EncodingNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitEnsureNode(org.jrubyparser.ast.EnsureNode node) {
        final RubyNode tryPart = (RubyNode) node.getBody().accept(this);
        final RubyNode ensurePart = (RubyNode) node.getEnsure().accept(this);
        return new EnsureNode(context, translate(node.getPosition()), tryPart, ensurePart);
    }

    @Override
    public Object visitEvStrNode(org.jrubyparser.ast.EvStrNode node) {
        return node.getBody().accept(this);
    }

    @Override
    public Object visitFCallNode(org.jrubyparser.ast.FCallNode node) {
        final org.jrubyparser.ast.Node receiver = new org.jrubyparser.ast.SelfNode(node.getPosition());
        final org.jrubyparser.ast.Node callNode = new org.jrubyparser.ast.CallNode(node.getPosition(), receiver, node.getName(), node.getArgs(), node.getIter());

        return callNode.accept(this);
    }

    @Override
    public Object visitFalseNode(org.jrubyparser.ast.FalseNode node) {
        return new BooleanLiteralNode(context, translate(node.getPosition()), false);
    }

    @Override
    public Object visitFixnumNode(org.jrubyparser.ast.FixnumNode node) {
        final long value = node.getValue();

        if (value >= RubyFixnum.MIN_VALUE && value <= RubyFixnum.MAX_VALUE) {
            return new FixnumLiteralNode(context, translate(node.getPosition()), (int) value);
        }
        return new BignumLiteralNode(context, translate(node.getPosition()), BigInteger.valueOf(value));
    }

    @Override
    public Object visitFlipNode(org.jrubyparser.ast.FlipNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode begin = (RubyNode) node.getBegin().accept(this);
        final RubyNode end = (RubyNode) node.getEnd().accept(this);

        final BooleanCastNode beginCast = BooleanCastNodeFactory.create(context, sourceSection, begin);
        final BooleanCastNode endCast = BooleanCastNodeFactory.create(context, sourceSection, end);
        final FlipFlopStateNode stateNode = createFlipFlopState(sourceSection, 0);

        return new FlipFlopNode(context, sourceSection, beginCast, endCast, stateNode, node.isExclusive());
    }

    protected FlipFlopStateNode createFlipFlopState(SourceSection sourceSection, int depth) {
        final FrameSlot frameSlot = environment.declareVar(environment.allocateLocalTemp());
        environment.getFlipFlopStates().add(frameSlot);

        if (depth == 0) {
            return new LocalFlipFlopStateNode(sourceSection, frameSlot);
        } else {
            return new LevelFlipFlopStateNode(sourceSection, depth, frameSlot);
        }
    }

    @Override
    public Object visitFloatNode(org.jrubyparser.ast.FloatNode node) {
        return new FloatLiteralNode(context, translate(node.getPosition()), node.getValue());
    }

    @Override
    public Object visitForNode(org.jrubyparser.ast.ForNode node) {
        /**
         * A Ruby for-loop, such as:
         * 
         * <pre>
         * for x in y
         *     z = x
         *     puts z
         * end
         * </pre>
         * 
         * naively desugars to:
         * 
         * <pre>
         * y.each do |x|
         *     z = x
         *     puts z
         * end
         * </pre>
         * 
         * The main difference is that z is always going to be local to the scope outside the block,
         * so it's a bit more like:
         * 
         * <pre>
         * z = nil unless z is already defined
         * y.each do |x|
         *    z = x
         *    puts x
         * end
         * </pre>
         * 
         * Which forces z to be defined in the correct scope. The parser already correctly calls z a
         * local, but then that causes us a problem as if we're going to translate to a block we
         * need a formal parameter - not a local variable. My solution to this is to add a
         * temporary:
         * 
         * <pre>
         * z = nil unless z is already defined
         * y.each do |temp|
         *    x = temp
         *    z = x
         *    puts x
         * end
         * </pre>
         * 
         * We also need that temp because the expression assigned in the for could be index
         * assignment, multiple assignment, or whatever:
         * 
         * <pre>
         * for x[0] in y
         *     z = x[0]
         *     puts z
         * end
         * </pre>
         * 
         * http://blog.grayproductions.net/articles/the_evils_of_the_for_loop
         * http://stackoverflow.com/questions/3294509/for-vs-each-in-ruby
         * 
         * The other complication is that normal locals should be defined in the enclosing scope,
         * unlike a normal block. We do that by setting a flag on this translator object when we
         * visit the new iter, translatingForStatement, which we recognise when visiting an iter
         * node.
         * 
         * Finally, note that JRuby's terminology is strange here. Normally 'iter' is a different
         * term for a block. Here, JRuby calls the object being iterated over the 'iter'.
         */

        final String temp = environment.allocateLocalTemp();

        final org.jrubyparser.ast.Node receiver = node.getIter();

        /*
         * The x in for x in ... is like the nodes in multiple assignment - it has a dummy RHS which
         * we need to replace with our temp. Just like in multiple assignment this is really awkward
         * with the JRuby AST.
         */

        final org.jrubyparser.ast.LocalVarNode readTemp = new org.jrubyparser.ast.LocalVarNode(node.getPosition(), 0, temp);
        final org.jrubyparser.ast.Node forVar = node.getVar();
        final org.jrubyparser.ast.Node assignTemp = setRHS(forVar, readTemp);

        final org.jrubyparser.ast.BlockNode bodyWithTempAssign = new org.jrubyparser.ast.BlockNode(node.getPosition());
        bodyWithTempAssign.add(assignTemp);
        bodyWithTempAssign.add(node.getBody());

        final org.jrubyparser.ast.ArgumentNode blockVar = new org.jrubyparser.ast.ArgumentNode(node.getPosition(), temp);
        final org.jrubyparser.ast.ListNode blockArgsPre = new org.jrubyparser.ast.ListNode(node.getPosition(), blockVar);
        final org.jrubyparser.ast.ArgsNode blockArgs = new org.jrubyparser.ast.ArgsNode(node.getPosition(), blockArgsPre, null, null, null, null, null, null);
        final org.jrubyparser.ast.IterNode block = new org.jrubyparser.ast.IterNode(node.getPosition(), blockArgs, node.getScope(), bodyWithTempAssign);

        final org.jrubyparser.ast.CallNode callNode = new org.jrubyparser.ast.CallNode(node.getPosition(), receiver, "each", null, block);

        translatingForStatement = true;
        final RubyNode translated = (RubyNode) callNode.accept(this);
        translatingForStatement = false;

        return translated;
    }

    private static org.jrubyparser.ast.Node setRHS(org.jrubyparser.ast.Node node, org.jrubyparser.ast.Node rhs) {
        if (node instanceof org.jrubyparser.ast.LocalAsgnNode) {
            final org.jrubyparser.ast.LocalAsgnNode localAsgnNode = (org.jrubyparser.ast.LocalAsgnNode) node;
            return new org.jrubyparser.ast.LocalAsgnNode(node.getPosition(), localAsgnNode.getName(), 0, rhs);
        } else if (node instanceof org.jrubyparser.ast.DAsgnNode) {
            final org.jrubyparser.ast.DAsgnNode dAsgnNode = (org.jrubyparser.ast.DAsgnNode) node;
            return new org.jrubyparser.ast.DAsgnNode(node.getPosition(), dAsgnNode.getName(), 0, rhs);
        } else if (node instanceof org.jrubyparser.ast.MultipleAsgnNode) {
            final org.jrubyparser.ast.MultipleAsgnNode multAsgnNode = (org.jrubyparser.ast.MultipleAsgnNode) node;
            return new org.jrubyparser.ast.MultipleAsgnNode(node.getPosition(), multAsgnNode.getPre(), multAsgnNode.getRest(), multAsgnNode.getPost());
        } else if (node instanceof org.jrubyparser.ast.InstAsgnNode) {
            final org.jrubyparser.ast.InstAsgnNode instAsgnNode = (org.jrubyparser.ast.InstAsgnNode) node;
            return new org.jrubyparser.ast.InstAsgnNode(node.getPosition(), instAsgnNode.getName(), rhs);
        } else if (node instanceof org.jrubyparser.ast.ClassVarAsgnNode) {
            final org.jrubyparser.ast.ClassVarAsgnNode instAsgnNode = (org.jrubyparser.ast.ClassVarAsgnNode) node;
            return new org.jrubyparser.ast.ClassVarAsgnNode(node.getPosition(), instAsgnNode.getName(), rhs);
        } else if (node instanceof org.jrubyparser.ast.ConstDeclNode) {
            final org.jrubyparser.ast.ConstDeclNode constDeclNode = (org.jrubyparser.ast.ConstDeclNode) node;
            return new org.jrubyparser.ast.ConstDeclNode(node.getPosition(), constDeclNode.getName(), (org.jrubyparser.ast.INameNode) constDeclNode.getConstNode(), rhs);
        } else {
            throw new UnsupportedOperationException("Don't know how to set the RHS of a " + node.getClass().getName());
        }
    }

    @Override
    public Object visitGlobalAsgnNode(org.jrubyparser.ast.GlobalAsgnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final String name = "$" + node.getName();
        final RubyNode rhs = (RubyNode) node.getValue().accept(this);

        if (FRAME_LOCAL_GLOBAL_VARIABLES.contains(name)) {
            context.implementationMessage("Assigning to frame local global variables not implemented at %s", node.getPosition());

            return rhs;
        } else {
            final ObjectLiteralNode globalVariablesObjectNode = new ObjectLiteralNode(context, sourceSection, context.getCoreLibrary().getGlobalVariablesObject());

            return new UninitializedWriteInstanceVariableNode(context, sourceSection, name, globalVariablesObjectNode, rhs);
        }
    }

    @Override
    public Object visitGlobalVarNode(org.jrubyparser.ast.GlobalVarNode node) {
        final String name = "$" + node.getName();
        final SourceSection sourceSection = translate(node.getPosition());

        if (FRAME_LOCAL_GLOBAL_VARIABLES.contains(name)) {
            // Assignment is implicit for many of these, so we need to declare when we use

            environment.declareVar(name);

            final RubyNode readNode = environment.findLocalVarNode(name, sourceSection);

            return readNode;
        } else {
            final ObjectLiteralNode globalVariablesObjectNode = new ObjectLiteralNode(context, sourceSection, context.getCoreLibrary().getGlobalVariablesObject());

            return new UninitializedReadInstanceVariableNode(context, sourceSection, name, globalVariablesObjectNode);
        }
    }

    @Override
    public Object visitHashNode(org.jrubyparser.ast.HashNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final List<RubyNode> keys = new ArrayList<>();
        final List<RubyNode> values = new ArrayList<>();

        final org.jrubyparser.ast.ListNode entries = node.getListNode();

        assert entries.size() % 2 == 0;

        for (int n = 0; n < entries.size(); n += 2) {
            if (entries.get(n) == null) {
                final NilNode nilNode = new NilNode(context, sourceSection);
                keys.add(nilNode);
            } else {
                keys.add((RubyNode) entries.get(n).accept(this));
            }

            if (entries.get(n + 1) == null) {
                final NilNode nilNode = new NilNode(context, sourceSection);
                values.add(nilNode);
            } else {
                values.add((RubyNode) entries.get(n + 1).accept(this));
            }
        }

        return new HashLiteralNode(translate(node.getPosition()), keys.toArray(new RubyNode[keys.size()]), values.toArray(new RubyNode[values.size()]), context);
    }

    @Override
    public Object visitIfNode(org.jrubyparser.ast.IfNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        org.jrubyparser.ast.Node thenBody = node.getThenBody();

        if (thenBody == null) {
            thenBody = new org.jrubyparser.ast.NilNode(node.getPosition());
        }

        org.jrubyparser.ast.Node elseBody = node.getElseBody();

        if (elseBody == null) {
            elseBody = new org.jrubyparser.ast.NilNode(node.getPosition());
        }

        RubyNode condition;

        if (node.getCondition() == null) {
            condition = new NilNode(context, sourceSection);
        } else {
            condition = (RubyNode) node.getCondition().accept(this);
        }

        final BooleanCastNode conditionCast = BooleanCastNodeFactory.create(context, sourceSection, condition);

        final RubyNode thenBodyTranslated = (RubyNode) thenBody.accept(this);
        final RubyNode elseBodyTranslated = (RubyNode) elseBody.accept(this);

        return new IfNode(context, sourceSection, conditionCast, thenBodyTranslated, elseBodyTranslated);
    }

    @Override
    public Object visitInstAsgnNode(org.jrubyparser.ast.InstAsgnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());
        final String nameWithoutSigil = node.getName();

        final RubyNode receiver = new SelfNode(context, sourceSection);

        RubyNode rhs;

        if (node.getValue() == null) {
            rhs = new DeadNode(context, sourceSection);
        } else {
            rhs = (RubyNode) node.getValue().accept(this);
        }

        return new UninitializedWriteInstanceVariableNode(context, sourceSection, nameWithoutSigil, receiver, rhs);
    }

    @Override
    public Object visitInstVarNode(org.jrubyparser.ast.InstVarNode node) {
        final SourceSection sourceSection = translate(node.getPosition());
        final String nameWithoutSigil = node.getName();

        final RubyNode receiver = new SelfNode(context, sourceSection);

        return new UninitializedReadInstanceVariableNode(context, sourceSection, nameWithoutSigil, receiver);
    }

    @Override
    public Object visitIterNode(org.jrubyparser.ast.IterNode node) {
        /*
         * In a block we do NOT allocate a new return ID - returns will return from the method, not
         * the block (in the general case, see Proc and the difference between Proc and Lambda for
         * specifics).
         */

        final boolean hasOwnScope = !translatingForStatement;

        // Unset this flag for any for any blocks within the for statement's body

        translatingForStatement = false;

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParser(), environment.getReturnID(), hasOwnScope, false,
                        new UniqueMethodIdentifier());
        final MethodTranslator methodCompiler = new MethodTranslator(context, this, newEnvironment, true, source);

        org.jrubyparser.ast.ArgsNode argsNode;

        if (node.getVar() instanceof org.jrubyparser.ast.ArgsNode) {
            argsNode = (org.jrubyparser.ast.ArgsNode) node.getVar();
        } else if (node.getVar() instanceof org.jrubyparser.ast.DAsgnNode) {
            final org.jrubyparser.ast.ArgumentNode arg = new org.jrubyparser.ast.ArgumentNode(node.getPosition(), ((org.jrubyparser.ast.DAsgnNode) node.getVar()).getName());
            final org.jrubyparser.ast.ListNode preArgs = new org.jrubyparser.ast.ArrayNode(node.getPosition(), arg);
            argsNode = new org.jrubyparser.ast.ArgsNode(node.getPosition(), preArgs, null, null, null, null, null, null);
        } else if (node.getVar() == null) {
            argsNode = null;
        } else {
            throw new UnsupportedOperationException();
        }

        return methodCompiler.compileFunctionNode(translate(node.getPosition()), "(block)", argsNode, node.getBody());
    }

    @Override
    public Object visitLiteralNode(org.jrubyparser.ast.LiteralNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitLocalAsgnNode(org.jrubyparser.ast.LocalAsgnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        if (environment.getNeverAssignInParentScope()) {
            environment.declareVar(node.getName());
        }

        RubyNode lhs = environment.findLocalVarNode(node.getName(), sourceSection);

        if (lhs == null) {
            if (environment.hasOwnScopeForAssignments()) {
                environment.declareVar(node.getName());
            }

            TranslatorEnvironment environmentToDeclareIn = environment;

            while (!environmentToDeclareIn.hasOwnScopeForAssignments()) {
                environmentToDeclareIn = environmentToDeclareIn.getParent();
            }

            environmentToDeclareIn.declareVar(node.getName());
            lhs = environment.findLocalVarNode(node.getName(), sourceSection);

            if (lhs == null) {
                throw new RuntimeException("shoudln't be here");
            }
        }

        RubyNode rhs;

        if (node.getValue() == null) {
            rhs = new DeadNode(context, sourceSection);
        } else {
            rhs = (RubyNode) node.getValue().accept(this);
        }

        RubyNode translated = ((ReadNode) lhs).makeWriteNode(rhs);

        final UniqueMethodIdentifier methodIdentifier = environment.findMethodForLocalVar(node.getName());

        return instrumenter.instrumentAsLocalAssignment(translated, methodIdentifier, node.getName());
    }

    @Override
    public Object visitLocalVarNode(org.jrubyparser.ast.LocalVarNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final String name = node.getName();

        RubyNode readNode = environment.findLocalVarNode(name, sourceSection);

        if (readNode == null) {
            context.implementationMessage("Local variable found by parser but not by translator - " + name + " at " + node.getPosition());
            readNode = environment.findLocalVarNode(environment.allocateLocalTemp(), sourceSection);
        }

        return readNode;
    }

    @Override
    public Object visitMatch2Node(org.jrubyparser.ast.Match2Node node) {
        final org.jrubyparser.ast.Node argsNode = buildArrayNode(node.getPosition(), node.getValue());
        final org.jrubyparser.ast.Node callNode = new org.jrubyparser.ast.CallNode(node.getPosition(), node.getReceiver(), "=~", argsNode, null);
        return callNode.accept(this);
    }

    @Override
    public Object visitMatch3Node(org.jrubyparser.ast.Match3Node node) {
        final org.jrubyparser.ast.Node argsNode = buildArrayNode(node.getPosition(), node.getValue());
        final org.jrubyparser.ast.Node callNode = new org.jrubyparser.ast.CallNode(node.getPosition(), node.getReceiver(), "=~", argsNode, null);
        return callNode.accept(this);
    }

    @Override
    public Object visitMatchNode(org.jrubyparser.ast.MatchNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitModuleNode(org.jrubyparser.ast.ModuleNode node) {
        // See visitClassNode

        final SourceSection sourceSection = translate(node.getPosition());

        final String name = node.getCPath().getName();

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParser(), environment.getParser().allocateReturnID(), true, true,
                        new UniqueMethodIdentifier());
        final ModuleTranslator classTranslator = new ModuleTranslator(context, this, newEnvironment, source);

        final MethodDefinitionNode definitionMethod = classTranslator.compileClassNode(node.getPosition(), node.getCPath().getName(), node.getBody());

        final DefineOrGetModuleNode defineModuleNode = new DefineOrGetModuleNode(context, sourceSection, name, getModuleToDefineModulesIn(sourceSection));

        return new OpenModuleNode(context, sourceSection, defineModuleNode, definitionMethod);
    }

    @Override
    public Object visitMultipleAsgnNode(org.jrubyparser.ast.MultipleAsgnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final org.jrubyparser.ast.ArrayNode preArray = (org.jrubyparser.ast.ArrayNode) node.getPre();
        final org.jrubyparser.ast.Node rhs = node.getValue();

        RubyNode rhsTranslated;

        if (rhs == null) {
            context.implementationMessage("warning: no RHS for multiple assignment - using noop");
            rhsTranslated = new NilNode(context, sourceSection);
        } else {
            rhsTranslated = (RubyNode) rhs.accept(this);
        }

        /*
         * One very common case is to do
         * 
         * a, b = c, d
         */

        if (preArray != null && node.getPost() == null && node.getRest() == null && rhsTranslated instanceof UninitialisedArrayLiteralNode &&
                        ((UninitialisedArrayLiteralNode) rhsTranslated).getValues().length == preArray.size()) {
            /*
             * We can deal with this common case be rewriting as
             * 
             * temp1 = c; temp2 = d; a = temp1; b = temp2
             * 
             * We can't just do
             * 
             * a = c; b = d
             * 
             * As we don't know if d depends on the original value of a.
             * 
             * We also need to return an array [c, d], but we make that result elidable so it isn't
             * executed if it isn't actually demanded.
             */

            final RubyNode[] rhsValues = ((UninitialisedArrayLiteralNode) rhsTranslated).getValues();
            final int assignedValuesCount = preArray.size();

            final RubyNode[] sequence = new RubyNode[assignedValuesCount * 2];

            final RubyNode[] tempValues = new RubyNode[assignedValuesCount];

            for (int n = 0; n < assignedValuesCount; n++) {
                final String tempName = environment.allocateLocalTemp();
                final RubyNode readTemp = environment.findLocalVarNode(tempName, sourceSection);
                final RubyNode assignTemp = ((ReadNode) readTemp).makeWriteNode(rhsValues[n]);
                final RubyNode assignFinalValue = translateDummyAssignment(preArray.get(n), readTemp);

                sequence[n] = assignTemp;
                sequence[assignedValuesCount + n] = assignFinalValue;

                tempValues[n] = readTemp;
            }

            final RubyNode blockNode = new SequenceNode(context, sourceSection, sequence);

            final UninitialisedArrayLiteralNode arrayNode = new UninitialisedArrayLiteralNode(context, sourceSection, tempValues);

            final ElidableResultNode elidableResult = new ElidableResultNode(context, sourceSection, blockNode, arrayNode);

            return elidableResult;
        } else if (preArray != null) {
            /*
             * The other simple case is
             * 
             * a, b, c = x
             * 
             * If x is an array, then it's
             * 
             * a[0] = x[0] etc
             * 
             * If x isn't an array then it's
             * 
             * a, b, c = [x, nil, nil]
             * 
             * Which I believe is the same effect as
             * 
             * a, b, c, = *x
             * 
             * So we insert the splat cast node, even though it isn't there.
             */

            /*
             * Create a temp for the array.
             */

            final String tempName = environment.allocateLocalTemp();

            /*
             * Create a sequence of instructions, with the first being the literal array assigned to
             * the temp.
             */

            final List<RubyNode> sequence = new ArrayList<>();

            final RubyNode splatCastNode = SplatCastNodeFactory.create(context, sourceSection, rhsTranslated);

            final RubyNode writeTemp = ((ReadNode) environment.findLocalVarNode(tempName, sourceSection)).makeWriteNode(splatCastNode);

            sequence.add(writeTemp);

            /*
             * Then index the temp array for each assignment on the LHS.
             */

            for (int n = 0; n < preArray.size(); n++) {
                final ArrayIndexNode assignedValue = ArrayIndexNodeFactory.create(context, sourceSection, n, environment.findLocalVarNode(tempName, sourceSection));

                sequence.add(translateDummyAssignment(preArray.get(n), assignedValue));
            }

            if (node.getRest() != null) {
                final ArrayRestNode assignedValue = new ArrayRestNode(context, sourceSection, preArray.size(), environment.findLocalVarNode(tempName, sourceSection));

                sequence.add(translateDummyAssignment(node.getRest(), assignedValue));
            }

            return new SequenceNode(context, sourceSection, sequence.toArray(new RubyNode[sequence.size()]));
        } else if (node.getPre() == null && node.getPost() == null && node.getRest() instanceof org.jrubyparser.ast.StarNode) {
            return rhsTranslated;
        } else if (node.getPre() == null && node.getPost() == null && node.getRest() != null && rhs != null && !(rhs instanceof org.jrubyparser.ast.ArrayNode)) {
            /*
             * *a = b
             * 
             * >= 1.8, this seems to be the same as:
             * 
             * a = *b
             */

            final RubyNode restTranslated = ((RubyNode) node.getRest().accept(this)).getNonProxyNode();

            /*
             * Sometimes rest is a corrupt write with no RHS, like in other multiple assignments,
             * and sometimes it is already a read.
             */

            ReadNode restRead;

            if (restTranslated instanceof ReadNode) {
                restRead = (ReadNode) restTranslated;
            } else if (restTranslated instanceof WriteNode) {
                restRead = (ReadNode) ((WriteNode) restTranslated).makeReadNode();
            } else {
                throw new RuntimeException("Unknown form of multiple assignment " + node + " at " + node.getPosition());
            }

            final SplatCastNode rhsSplatCast = SplatCastNodeFactory.create(context, sourceSection, rhsTranslated);

            return restRead.makeWriteNode(rhsSplatCast);
        } else if (node.getPre() == null && node.getPost() == null && node.getRest() != null && rhs != null && rhs instanceof org.jrubyparser.ast.ArrayNode) {
            /*
             * *a = [b, c]
             * 
             * This seems to be the same as:
             * 
             * a = [b, c]
             */

            final RubyNode restTranslated = ((RubyNode) node.getRest().accept(this)).getNonProxyNode();

            /*
             * Sometimes rest is a corrupt write with no RHS, like in other multiple assignments,
             * and sometimes it is already a read.
             */

            ReadNode restRead;

            if (restTranslated instanceof ReadNode) {
                restRead = (ReadNode) restTranslated;
            } else if (restTranslated instanceof WriteNode) {
                restRead = (ReadNode) ((WriteNode) restTranslated).makeReadNode();
            } else {
                throw new RuntimeException("Unknown form of multiple assignment " + node + " at " + node.getPosition());
            }

            return restRead.makeWriteNode(rhsTranslated);
        } else {
            throw new RuntimeException("Unknown form of multiple assignment " + node + " at " + node.getPosition());
        }
    }

    private RubyNode translateDummyAssignment(org.jrubyparser.ast.Node dummyAssignment, RubyNode rhs) {
        final SourceSection sourceSection = translate(dummyAssignment.getPosition());

        /*
         * This is tricky. To represent the RHS of a multiple assignment they use corrupt assignment
         * values, in some cases with no value to be assigned, and in other cases with a dummy
         * value. We can't visit them normally, as they're corrupt. We can't just modify them to
         * have our RHS, as that's a node in our AST, not theirs. We can't use a dummy value in
         * their AST because I can't add new visitors to this interface.
         */

        RubyNode translated;

        if (dummyAssignment instanceof org.jrubyparser.ast.LocalAsgnNode) {
            /*
             * They have a dummy NilImplicitNode as the RHS. Translate, convert to read, convert to
             * write which allows us to set the RHS.
             */

            final WriteNode dummyTranslated = (WriteNode) ((RubyNode) dummyAssignment.accept(this)).getNonProxyNode();
            translated = ((ReadNode) dummyTranslated.makeReadNode()).makeWriteNode(rhs);
        } else if (dummyAssignment instanceof org.jrubyparser.ast.InstAsgnNode) {
            /*
             * Same as before, just a different type of assignment.
             */

            final WriteInstanceVariableNode dummyTranslated = (WriteInstanceVariableNode) dummyAssignment.accept(this);
            translated = dummyTranslated.makeReadNode().makeWriteNode(rhs);
        } else if (dummyAssignment instanceof org.jrubyparser.ast.AttrAssignNode) {
            /*
             * They've given us an AttrAssignNode with the final argument, the assigned value,
             * missing. If we translate that we'll get foo.[]=(index), so missing the value. To
             * solve we have a special version of the visitCallNode that allows us to pass another
             * already translated argument, visitCallNodeExtraArgument. However, we initially have
             * an AttrAssignNode, so we also need a special version of that.
             */

            final org.jrubyparser.ast.AttrAssignNode dummyAttrAssignment = (org.jrubyparser.ast.AttrAssignNode) dummyAssignment;
            translated = visitAttrAssignNodeExtraArgument(dummyAttrAssignment, rhs);
        } else if (dummyAssignment instanceof org.jrubyparser.ast.DAsgnNode) {
            final RubyNode dummyTranslated = (RubyNode) dummyAssignment.accept(this);

            if (dummyTranslated.getNonProxyNode() instanceof WriteLevelVariableNode) {
                translated = ((ReadNode) ((WriteLevelVariableNode) dummyTranslated.getNonProxyNode()).makeReadNode()).makeWriteNode(rhs);
            } else {
                translated = ((ReadNode) ((WriteLocalVariableNode) dummyTranslated.getNonProxyNode()).makeReadNode()).makeWriteNode(rhs);
            }
        } else {
            translated = ((ReadNode) environment.findLocalVarNode(environment.allocateLocalTemp(), sourceSection)).makeWriteNode(rhs);
        }

        return translated;
    }

    @Override
    public Object visitNewlineNode(org.jrubyparser.ast.NewlineNode node) {
        RubyNode translated = (RubyNode) node.getNextNode().accept(this);
        return instrumenter.instrumentAsStatement(translated);
    }

    @Override
    public Object visitNextNode(org.jrubyparser.ast.NextNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode resultNode;

        if (node.getValueNode() == null) {
            resultNode = new NilNode(context, sourceSection);
        } else {
            resultNode = (RubyNode) node.getValueNode().accept(this);
        }

        return new NextNode(context, sourceSection, resultNode);
    }

    @Override
    public Object visitNilNode(org.jrubyparser.ast.NilNode node) {
        return new NilNode(context, translate(node.getPosition()));
    }

    @Override
    public Object visitNotNode(org.jrubyparser.ast.NotNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final BooleanCastNode booleanCastNode = BooleanCastNodeFactory.create(context, sourceSection, (RubyNode) node.getCondition().accept(this));

        return new NotNode(context, sourceSection, booleanCastNode);
    }

    @Override
    public Object visitNthRefNode(org.jrubyparser.ast.NthRefNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final String name = "$" + node.getMatchNumber();

        RubyNode readLocal = environment.findLocalVarNode(name, sourceSection);

        if (readLocal == null) {
            environment.declareVar(name);
            readLocal = environment.findLocalVarNode(name, sourceSection);
        }

        return readLocal;
    }

    @Override
    public Object visitOpAsgnAndNode(org.jrubyparser.ast.OpAsgnAndNode node) {
        final org.jrubyparser.ast.Node lhs = node.getFirst();
        final org.jrubyparser.ast.Node rhs = node.getSecond();

        return AndNodeFactory.create(context, translate(node.getPosition()), (RubyNode) lhs.accept(this), (RubyNode) rhs.accept(this));
    }

    @Override
    public Object visitOpAsgnNode(org.jrubyparser.ast.OpAsgnNode node) {
        /*
         * We're going to de-sugar a.foo += c into a.foo = a.foo + c. Note that we can't evaluate a
         * more than once, so we put it into a temporary, and we're doing something more like:
         * 
         * temp = a; temp.foo = temp.foo + c
         */

        final String temp = environment.allocateLocalTemp();
        final org.jrubyparser.ast.Node writeReceiverToTemp = new org.jrubyparser.ast.LocalAsgnNode(node.getPosition(), temp, 0, node.getReceiver());
        final org.jrubyparser.ast.Node readReceiverFromTemp = new org.jrubyparser.ast.LocalVarNode(node.getPosition(), 0, temp);

        final org.jrubyparser.ast.Node readMethod = new org.jrubyparser.ast.CallNode(node.getPosition(), readReceiverFromTemp, node.getVariableName(), null);
        final org.jrubyparser.ast.Node operation = new org.jrubyparser.ast.CallNode(node.getPosition(), readMethod, node.getOperatorName(), buildArrayNode(node.getPosition(), node.getValue()));
        final org.jrubyparser.ast.Node writeMethod = new org.jrubyparser.ast.CallNode(node.getPosition(), readReceiverFromTemp, node.getVariableName() + "=", buildArrayNode(node.getPosition(),
                        operation));

        final org.jrubyparser.ast.BlockNode block = new org.jrubyparser.ast.BlockNode(node.getPosition());
        block.add(writeReceiverToTemp);
        block.add(writeMethod);

        return block.accept(this);
    }

    @Override
    public Object visitOpAsgnOrNode(org.jrubyparser.ast.OpAsgnOrNode node) {
        /*
         * De-sugar x ||= y into x || x = y. No repeated evaluations there so it's easy. It's also
         * basically how jruby-parser represents it already. We'll do it directly, rather than via
         * another JRuby AST node.
         */

        final org.jrubyparser.ast.Node lhs = node.getFirst();
        final org.jrubyparser.ast.Node rhs = node.getSecond();

        return OrNodeFactory.create(context, translate(node.getPosition()), (RubyNode) lhs.accept(this), (RubyNode) rhs.accept(this));
    }

    @Override
    public Object visitOpElementAsgnNode(org.jrubyparser.ast.OpElementAsgnNode node) {
        /*
         * We're going to de-sugar a[b] += c into a[b] = a[b] + c. See discussion in
         * visitOpAsgnNode.
         */

        org.jrubyparser.ast.Node index;

        if (node.getArgs() == null) {
            index = null;
        } else {
            index = node.getArgs().childNodes().get(0);
        }

        final org.jrubyparser.ast.Node operand = node.getValue();

        final String temp = environment.allocateLocalTemp();
        final org.jrubyparser.ast.Node writeArrayToTemp = new org.jrubyparser.ast.LocalAsgnNode(node.getPosition(), temp, 0, node.getReceiver());
        final org.jrubyparser.ast.Node readArrayFromTemp = new org.jrubyparser.ast.LocalVarNode(node.getPosition(), 0, temp);

        final org.jrubyparser.ast.Node arrayRead = new org.jrubyparser.ast.CallNode(node.getPosition(), readArrayFromTemp, "[]", buildArrayNode(node.getPosition(), index));

        final String op = node.getOperatorName();

        org.jrubyparser.ast.Node operation = null;

        if (op.equals("||")) {
            operation = new org.jrubyparser.ast.OrNode(node.getPosition(), arrayRead, operand);
        } else if (op.equals("&&")) {
            operation = new org.jrubyparser.ast.AndNode(node.getPosition(), arrayRead, operand);
        } else {
            operation = new org.jrubyparser.ast.CallNode(node.getPosition(), arrayRead, node.getOperatorName(), buildArrayNode(node.getPosition(), operand));
        }

        final org.jrubyparser.ast.Node arrayWrite = new org.jrubyparser.ast.CallNode(node.getPosition(), readArrayFromTemp, "[]=", buildArrayNode(node.getPosition(), index, operation));

        final org.jrubyparser.ast.BlockNode block = new org.jrubyparser.ast.BlockNode(node.getPosition());
        block.add(writeArrayToTemp);
        block.add(arrayWrite);

        return block.accept(this);
    }

    private static org.jrubyparser.ast.ArrayNode buildArrayNode(org.jrubyparser.SourcePosition sourcePosition, org.jrubyparser.ast.Node first, org.jrubyparser.ast.Node... rest) {
        if (first == null) {
            return new org.jrubyparser.ast.ArrayNode(sourcePosition);
        }

        final org.jrubyparser.ast.ArrayNode array = new org.jrubyparser.ast.ArrayNode(sourcePosition, first);

        for (org.jrubyparser.ast.Node node : rest) {
            array.add(node);
        }

        return array;
    }

    @Override
    public Object visitOrNode(org.jrubyparser.ast.OrNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode x;

        if (node.getFirst() == null) {
            x = new NilNode(context, sourceSection);
        } else {
            x = (RubyNode) node.getFirst().accept(this);
        }

        RubyNode y;

        if (node.getSecond() == null) {
            y = new NilNode(context, sourceSection);
        } else {
            y = (RubyNode) node.getSecond().accept(this);
        }

        return OrNodeFactory.create(context, sourceSection, x, y);
    }

    @Override
    public Object visitPostExeNode(org.jrubyparser.ast.PostExeNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitPreExeNode(org.jrubyparser.ast.PreExeNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitRedoNode(org.jrubyparser.ast.RedoNode node) {
        return new RedoNode(context, translate(node.getPosition()));
    }

    @Override
    public Object visitRegexpNode(org.jrubyparser.ast.RegexpNode node) {
        RubyRegexp regexp;

        try {
            final String patternText = node.getValue();

            int flags = Pattern.MULTILINE | Pattern.UNIX_LINES;

            final org.jrubyparser.RegexpOptions options = node.getOptions();

            if (options.isIgnorecase()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }

            if (options.isMultiline()) {
                // TODO(cs): isn't this the default?
                flags |= Pattern.MULTILINE;
            }

            final Pattern pattern = Pattern.compile(patternText, flags);

            regexp = new RubyRegexp(context.getCoreLibrary().getRegexpClass(), pattern);
        } catch (PatternSyntaxException e) {
            context.implementationMessage("failed to parse Ruby regexp " + node.getValue() + " as Java regexp - replacing with .");
            regexp = new RubyRegexp(context.getCoreLibrary().getRegexpClass(), ".");
        }

        final ObjectLiteralNode literalNode = new ObjectLiteralNode(context, translate(node.getPosition()), regexp);
        return literalNode;
    }

    @Override
    public Object visitRescueBodyNode(org.jrubyparser.ast.RescueBodyNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitRescueNode(org.jrubyparser.ast.RescueNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode tryPart;

        if (node.getBody() != null) {
            tryPart = (RubyNode) node.getBody().accept(this);
        } else {
            tryPart = new NilNode(context, sourceSection);
        }

        final List<RescueNode> rescueNodes = new ArrayList<>();

        org.jrubyparser.ast.RescueBodyNode rescueBody = node.getRescue();

        while (rescueBody != null) {
            if (rescueBody.getExceptions() != null) {
                if (rescueBody.getExceptions() instanceof org.jrubyparser.ast.ArrayNode) {
                    final List<org.jrubyparser.ast.Node> exceptionNodes = ((org.jrubyparser.ast.ArrayNode) rescueBody.getExceptions()).childNodes();

                    final RubyNode[] handlingClasses = new RubyNode[exceptionNodes.size()];

                    for (int n = 0; n < handlingClasses.length; n++) {
                        handlingClasses[n] = (RubyNode) exceptionNodes.get(n).accept(this);
                    }

                    RubyNode translatedBody;

                    if (rescueBody.getBody() == null) {
                        translatedBody = new NilNode(context, sourceSection);
                    } else {
                        translatedBody = (RubyNode) rescueBody.getBody().accept(this);
                    }

                    final RescueClassesNode rescueNode = new RescueClassesNode(context, sourceSection, handlingClasses, translatedBody);
                    rescueNodes.add(rescueNode);
                } else if (rescueBody.getExceptions() instanceof org.jrubyparser.ast.SplatNode) {
                    final org.jrubyparser.ast.SplatNode splat = (org.jrubyparser.ast.SplatNode) rescueBody.getExceptions();

                    RubyNode splatTranslated;

                    if (splat.getValue() == null) {
                        splatTranslated = new NilNode(context, sourceSection);
                    } else {
                        splatTranslated = (RubyNode) splat.getValue().accept(this);
                    }

                    RubyNode bodyTranslated;

                    if (rescueBody.getBody() == null) {
                        bodyTranslated = new NilNode(context, sourceSection);
                    } else {
                        bodyTranslated = (RubyNode) rescueBody.getBody().accept(this);
                    }

                    final RescueSplatNode rescueNode = new RescueSplatNode(context, sourceSection, splatTranslated, bodyTranslated);
                    rescueNodes.add(rescueNode);
                } else {
                    unimplemented(node);
                }
            } else {
                RubyNode bodyNode;

                if (rescueBody.getBody() == null) {
                    bodyNode = new NilNode(context, sourceSection);
                } else {
                    bodyNode = (RubyNode) rescueBody.getBody().accept(this);
                }

                final RescueAnyNode rescueNode = new RescueAnyNode(context, sourceSection, bodyNode);
                rescueNodes.add(rescueNode);
            }

            rescueBody = rescueBody.getOptRescue();
        }

        RubyNode elsePart;

        if (node.getElse() != null) {
            elsePart = (RubyNode) node.getElse().accept(this);
        } else {
            elsePart = new NilNode(context, sourceSection);
        }

        return new TryNode(context, sourceSection, tryPart, rescueNodes.toArray(new RescueNode[rescueNodes.size()]), elsePart);
    }

    @Override
    public Object visitRestArgNode(org.jrubyparser.ast.RestArgNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitRetryNode(org.jrubyparser.ast.RetryNode node) {
        return new RetryNode(context, translate(node.getPosition()));
    }

    @Override
    public Object visitReturnNode(org.jrubyparser.ast.ReturnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode translatedChild;

        if (node.getValue() == null) {
            translatedChild = new NilNode(context, sourceSection);
        } else {
            translatedChild = (RubyNode) node.getValue().accept(this);
        }

        return new ReturnNode(context, sourceSection, environment.getReturnID(), translatedChild);
    }

    @Override
    public Object visitRootNode(org.jrubyparser.ast.RootNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitSClassNode(org.jrubyparser.ast.SClassNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParser(), environment.getParser().allocateReturnID(), true, true,
                        new UniqueMethodIdentifier());
        final ModuleTranslator classTranslator = new ModuleTranslator(context, this, newEnvironment, source);

        final MethodDefinitionNode definitionMethod = classTranslator.compileClassNode(node.getPosition(), "singleton", node.getBody());

        final RubyNode receiverNode = (RubyNode) node.getReceiver().accept(this);

        final SingletonClassNode singletonClassNode = new SingletonClassNode(context, sourceSection, receiverNode);

        return new OpenModuleNode(context, sourceSection, singletonClassNode, definitionMethod);
    }

    @Override
    public Object visitSValueNode(org.jrubyparser.ast.SValueNode node) {
        return node.getValue().accept(this);
    }

    @Override
    public Object visitSelfNode(org.jrubyparser.ast.SelfNode node) {
        return new SelfNode(context, translate(node.getPosition()));
    }

    @Override
    public Object visitSplatNode(org.jrubyparser.ast.SplatNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode value;

        if (node.getValue() == null) {
            value = new NilNode(context, sourceSection);
        } else {
            value = (RubyNode) node.getValue().accept(this);
        }

        return SplatCastNodeFactory.create(context, sourceSection, value);
    }

    @Override
    public Object visitStrNode(org.jrubyparser.ast.StrNode node) {
        return new StringLiteralNode(context, translate(node.getPosition()), node.getValue());
    }

    @Override
    public Object visitSuperNode(org.jrubyparser.ast.SuperNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitSymbolNode(org.jrubyparser.ast.SymbolNode node) {
        return new ObjectLiteralNode(context, translate(node.getPosition()), new RubySymbol(context.getCoreLibrary().getSymbolClass(), node.getName()));
    }

    @Override
    public Object visitToAryNode(org.jrubyparser.ast.ToAryNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitTrueNode(org.jrubyparser.ast.TrueNode node) {
        return new BooleanLiteralNode(context, translate(node.getPosition()), true);
    }

    @Override
    public Object visitUndefNode(org.jrubyparser.ast.UndefNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitUntilNode(org.jrubyparser.ast.UntilNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode condition;

        if (node.getCondition() == null) {
            condition = new NilNode(context, sourceSection);
        } else {
            condition = (RubyNode) node.getCondition().accept(this);
        }

        final BooleanCastNode conditionCast = BooleanCastNodeFactory.create(context, sourceSection, condition);
        final NotNode conditionCastNot = new NotNode(context, sourceSection, conditionCast);
        final BooleanCastNode conditionCastNotCast = BooleanCastNodeFactory.create(context, sourceSection, conditionCastNot);

        final RubyNode body = (RubyNode) node.getBody().accept(this);

        return new WhileNode(context, sourceSection, conditionCastNotCast, body);
    }

    @Override
    public Object visitVAliasNode(org.jrubyparser.ast.VAliasNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitVCallNode(org.jrubyparser.ast.VCallNode node) {
        final org.jrubyparser.ast.Node receiver = new org.jrubyparser.ast.SelfNode(node.getPosition());
        final org.jrubyparser.ast.Node args = null;
        final org.jrubyparser.ast.Node callNode = new org.jrubyparser.ast.CallNode(node.getPosition(), receiver, node.getName(), args);

        return callNode.accept(this);
    }

    @Override
    public Object visitWhenNode(org.jrubyparser.ast.WhenNode node) {
        return unimplemented(node);
    }

    @Override
    public Object visitWhileNode(org.jrubyparser.ast.WhileNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        RubyNode condition;

        if (node.getCondition() == null) {
            condition = new NilNode(context, sourceSection);
        } else {
            condition = (RubyNode) node.getCondition().accept(this);
        }

        final BooleanCastNode conditionCast = BooleanCastNodeFactory.create(context, sourceSection, condition);

        final RubyNode body = (RubyNode) node.getBody().accept(this);

        return new WhileNode(context, sourceSection, conditionCast, body);
    }

    @Override
    public Object visitXStrNode(org.jrubyparser.ast.XStrNode node) {
        SourceSection sourceSection = translate(node.getPosition());

        final StringLiteralNode literal = new StringLiteralNode(context, sourceSection, node.getValue());

        return new SystemNode(context, sourceSection, literal);
    }

    @Override
    public Object visitYieldNode(org.jrubyparser.ast.YieldNode node) {
        final List<org.jrubyparser.ast.Node> arguments = new ArrayList<>();

        final org.jrubyparser.ast.Node argsNode = node.getArgs();

        if (argsNode != null) {
            if (argsNode instanceof org.jrubyparser.ast.ListNode) {
                arguments.addAll(((org.jrubyparser.ast.ListNode) node.getArgs()).childNodes());
            } else {
                arguments.add(node.getArgs());
            }
        }

        final List<RubyNode> argumentsTranslated = new ArrayList<>();

        for (org.jrubyparser.ast.Node argument : arguments) {
            argumentsTranslated.add((RubyNode) argument.accept(this));
        }

        final RubyNode[] argumentsTranslatedArray = argumentsTranslated.toArray(new RubyNode[argumentsTranslated.size()]);

        return new YieldNode(context, translate(node.getPosition()), argumentsTranslatedArray);
    }

    @Override
    public Object visitZArrayNode(org.jrubyparser.ast.ZArrayNode node) {
        final RubyNode[] values = new RubyNode[0];

        return new UninitialisedArrayLiteralNode(context, translate(node.getPosition()), values);
    }

    @Override
    public Object visitZSuperNode(org.jrubyparser.ast.ZSuperNode node) {
        return unimplemented(node);
    }

    public Object visitArgumentNode(org.jrubyparser.ast.ArgumentNode node) {
        return unimplemented(node);
    }

    public Object visitCommentNode(org.jrubyparser.ast.CommentNode node) {
        return unimplemented(node);
    }

    public Object visitKeywordArgNode(org.jrubyparser.ast.KeywordArgNode node) {
        return unimplemented(node);
    }

    public Object visitKeywordRestArgNode(org.jrubyparser.ast.KeywordRestArgNode node) {
        return unimplemented(node);
    }

    public Object visitListNode(org.jrubyparser.ast.ListNode node) {
        return unimplemented(node);
    }

    public Object visitMethodNameNode(org.jrubyparser.ast.MethodNameNode node) {
        return unimplemented(node);
    }

    public Object visitOptArgNode(org.jrubyparser.ast.OptArgNode node) {
        return unimplemented(node);
    }

    public Object visitSyntaxNode(org.jrubyparser.ast.SyntaxNode node) {
        return unimplemented(node);
    }

    public Object visitImplicitNilNode(org.jrubyparser.ast.ImplicitNilNode node) {
        return new NilNode(context, translate(node.getPosition()));
    }

    public Object visitLambdaNode(org.jrubyparser.ast.LambdaNode node) {
        // TODO(cs): code copied and modified from visitIterNode - extract common

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParser(), environment.getReturnID(), false, false, new UniqueMethodIdentifier());
        final MethodTranslator methodCompiler = new MethodTranslator(context, this, newEnvironment, false, source);

        org.jrubyparser.ast.ArgsNode argsNode;

        if (node.getVar() instanceof org.jrubyparser.ast.ArgsNode) {
            argsNode = (org.jrubyparser.ast.ArgsNode) node.getVar();
        } else if (node.getVar() instanceof org.jrubyparser.ast.DAsgnNode) {
            final org.jrubyparser.ast.ArgumentNode arg = new org.jrubyparser.ast.ArgumentNode(node.getPosition(), ((org.jrubyparser.ast.DAsgnNode) node.getVar()).getName());
            final org.jrubyparser.ast.ListNode preArgs = new org.jrubyparser.ast.ArrayNode(node.getPosition(), arg);
            argsNode = new org.jrubyparser.ast.ArgsNode(node.getPosition(), preArgs, null, null, null, null, null, null);
        } else if (node.getVar() == null) {
            argsNode = null;
        } else {
            throw new UnsupportedOperationException();
        }

        final MethodDefinitionNode definitionNode = methodCompiler.compileFunctionNode(translate(node.getPosition()), "(lambda)", argsNode, node.getBody());

        return new LambdaNode(context, translate(node.getPosition()), definitionNode);
    }

    public Object visitUnaryCallNode(org.jrubyparser.ast.UnaryCallNode node) {
        final org.jrubyparser.ast.Node callNode = new org.jrubyparser.ast.CallNode(node.getPosition(), node.getReceiver(), node.getName(), null, null);
        return callNode.accept(this);
    }

    protected Object unimplemented(org.jrubyparser.ast.Node node) {
        context.implementationMessage("warning: %s at %s does nothing", node, node.getPosition());
        return new NilNode(context, translate(node.getPosition()));
    }

    protected SourceSection translate(final org.jrubyparser.SourcePosition sourcePosition) {
        try {
            // TODO(cs): get an identifier
            final String identifier = "(identifier)";

            // TODO(cs): work out the start column
            final int startColumn = -1;

            final int charLength = sourcePosition.getEndOffset() - sourcePosition.getStartOffset();

            return new DefaultSourceSection(source, identifier, sourcePosition.getStartLine() + 1, startColumn, sourcePosition.getStartOffset(), charLength);
        } catch (UnsupportedOperationException e) {
            // In some circumstances JRuby can't tell you what the position is
            return translate(new org.jrubyparser.SourcePosition("(unknown)", 0, 0));
        }
    }

    protected SequenceNode initFlipFlopStates(SourceSection sourceSection) {
        final RubyNode[] initNodes = new RubyNode[environment.getFlipFlopStates().size()];

        for (int n = 0; n < initNodes.length; n++) {
            initNodes[n] = new InitFlipFlopSlotNode(context, sourceSection, environment.getFlipFlopStates().get(n));
        }

        return new SequenceNode(context, sourceSection, initNodes);
    }

}
