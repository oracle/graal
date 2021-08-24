package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * INVOKESPECIAL bytecode.
 *
 * <p>
 * The receiver must be included as the first element of the arguments passed to
 * {@link #execute(StaticObject, Object[])}. e.g.
 * <code>invokeVirtual.execute(virtualMethod, args[0], args);</code>
 * </p>
 *
 * <p>
 * Method resolution does not perform any access checks, the caller is responsible to pass a
 * compatible receiver.
 * </p>
 */
@NodeInfo(shortName = "INVOKEINTERFACE")
public abstract class InvokeInterface extends Node {

    final Method resolutionSeed;

    protected InvokeInterface(Method resolutionSeed) {
        this.resolutionSeed = resolutionSeed;
    }

    public abstract Object execute(StaticObject receiver, Object[] args);

    @Specialization
    Object executeWithNullCheck(StaticObject receiver, Object[] args,
                    @Cached NullCheck nullCheck,
                    @Cached("create(resolutionSeed)") WithoutNullCheck invokeInterface) {
        assert args[0] == receiver;
        return invokeInterface.execute(nullCheck.execute(receiver), args);
    }

    @ReportPolymorphism
    @ImportStatic(InvokeInterface.class)
    @NodeInfo(shortName = "INVOKEINTERFACE !nullcheck")
    public abstract static class WithoutNullCheck extends Node {

        protected static final int LIMIT = 4;

        final Method resolutionSeed;

        protected WithoutNullCheck(Method resolutionSeed) {
            this.resolutionSeed = resolutionSeed;
        }

        public abstract Object execute(StaticObject receiver, Object[] args);

        @SuppressWarnings("unused")
        @Specialization(limit = "LIMIT", //
                        guards = "receiver.getKlass() == cachedKlass", //
                        assumptions = "resolvedMethod.getAssumption()")
        Object callDirect(StaticObject receiver, Object[] args,
                        @Cached("receiver.getKlass()") Klass cachedKlass,
                        @Cached("methodLookup(resolutionSeed, receiver)") Method.MethodVersion resolvedMethod,
                        @Cached("create(resolvedMethod.getMethod().getCallTargetNoInit())") DirectCallNode directCallNode) {
            assert args[0] == receiver;
            assert !StaticObject.isNull(receiver);
            assert resolvedMethod.getMethod().getDeclaringKlass().isInitialized();
            return directCallNode.call(args);
        }

        @Specialization
        @ReportPolymorphism.Megamorphic
        Object callIndirect(StaticObject receiver, Object[] args,
                        @Cached IndirectCallNode indirectCallNode) {
            assert args[0] == receiver;
            assert !StaticObject.isNull(receiver);
            // itable lookup.
            Method.MethodVersion target = methodLookup(resolutionSeed, receiver);
            assert target.getMethod().getDeclaringKlass().isInitialized();
            return indirectCallNode.call(target.getCallTarget(), args);
        }
    }

    static Method.MethodVersion methodLookup(Method resolutionSeed, StaticObject receiver) {
        assert !receiver.getKlass().isArray();
        if (resolutionSeed.isRemovedByRedefition()) {
            /*
             * Accept a slow path once the method has been removed put method behind a boundary to
             * avoid a deopt loop
             */
            return ClassRedefinition.handleRemovedMethod(resolutionSeed, receiver.getKlass(), receiver).getMethodVersion();
        }

        int iTableIndex = resolutionSeed.getITableIndex();
        Method method = ((ObjectKlass) receiver.getKlass()).itableLookup(resolutionSeed.getDeclaringKlass(), iTableIndex);
        if (!method.isPublic()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = receiver.getKlass().getMeta();
            throw meta.throwException(meta.java_lang_IllegalAccessError);
        }
        return method.getMethodVersion();
    }

    @GenerateUncached
    @NodeInfo(shortName = "INVOKEINTERFACE dynamic")
    public abstract static class Dynamic extends Node {

        protected static final int LIMIT = 4;

        public abstract Object execute(Method resolutionSeed, StaticObject receiver, Object[] args);

        @Specialization
        Object executeWithNullCheck(Method resolutionSeed, StaticObject receiver, Object[] args,
                        @Cached NullCheck nullCheck,
                        @Cached WithoutNullCheck invokeInterface) {
            assert args[0] == receiver;
            return invokeInterface.execute(resolutionSeed, nullCheck.execute(receiver), args);
        }

        @GenerateUncached
        @ReportPolymorphism
        @NodeInfo(shortName = "INVOKEINTERFACE dynamic !nullcheck")
        public abstract static class WithoutNullCheck extends Node {

            protected static final int LIMIT = 4;

            public abstract Object execute(Method resolutionSeed, StaticObject receiver, Object[] args);

            @Specialization(limit = "LIMIT", //
                            guards = "resolutionSeed == cachedResolutionSeed")
            Object doCached(Method resolutionSeed, StaticObject receiver, Object[] args,
                            @Cached("resolutionSeed") Method cachedResolutionSeed,
                            @Cached("create(cachedResolutionSeed)") InvokeInterface.WithoutNullCheck invokeInterface) {
                assert args[0] == receiver;
                assert !StaticObject.isNull(receiver);
                return invokeInterface.execute(receiver, args);
            }

            @ReportPolymorphism.Megamorphic
            @Specialization(replaces = "doCached")
            Object doGeneric(Method resolutionSeed, StaticObject receiver, Object[] args,
                            @Cached IndirectCallNode indirectCallNode) {
                assert args[0] == receiver;
                assert !StaticObject.isNull(receiver);
                Method.MethodVersion target = methodLookup(resolutionSeed, receiver);
                assert target.getMethod().getDeclaringKlass().isInitialized();
                return indirectCallNode.call(target.getCallTarget(), args);
            }
        }
    }
}
