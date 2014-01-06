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
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.objects.*;

@NodeInfo(shortName = "@Object=")
public class WriteObjectInstanceVariableNode extends WriteSpecializedInstanceVariableNode {

    private final ObjectStorageLocation storageLocation;

    public WriteObjectInstanceVariableNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver, RubyNode rhs, ObjectLayout objectLayout,
                    ObjectStorageLocation storageLocation) {
        super(context, sourceSection, name, receiver, rhs, objectLayout);
        this.storageLocation = storageLocation;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyBasicObject receiverObject = (RubyBasicObject) receiver.execute(frame);
        final Object value = rhs.execute(frame);

        final ObjectLayout receiverLayout = receiverObject.getObjectLayout();

        if (receiverLayout != objectLayout) {
            CompilerDirectives.transferToInterpreter();
            receiverObject.setInstanceVariable(name, value);
            replace(respecialize(receiverObject));
            return value;
        }

        assert receiverLayout != null;

        storageLocation.write(receiverObject, value);
        return value;
    }

}
