package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

/**
 * The root of all executable bits in Espresso, includes everything that can be called a "method" in
 * Java. Regular (concrete) Java methods, native methods and intrinsics/substitutions.
 */
public abstract class EspressoRootNode extends RootNode {
    // TODO(peterssen): This could be ObjectKlass bar array methods. But those methods could be
    // redirected e.g. arrays .clone method to Object.clone.
    // private final /* ObjectKlass */ Klass declaringKlass;
    private final Method method;

    public final Method getMethod() {
        return method;
    }

    protected EspressoRootNode(EspressoLanguage language, Method method) {
        super(language);
        this.method = method;
    }
}
