package com.oracle.truffle.espresso.nodes.helper;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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

    public abstract Object execute(Method method, Object receiver, Object[] arguments) throws ArityException, UnsupportedMessageException, UnsupportedTypeException;

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
                    @Cached(value = "createToHost(method.getParameterCount())", allowUncached = true) ToEspressoNode[] toEspressoNodes)
                    throws ArityException, UnsupportedMessageException, UnsupportedTypeException {
        int arity = cachedMethod.getParameterCount();
        if (arguments.length != arity) {
            throw ArityException.create(arity, arguments.length);
        }
        Klass[] types = cachedMethod.resolveParameterKlasses();
        int parameterCount = method.getParameterCount();
        Object[] convertedArguments = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            convertedArguments[i] = toEspressoNodes[i].execute(arguments[i], types[i]);
        }
        return cachedMethod.invokeDirect(receiver, convertedArguments);
    }
}
