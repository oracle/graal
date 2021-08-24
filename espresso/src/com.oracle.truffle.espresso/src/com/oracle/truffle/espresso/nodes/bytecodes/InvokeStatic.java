package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;

/**
 * INVOKESTATIC bytecode.
 *
 * <h3>Note</h3> The declaring class must be initialized before executing the method. Linking native
 * methods before, so class initialization must happen before the call target is computed.
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link AbstractMethodError} if the resolved method is abstract.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "INVOKESTATIC")
public abstract class InvokeStatic extends Node {

    public abstract Object execute(Method staticMethod, Object[] args);

    @Specialization
    Object callWithClassInitCheck(Method staticMethod, Object[] args,
                    @Cached InitCheck initCheck,
                    @Cached WithoutClassInitCheck invokeVirtual) {
        initCheck.execute(staticMethod.getDeclaringKlass());
        return invokeVirtual.execute(staticMethod, args);
    }

    @GenerateUncached
    @NodeInfo(shortName = "INVOKESTATIC !initcheck")
    public abstract static class WithoutClassInitCheck extends Node {

        protected static final int LIMIT = 2;

        public abstract Object execute(Method staticMethod, Object[] args);

        static boolean isInitializedOrInitializing(ObjectKlass klass) {
            int state = klass.getState();
            return state == ObjectKlass.INITIALIZED ||
                            state == ObjectKlass.ERRONEOUS ||
                            state >= ObjectKlass.PREPARED && Thread.holdsLock(klass); // initializing
                                                                                      // thread
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "LIMIT", //
                        guards = {
                                        "staticMethod == cachedStaticMethod"
                        }, //
                        assumptions = "resolvedMethod.getAssumption()")
        Object callDirect(Method staticMethod, Object[] args,
                        @Cached("staticMethod") Method cachedStaticMethod,
                        @Cached("methodLookup(cachedStaticMethod)") Method.MethodVersion resolvedMethod,
                        @Cached("create(resolvedMethod.getCallTargetNoInit())") DirectCallNode directCallNode) {
            assert isInitializedOrInitializing(resolvedMethod.getMethod().getDeclaringKlass());
            return directCallNode.call(args);
        }

        @Specialization(replaces = "callDirect")
        Object callIndirect(Method staticMethod, Object[] args,
                        @Cached IndirectCallNode indirectCallNode) {
            Method.MethodVersion target = methodLookup(staticMethod);
            assert isInitializedOrInitializing(target.getMethod().getDeclaringKlass());
            return indirectCallNode.call(target.getCallTarget(), args);
        }

        protected static Method.MethodVersion methodLookup(Method staticMethod) {
            assert staticMethod.isStatic();
            if (staticMethod.isRemovedByRedefition()) {
                /*
                 * Accept a slow path once the method has been removed put method behind a boundary
                 * to avoid a deopt loop.
                 */
                return ClassRedefinition.handleRemovedMethod(staticMethod, staticMethod.getDeclaringKlass(), null).getMethodVersion();
            }
            return staticMethod.getMethodVersion();
        }
    }
}
