package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
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
@GenerateUncached
public abstract class InteropLookupAndInvoke extends EspressoNode {
    public abstract Object execute(StaticObject receiver, Klass klass, Object[] arguments, String member, boolean isVirtual)
                    throws ArityException, UnsupportedTypeException;

    @Specialization(guards = "isVirtual")
    @SuppressWarnings("unused")
    public Object doVirtual(StaticObject receiver, Klass klass, Object[] arguments, String member, boolean isVirtual,
                    @Cached LookupVirtualMethodNode lookup,
                    @Cached @Shared OverLoadedMethodSelectorNode selector,
                    @Cached @Shared ToEspressoNode.DynamicToEspresso toEspresso,
                    @Cached @Shared InvokeEspressoNode invoke,
                    @Cached @Shared InlinedBranchProfile single,
                    @Cached @Shared InlinedBranchProfile nonVarargs,
                    @Cached @Shared InlinedBranchProfile varargs,
                    @Cached @Shared InlinedBranchProfile multiple,
                    @Cached @Shared InlinedBranchProfile error,
                    @Cached @Shared InlinedBranchProfile exception)
                    throws ArityException, UnsupportedTypeException {
        assert receiver != null;
        Method[] candidates = lookup.execute(klass, member, arguments.length);
        if (candidates != null) {
            return selectAndInvoke(receiver, arguments, candidates, selector, toEspresso, invoke,
                            single, nonVarargs, varargs, multiple, error, exception);
        }
        error.enter(this);
        throw ArityException.create(arguments.length + 1, -1, arguments.length);
    }

    @Specialization(guards = "!isVirtual")
    @SuppressWarnings("unused")
    public Object doNonVirtual(StaticObject receiver, Klass klass, Object[] arguments, String member, boolean isVirtual,
                    @Cached LookupDeclaredMethod lookup,
                    @Cached @Shared OverLoadedMethodSelectorNode selector,
                    @Cached @Shared ToEspressoNode.DynamicToEspresso toEspresso,
                    @Cached @Shared InvokeEspressoNode invoke,
                    @Cached @Shared InlinedBranchProfile single,
                    @Cached @Shared InlinedBranchProfile nonVarargs,
                    @Cached @Shared InlinedBranchProfile varargs,
                    @Cached @Shared InlinedBranchProfile multiple,
                    @Cached @Shared InlinedBranchProfile error,
                    @Cached @Shared InlinedBranchProfile exception)
                    throws ArityException, UnsupportedTypeException {
        boolean isStatic = receiver == null;
        Method[] candidates = lookup.execute(klass, member, true, isStatic, arguments.length);
        if (candidates != null) {
            return selectAndInvoke(receiver, arguments, candidates, selector, toEspresso, invoke,
                            single, nonVarargs, varargs, multiple, error, exception);
        }
        error.enter(this);
        throw ArityException.create(arguments.length + 1, -1, arguments.length);
    }

    private Object selectAndInvoke(StaticObject receiver, Object[] arguments, Method[] candidates,
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
                single.enter(this);
                // common case with no overloads
                Method m = candidates[0];
                assert m.isPublic();
                if (!m.isVarargs()) {
                    nonVarargs.enter(this);
                    assert m.getParameterCount() == arguments.length;
                    return invoke.execute(m, receiver, arguments);
                } else {
                    varargs.enter(this);
                    CandidateMethodWithArgs matched = MethodArgsUtils.matchCandidate(m, arguments, m.resolveParameterKlasses(), toEspresso);
                    if (matched != null) {
                        matched = MethodArgsUtils.ensureVarArgsArrayCreated(matched);
                        if (matched != null) {
                            return invoke.execute(matched.getMethod(), receiver, matched.getConvertedArgs(), true);
                        }
                    }
                    error.enter(this);
                    throw UnsupportedTypeException.create(arguments);
                }
            } else {
                multiple.enter(this);
                // multiple overloaded methods found
                // find method with type matches
                CandidateMethodWithArgs typeMatched = selector.execute(candidates, arguments);
                if (typeMatched != null) {
                    // single match found!
                    return invoke.execute(typeMatched.getMethod(), receiver, typeMatched.getConvertedArgs(), true);
                } else {
                    // unable to select exactly one best candidate for the input args!
                    error.enter(this);
                    throw UnsupportedTypeException.create(arguments);
                }
            }
        } catch (EspressoException e) {
            exception.enter(this);
            Meta meta = e.getGuestException().getKlass().getMeta();
            EspressoLanguage language = meta.getLanguage();
            throw InteropUtils.unwrapExceptionBoundary(language, e, meta);
        }
    }
}
