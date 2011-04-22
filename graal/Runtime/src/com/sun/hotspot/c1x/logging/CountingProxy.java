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
package com.sun.hotspot.c1x.logging;

import java.lang.reflect.*;
import java.util.*;

import com.sun.hotspot.c1x.server.*;

/**
 * A java.lang.reflect proxy that hierarchically logs all method invocations along with their parameters and return
 * values.
 *
 * @author Lukas Stadler
 */
public class CountingProxy<T> implements InvocationHandler {

    public static final boolean ENABLED = Boolean.valueOf(System.getProperty("c1x.countcalls"));

    private T delegate;

    private Map<Method, Long> calls = new HashMap<Method, Long>();

    public CountingProxy(T delegate) {
        assert ENABLED;
        System.out.println("Counting proxy for " + delegate.getClass().getSimpleName() + " created");
        this.delegate = delegate;
        proxies.add(this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        int argCount = args == null ? 0 : args.length;
        if (method.getParameterTypes().length != argCount) {
            throw new RuntimeException("wrong parameter count");
        }
        final Object result;
        long count = calls.containsKey(method) ? calls.get(method) : 0;
        calls.put(method, count + 1);
        try {
            if (args == null) {
                result = method.invoke(delegate);
            } else {
                result = method.invoke(delegate, args);
            }
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return result;
    }

    public static <T> T getProxy(Class<T> interf, T delegate) {
        Class<?>[] interfaces = ReplacingStreams.getAllInterfaces(delegate.getClass());
        Object obj = Proxy.newProxyInstance(interf.getClassLoader(), interfaces, new CountingProxy<T>(delegate));
        return interf.cast(obj);
    }

    private static ArrayList<CountingProxy> proxies = new ArrayList<CountingProxy>();

    static {
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void run() {
                    for (CountingProxy proxy : proxies) {
                        proxy.print();
                    }
                }
            });
        }
    }

    protected void print() {
        long sum = 0;
        for (Map.Entry<Method, Long> entry : calls.entrySet()) {
            Method method = entry.getKey();
            long count = entry.getValue();
            sum += count;
            System.out.println(delegate.getClass().getSimpleName() + "." + method.getName() + ": " + count);
        }
        System.out.println(delegate.getClass().getSimpleName() + " calls: " + sum);
    }
}
