/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.compiler.hsail.test.infra;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import com.amd.okra.*;

/**
 * Abstract class on which the HSAIL unit tests are built. Executes a method or lambda on both the
 * Java side and the Okra side and compares the results for fields that are annotated with
 * {@link KernelTester.Result}.
 */
public abstract class KernelTester {

    /**
     * Denotes a field whose value is to be compared as part of computing the result of a test.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Result {
    }

    // Using these in case we want to compile with Java 7.
    public interface MyIntConsumer {

        void accept(int value);
    }

    public interface MyObjConsumer {

        void accept(Object obj);
    }

    public enum DispatchMode {
        SEQ, JTP, OKRA
    }

    public enum HsailMode {
        COMPILED, INJECT_HSAIL, INJECT_OCL
    }

    public DispatchMode dispatchMode;
    // Where the hsail comes from.
    private HsailMode hsailMode;
    protected Method testMethod;
    // What type of okra dispatch to use when client calls.
    private boolean useLambdaMethod;
    private Class<?>[] testMethodParams = null;
    private int id = nextId.incrementAndGet();
    static AtomicInteger nextId = new AtomicInteger(0);
    public static Logger logger;
    private OkraContext okraContext;
    private OkraKernel okraKernel;
    private static final String propPkgName = KernelTester.class.getPackage().getName();
    private static Level logLevel;
    private static ConsoleHandler consoleHandler;
    private boolean runOkraFirst = Boolean.getBoolean("kerneltester.runOkraFirst");

    static {
        logger = Logger.getLogger(propPkgName);
        logLevel = Level.parse(System.getProperty("kerneltester.logLevel", "OFF"));

        // This block configure the logger with handler and formatter.
        consoleHandler = new ConsoleHandler();
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false);
        SimpleFormatter formatter = new SimpleFormatter() {

            @SuppressWarnings("sync-override")
            @Override
            public String format(LogRecord record) {
                return (record.getMessage() + "\n");
            }
        };
        consoleHandler.setFormatter(formatter);
        setLogLevel(logLevel);
    }

    private static boolean gaveNoOkraWarning = false;
    private boolean onSimulator;
    private final boolean okraLibExists;

    public boolean runningOnSimulator() {
        return onSimulator;
    }

    public KernelTester(boolean okraLibExists) {
        dispatchMode = DispatchMode.SEQ;
        hsailMode = HsailMode.COMPILED;
        useLambdaMethod = false;
        this.okraLibExists = okraLibExists;
    }

    public abstract void runTest();

    // Default comparison is to compare all things marked @Result.
    public boolean compareResults(KernelTester base) {
        Class<?> clazz = this.getClass();
        while (clazz != null && clazz != KernelTester.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    Result annos = f.getAnnotation(Result.class);
                    if (annos != null) {
                        logger.fine("@Result field = " + f);
                        Object myResult = getFieldFromObject(f, this);
                        Object otherResult = getFieldFromObject(f, base);
                        boolean same = compareObjects(myResult, otherResult);
                        logger.fine("comparing " + myResult + ", " + otherResult + ", match=" + same);
                        if (!same) {
                            logger.severe("mismatch comparing " + f + ", " + myResult + " vs. " + otherResult);
                            logSevere("FAILED!!! " + this.getClass());
                            return false;
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        logInfo("PASSED: " + this.getClass());
        return true;
    }

    private boolean compareObjects(Object first, Object second) {
        if (first == null) {
            return (second == null);
        }
        if (second == null) {
            return (first == null);
        }
        Class<?> clazz = first.getClass();
        if (clazz != second.getClass()) {
            return false;
        }
        if (!clazz.isArray()) {
            // Non arrays.
            if (clazz.equals(float.class) || clazz.equals(double.class)) {
                return isEqualsFP((double) first, (double) second);
            } else {
                return first.equals(second);
            }
        } else {
            // Handle the case where Objects are arrays.
            ArrayComparer comparer;
            if (clazz.equals(float[].class) || clazz.equals(double[].class)) {
                comparer = new FPArrayComparer();
            } else if (clazz.equals(long[].class) || clazz.equals(int[].class) || clazz.equals(byte[].class)) {
                comparer = new IntArrayComparer();
            } else if (clazz.equals(boolean[].class)) {
                comparer = new BooleanArrayComparer();
            } else {
                comparer = new ObjArrayComparer();
            }
            return comparer.compareArrays(first, second);
        }
    }

    static final int MISMATCHLIMIT = 10;
    static final int ELEMENTDISPLAYLIMIT = 20;

    public int getMisMatchLimit() {
        return MISMATCHLIMIT;
    }

    public int getElementDisplayLimit() {
        return ELEMENTDISPLAYLIMIT;
    }

    abstract class ArrayComparer {

        abstract Object getElement(Object ary, int index);

        // Equality test, can be overridden
        boolean isEquals(Object firstElement, Object secondElement) {
            return firstElement.equals(secondElement);
        }

        boolean compareArrays(Object first, Object second) {
            int len = Array.getLength(first);
            if (len != Array.getLength(second)) {
                return false;
            }
            // If info logLevel, build string of first few elements from first array.
            if (logLevel.intValue() <= Level.INFO.intValue()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(len, getElementDisplayLimit()); i++) {
                    sb.append(getElement(first, i));
                    sb.append(", ");
                }
                logger.info(sb.toString());
            }
            boolean success = true;
            int mismatches = 0;
            for (int i = 0; i < len; i++) {
                Object firstElement = getElement(first, i);
                Object secondElement = getElement(second, i);
                if (!isEquals(firstElement, secondElement)) {
                    logSevere("mismatch at index " + i + ", expected " + secondElement + ", saw " + firstElement);
                    success = false;
                    mismatches++;
                    if (mismatches >= getMisMatchLimit()) {
                        logSevere("...Truncated");
                        break;
                    }
                }
            }
            return success;
        }
    }

    class FPArrayComparer extends ArrayComparer {

        @Override
        Object getElement(Object ary, int index) {
            return Array.getDouble(ary, index);
        }

        @Override
        boolean isEquals(Object firstElement, Object secondElement) {
            return isEqualsFP((double) firstElement, (double) secondElement);
        }
    }

    class IntArrayComparer extends ArrayComparer {

        @Override
        Object getElement(Object ary, int index) {
            return Array.getLong(ary, index);
        }
    }

    class BooleanArrayComparer extends ArrayComparer {

        @Override
        Object getElement(Object ary, int index) {
            return Array.getBoolean(ary, index);
        }
    }

    class ObjArrayComparer extends ArrayComparer {

        @Override
        Object getElement(Object ary, int index) {
            return Array.get(ary, index);
        }

        @Override
        boolean isEquals(Object firstElement, Object secondElement) {
            return compareObjects(firstElement, secondElement);
        }
    }

    /**
     * Tests two floating point values for equality.
     */
    public boolean isEqualsFP(double first, double second) {
        // Special case for checking whether expected and actual values are both NaNs.
        if (Double.isNaN(first) && Double.isNaN(second)) {
            return true;
        }
        return first == second;
    }

