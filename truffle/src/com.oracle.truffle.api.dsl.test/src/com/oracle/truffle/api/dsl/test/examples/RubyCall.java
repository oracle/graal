/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.dsl.test.examples;

import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createArguments;
import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createTarget;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.examples.RubyCallFactory.RubyDispatchNodeGen;
import com.oracle.truffle.api.dsl.test.examples.RubyCallFactory.RubyHeadNodeGen;
import com.oracle.truffle.api.dsl.test.examples.RubyCallFactory.RubyLookupNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.CyclicAssumption;

/**
 * This example illustrates a simplified version of a Ruby function call semantics (RubyHeadNode).
 * The example usage shows how methods can be redefined in this implementation.
 */
@SuppressWarnings("unused")
public class RubyCall {

    @Test
    public void testCall() {
        RubyHeadNode node = RubyHeadNodeGen.create(createArguments(4));
        CallTarget nodeTarget = createTarget(node);
        final Object firstArgument = "someArgument";

        // dummyMethod is just going to return the some argument of the function
        final Object testMethodName = "getSomeArgument";
        // implementation returns first argument
        InternalMethod aClassTestMethod = new InternalMethod(ExampleNode.createDummyTarget(3));
        // implementation returns second argument
        InternalMethod bClassTestMethod = new InternalMethod(ExampleNode.createDummyTarget(4));
        // implementation returns third argument
        InternalMethod cClassTestMethod = new InternalMethod(ExampleNode.createDummyTarget(5));

        // defines hierarchy C extends B extends A
        RubyClass aClass = new RubyClass("A", null);
        RubyClass bClass = new RubyClass("B", aClass);
        RubyClass cClass = new RubyClass("C", bClass);

        RubyObject aInstance = new RubyObject(aClass);
        RubyObject bInstance = new RubyObject(bClass);
        RubyObject cInstance = new RubyObject(cClass);

        // undefined method call
        assertEquals(RubyObject.NIL, nodeTarget.call(cInstance, testMethodName, null, new Object[]{firstArgument}));

        // method defined in a
        aClass.addMethod(testMethodName, aClassTestMethod);
        assertEquals(firstArgument, nodeTarget.call(aInstance, testMethodName, null, new Object[]{firstArgument}));
        assertEquals(firstArgument, nodeTarget.call(bInstance, testMethodName, null, new Object[]{firstArgument}));
        assertEquals(firstArgument, nodeTarget.call(cInstance, testMethodName, null, new Object[]{firstArgument}));

        // method redefined in b
        bClass.addMethod(testMethodName, bClassTestMethod);
        assertEquals(firstArgument, nodeTarget.call(aInstance, testMethodName, null, new Object[]{firstArgument}));
        assertEquals(firstArgument, nodeTarget.call(bInstance, testMethodName, null, new Object[]{null, firstArgument}));
        assertEquals(firstArgument, nodeTarget.call(cInstance, testMethodName, null, new Object[]{null, firstArgument}));

        // method redefined in c
        cClass.addMethod(testMethodName, cClassTestMethod);
        assertEquals(firstArgument, nodeTarget.call(aInstance, testMethodName, null, new Object[]{firstArgument}));
        assertEquals(firstArgument, nodeTarget.call(bInstance, testMethodName, null, new Object[]{null, firstArgument}));
        assertEquals(firstArgument, nodeTarget.call(cInstance, testMethodName, null, new Object[]{null, null, firstArgument}));

    }

    public static class RubyHeadNode extends ExampleNode {

        @Child private RubyLookupNode lookup = RubyLookupNodeGen.create();
        @Child private RubyDispatchNode dispatch = RubyDispatchNodeGen.create();

        @Specialization
        public Object doCall(VirtualFrame frame, RubyObject receiverObject, Object methodName, Object blockObject, Object... argumentsObjects) {
            InternalMethod method = lookup.executeLookup(receiverObject, methodName);

            Object[] packedArguments = new Object[argumentsObjects.length + 3];
            packedArguments[0] = method;
            packedArguments[1] = receiverObject;
            packedArguments[2] = blockObject;
            System.arraycopy(argumentsObjects, 0, packedArguments, 3, argumentsObjects.length);

            return dispatch.executeDispatch(frame, method, packedArguments);
        }
    }

    public abstract static class RubyLookupNode extends Node {

        public abstract InternalMethod executeLookup(RubyObject receiver, Object method);

        @Specialization(guards = "receiver.getRubyClass() == cachedClass", assumptions = "cachedClass.getDependentAssumptions()")
        protected static InternalMethod cachedLookup(RubyObject receiver, Object name, //
                        @Cached("receiver.getRubyClass()") RubyClass cachedClass, //
                        @Cached("genericLookup(receiver, name)") InternalMethod cachedLookup) {
            return cachedLookup;
        }

