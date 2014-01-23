/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.parser;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.constants.*;
import com.oracle.truffle.ruby.nodes.control.*;
import com.oracle.truffle.ruby.nodes.literal.*;
import com.oracle.truffle.ruby.nodes.methods.*;
import com.oracle.truffle.ruby.nodes.objects.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * Translates module and class nodes.
 * <p>
 * In Ruby, a module or class definition is somewhat like a method. It has a local scope and a value
 * for self, which is the module or class object that is being defined. Therefore for a module or
 * class definition we translate into a special method. We run that method with self set to be the
 * newly allocated module or class. We then have to treat at least method and constant definitions
 * differently.
 */
class ModuleTranslator extends Translator {

    public ModuleTranslator(RubyContext context, Translator parent, TranslatorEnvironment environment, Source source) {
        super(context, parent, environment, source);
    }

    public MethodDefinitionNode compileClassNode(org.jrubyparser.SourcePosition sourcePosition, String name, org.jrubyparser.ast.Node bodyNode) {
        final SourceSection sourceSection = translate(sourcePosition);

        environment.addMethodDeclarationSlots();

        final String methodName = "(" + name + "-def" + ")";
        environment.setMethodName(methodName);

        RubyNode body;

        if (bodyNode != null) {
            body = (RubyNode) bodyNode.accept(this);
        } else {
            body = new NilNode(context, sourceSection);
        }

        if (environment.getFlipFlopStates().size() > 0) {
            body = new SequenceNode(context, sourceSection, initFlipFlopStates(sourceSection), body);
        }

        body = new CatchReturnNode(context, sourceSection, body, environment.getReturnID());

        final RubyRootNode pristineRootNode = new RubyRootNode(sourceSection, environment.getFrameDescriptor(), methodName, body);

        final CallTarget callTarget = Truffle.getRuntime().createCallTarget(NodeUtil.cloneNode(pristineRootNode));

        return new MethodDefinitionNode(context, sourceSection, methodName, environment.getUniqueMethodIdentifier(), environment.getFrameDescriptor(), environment.needsDeclarationFrame(),
                        pristineRootNode, callTarget);
    }

    @Override
    public Object visitConstDeclNode(org.jrubyparser.ast.ConstDeclNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final SelfNode selfNode = new SelfNode(context, sourceSection);

        return new WriteConstantNode(context, sourceSection, node.getName(), selfNode, (RubyNode) node.getValue().accept(this));
    }

    @Override
    public Object visitConstNode(org.jrubyparser.ast.ConstNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final SelfNode selfNode = new SelfNode(context, sourceSection);

        return new UninitializedReadConstantNode(context, sourceSection, node.getName(), selfNode);
    }

    @Override
    public Object visitDefnNode(org.jrubyparser.ast.DefnNode node) {
        /*
         * The top-level translator puts methods into Object. We put ours into the self, which is
         * the class being defined.
         */

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParser(), environment.getParser().allocateReturnID(), true, true,
                        new UniqueMethodIdentifier());
        final MethodTranslator methodCompiler = new MethodTranslator(context, this, newEnvironment, false, source);
        final MethodDefinitionNode functionExprNode = methodCompiler.compileFunctionNode(translate(node.getPosition()), node.getName(), node.getArgs(), node.getBody());

        final SourceSection sourceSection = translate(node.getPosition());
        return new AddMethodNode(context, sourceSection, new SelfNode(context, sourceSection), functionExprNode);
    }

    @Override
    public Object visitClassVarAsgnNode(org.jrubyparser.ast.ClassVarAsgnNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final RubyNode receiver = new SelfNode(context, sourceSection);

        final RubyNode rhs = (RubyNode) node.getValue().accept(this);

        return new WriteClassVariableNode(context, sourceSection, node.getName(), receiver, rhs);
    }

    @Override
    public Object visitClassVarNode(org.jrubyparser.ast.ClassVarNode node) {
        final SourceSection sourceSection = translate(node.getPosition());
        return new ReadClassVariableNode(context, sourceSection, node.getName(), new SelfNode(context, sourceSection));
    }

    @Override
    public Object visitAliasNode(org.jrubyparser.ast.AliasNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final org.jrubyparser.ast.LiteralNode oldName = (org.jrubyparser.ast.LiteralNode) node.getOldName();
        final org.jrubyparser.ast.LiteralNode newName = (org.jrubyparser.ast.LiteralNode) node.getNewName();

        return new AliasNode(context, sourceSection, new SelfNode(context, sourceSection), newName.getName(), oldName.getName());
    }

    @Override
    protected RubyNode getModuleToDefineModulesIn(SourceSection sourceSection) {
        return new SelfNode(context, sourceSection);
    }

}