    public void setDispatchMode(DispatchMode dispatchMode) {
        this.dispatchMode = dispatchMode;
    }

    public void setHsailMode(HsailMode hsailMode) {
        this.hsailMode = hsailMode;
    }

    /**
     * Return a clone of this instance unless overridden, we just call the null constructor.
     */
    public KernelTester newInstance() {
        try {
            return this.getClass().getConstructor((Class<?>[]) null).newInstance();
        } catch (Throwable t) {
            fail("Unexpected exception " + t);
            return null;
        }
    }

    public Method getMethodFromMethodName(String methName, Class<?> clazz) {
        Class<?> clazz2 = clazz;
        while (clazz2 != null) {
            for (Method m : clazz2.getDeclaredMethods()) {
                logger.fine(" in " + clazz2 + ", trying to match " + m);
                if (m.getName().equals(methName)) {
                    testMethodParams = m.getParameterTypes();
                    if (logLevel.intValue() <= Level.FINE.intValue()) {
                        logger.fine(" in " + clazz2 + ", matched " + m);
                        logger.fine("parameter types are...");
                        int paramNum = 0;
                        for (Class<?> pclazz : testMethodParams) {
                            logger.fine(paramNum++ + ") " + pclazz.toString());
                        }
                    }
                    return m;
                }
            }
            // Didn't find it in current clazz, try superclass.
            clazz2 = clazz2.getSuperclass();
        }
        // If we got this far, no match.
        return null;
    }