        @Specialization(replaces = "cachedLookup")
        protected static InternalMethod genericLookup(RubyObject receiver, Object name) {
            return receiver.getRubyClass().lookup(name);
        }

    }

    @ImportStatic(InternalMethod.class)
    public abstract static class RubyDispatchNode extends Node {

        public abstract Object executeDispatch(VirtualFrame frame, InternalMethod function, Object[] packedArguments);

        /*
         * Please note that cachedMethod != METHOD_MISSING is invoked once at specialization
         * instantiation. It is never executed on the fast path.
         */
        @Specialization(guards = {"method == cachedMethod", "cachedMethod != METHOD_MISSING"})
        protected static Object directCall(InternalMethod method, Object[] arguments, //
                        @Cached("method") InternalMethod cachedMethod, //
                        @Cached("create(cachedMethod.getTarget())") DirectCallNode callNode) {
            return callNode.call(arguments);
        }

        /*
         * The method == METHOD_MISSING can fold if the RubyLookup results just in a single entry
         * returning the constant METHOD_MISSING.
         */
        @Specialization(guards = "method == METHOD_MISSING")
        protected static Object methodMissing(InternalMethod method, Object[] arguments) {
            // a real implementation would do a call to a method named method_missing here
            return RubyObject.NIL;
        }

        @Specialization(replaces = "directCall", guards = "method != METHOD_MISSING")
        protected static Object indirectCall(InternalMethod method, Object[] arguments, //
                        @Cached("create()") IndirectCallNode callNode) {
            return callNode.call(method.getTarget(), arguments);
        }

    }

    public static final class RubyObject {

        public static final RubyObject NIL = new RubyObject(null);

        private final RubyClass rubyClass;

        public RubyObject(RubyClass rubyClass) {
            this.rubyClass = rubyClass;
        }

        public RubyClass getRubyClass() {
            return rubyClass;
        }

        @Override
        public String toString() {
            return "RubyObject[class=" + rubyClass + "]";
        }

    }

    public static final class RubyClass /* this would extend RubyModule */ {

        private final String name;
        private final RubyClass parent; // this would be a RubyModule
        private final CyclicAssumption unmodified;
        private final Map<Object, InternalMethod> methods = new HashMap<>();
        private Assumption[] cachedDependentAssumptions;
        private final int depth;

        public RubyClass(String name, RubyClass parent) {
            this.name = name;
            this.parent = parent;
            this.unmodified = new CyclicAssumption("unmodified class " + name);

            // lookup depth for array allocation
            RubyClass clazz = parent;
            int currentDepth = 1;
            while (clazz != null) {
                currentDepth++;
                clazz = clazz.parent;
            }
            this.depth = currentDepth;
        }

        @TruffleBoundary
        public InternalMethod lookup(Object methodName) {
            InternalMethod method = methods.get(methodName);
            if (method == null) {
                if (parent != null) {
                    return parent.lookup(methodName);
                } else {
                    return InternalMethod.METHOD_MISSING;
                }
            } else {
                return method;
            }
        }

        @TruffleBoundary
        public void addMethod(Object methodName, InternalMethod method) {
            // check for existing method omitted for simplicity
            this.methods.put(methodName, method);
            this.unmodified.invalidate();
        }

        /*
         * Method collects all unmodified assumptions in the class hierarchy. The result is cached
         * per class to void recreation per call site.
         */
        @TruffleBoundary
        public Assumption[] getDependentAssumptions() {
            Assumption[] dependentAssumptions = cachedDependentAssumptions;
            if (dependentAssumptions != null) {
                // we can use the cached dependent assumptions only if they are still valid
                for (Assumption assumption : cachedDependentAssumptions) {
                    if (!assumption.isValid()) {
                        dependentAssumptions = null;
                        break;
                    }
                }
            }
            if (dependentAssumptions == null) {
                cachedDependentAssumptions = dependentAssumptions = createDependentAssumptions();
            }
            return dependentAssumptions;
        }

        @Override
        public String toString() {
            return "RubyClass[name=" + name + "]";
        }

        private Assumption[] createDependentAssumptions() {
            Assumption[] dependentAssumptions;
            RubyClass clazz = this;
            dependentAssumptions = new Assumption[depth];

            // populate array
            int index = 0;
            do {
                dependentAssumptions[index] = clazz.unmodified.getAssumption();
                index++;
                clazz = clazz.parent;
            } while (clazz != null);
            return dependentAssumptions;
        }
    }

    public static final class InternalMethod {

        public static final InternalMethod METHOD_MISSING = new InternalMethod(null);

        private final CallTarget target;

        public InternalMethod(CallTarget target) {
            this.target = target;
        }

        public CallTarget getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return "InternalMethod[target=" + getTarget() + "]";
        }

    }

}
