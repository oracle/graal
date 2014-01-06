/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import java.math.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

@CoreClass(name = "Object")
public abstract class ObjectNodes {

    @CoreMethod(names = "class", maxArgs = 0)
    public abstract static class ClassNode extends CoreMethodNode {

        public ClassNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ClassNode(ClassNode prev) {
            super(prev);
        }

        @Specialization
        public RubyClass getClass(boolean value) {
            if (value) {
                return getContext().getCoreLibrary().getTrueClass();
            } else {
                return getContext().getCoreLibrary().getFalseClass();
            }
        }

        @Specialization
        public RubyClass getClass(@SuppressWarnings("unused") int value) {
            return getContext().getCoreLibrary().getFixnumClass();
        }

        @Specialization
        public RubyClass getClass(@SuppressWarnings("unused") BigInteger value) {
            return getContext().getCoreLibrary().getBignumClass();
        }

        @Specialization
        public RubyClass getClass(@SuppressWarnings("unused") double value) {
            return getContext().getCoreLibrary().getFloatClass();
        }

        @Specialization
        public RubyClass getClass(RubyBasicObject self) {
            return self.getRubyClass();
        }

    }

    @CoreMethod(names = "dup", maxArgs = 0)
    public abstract static class DupNode extends CoreMethodNode {

        public DupNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DupNode(DupNode prev) {
            super(prev);
        }

        @Specialization
        public Object dup(RubyObject self) {
            return self.dup();
        }

    }

    @CoreMethod(names = "extend", isSplatted = true, minArgs = 1)
    public abstract static class ExtendNode extends CoreMethodNode {

        public ExtendNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExtendNode(ExtendNode prev) {
            super(prev);
        }

        @Specialization
        public RubyBasicObject extend(RubyBasicObject self, Object[] args) {
            for (int n = 0; n < args.length; n++) {
                self.extend((RubyModule) args[n]);
            }

            return self;
        }

    }

    @CoreMethod(names = "freeze", maxArgs = 0)
    public abstract static class FreezeNode extends CoreMethodNode {

        public FreezeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public FreezeNode(FreezeNode prev) {
            super(prev);
        }

        @Specialization
        public RubyObject freeze(RubyObject self) {
            self.frozen = true;
            return self;
        }

    }

    @CoreMethod(names = "frozen?", maxArgs = 0)
    public abstract static class FrozenNode extends CoreMethodNode {

        public FrozenNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public FrozenNode(FrozenNode prev) {
            super(prev);
        }

        @Specialization
        public boolean isFrozen(RubyObject self) {
            return self.frozen;
        }

    }

    @CoreMethod(names = "inspect", maxArgs = 0)
    public abstract static class InspectNode extends CoreMethodNode {

        public InspectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InspectNode(InspectNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString inspect(boolean value) {
            return getContext().makeString(Boolean.toString(value));
        }

        @Specialization
        public RubyString inspect(int value) {
            return getContext().makeString(Integer.toString(value));
        }

        @Specialization
        public RubyString inspect(BigInteger value) {
            return getContext().makeString(value.toString());
        }

        @Specialization
        public RubyString inspect(double value) {
            return getContext().makeString(Double.toString(value));
        }

        @Specialization
        public RubyString inspect(RubyObject self) {
            return getContext().makeString(self.inspect());
        }

    }

    @CoreMethod(names = "instance_eval", needsBlock = true, maxArgs = 0)
    public abstract static class InstanceEvalNode extends CoreMethodNode {

        public InstanceEvalNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InstanceEvalNode(InstanceEvalNode prev) {
            super(prev);
        }

        @Specialization
        public Object instanceEval(VirtualFrame frame, RubyObject self, RubyProc block) {
            return block.callWithModifiedSelf(frame.pack(), self);
        }

    }

    @CoreMethod(names = "instance_variable_defined?", minArgs = 1, maxArgs = 1)
    public abstract static class InstanceVariableDefinedNode extends CoreMethodNode {

        public InstanceVariableDefinedNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InstanceVariableDefinedNode(InstanceVariableDefinedNode prev) {
            super(prev);
        }

