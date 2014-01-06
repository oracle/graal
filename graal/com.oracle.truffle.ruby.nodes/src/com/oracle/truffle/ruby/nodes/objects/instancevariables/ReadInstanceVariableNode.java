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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.objects.*;

public abstract class ReadInstanceVariableNode extends RubyNode implements ReadNode {

    protected final String name;
    @Child protected RubyNode receiver;

    public ReadInstanceVariableNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver) {
        super(context, sourceSection);
        this.name = name;
        this.receiver = adoptChild(receiver);
    }

    public ReadInstanceVariableNode respecialize(RubyBasicObject receiverObject) {
        final StorageLocation storageLocation = receiverObject.getUpdatedObjectLayout().findStorageLocation(name);

        if (storageLocation == null) {
            return new ReadMissingInstanceVariableNode(getSourceSection(), name, receiver, receiverObject.getObjectLayout(), getContext());
        }

        if (storageLocation instanceof FixnumStorageLocation) {
            return new ReadFixnumInstanceVariableNode(getContext(), getSourceSection(), name, receiver, storageLocation.getObjectLayout(), (FixnumStorageLocation) storageLocation);
        } else if (storageLocation instanceof FloatStorageLocation) {
            return new ReadFloatInstanceVariableNode(getContext(), getSourceSection(), name, receiver, storageLocation.getObjectLayout(), (FloatStorageLocation) storageLocation);
        } else {
            return new ReadObjectInstanceVariableNode(getContext(), getSourceSection(), name, receiver, storageLocation.getObjectLayout(), (ObjectStorageLocation) storageLocation);
        }
    }

    public WriteInstanceVariableNode makeWriteNode(RubyNode rhs) {
        return new UninitializedWriteInstanceVariableNode(getContext(), getEncapsulatingSourceSection(), name, (RubyNode) receiver.copy(), rhs);
    }

    public String getName() {
        return name;
    }

    public RubyNode getReceiver() {
        return receiver;
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyContext context = getContext();

        try {
            final Object receiverObject = receiver.execute(frame);
            final RubyBasicObject receiverRubyObject = context.getCoreLibrary().box(receiverObject);

            final ObjectLayout layout = receiverRubyObject.getObjectLayout();
            final StorageLocation storageLocation = layout.findStorageLocation(name);

            if (storageLocation.isSet(receiverRubyObject)) {
                return context.makeString("instance-variable");
            } else {
                return NilPlaceholder.INSTANCE;
            }
        } catch (Exception e) {
            return NilPlaceholder.INSTANCE;
        }
    }
}
