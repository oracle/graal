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

@NodeInfo(shortName = "@Object")
public class ReadObjectInstanceVariableNode extends ReadSpecializedInstanceVariableNode {

    private final ObjectStorageLocation storageLocation;

    public ReadObjectInstanceVariableNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver, ObjectLayout objectLayout, ObjectStorageLocation storageLocation) {
        super(context, sourceSection, name, receiver, objectLayout);
        this.storageLocation = storageLocation;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyBasicObject receiverObject = (RubyBasicObject) receiver.execute(frame);

        final ObjectLayout receiverLayout = receiverObject.getObjectLayout();

        final boolean condition = receiverLayout == objectLayout;

        if (condition) {
            assert receiverLayout != null;
            return storageLocation.read(receiverObject, condition);
        } else {
            CompilerDirectives.transferToInterpreter();
            replace(respecialize(receiverObject));
            return receiverObject.getInstanceVariable(name);
        }
    }

}