    private void setTestMethod(String methName, Class<?> inClazz) {
        testMethod = getMethodFromMethodName(methName, inClazz);
        if (testMethod == null) {
            fail("cannot find method " + methName + " in class " + inClazz);
        } else {
            // Print info but only for first such class.
            if (id == 1) {
                logger.fine("testMethod to be compiled is \n   " + testMethod);
            }
        }
    }

    // Default is method name "run", but could be overridden.
    private final String defaultMethodName = "run";

    public String getTestMethodName() {
        return defaultMethodName;
    }

    /**
     * The dispatchMethodKernel dispatches a non-lambda method. All the parameters of the compiled
     * method are supplied as parameters to this call.
     */
    public void dispatchMethodKernel(int range, Object... args) {
        if (testMethod == null) {
            setTestMethod(getTestMethodName(), this.getClass());
        }
        if (dispatchMode == DispatchMode.SEQ) {
            dispatchMethodKernelSeq(range, args);
        } else if (dispatchMode == DispatchMode.OKRA) {
            dispatchMethodKernelOkra(range, args);
        }
    }

    /**
     * The "array stream" version of {@link #dispatchMethodKernel(int, Object...)}.
     */
    public void dispatchMethodKernel(Object[] ary, Object... args) {
        if (testMethod == null) {
            setTestMethod(getTestMethodName(), this.getClass());
        }
        if (dispatchMode == DispatchMode.SEQ) {
            dispatchMethodKernelSeq(ary, args);
        } else if (dispatchMode == DispatchMode.OKRA) {
            dispatchMethodKernelOkra(ary, args);
        }
    }

    /**
     * This dispatchLambdaMethodKernel dispatches the lambda version of a kernel where the "kernel"
     * is for the lambda method itself (like lambda$0).
     */
    public void dispatchLambdaMethodKernel(int range, MyIntConsumer consumer) {
        if (testMethod == null) {
            setTestMethod(findLambdaMethodName(), this.getClass());
        }
        if (dispatchMode == DispatchMode.SEQ) {
            dispatchLambdaKernelSeq(range, consumer);
        } else if (dispatchMode == DispatchMode.OKRA) {
            dispatchLambdaMethodKernelOkra(range, consumer);
        }
    }

    public void dispatchLambdaMethodKernel(Object[] ary, MyObjConsumer consumer) {
        if (testMethod == null) {
            setTestMethod(findLambdaMethodName(), this.getClass());
        }
        if (dispatchMode == DispatchMode.SEQ) {
            dispatchLambdaKernelSeq(ary, consumer);
        } else if (dispatchMode == DispatchMode.OKRA) {
            dispatchLambdaMethodKernelOkra(ary, consumer);
        }
    }

    /**
     * The dispatchLambdaKernel dispatches the lambda version of a kernel where the "kernel" is for
     * the xxx$$Lambda.accept method in the wrapper for the lambda. Note that the useLambdaMethod
     * boolean provides a way of actually invoking dispatchLambdaMethodKernel from this API.
     */
    public void dispatchLambdaKernel(int range, MyIntConsumer consumer) {
        if (useLambdaMethod) {
            dispatchLambdaMethodKernel(range, consumer);
            return;
        }
        if (testMethod == null) {
            setTestMethod("accept", consumer.getClass());
        }
        if (dispatchMode == DispatchMode.SEQ) {
            dispatchLambdaKernelSeq(range, consumer);
        } else if (dispatchMode == DispatchMode.OKRA) {
            dispatchLambdaKernelOkra(range, consumer);
        }
    }

    public void dispatchLambdaKernel(Object[] ary, MyObjConsumer consumer) {
        if (useLambdaMethod) {
            dispatchLambdaMethodKernel(ary, consumer);
            return;
        }
        if (testMethod == null) {
            setTestMethod("accept", consumer.getClass());
        }
        if (dispatchMode == DispatchMode.SEQ) {
            dispatchLambdaKernelSeq(ary, consumer);
        } else if (dispatchMode == DispatchMode.OKRA) {
            dispatchLambdaKernelOkra(ary, consumer);
        }
    }

