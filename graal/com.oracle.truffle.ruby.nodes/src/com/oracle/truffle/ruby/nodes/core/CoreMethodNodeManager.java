/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.control.*;
import com.oracle.truffle.ruby.nodes.debug.*;
import com.oracle.truffle.ruby.nodes.methods.arguments.*;
import com.oracle.truffle.ruby.nodes.objects.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;

public abstract class CoreMethodNodeManager {

    /**
     * Register all the nodes that represent core methods as methods with their respective classes,
     * given the Object class object, which should already be initialized with all the core classes.
     */
    public static void addMethods(RubyClass rubyObjectClass) {
        for (MethodDetails methodDetails : getMethods()) {
            addMethod(rubyObjectClass, methodDetails);
        }
    }

    /**
     * Collect up all the core method nodes. Abstracted to allow the SVM to implement at compile
     * type.
     */
    public static List<MethodDetails> getMethods() {
        final List<MethodDetails> methods = new ArrayList<>();
        getMethods(methods, ArrayNodesFactory.getFactories());
        getMethods(methods, BasicObjectNodesFactory.getFactories());
        getMethods(methods, BignumNodesFactory.getFactories());
        getMethods(methods, ClassNodesFactory.getFactories());
        getMethods(methods, ContinuationNodesFactory.getFactories());
        getMethods(methods, ComparableNodesFactory.getFactories());
        getMethods(methods, DirNodesFactory.getFactories());
        getMethods(methods, ExceptionNodesFactory.getFactories());
        getMethods(methods, FalseClassNodesFactory.getFactories());
        getMethods(methods, FiberNodesFactory.getFactories());
        getMethods(methods, FileNodesFactory.getFactories());
        getMethods(methods, FixnumNodesFactory.getFactories());
        getMethods(methods, FloatNodesFactory.getFactories());
        getMethods(methods, HashNodesFactory.getFactories());
        getMethods(methods, KernelNodesFactory.getFactories());
        getMethods(methods, MainNodesFactory.getFactories());
        getMethods(methods, MatchDataNodesFactory.getFactories());
        getMethods(methods, MathNodesFactory.getFactories());
        getMethods(methods, ModuleNodesFactory.getFactories());
        getMethods(methods, NilClassNodesFactory.getFactories());
        getMethods(methods, ObjectNodesFactory.getFactories());
        getMethods(methods, ObjectSpaceNodesFactory.getFactories());
        getMethods(methods, ProcessNodesFactory.getFactories());
        getMethods(methods, ProcNodesFactory.getFactories());
        getMethods(methods, RangeNodesFactory.getFactories());
        getMethods(methods, RegexpNodesFactory.getFactories());
        getMethods(methods, SignalNodesFactory.getFactories());
        getMethods(methods, StringNodesFactory.getFactories());
        getMethods(methods, StructNodesFactory.getFactories());
        getMethods(methods, SymbolNodesFactory.getFactories());
        getMethods(methods, ThreadNodesFactory.getFactories());
        getMethods(methods, TimeNodesFactory.getFactories());
        getMethods(methods, TrueClassNodesFactory.getFactories());
        getMethods(methods, DebugNodesFactory.getFactories());
        return methods;
    }

    /**
     * Collect up the core methods created by a factory.
     */
    private static void getMethods(List<MethodDetails> methods, List<? extends NodeFactory<? extends CoreMethodNode>> nodeFactories) {
        for (NodeFactory<? extends RubyNode> nodeFactory : nodeFactories) {
            final GeneratedBy generatedBy = nodeFactory.getClass().getAnnotation(GeneratedBy.class);
            final Class<?> nodeClass = generatedBy.value();
            final CoreClass classAnnotation = nodeClass.getEnclosingClass().getAnnotation(CoreClass.class);
            final CoreMethod methodAnnotation = nodeClass.getAnnotation(CoreMethod.class);
            methods.add(new MethodDetails(classAnnotation, methodAnnotation, nodeFactory));
        }
    }

