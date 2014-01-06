/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
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

@NodeInfo(shortName = "add-method")
public class AddMethodNode extends RubyNode {

    @Child protected RubyNode receiver;
    @Child protected MethodDefinitionNode method;

    public AddMethodNode(RubyContext context, SourceSection section, RubyNode receiver, MethodDefinitionNode method) {
        super(context, section);
        this.receiver = adoptChild(receiver);
        this.method = adoptChild(method);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiver.execute(frame);

        final RubyMethod methodObject = (RubyMethod) method.execute(frame);

        final FrameSlot moduleFunctionFlagSlot = frame.getFrameDescriptor().findFrameSlot(RubyModule.MODULE_FUNCTION_FLAG_FRAME_SLOT_ID);

        boolean moduleFunctionFlag;

        if (moduleFunctionFlagSlot == null) {
            moduleFunctionFlag = false;
        } else {
            Object moduleFunctionObject;

            try {
                moduleFunctionObject = frame.getObject(moduleFunctionFlagSlot);
            } catch (FrameSlotTypeException e) {
                throw new RuntimeException(e);
            }

            if (moduleFunctionObject instanceof Boolean) {
                moduleFunctionFlag = (boolean) moduleFunctionObject;
            } else {
                moduleFunctionFlag = false;
            }
        }

        final RubyModule module = (RubyModule) receiverObject;

        final RubyMethod methodWithDeclaringModule = methodObject.withDeclaringModule(module);

        module.addMethod(methodWithDeclaringModule);

        if (moduleFunctionFlag) {
            module.getSingletonClass().addMethod(methodWithDeclaringModule);
        }

        return NilPlaceholder.INSTANCE;
    }
}
