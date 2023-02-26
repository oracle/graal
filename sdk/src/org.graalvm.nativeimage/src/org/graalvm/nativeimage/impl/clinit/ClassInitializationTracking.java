/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.impl.clinit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClassInitializationTracking {

    public static final Map<Class<?>, StackTraceElement[]> initializedClasses = new ConcurrentHashMap<>();

    /**
     * Instantiated objects must be traced using their identities as their hashCode may change
     * during the execution. We also want two objects of the same class that have the same hash and
     * are equal to be mapped as two distinct entries.
     */
    public static final Map<Object, StackTraceElement[]> instantiatedObjects = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * This method is called from the native-image diagnostics agent to report class initialization.
     */
    @SuppressWarnings({"unused"})
    public static void reportClassInitialized(Class<?> clazz, StackTraceElement[] stackTrace) {
        initializedClasses.put(clazz, relevantStackTrace(stackTrace));
    }

    /**
     * This method is called from the native-image-diagnostics agent to report object instantiation.
     */
    @SuppressWarnings({"unused"})
    public static void reportObjectInstantiated(Object o, StackTraceElement[] stackTrace) {
        instantiatedObjects.putIfAbsent(o, relevantStackTrace(stackTrace));
    }

    /**
     * If the stack trace contains class initializaiton, returns the stack frames up to the last
     * initialization. Otherwise returns the whole stack trace. The method never returns the stack
     * from the instrumented part.
     *
     * This method can be refined on a case-by-case basis to print nicer traces.
     *
     * @return a stack trace that led to erroneous situation
     */
    private static StackTraceElement[] relevantStackTrace(StackTraceElement[] stack) {
        ArrayList<StackTraceElement> filteredStack = new ArrayList<>();
        int lastClinit = 0;
        boolean containsLambdaMetaFactory = false;
        for (int i = 0; i < stack.length; i++) {
            StackTraceElement stackTraceElement = stack[i];
            if ("<clinit>".equals(stackTraceElement.getMethodName())) {
                lastClinit = i;
            }
            if (stackTraceElement.getClassName().equals("java.lang.invoke.LambdaMetafactory")) {
                containsLambdaMetaFactory = true;
            }
            filteredStack.add(stackTraceElement);
        }
        List<StackTraceElement> finalStack = lastClinit != 0 && !containsLambdaMetaFactory ? filteredStack.subList(0, lastClinit + 1) : filteredStack;
        return finalStack.toArray(new StackTraceElement[0]);
    }

}
