package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * The root of all executable bits in Espresso, includes everything that can be called a "method" in
 * Java. Regular (concrete) Java methods, native methods and intrinsics/substitutions.
 */
public abstract class EspressoRootNode extends RootNode implements ContextAccess {
    // TODO(peterssen): This could be ObjectKlass bar array methods. But those methods could be
    // redirected e.g. arrays .clone method to Object.clone.
    // private final /* ObjectKlass */ Klass declaringKlass;
    private final Method method;

    public final Method getMethod() {
        return method;
    }

    protected EspressoRootNode(Method method) {
        super(method.getEspressoLanguage());
        this.method = method;
    }

    protected EspressoRootNode(Method method, FrameDescriptor frameDescriptor) {
        super(method.getEspressoLanguage(), frameDescriptor);
        this.method = method;
    }

    @Override
    public EspressoContext getContext() {
        return method.getContext();
    }

    @Override
    public String getName() {
        // TODO(peterssen): Set proper location.
        return getClass().getSimpleName() + "<" + getMethod().getDeclaringKlass().getType() + "." + getMethod().getName() + getMethod().getRawSignature() + ">";
    }
}