    /**
     * Take a core method node factory, the annotations for the class and method, and add it as a
     * method on the correct class.
     */
    private static void addMethod(RubyClass rubyObjectClass, MethodDetails methodDetails) {
        assert rubyObjectClass != null;
        assert methodDetails != null;

        final RubyContext context = rubyObjectClass.getContext();

        RubyModule module;

        if (methodDetails.getClassAnnotation().name().equals("main")) {
            module = context.getCoreLibrary().getMainObject().getSingletonClass();
        } else {
            module = (RubyModule) rubyObjectClass.lookupConstant(methodDetails.getClassAnnotation().name());
        }

        assert module != null : methodDetails.getClassAnnotation().name();

        final List<String> names = Arrays.asList(methodDetails.getMethodAnnotation().names());
        assert names.size() >= 1;

        final String canonicalName = names.get(0);
        final List<String> aliases = names.subList(1, names.size());

        final UniqueMethodIdentifier uniqueIdentifier = new UniqueMethodIdentifier();
        final Visibility visibility = Visibility.PUBLIC;

        final RubyRootNode pristineRootNode = makeGenericMethod(context, methodDetails);
        final CallTarget callTarget = Truffle.getRuntime().createCallTarget(NodeUtil.cloneNode(pristineRootNode));

        final String intrinsicName = methodDetails.getClassAnnotation().name() + "#" + canonicalName;

        final InlinableMethodImplementation methodImplementation = new InlinableMethodImplementation(callTarget, null, new FrameDescriptor(), pristineRootNode, true,
                        methodDetails.getMethodAnnotation().appendCallNode());
        final RubyMethod method = new RubyMethod(pristineRootNode.getSourceSection(), module, uniqueIdentifier, intrinsicName, canonicalName, visibility, false, methodImplementation);

        module.addMethod(method);

        if (methodDetails.getMethodAnnotation().isModuleMethod()) {
            module.getSingletonClass().addMethod(method);
        }

        for (String alias : aliases) {
            final RubyMethod withAlias = method.withNewName(alias);

            module.addMethod(withAlias);

            if (methodDetails.getMethodAnnotation().isModuleMethod()) {
                module.getSingletonClass().addMethod(withAlias);
            }
        }
    }

    private static RubyRootNode makeGenericMethod(RubyContext context, MethodDetails methodDetails) {
        final SourceSection sourceSection = new CoreSourceSection(methodDetails.getClassAnnotation().name() + "#" + methodDetails.getMethodAnnotation().names()[0]);

        final Arity arity = new Arity(methodDetails.getMethodAnnotation().minArgs(), methodDetails.getMethodAnnotation().maxArgs());

        final List<RubyNode> argumentsNodes = new ArrayList<>();

        if (methodDetails.getMethodAnnotation().needsSelf()) {
            argumentsNodes.add(new SelfNode(context, sourceSection));
        }

        if (methodDetails.getMethodAnnotation().isSplatted()) {
            argumentsNodes.add(new ReadAllArgumentsNode(context, sourceSection));
        } else {
            assert arity.getMaximum() != Arity.NO_MAXIMUM;

            for (int n = 0; n < arity.getMaximum(); n++) {
                argumentsNodes.add(new ReadPreArgumentNode(context, sourceSection, n, true));
            }
        }

        if (methodDetails.getMethodAnnotation().needsBlock()) {
            argumentsNodes.add(new ReadBlockArgumentNode(context, sourceSection, true));
        }

        final RubyNode methodNode = methodDetails.getNodeFactory().createNode(context, sourceSection, argumentsNodes.toArray(new RubyNode[argumentsNodes.size()]));
        final CheckArityNode checkArity = new CheckArityNode(context, sourceSection, arity);
        final SequenceNode block = new SequenceNode(context, sourceSection, checkArity, methodNode);

        return new RubyRootNode(sourceSection, null, methodDetails.getClassAnnotation().name() + "#" + methodDetails.getMethodAnnotation().names()[0] + "(core)", block);
    }

    public static class MethodDetails {

        private final CoreClass classAnnotation;
        private final CoreMethod methodAnnotation;
        private final NodeFactory<? extends RubyNode> nodeFactory;

        public MethodDetails(CoreClass classAnnotation, CoreMethod methodAnnotation, NodeFactory<? extends RubyNode> nodeFactory) {
            assert classAnnotation != null;
            assert methodAnnotation != null;
            assert nodeFactory != null;
            this.classAnnotation = classAnnotation;
            this.methodAnnotation = methodAnnotation;
            this.nodeFactory = nodeFactory;
        }

        public CoreClass getClassAnnotation() {
            return classAnnotation;
        }

        public CoreMethod getMethodAnnotation() {
            return methodAnnotation;
        }

        public NodeFactory<? extends RubyNode> getNodeFactory() {
            return nodeFactory;
        }

    }

}
