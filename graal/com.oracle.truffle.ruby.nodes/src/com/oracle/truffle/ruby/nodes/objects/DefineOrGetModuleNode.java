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
 * Define a new module, or get the existing one of the same name.
 */
public class DefineOrGetModuleNode extends RubyNode {

    private final String name;
    @Child protected RubyNode moduleDefinedIn;

    public DefineOrGetModuleNode(RubyContext context, SourceSection sourceSection, String name, RubyNode moduleDefinedIn) {
        super(context, sourceSection);
        this.name = name;
        this.moduleDefinedIn = adoptChild(moduleDefinedIn);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        CompilerAsserts.neverPartOfCompilation();

        final RubyContext context = getContext();

        final RubyModule moduleDefinedInObject = (RubyModule) moduleDefinedIn.execute(frame);

        // Look for a current definition of the module, or create a new one

        final Object constantValue = moduleDefinedInObject.lookupConstant(name);

        RubyModule definingModule;

        if (constantValue == null) {
            final Object self = frame.getArguments(RubyArguments.class).getSelf();

            RubyModule parentModule;

            if (self instanceof RubyModule) {
                parentModule = (RubyModule) self;
            } else {
                // Because it's top level, and so self is the magic main object
                parentModule = null;
            }

            definingModule = new RubyModule(context.getCoreLibrary().getModuleClass(), parentModule, name);
            moduleDefinedInObject.setConstant(name, definingModule);
            moduleDefinedInObject.getSingletonClass().setConstant(name, definingModule);
        } else {
            if (constantValue instanceof RubyModule) {
                definingModule = (RubyModule) constantValue;
            } else {
                throw new RaiseException(context.getCoreLibrary().typeErrorIsNotA(constantValue.toString(), "module"));
            }
        }

        return definingModule;
    }

}
