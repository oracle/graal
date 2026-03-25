package com.oracle.svm.agentproxy;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A JDK dynamic proxy for {@link Instrumentation} that intercepts
 * {@link Instrumentation#addTransformer(ClassFileTransformer, boolean)} calls
 * and wraps the
 * supplied {@link ClassFileTransformer} with a
 * {@link ClassFileTransformerProxy}.
 *
 * <p>
 * The interception is necessary because transformers registered by target
 * agents may attempt
 * to rewrite classes that belong to boot/platform modules or GraalVM system
 * modules. By routing
 * every transformer through {@link ClassFileTransformerProxy}, such
 * transformations are silently
 * suppressed for protected modules while still being applied to application
 * classes.
 *
 * <p>
 * All other {@link Instrumentation} methods are forwarded to the original
 * instance unchanged.
 */
public class InstrumentationProxy implements InvocationHandler {

    /** The actual {@link Instrumentation} instance provided by the JVM. */
    private Instrumentation originalInst;

    private InstrumentationProxy(Instrumentation originalInst) {
        this.originalInst = originalInst;
    }

    /**
     * Intercepts calls to the two-argument {@code addTransformer} method and wraps
     * the provided
     * {@link ClassFileTransformer} with a {@link ClassFileTransformerProxy} before
     * forwarding to
     * the actual {@link Instrumentation}. All other method calls are forwarded
     * directly.
     *
     * @param proxy  the proxy instance the method was invoked on
     * @param method the interface method being invoked
     * @param args   the arguments passed to the method
     * @return the result of the delegated call on the original
     *         {@link Instrumentation}
     * @throws Throwable if the delegated call throws
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("addTransformer") && args.length == 2) {
            // Wrap the transformer so that transformations targeting protected modules are
            // filtered out.
            ClassFileTransformer classFileTransformerProxy = ClassFileTransformerProxy
                    .createProxy((ClassFileTransformer) args[0]);
            args[0] = classFileTransformerProxy;
            return method.invoke(originalInst, args);
        } else {
            // All other Instrumentation methods are forwarded transparently.
            return method.invoke(originalInst, args);
        }
    }

    /**
     * Creates a JDK dynamic proxy for the given {@link Instrumentation} instance.
     *
     * @param target the actual {@link Instrumentation} to wrap
     * @return a proxy that implements {@link Instrumentation} and intercepts
     *         transformer registration
     */
    public static Instrumentation createProxy(Instrumentation target) {
        return (Instrumentation) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[] { Instrumentation.class },
                new InstrumentationProxy(target));
    }
}
