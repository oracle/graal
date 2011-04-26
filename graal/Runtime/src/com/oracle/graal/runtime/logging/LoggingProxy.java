/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime.logging;

import java.lang.reflect.*;

import com.oracle.graal.runtime.server.*;

/**
 * A java.lang.reflect proxy that hierarchically logs all method invocations along with their parameters and return values.
 *
 * @author Lukas Stadler
 */
public class LoggingProxy<T> implements InvocationHandler {

    private T delegate;

    public LoggingProxy(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        int argCount = args == null ? 0 : args.length;
        if (method.getParameterTypes().length != argCount) {
            throw new RuntimeException("wrong parameter count");
        }
        StringBuilder str = new StringBuilder();
        str.append(method.getReturnType().getSimpleName() + " " + method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(");
        for (int i = 0; i < argCount; i++) {
            str.append(i == 0 ? "" : ", ");
            str.append(Logger.pretty(args[i]));
        }
        str.append(")");
        Logger.startScope(str.toString());
        final Object result;
        try {
            if (args == null) {
                result = method.invoke(delegate);
            } else {
                result = method.invoke(delegate, args);
            }
        } catch (InvocationTargetException e) {
            Logger.endScope(" = Exception " + e.getMessage());
            throw e.getCause();
        }
        Logger.endScope(" = " + Logger.pretty(result));
        return result;
    }

    /**
     * The object returned by this method will implement all interfaces that are implemented by delegate.
     */
    public static <T> T getProxy(Class<T> interf, T delegate) {
        Class<?>[] interfaces = ReplacingStreams.getAllInterfaces(delegate.getClass());
        Object obj = Proxy.newProxyInstance(interf.getClassLoader(), interfaces, new LoggingProxy<T>(delegate));
        return interf.cast(obj);
    }
}
