/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.methods;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * Define a method. That is, store the definition of a method and when executed produce the
 * executable object that results.
 */
@NodeInfo(shortName = "method-def")
public class MethodDefinitionNode extends RubyNode {

    protected final String name;
    protected final UniqueMethodIdentifier uniqueIdentifier;

    protected final FrameDescriptor frameDescriptor;
    protected final RubyRootNode pristineRootNode;

    protected final CallTarget callTarget;

    protected final boolean requiresDeclarationFrame;

    public MethodDefinitionNode(RubyContext context, SourceSection sourceSection, String name, UniqueMethodIdentifier uniqueIdentifier, FrameDescriptor frameDescriptor,
                    boolean requiresDeclarationFrame, RubyRootNode pristineRootNode, CallTarget callTarget) {
        super(context, sourceSection);
        this.name = name;
        this.uniqueIdentifier = uniqueIdentifier;
        this.frameDescriptor = frameDescriptor;
        this.requiresDeclarationFrame = requiresDeclarationFrame;
        this.pristineRootNode = pristineRootNode;
        this.callTarget = callTarget;
    }

    public RubyMethod executeMethod(VirtualFrame frame) {
        CompilerDirectives.transferToInterpreter();

        MaterializedFrame declarationFrame;

        if (requiresDeclarationFrame) {
            declarationFrame = frame.materialize();
        } else {
            declarationFrame = null;
        }

        final FrameSlot visibilitySlot = frame.getFrameDescriptor().findFrameSlot(RubyModule.VISIBILITY_FRAME_SLOT_ID);

        Visibility visibility;

        if (visibilitySlot == null) {
            visibility = Visibility.PUBLIC;
        } else {
            Object visibilityObject;

            try {
                visibilityObject = frame.getObject(visibilitySlot);
            } catch (FrameSlotTypeException e) {
                throw new RuntimeException(e);
            }

            if (visibilityObject instanceof Visibility) {
                visibility = (Visibility) visibilityObject;
            } else {
                visibility = Visibility.PUBLIC;
            }
        }

        final InlinableMethodImplementation methodImplementation = new InlinableMethodImplementation(callTarget, declarationFrame, frameDescriptor, pristineRootNode, false, false);
        return new RubyMethod(getSourceSection(), null, uniqueIdentifier, null, name, visibility, false, methodImplementation);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeMethod(frame);
    }

    public String getName() {
        return name;
    }

    public CallTarget getCallTarget() {
        return callTarget;
    }

}
