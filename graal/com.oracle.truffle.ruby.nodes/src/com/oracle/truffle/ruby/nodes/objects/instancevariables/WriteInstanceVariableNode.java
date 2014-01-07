/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.objects.instancevariables;

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.objects.*;

public abstract class WriteInstanceVariableNode extends RubyNode implements WriteNode {

    protected final String name;
    @Child protected RubyNode receiver;
    @Child protected RubyNode rhs;

    public WriteInstanceVariableNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver, RubyNode rhs) {
        super(context, sourceSection);
        this.name = name;
        this.receiver = adoptChild(receiver);
        this.rhs = adoptChild(rhs);
    }

    public WriteInstanceVariableNode respecialize(RubyBasicObject receiverObject) {
        StorageLocation storageLocation = receiverObject.getObjectLayout().findStorageLocation(name);

        if (storageLocation == null) {
            throw new RuntimeException("Storage location should be found at this point");
        }

        if (storageLocation instanceof FixnumStorageLocation) {
            return new WriteFixnumInstanceVariableNode(getContext(), getSourceSection(), name, receiver, rhs, storageLocation.getObjectLayout(), (FixnumStorageLocation) storageLocation);
        } else if (storageLocation instanceof FloatStorageLocation) {
            return new WriteFloatInstanceVariableNode(getContext(), getSourceSection(), name, receiver, rhs, storageLocation.getObjectLayout(), (FloatStorageLocation) storageLocation);
        } else {
            return new WriteObjectInstanceVariableNode(getContext(), getSourceSection(), name, receiver, rhs, storageLocation.getObjectLayout(), (ObjectStorageLocation) storageLocation);
        }
    }

    @Override
    public ReadInstanceVariableNode makeReadNode() {
        return new UninitializedReadInstanceVariableNode(getContext(), getEncapsulatingSourceSection(), name, (RubyNode) receiver.copy());
    }

    public String getName() {
        return name;
    }

    public RubyNode getReceiver() {
        return receiver;
    }

    public RubyNode getRHS() {
        return rhs;
    }

}
