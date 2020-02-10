package com.oracle.truffle.espresso.nodes.helper;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

@GenerateUncached
public abstract class InvokeEspressoNode extends Node {
    static final int LIMIT = 3;

    InvokeEspressoNode() {
    }

    static InvokeEspressoNode create() {
        return InvokeEspressoNodeGen.create();
    }

    public final Object execute(Method method, Object receiver, Object[] arguments) throws UnsupportedTypeException, ArityException {
        try {
            return executeImpl(method, receiver, arguments);
        } catch (ClassCastException | NullPointerException e) {
            // conversion failed by ToJavaNode
            throw UnsupportedTypeException.create(arguments);
        } catch (UnsupportedTypeException | ArityException e) {
            throw e;
        }
    }

    protected abstract Object executeImpl(Method method, Object receiver, Object[] args) throws UnsupportedTypeException, ArityException;

    static ToEspressoNode[] createToHost(int argsLength) {
        ToEspressoNode[] toEspresso = new ToEspressoNode[argsLength];
        for (int i = 0; i < argsLength; i++) {
            toEspresso[i] = ToEspressoNodeGen.create();
        }
        return toEspresso;
    }

    @SuppressWarnings("unused")
    @ExplodeLoop
    @Specialization(guards = {"method == cachedMethod"}, limit = "LIMIT")
    Object doCached(Method method, Object receiver, Object[] arguments,
                    @Cached("method") Method cachedMethod,
                    @Cached(value = "createToHost(method.getParameterCount())", allowUncached = true) ToEspressoNode[] toEspressoNodes) throws ArityException {
        int arity = cachedMethod.getParameterCount();
        if (arguments.length != arity) {
            throw ArityException.create(arity, arguments.length);
        }
        Klass[] types = cachedMethod.resolveParameterKlasses();
        Object[] convertedArguments = new Object[arguments.length];
        for (int i = 0; i < toEspressoNodes.length; i++) {
            convertedArguments[i] = toEspressoNodes[i].execute(arguments[i], types[i]);
        }
        return cachedMethod.invokeDirect(receiver, convertedArguments);
    }
}
