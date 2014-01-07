/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.constants;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents a constant read from some object and cached, with the assumption that the object it
 * was read from is unmodified. If that assumption does not hold the read is uninitialized. If the
 * class of the receiver changes we also uninitialize.
 */
@NodeInfo(shortName = "cached-read-constant")
public class CachedReadConstantNode extends ReadConstantNode {

    private final RubyClass expectedClass;
    private final Assumption unmodifiedAssumption;

    private final Object value;

    private final boolean hasBoolean;
    private final boolean booleanValue;

    private final boolean hasInt;
    private final int intValue;

    private final boolean hasDouble;
    private final double doubleValue;

    private final BranchProfile boxBranchProfile = new BranchProfile();

    public CachedReadConstantNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver, RubyClass expectedClass, Object value) {
        super(context, sourceSection, name, receiver);

        this.expectedClass = expectedClass;
        unmodifiedAssumption = expectedClass.getUnmodifiedAssumption();

        this.value = value;

        /*
         * We could do this lazily as needed, but I'm sure the compiler will appreciate the fact
         * that these fields are all final.
         */

        if (value instanceof Boolean) {
            hasBoolean = true;
            booleanValue = (boolean) value;

            hasInt = false;
            intValue = -1;

            hasDouble = false;
            doubleValue = -1;
        } else if (value instanceof Integer) {
            hasBoolean = false;
            booleanValue = false;

            hasInt = true;
            intValue = (int) value;

            hasDouble = true;
            doubleValue = (int) value;
        } else if (value instanceof Double) {
            hasBoolean = false;
            booleanValue = false;

            hasInt = false;
            intValue = -1;

            hasDouble = true;
            doubleValue = (double) value;
        } else {
            hasBoolean = false;
            booleanValue = false;

            hasInt = false;
            intValue = -1;

            hasDouble = false;
            doubleValue = -1;
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            guard(frame);
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }

        return value;
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        guard(frame);

        if (hasBoolean) {
            return booleanValue;
        } else {
            throw new UnexpectedResultException(value);
        }
    }

    @Override
    public int executeFixnum(VirtualFrame frame) throws UnexpectedResultException {
        guard(frame);

        if (hasInt) {
            return intValue;
        } else {
            throw new UnexpectedResultException(value);
        }
    }

    @Override
    public double executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        guard(frame);

        if (hasDouble) {
            return doubleValue;
        } else {
            throw new UnexpectedResultException(value);
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
    }

    public void guard(VirtualFrame frame) throws UnexpectedResultException {
        final RubyContext context = getContext();

        final Object receiverObject = receiver.execute(frame);

        RubyBasicObject receiverRubyObject;

        // TODO(CS): put the boxing into a separate node that can specialize for each type it sees

        if (receiverObject instanceof RubyBasicObject) {
            receiverRubyObject = (RubyBasicObject) receiverObject;
        } else {
            boxBranchProfile.enter();
            receiverRubyObject = context.getCoreLibrary().box(receiverObject);
        }

        if (receiverRubyObject.getRubyClass() != expectedClass) {
            CompilerDirectives.transferToInterpreter();
            throw new UnexpectedResultException(uninitialize(receiverRubyObject));
        }

        try {
            unmodifiedAssumption.check();
        } catch (InvalidAssumptionException e) {
            throw new UnexpectedResultException(uninitialize(receiverRubyObject));
        }
    }

    private Object uninitialize(RubyBasicObject receiverObject) {
        return replace(new UninitializedReadConstantNode(getContext(), getSourceSection(), name, receiver)).execute(receiverObject);
    }

}
