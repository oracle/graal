package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

@GenerateUncached
public abstract class LookupDeclaredMethod extends Node {

    public static final int LIMIT = 2;

    public abstract Method execute(Klass klass, String methodName, boolean publicOnly, boolean isStatic, int arity);

    @SuppressWarnings("unused")
    @Specialization(guards = {"klass.equals(cachedKlass)", "methodName.equals(cachedMethodName)", "publicOnly == cachedPublicOnly", "isStatic == cachedIsStatic",
                    "arity == cachedArity"}, limit = "LIMIT")
    Method doCached(Klass klass,
                    String methodName,
                    boolean publicOnly,
                    boolean isStatic,
                    int arity,
                    @Cached("klass") Klass cachedKlass,
                    @Cached("methodName") String cachedMethodName,
                    @Cached("publicOnly") boolean cachedPublicOnly,
                    @Cached("isStatic") boolean cachedIsStatic,
                    @Cached("arity") int cachedArity,
                    @Cached("doGeneric(klass, methodName, publicOnly, isStatic, arity)") Method method) {
        return method;
    }

    @Specialization(replaces = "doCached")
    Method doGeneric(Klass klass, String methodName, boolean publicOnly, boolean isStatic, int arity) {
        for (Method m : klass.getDeclaredMethods()) {
            if (m.isPublic() == publicOnly && m.isStatic() == isStatic && m.getName().toString().equals(methodName)) {
                if (m.getParameterCount() == arity) {
                    return m;
                }
            }
        }
        return null;
    }
}
