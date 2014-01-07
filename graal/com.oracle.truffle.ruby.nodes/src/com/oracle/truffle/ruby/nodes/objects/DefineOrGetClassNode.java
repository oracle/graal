/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.objects;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Define a new class, or get the existing one of the same name.
 */
public class DefineOrGetClassNode extends RubyNode {

    private final String name;
    @Child protected RubyNode moduleDefinedIn;
    @Child protected RubyNode superClass;

    public DefineOrGetClassNode(RubyContext context, SourceSection sourceSection, String name, RubyNode moduleDefinedIn, RubyNode superClass) {
        super(context, sourceSection);
        this.name = name;
        this.moduleDefinedIn = adoptChild(moduleDefinedIn);
        this.superClass = adoptChild(superClass);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        CompilerAsserts.neverPartOfCompilation();

        final RubyContext context = getContext();

        final RubyModule moduleDefinedInObject = (RubyModule) moduleDefinedIn.execute(frame);

        // Look for a current definition of the class, or create a new one

        final Object constantValue = moduleDefinedInObject.lookupConstant(name);

        RubyClass definingClass;

        if (constantValue == null) {
            final Object self = frame.getArguments(RubyArguments.class).getSelf();

            RubyModule parentModule;

            if (self instanceof RubyModule) {
                parentModule = (RubyModule) self;
            } else {
                // Because it's top level, and so self is the magic main object
                parentModule = null;
            }

            final RubyClass superClassObject = (RubyClass) superClass.execute(frame);

            if (superClassObject instanceof RubyException.RubyExceptionClass) {
                definingClass = new RubyException.RubyExceptionClass(superClassObject, name);
            } else {
                definingClass = new RubyClass(parentModule, superClassObject, name);
            }

            moduleDefinedInObject.setConstant(name, definingClass);
            moduleDefinedInObject.getSingletonClass().setConstant(name, definingClass);

            definingClass.getRubyClass().include(moduleDefinedInObject);
        } else {
            if (constantValue instanceof RubyClass) {
                definingClass = (RubyClass) constantValue;
            } else {
                throw new RaiseException(context.getCoreLibrary().typeErrorIsNotA(constantValue.toString(), "class"));
            }
        }

        return definingClass;
    }
}
