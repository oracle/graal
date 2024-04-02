package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.interop.CandidateMethodWithArgs;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethod;
import com.oracle.truffle.espresso.nodes.interop.LookupVirtualMethodNode;
import com.oracle.truffle.espresso.nodes.interop.MethodArgsUtils;
import com.oracle.truffle.espresso.nodes.interop.OverLoadedMethodSelectorNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.InteropUtils;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Lookups and invokes an Espresso method with interop conventions.
 *
 * <p>
 * Espresso maintains some invariants at the boundary e.g. Foreign objects are always "wrapped" (and
 * a class assigned) when entering into Espresso and always unwrapped when returning. Foreign
 * exceptions (leaving this method) are always unwrapped. Besides these invariants, Espresso tries
 * to select the (most) specific method in case of ambiguity caused by method overloads or
 * vargars...
 *
 * <p>
 * This helper node should be used by all interop invocations into Espresso (instance/static methods
 * and constructors) so the behavior is consistent.
 *
 * <p>
 * throws UnsupportedTypeException if the arguments cannot be coerced to the target method
 * signature, or when a single (most) specific method cannot be disambiguated e.g. overloads or
 * varargs...
 */
public abstract class InteropLookupAndInvoke extends EspressoNode {
    public abstract Object execute(StaticObject receiver, Klass klass, Object[] arguments, String member)
                    throws ArityException, UnsupportedTypeException;

    @GenerateUncached
    public abstract static class Virtual extends InteropLookupAndInvoke {
        @Specialization
        public Object doVirtual(StaticObject receiver, Klass klass, Object[] arguments, String member,
                        @Cached LookupVirtualMethodNode lookup,
                        @Cached OverLoadedMethodSelectorNode selector,
                        @Cached ToEspressoNode.DynamicToEspresso toEspresso,
                        @Cached InvokeEspressoNode invoke,
                        @Cached InlinedBranchProfile single,
                        @Cached InlinedBranchProfile nonVarargs,
                        @Cached InlinedBranchProfile varargs,
                        @Cached InlinedBranchProfile multiple,
                        @Cached InlinedBranchProfile error,
                        @Cached InlinedBranchProfile exception)
                        throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            Method[] candidates = lookup.execute(klass, member, arguments.length);
            if (candidates != null) {
                return selectAndInvoke(receiver, arguments, candidates, this, selector, toEspresso, invoke,
                                single, nonVarargs, varargs, multiple, error, exception);
            }
            error.enter(this);
            throw ArityException.create(arguments.length + 1, -1, arguments.length);
        }
    }

    @GenerateUncached
    public abstract static class NonVirtual extends InteropLookupAndInvoke {
        @Specialization
        public Object doNonVirtual(StaticObject receiver, Klass klass, Object[] arguments, String member,
                        @Cached LookupDeclaredMethod lookup,
                        @Cached OverLoadedMethodSelectorNode selector,
                        @Cached ToEspressoNode.DynamicToEspresso toEspresso,
                        @Cached InvokeEspressoNode invoke,
                        @Cached InlinedBranchProfile single,
                        @Cached InlinedBranchProfile nonVarargs,
                        @Cached InlinedBranchProfile varargs,
                        @Cached InlinedBranchProfile multiple,
                        @Cached InlinedBranchProfile error,
                        @Cached InlinedBranchProfile exception)
                        throws ArityException, UnsupportedTypeException {
            boolean isStatic = receiver == null;
            Method[] candidates = lookup.execute(klass, member, true, isStatic, arguments.length);
            if (candidates != null) {
                return selectAndInvoke(receiver, arguments, candidates, this, selector, toEspresso, invoke,
                                single, nonVarargs, varargs, multiple, error, exception);
            }
            error.enter(this);
            throw ArityException.create(arguments.length + 1, -1, arguments.length);
        }
    }

    private static Object selectAndInvoke(StaticObject receiver, Object[] arguments, Method[] candidates,
                    InteropLookupAndInvoke node,
                    OverLoadedMethodSelectorNode selector,
                    ToEspressoNode.DynamicToEspresso toEspresso,
                    InvokeEspressoNode invoke,
                    InlinedBranchProfile single,
                    InlinedBranchProfile nonVarargs,
                    InlinedBranchProfile varargs,
                    InlinedBranchProfile multiple,
                    InlinedBranchProfile error,
                    InlinedBranchProfile exception)
                    throws ArityException, UnsupportedTypeException {
        assert candidates.length > 0;
        try {
            if (candidates.length == 1) {
                single.enter(node);
                // common case with no overloads
                Method m = candidates[0];
                assert m.isPublic();
                if (!m.isVarargs()) {
                    nonVarargs.enter(node);
                    assert m.getParameterCount() == arguments.length;
                    return invoke.execute(m, receiver, arguments);
                } else {
                    varargs.enter(node);
                    CandidateMethodWithArgs matched = MethodArgsUtils.matchCandidate(m, arguments, m.resolveParameterKlasses(), toEspresso);
                    if (matched != null) {
                        matched = MethodArgsUtils.ensureVarArgsArrayCreated(matched);
                        if (matched != null) {
                            return invoke.execute(matched.getMethod(), receiver, matched.getConvertedArgs(), true);
                        }
                    }
                    error.enter(node);
                    throw UnsupportedTypeException.create(arguments);
                }
            } else {
                multiple.enter(node);
                // multiple overloaded methods found
                // find method with type matches
                CandidateMethodWithArgs typeMatched = selector.execute(candidates, arguments);
                if (typeMatched != null) {
                    // single match found!
                    return invoke.execute(typeMatched.getMethod(), receiver, typeMatched.getConvertedArgs(), true);
                } else {
                    // unable to select exactly one best candidate for the input args!
                    error.enter(node);
                    throw UnsupportedTypeException.create(arguments);
                }
            }
        } catch (EspressoException e) {
            exception.enter(node);
            Meta meta = e.getGuestException().getKlass().getMeta();
            EspressoLanguage language = meta.getLanguage();
            throw InteropUtils.unwrapExceptionBoundary(language, e, meta);
        }
    }
}