    private ArrayList<String> getLambdaMethodNames() {
        Class<?> clazz = this.getClass();
        ArrayList<String> lambdaNames = new ArrayList<>();
        while (clazz != null && (lambdaNames.size() == 0)) {
            for (Method m : clazz.getDeclaredMethods()) {
                logger.fine(" in " + clazz + ", trying to match " + m);
                if (m.getName().startsWith("lambda$")) {
                    lambdaNames.add(m.getName());
                }
            }
            // Didn't find it in current clazz, try superclass.
            clazz = clazz.getSuperclass();
        }
        return lambdaNames;
    }

    /**
     * findLambdaMethodName finds a name in the class starting with lambda$. If we find more than
     * one, throw an error, and tell user to override explicitly
     */
    private String findLambdaMethodName() {
        // If user overrode getTestMethodName, use that name.
        if (!getTestMethodName().equals(defaultMethodName)) {
            return getTestMethodName();
        } else {
            ArrayList<String> lambdaNames = getLambdaMethodNames();
            switch (lambdaNames.size()) {
                case 1:
                    return lambdaNames.get(0);
                case 0:
                    fail("No lambda method found in " + this.getClass());
                    return null;
                default:
                    // More than one lambda.
                    String msg = "Multiple lambda methods found in " + this.getClass() + "\nYou should override getTestMethodName with one of the following\n";
                    for (String name : lambdaNames) {
                        msg = msg + name + "\n";
                    }
                    fail(msg);
                    return null;
            }
        }
    }

    /**
     * The getCompiledHSAILSource returns the string of HSAIL code for the compiled method. By
     * default, throws an error. In graal for instance, this would be overridden in
     * GraalKernelTester.
     */
    public String getCompiledHSAILSource(Method testMethod1) {
        fail("no compiler connected so unable to compile " + testMethod1 + "\nYou could try injecting HSAIL or OpenCL");
        return null;
    }

    public String getHSAILSource(Method testMethod1) {
        switch (hsailMode) {
            case COMPILED:
                return getCompiledHSAILSource(testMethod1);
            case INJECT_HSAIL:
                return getHsailFromClassnameHsailFile();
            case INJECT_OCL:
                return getHsailFromClassnameOclFile();
            default:
                fail("unknown hsailMode = " + hsailMode);
                return null;
        }
    }

    /**
     * The getHSAILKernelName returns the name of the hsail kernel. By default we use 'run'. unless
     * coming from opencl injection. Could be overridden by the junit test.
     */
    public String getHSAILKernelName() {
        return (hsailMode != HsailMode.INJECT_OCL ? "&run" : "&__OpenCL_run_kernel");
    }

    private void createOkraKernel() {
        // Call routines in the derived class to get the hsail code and kernel name.
        String hsailSource = getHSAILSource(testMethod);
        if (!okraLibExists) {
            if (!gaveNoOkraWarning) {
                logger.severe("No Okra library detected, skipping all KernelTester tests in " + this.getClass().getPackage().getName());
                gaveNoOkraWarning = true;
            }
        }
        // Ignore any kerneltester test if okra does not exist.
        assumeTrue(okraLibExists);
        // Control which okra instances can run the tests.
        onSimulator = OkraContext.isSimulator();
        okraContext = new OkraContext();
        if (!okraContext.isValid()) {
            fail("...unable to create context");
        }
        // Control verbosity in okra from our logLevel.
        if (logLevel.intValue() <= Level.INFO.intValue()) {
            okraContext.setVerbose(true);
        }
        okraKernel = new OkraKernel(okraContext, hsailSource, getHSAILKernelName());
        if (!okraKernel.isValid()) {
            fail("...unable to create kernel");
        }
    }

    /**
     * Dispatches an okra kernel over a given range using JNI. Protected so that it can be
     * overridden in {@link GraalKernelTester} which will dispatch without JNI.
     */
    protected void dispatchKernelOkra(int range, Object... args) {
        if (okraKernel == null) {
            createOkraKernel();
        }
        if (logLevel.intValue() <= Level.FINE.intValue()) {
            logger.fine("Arguments passed to okra...");
            for (Object arg : args) {
                logger.fine("  " + arg);
            }
        }
        okraKernel.setLaunchAttributes(range);
        okraKernel.dispatchWithArgs(args);
    }