        @Specialization
        public boolean isInstanceVariableDefined(RubyBasicObject object, RubyString name) {
            return object.isInstanceVariableDefined(RubyObject.checkInstanceVariableName(getContext(), name.toString()));
        }

        @Specialization
        public boolean isInstanceVariableDefined(RubyBasicObject object, RubySymbol name) {
            return object.isInstanceVariableDefined(RubyObject.checkInstanceVariableName(getContext(), name.toString()));
        }

    }

    @CoreMethod(names = "instance_variable_get", minArgs = 1, maxArgs = 1)
    public abstract static class InstanceVariableGetNode extends CoreMethodNode {

        public InstanceVariableGetNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InstanceVariableGetNode(InstanceVariableGetNode prev) {
            super(prev);
        }

        @Specialization
        public Object isInstanceVariableGet(RubyBasicObject object, RubyString name) {
            return object.getInstanceVariable(RubyObject.checkInstanceVariableName(getContext(), name.toString()));
        }

        @Specialization
        public Object isInstanceVariableGet(RubyBasicObject object, RubySymbol name) {
            return object.getInstanceVariable(RubyObject.checkInstanceVariableName(getContext(), name.toString()));
        }

    }

    @CoreMethod(names = "instance_variable_set", minArgs = 2, maxArgs = 2)
    public abstract static class InstanceVariableSetNode extends CoreMethodNode {

        public InstanceVariableSetNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InstanceVariableSetNode(InstanceVariableSetNode prev) {
            super(prev);
        }

        @Specialization
        public Object isInstanceVariableSet(RubyBasicObject object, RubyString name, Object value) {
            object.setInstanceVariable(RubyObject.checkInstanceVariableName(getContext(), name.toString()), value);
            return value;
        }

        @Specialization
        public Object isInstanceVariableSet(RubyBasicObject object, RubySymbol name, Object value) {
            object.setInstanceVariable(RubyObject.checkInstanceVariableName(getContext(), name.toString()), value);
            return value;
        }

    }

    @CoreMethod(names = "instance_variables", maxArgs = 0)
    public abstract static class InstanceVariablesNode extends CoreMethodNode {

        public InstanceVariablesNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InstanceVariablesNode(InstanceVariablesNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray instanceVariables(RubyObject self) {
            final String[] instanceVariableNames = self.getInstanceVariableNames();

            Arrays.sort(instanceVariableNames);

            final RubyArray array = new RubyArray(getContext().getCoreLibrary().getArrayClass());

            for (String name : instanceVariableNames) {
                array.push(new RubyString(getContext().getCoreLibrary().getStringClass(), name));
            }

            return array;
        }

    }

    @CoreMethod(names = {"is_a?", "instance_of?", "kind_of?"}, minArgs = 1, maxArgs = 1)
    public abstract static class IsANode extends CoreMethodNode {

        public IsANode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public IsANode(IsANode prev) {
            super(prev);
        }

        @Specialization
        public boolean isA(@SuppressWarnings("unused") RubyObject self, @SuppressWarnings("unused") NilPlaceholder nil) {
            return false;
        }

        @Specialization
        public boolean isA(RubyObject self, RubyClass rubyClass) {
            return self.getRubyClass().assignableTo(rubyClass);
        }

    }

    @CoreMethod(names = "methods", minArgs = 0, maxArgs = 1)
    public abstract static class MethodsNode extends CoreMethodNode {

        public MethodsNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public MethodsNode(MethodsNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray methods(RubyObject self, boolean includeInherited) {
            if (!includeInherited) {
                self.getRubyClass().getContext().implementationMessage("Object#methods always returns inherited methods at the moment");
            }

            return methods(self, UndefinedPlaceholder.INSTANCE);
        }

        @Specialization
        public RubyArray methods(RubyObject self, @SuppressWarnings("unused") UndefinedPlaceholder includeInherited) {
            final RubyArray array = new RubyArray(self.getRubyClass().getContext().getCoreLibrary().getArrayClass());

            final Map<String, RubyMethod> methods = new HashMap<>();

            self.getLookupNode().getMethods(methods);

            for (RubyMethod method : methods.values()) {
                if (method.getVisibility() == Visibility.PUBLIC || method.getVisibility() == Visibility.PROTECTED) {
                    array.push(new RubySymbol(self.getRubyClass().getContext().getCoreLibrary().getSymbolClass(), method.getName()));
                }
            }

            return array;
        }

    }

