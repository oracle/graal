/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.control;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents a block of code run with exception handlers. There's no {@code try} keyword in Ruby -
 * it's implicit - but it's similar to a try statement in any other language.
 */
@NodeInfo(shortName = "try")
public class TryNode extends RubyNode {

    @Child protected RubyNode tryPart;
    @Children final RescueNode[] rescueParts;
    @Child protected RubyNode elsePart;

    private final BranchProfile controlFlowProfile = new BranchProfile();

    public TryNode(RubyContext context, SourceSection sourceSection, RubyNode tryPart, RescueNode[] rescueParts, RubyNode elsePart) {
        super(context, sourceSection);
        this.tryPart = adoptChild(tryPart);
        this.rescueParts = adoptChildren(rescueParts);
        this.elsePart = adoptChild(elsePart);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        while (true) {
            try {
                final Object result = tryPart.execute(frame);
                elsePart.executeVoid(frame);
                return result;
            } catch (ControlFlowException exception) {
                controlFlowProfile.enter();

                throw exception;
            } catch (RuntimeException exception) {
                CompilerDirectives.transferToInterpreter();

                try {
                    return handleException(frame, exception);
                } catch (RetryException e) {
                    continue;
                }
            }
        }
    }

    private Object handleException(VirtualFrame frame, RuntimeException exception) {
        CompilerAsserts.neverPartOfCompilation();

        final RubyContext context = getContext();

        final RubyBasicObject rubyException = ExceptionTranslator.translateException(context, exception);

        context.getCoreLibrary().getGlobalVariablesObject().setInstanceVariable("$!", rubyException);

        for (RescueNode rescue : rescueParts) {
            if (rescue.canHandle(frame, rubyException)) {
                return rescue.execute(frame);
            }
        }

        throw exception;
    }

}