    // int stream version
    private void dispatchMethodKernelSeq(int range, Object... args) {
        Object[] invokeArgs = new Object[args.length + 1];
        // Need space on the end for the gid parameter.
        System.arraycopy(args, 0, invokeArgs, 0, args.length);
        int gidArgIndex = invokeArgs.length - 1;
        if (logLevel.intValue() <= Level.FINE.intValue()) {
            for (Object arg : args) {
                logger.fine(arg.toString());
            }
        }
        for (int rangeIndex = 0; rangeIndex < range; rangeIndex++) {
            invokeArgs[gidArgIndex] = rangeIndex;
            invokeMethodKernelSeq(invokeArgs, rangeIndex);
        }
    }

    // array stream version
    private void dispatchMethodKernelSeq(Object[] ary, Object... args) {
        Object[] invokeArgs = new Object[args.length + 1];
        // Need space on the end for the final obj parameter.
        System.arraycopy(args, 0, invokeArgs, 0, args.length);
        int objArgIndex = invokeArgs.length - 1;
        if (logLevel.intValue() <= Level.FINE.intValue()) {
            for (Object arg : args) {
                logger.fine(arg.toString());
            }
        }
        int range = ary.length;
        for (int rangeIndex = 0; rangeIndex < range; rangeIndex++) {
            invokeArgs[objArgIndex] = ary[rangeIndex];
            invokeMethodKernelSeq(invokeArgs, rangeIndex);
        }
    }

    private void invokeMethodKernelSeq(Object[] invokeArgs, int rangeIndex) {
        try {
            testMethod.invoke(this, invokeArgs);
        } catch (IllegalAccessException e) {
            fail("could not invoke " + testMethod + ", make sure it is public");
        } catch (IllegalArgumentException e) {
            fail("wrong arguments invoking " + testMethod + ", check number and type of args passed to dispatchMethodKernel");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            String errstr = testMethod + " threw an exception on gid=" + rangeIndex + ", exception was " + cause;
            fail(errstr);
        } catch (Exception e) {
            fail("Unknown exception " + e + " invoking " + testMethod);
        }
    }

    // int stream version
    private void dispatchMethodKernelOkra(int range, Object... args) {
        Object[] fixedArgs = fixArgTypes(args);
        if (Modifier.isStatic(testMethod.getModifiers())) {
            dispatchKernelOkra(range, fixedArgs);
        } else {
            // If it is a non-static method we have to push "this" as the first argument.
            Object[] newFixedArgs = new Object[fixedArgs.length + 1];
            System.arraycopy(fixedArgs, 0, newFixedArgs, 1, fixedArgs.length);
            newFixedArgs[0] = this;
            dispatchKernelOkra(range, newFixedArgs);
        }
    }

    // array stream version
    private void dispatchMethodKernelOkra(Object[] ary, Object... args) {
        // add the ary itself as the last arg in the passed parameter list
        Object[] argsWithAry = new Object[args.length + 1];
        System.arraycopy(args, 0, argsWithAry, 0, args.length);
        argsWithAry[argsWithAry.length - 1] = ary;

        Object[] fixedArgs = fixArgTypes(argsWithAry);
        int range = ary.length;
        if (Modifier.isStatic(testMethod.getModifiers())) {
            dispatchKernelOkra(range, fixedArgs);
        } else {
            // If it is a non-static method we have to push "this" as the first argument.
            Object[] newFixedArgs = new Object[fixedArgs.length + 1];
            System.arraycopy(fixedArgs, 0, newFixedArgs, 1, fixedArgs.length);
            newFixedArgs[0] = this;
            dispatchKernelOkra(range, newFixedArgs);
        }
    }

