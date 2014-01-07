/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.methods;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Any kind of Ruby method - so normal methods in classes and modules, but also blocks, procs,
 * lambdas and native methods written in Java.
 */
public class RubyMethod {

    private final SourceSection sourceSection;
    private final RubyModule declaringModule;
    private final UniqueMethodIdentifier uniqueIdentifier;
    private final String intrinsicName;
    private final String name;
    private final Visibility visibility;
    private final boolean undefined;

    private final MethodImplementation implementation;

    public RubyMethod(SourceSection sourceSection, RubyModule declaringModule, UniqueMethodIdentifier uniqueIdentifier, String intrinsicName, String name, Visibility visibility, boolean undefined,
                    MethodImplementation implementation) {
        this.sourceSection = sourceSection;
        this.declaringModule = declaringModule;
        this.uniqueIdentifier = uniqueIdentifier;
        this.intrinsicName = intrinsicName;
        this.name = name;
        this.visibility = visibility;
        this.undefined = undefined;
        this.implementation = implementation;
    }

    public Object call(PackedFrame caller, Object self, RubyProc block, Object... args) {
        assert RubyContext.shouldObjectBeVisible(self);
        assert RubyContext.shouldObjectsBeVisible(args);

        final Object result = implementation.call(caller, self, block, args);

        assert RubyContext.shouldObjectBeVisible(result);

        return result;
    }

    public SourceSection getSourceSection() {
        return sourceSection;
    }

    public UniqueMethodIdentifier getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    public String getIntrinsicName() {
        return intrinsicName;
    }

    public String getName() {
        return name;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public boolean isUndefined() {
        return undefined;
    }

    public MethodImplementation getImplementation() {
        return implementation;
    }

    public RubyMethod withNewName(String newName) {
        if (newName.equals(name)) {
            return this;
        }

        return new RubyMethod(sourceSection, declaringModule, uniqueIdentifier, intrinsicName, newName, visibility, undefined, implementation);
    }

    public RubyMethod withNewVisibility(Visibility newVisibility) {
        if (newVisibility == visibility) {
            return this;
        }

        return new RubyMethod(sourceSection, declaringModule, uniqueIdentifier, intrinsicName, name, newVisibility, undefined, implementation);
    }

    public RubyMethod withDeclaringModule(RubyModule newDeclaringModule) {
        if (newDeclaringModule == declaringModule) {
            return this;
        }

        return new RubyMethod(sourceSection, newDeclaringModule, uniqueIdentifier, intrinsicName, name, visibility, undefined, implementation);
    }

    public RubyMethod undefined() {
        if (undefined) {
            return this;
        }

        return new RubyMethod(sourceSection, declaringModule, uniqueIdentifier, intrinsicName, name, visibility, true, implementation);
    }

    public boolean isVisibleTo(RubyBasicObject caller) {
        if (caller instanceof RubyModule) {
            if (isVisibleTo((RubyModule) caller)) {
                return true;
            }
        }

        if (isVisibleTo(caller.getRubyClass())) {
            return true;
        }

        if (isVisibleTo(caller.getSingletonClass())) {
            return true;
        }

        return false;
    }

    private boolean isVisibleTo(RubyModule module) {
        switch (visibility) {
            case PUBLIC:
                return true;

            case PROTECTED:
                return true;

            case PRIVATE:
                if (module == declaringModule) {
                    return true;
                }

                if (module.getSingletonClass() == declaringModule) {
                    return true;
                }

                if (module.getParentModule() != null && isVisibleTo(module.getParentModule())) {
                    return true;
                }

                return false;

            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return implementation.toString();
    }

}