    @CoreMethod(names = "nil?", needsSelf = false, maxArgs = 0)
    public abstract static class NilNode extends CoreMethodNode {

        public NilNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public NilNode(NilNode prev) {
            super(prev);
        }

        @Specialization
        public boolean nil() {
            return false;
        }
    }

    @CoreMethod(names = "object_id", needsSelf = true, maxArgs = 0)
    public abstract static class ObjectIDNode extends CoreMethodNode {

        public ObjectIDNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ObjectIDNode(ObjectIDNode prev) {
            super(prev);
        }

        @Specialization
        public Object objectID(RubyBasicObject object) {
            return GeneralConversions.fixnumOrBignum(object.getObjectID());
        }

    }

    @CoreMethod(names = "respond_to?", minArgs = 1, maxArgs = 2)
    public abstract static class RespondToNode extends CoreMethodNode {

        public RespondToNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RespondToNode(RespondToNode prev) {
            super(prev);
        }

        @Specialization(order = 1)
        public boolean doesRespondTo(Object object, RubyString name, @SuppressWarnings("unused") UndefinedPlaceholder checkVisibility) {
            return doesRespondTo(getContext().getCoreLibrary().box(object), name.toString(), false);
        }

        @Specialization(order = 2)
        public boolean doesRespondTo(Object object, RubyString name, boolean dontCheckVisibility) {
            return doesRespondTo(getContext().getCoreLibrary().box(object), name.toString(), dontCheckVisibility);
        }

        @Specialization(order = 3)
        public boolean doesRespondTo(Object object, RubySymbol name, @SuppressWarnings("unused") UndefinedPlaceholder checkVisibility) {
            return doesRespondTo(getContext().getCoreLibrary().box(object), name.toString(), false);
        }

        @Specialization(order = 4)
        public boolean doesRespondTo(Object object, RubySymbol name, boolean dontCheckVisibility) {
            return doesRespondTo(getContext().getCoreLibrary().box(object), name.toString(), dontCheckVisibility);
        }

        private static boolean doesRespondTo(RubyBasicObject object, String name, boolean dontCheckVisibility) {
            final RubyMethod method = object.getLookupNode().lookupMethod(name);

            if (method == null || method.isUndefined()) {
                return false;
            }

            if (dontCheckVisibility) {
                return true;
            } else {
                return method.getVisibility() == Visibility.PUBLIC;
            }
        }

    }

    @CoreMethod(names = "singleton_class", maxArgs = 0)
    public abstract static class SingletonClassNode extends CoreMethodNode {

        public SingletonClassNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SingletonClassNode(SingletonClassNode prev) {
            super(prev);
        }

        @Specialization
        public RubyClass singletonClass(RubyBasicObject self) {
            return self.getSingletonClass();
        }

    }

    @CoreMethod(names = "singleton_methods", minArgs = 0, maxArgs = 1)
    public abstract static class SingletonMethodsNode extends CoreMethodNode {

        public SingletonMethodsNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SingletonMethodsNode(SingletonMethodsNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray singletonMethods(RubyObject self, boolean includeInherited) {
            if (!includeInherited) {
                self.getRubyClass().getContext().implementationMessage("Object#singleton_methods always returns inherited methods at the moment");
            }

            return singletonMethods(self, UndefinedPlaceholder.INSTANCE);
        }

        @Specialization
        public RubyArray singletonMethods(RubyObject self, @SuppressWarnings("unused") UndefinedPlaceholder includeInherited) {
            final RubyArray array = new RubyArray(self.getRubyClass().getContext().getCoreLibrary().getArrayClass());

            for (RubyMethod method : self.getSingletonClass().getDeclaredMethods()) {
                array.push(new RubySymbol(self.getRubyClass().getContext().getCoreLibrary().getSymbolClass(), method.getName()));
            }

            return array;
        }

    }

    @CoreMethod(names = "to_s", maxArgs = 0)
    public abstract static class ToSNode extends CoreMethodNode {

        public ToSNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToSNode(ToSNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString toS(RubyObject self) {
            return getContext().makeString(self.toString());
        }

    }

}