    /**
     * For primitive arg parameters, make sure arg types are cast to whatever the testMethod
     * signature says they should be.
     */
    protected Object[] fixArgTypes(Object[] args) {
        Object[] fixedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> paramClass = testMethodParams[i];
            if (paramClass.equals(Float.class) || paramClass.equals(float.class)) {
                fixedArgs[i] = ((Number) args[i]).floatValue();
            } else if (paramClass.equals(Integer.class) || paramClass.equals(int.class)) {
                fixedArgs[i] = ((Number) args[i]).intValue();
            } else if (paramClass.equals(Long.class) || paramClass.equals(long.class)) {
                fixedArgs[i] = ((Number) args[i]).longValue();
            } else if (paramClass.equals(Double.class) || paramClass.equals(double.class)) {
                fixedArgs[i] = ((Number) args[i]).doubleValue();
            } else if (paramClass.equals(Byte.class) || paramClass.equals(byte.class)) {
                fixedArgs[i] = ((Number) args[i]).byteValue();
            } else if (paramClass.equals(Boolean.class) || paramClass.equals(boolean.class)) {
                fixedArgs[i] = (boolean) args[i];
            } else {
                // All others just move unchanged.
                fixedArgs[i] = args[i];
            }
        }
        return fixedArgs;
    }

    /**
     * Dispatching a lambda on the java side is simple.
     */
    @SuppressWarnings("static-method")
    private void dispatchLambdaKernelSeq(int range, MyIntConsumer consumer) {
        for (int i = 0; i < range; i++) {
            consumer.accept(i);
        }
    }

    @SuppressWarnings("static-method")
    private void dispatchLambdaKernelSeq(Object[] ary, MyObjConsumer consumer) {
        for (Object obj : ary) {
            consumer.accept(obj);
        }
    }

    /**
     * The dispatchLambdaMethodKernelOkra dispatches in the case where the hsail kernel implements
     * the lambda method itself as opposed to the wrapper that calls the lambda method. From the
     * consumer object, we need to find the fields and pass them to the kernel.
     */
    protected void dispatchLambdaMethodKernelOkra(int range, MyIntConsumer consumer) {
        logger.info("To determine parameters to pass to hsail kernel, we will examine   " + consumer.getClass());
        Field[] fields = consumer.getClass().getDeclaredFields();
        Object[] args = new Object[fields.length];
        int argIndex = 0;
        for (Field f : fields) {
            logger.info("... " + f);
            args[argIndex++] = getFieldFromObject(f, consumer);
        }
        dispatchKernelOkra(range, args);
    }

    private void dispatchLambdaMethodKernelOkra(Object[] ary, MyObjConsumer consumer) {
        logger.info("To determine parameters to pass to hsail kernel, we will examine   " + consumer.getClass());
        Field[] fields = consumer.getClass().getDeclaredFields();
        Object[] args = new Object[fields.length + 1];  // + 1 because we also pass the array
        int argIndex = 0;
        for (Field f : fields) {
            logger.info("... " + f);
            args[argIndex++] = getFieldFromObject(f, consumer);
        }
        args[argIndex] = ary;
        dispatchKernelOkra(ary.length, args);
    }

    /**
     * The dispatchLambdaKernelOkra dispatches in the case where the hsail kernel where the hsail
     * kernel implements the accept method of the wrapper that calls the lambda method as opposed to
     * the actual lambda method itself.
     */
    private void dispatchLambdaKernelOkra(int range, MyIntConsumer consumer) {
        // The "wrapper" method always has only one arg consisting of the consumer.
        Object[] args = new Object[1];
        args[0] = consumer;
        dispatchKernelOkra(range, args);
    }

    private void dispatchLambdaKernelOkra(Object[] ary, MyObjConsumer consumer) {
        // The "wrapper" method always has only one arg consisting of the consumer.
        Object[] args = new Object[2];
        args[0] = consumer;
        args[1] = ary;
        dispatchKernelOkra(ary.length, args);
    }

    private void disposeKernelOkra() {
        if (okraContext != null) {
            okraContext.dispose();
        }
    }

    private void compareOkraToSeq(HsailMode hsailModeToUse) {
        compareOkraToSeq(hsailModeToUse, false);
    }

    /**
     * Runs this instance on OKRA, and as SEQ and compares the output of the two executions. the
     * runOkraFirst flag controls which order they are done in. Note the compiler must use eager
     * resolving if Okra is done first.
     */
    private void compareOkraToSeq(HsailMode hsailModeToUse, boolean useLambda) {
        KernelTester testerSeq;
        if (runOkraFirst) {
            runOkraInstance(hsailModeToUse, useLambda);
            testerSeq = runSeqInstance();
        } else {
            testerSeq = runSeqInstance();
            runOkraInstance(hsailModeToUse, useLambda);
        }
        assertTrue("failed comparison to SEQ", compareResults(testerSeq));
    }

    private void runOkraInstance(HsailMode hsailModeToUse, boolean useLambda) {
        // run Okra instance in exiting KernelTester object
        this.setHsailMode(hsailModeToUse);
        this.setDispatchMode(DispatchMode.OKRA);
        this.useLambdaMethod = useLambda;
        this.runTest();
        this.disposeKernelOkra();
    }

    private KernelTester runSeqInstance() {
        // Create and run sequential instance.
        KernelTester testerSeq = newInstance();
        testerSeq.setDispatchMode(DispatchMode.SEQ);
        testerSeq.runTest();
        return testerSeq;
    }

    public void testGeneratedHsail() {
        compareOkraToSeq(HsailMode.COMPILED);
    }

    public void testGeneratedHsailUsingLambdaMethod() {
        compareOkraToSeq(HsailMode.COMPILED, true);
    }

    public void testInjectedHsail() {
        newInstance().compareOkraToSeq(HsailMode.INJECT_HSAIL);
    }

    public void testInjectedOpencl() {
        newInstance().compareOkraToSeq(HsailMode.INJECT_OCL);
    }

    protected static Object getFieldFromObject(Field f, Object fromObj) {
        try {
            f.setAccessible(true);
            Type type = f.getType();
            logger.info("type = " + type);
            if (type == double.class) {
                return f.getDouble(fromObj);
            } else if (type == float.class) {
                return f.getFloat(fromObj);
            } else if (type == long.class) {
                return f.getLong(fromObj);
            } else if (type == int.class) {
                return f.getInt(fromObj);
            } else if (type == byte.class) {
                return f.getByte(fromObj);
            } else if (type == boolean.class) {
                return f.getBoolean(fromObj);
            } else {
                return f.get(fromObj);
            }
        } catch (Exception e) {
            fail("unable to get field " + f + " from " + fromObj);
            return null;
        }
    }

    public static void checkFileExists(String fileName) {
        assertTrue(fileName + " does not exist", fileExists(fileName));
    }

    public static boolean fileExists(String fileName) {
        return new File(fileName).exists();
    }

    public static String getFileAsString(String sourceFileName) {
        String source = null;
        try {
            checkFileExists(sourceFileName);
            source = new String(Files.readAllBytes(FileSystems.getDefault().getPath(sourceFileName)));
        } catch (IOException e) {
            fail("could not open file " + sourceFileName);
            return null;
        }
        return source;
    }

    public static String getHsailFromFile(String sourceFileName) {
        logger.severe("... getting hsail from file " + sourceFileName);
        return getFileAsString(sourceFileName);
    }

    private static void executeCmd(String... cmd) {
        logger.info("spawning" + Arrays.toString(cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            if (logLevel.intValue() <= Level.INFO.intValue()) {
                InputStream in = p.getInputStream();
                BufferedInputStream buf = new BufferedInputStream(in);
                InputStreamReader inread = new InputStreamReader(buf);
                BufferedReader bufferedreader = new BufferedReader(inread);
                String line;
                while ((line = bufferedreader.readLine()) != null) {
                    logger.info(line);
                }
            }
            p.waitFor();
        } catch (Exception e) {
            fail("could not execute <" + Arrays.toString(cmd) + ">");
        }
    }

    public static String getHsailFromOpenCLFile(String openclFileName) {
        String openclHsailFile = "opencl_out.hsail";
        String tmpTahitiFile = "_temp_0_Tahiti.txt";
        checkFileExists(openclFileName);
        logger.severe("...converting " + openclFileName + " to HSAIL...");
        executeCmd("aoc2", "-m64", "-I./", "-march=hsail", openclFileName);
        if (fileExists(tmpTahitiFile)) {
            return getFileAsString(tmpTahitiFile);
        } else {
            executeCmd("HSAILasm", "-disassemble", "-o", openclHsailFile, openclFileName.replace(".cl", ".bin"));
            checkFileExists(openclHsailFile);
            return getFileAsString(openclHsailFile);
        }
    }

    public String getHsailFromClassnameHsailFile() {
        return (getHsailFromFile(this.getClass().getSimpleName() + ".hsail"));
    }

    public String getHsailFromClassnameOclFile() {
        return (getHsailFromOpenCLFile(this.getClass().getSimpleName() + ".cl"));
    }

    public static void logInfo(String msg) {
        logger.info(msg);
    }

    public static void logSevere(String msg) {
        logger.severe(msg);
    }

    public static void setLogLevel(Level level) {
        logLevel = level;
        logger.setLevel(level);
        consoleHandler.setLevel(level);
    }
}
