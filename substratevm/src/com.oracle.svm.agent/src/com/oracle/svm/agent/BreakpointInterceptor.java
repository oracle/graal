/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.agent;

import static com.oracle.svm.agent.Support.check;
import static com.oracle.svm.agent.Support.checkNoException;
import static com.oracle.svm.agent.Support.clearException;
import static com.oracle.svm.agent.Support.fromCString;
import static com.oracle.svm.agent.Support.fromJniString;
import static com.oracle.svm.agent.Support.getCallerClass;
import static com.oracle.svm.agent.Support.getClassNameOr;
import static com.oracle.svm.agent.Support.getObjectArgument;
import static com.oracle.svm.agent.Support.handles;
import static com.oracle.svm.agent.Support.jniFunctions;
import static com.oracle.svm.agent.Support.jvmtiEnv;
import static com.oracle.svm.agent.Support.jvmtiFunctions;
import static com.oracle.svm.agent.Support.testException;
import static com.oracle.svm.agent.Support.toCString;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_BREAKPOINT;
import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.agent.jvmti.JvmtiCapabilities;
import com.oracle.svm.agent.jvmti.JvmtiEnv;
import com.oracle.svm.agent.jvmti.JvmtiError;
import com.oracle.svm.agent.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.agent.jvmti.JvmtiEventMode;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallBooleanMethod0FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod0FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod1FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod2FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod3FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

/*
 * NOTE: With most of our breakpoints, we recursively call the intercepted method ourselves to
 * inspect its return value and determine whether it provides a valid result. This permits us to
 * identify probing.
 *
 * Many of the methods are caller-sensitive, so when we call them from a breakpoint, they
 * observe a different caller class and therefore can behave differently. Short of using
 * bytecode instrumentation to read the return value, there seems to be no strictly better
 * approach (and instrumenting java.lang.Class and friends might be tricky, too). It would be
 * possible to set breakpoints at return bytecodes instead, but then there is no way to retrieve
 * the return value from the operand stack.
 */

final class BreakpointInterceptor {
    private static TraceWriter traceWriter;

    private static Map<Long, Breakpoint> installedBreakpoints;

    private static final ThreadLocal<Boolean> recursive = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static void traceBreakpoint(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, String function, Object result, Object... args) {
        traceWriter.traceCall("reflect",
                        function,
                        getClassNameOr(env, clazz, null, TraceWriter.UNKNOWN_VALUE),
                        getClassNameOr(env, declaringClass, null, TraceWriter.UNKNOWN_VALUE),
                        getClassNameOr(env, callerClass, null, TraceWriter.UNKNOWN_VALUE),
                        result,
                        args);
        guarantee(!testException(env));
    }

    private static void forName(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle name = getObjectArgument(0);
        boolean initializeValid = true;
        boolean classLoaderValid = true;
        CIntPointer initializePtr = StackValue.get(CIntPointer.class);
        WordPointer classLoaderPtr = StackValue.get(WordPointer.class);
        if (bp.method == handles().javaLangClassForName3) {
            initializeValid = (jvmtiFunctions().GetLocalInt().invoke(jvmtiEnv(), nullHandle(), 0, 1, initializePtr) == JvmtiError.JVMTI_ERROR_NONE);
            classLoaderValid = (jvmtiFunctions().GetLocalObject().invoke(jvmtiEnv(), nullHandle(), 0, 2, classLoaderPtr) == JvmtiError.JVMTI_ERROR_NONE);
        } else {
            initializePtr.write(1);
            classLoaderPtr.write(nullHandle());
            if (callerClass.notEqual(nullHandle())) {
                /*
                 * NOTE: we use our direct caller class, but this class might be skipped over by
                 * Class.forName(nameOnly) in its security stackwalk for @CallerSensitive, leading
                 * to different behavior of our call and the original call.
                 */
                classLoaderValid = (jvmtiFunctions().GetClassLoader().invoke(jvmtiEnv(), callerClass, classLoaderPtr) == JvmtiError.JVMTI_ERROR_NONE);
            }
        }
        Object result = TraceWriter.UNKNOWN_VALUE;
        if (initializeValid && classLoaderValid) {
            result = nullHandle().notEqual(jniFunctions().<CallObjectMethod3FunctionPointer> getCallStaticObjectMethod().invoke(
                            jni, bp.clazz, handles().javaLangClassForName3, name, WordFactory.signed(initializePtr.read()), classLoaderPtr.read()));
            if (clearException(jni)) {
                result = false;
            }
        }
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, result, fromJniString(jni, name));
    }

    private static void getMembers(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        // No need to recursively call these methods because they can only
        // return an empty array or throw a SecurityException.
        JNIObjectHandle self = getObjectArgument(0);
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, null);
    }

    private static void getField(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetField(jni, callerClass, bp, true);
    }

    private static void getDeclaredField(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetField(jni, callerClass, bp, false);
    }

    private static void handleGetField(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean findDeclaring) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        JNIObjectHandle result = jniFunctions().<CallObjectMethod1FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, name);
        if (clearException(jni)) {
            result = nullHandle();
        }
        JNIObjectHandle declaring = nullHandle();
        if (findDeclaring && result.notEqual(nullHandle())) {
            declaring = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, result, handles().javaLangReflectMemberGetDeclaringClass);
            if (clearException(jni)) {
                declaring = nullHandle();
            }
        }
        traceBreakpoint(jni, self, declaring, callerClass, bp.specification.methodName, result.notEqual(nullHandle()), fromJniString(jni, name));
    }

    private static void getConstructor(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle paramTypesHandle = getObjectArgument(1);
        boolean result = nullHandle().notEqual(jniFunctions().<CallObjectMethod1FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, paramTypesHandle));
        if (clearException(jni)) {
            result = false;
        }
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, result, paramTypes);
    }

    private static void getMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetMethod(jni, callerClass, bp, true);
    }

    private static void getDeclaredMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetMethod(jni, callerClass, bp, false);
    }

    private static void handleGetMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean findDeclaring) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle nameHandle = getObjectArgument(1);
        JNIObjectHandle paramTypesHandle = getObjectArgument(2);
        JNIObjectHandle result = jniFunctions().<CallObjectMethod2FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, nameHandle, paramTypesHandle);
        if (clearException(jni)) {
            result = nullHandle();
        }
        JNIObjectHandle declaring = nullHandle();
        if (findDeclaring && result.notEqual(nullHandle())) {
            declaring = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, result, handles().javaLangReflectMemberGetDeclaringClass);
            if (clearException(jni)) {
                declaring = nullHandle();
            }
        }
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        traceBreakpoint(jni, self, declaring, callerClass, bp.specification.methodName, result.notEqual(nullHandle()), fromJniString(jni, nameHandle), paramTypes);
    }

    private static void getEnclosingMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        Object result = TraceWriter.EXPLICIT_NULL;
        JNIObjectHandle enclosing = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method);
        if (!clearException(jni) && enclosing.notEqual(nullHandle())) {
            result = TraceWriter.UNKNOWN_VALUE;
            JNIMethodId enclosingID = jniFunctions().getFromReflectedMethod().invoke(jni, enclosing);
            if (!clearException(jni) && enclosingID.isNonNull()) {
                WordPointer holderPtr = StackValue.get(WordPointer.class);
                if (jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), enclosingID, holderPtr) == JvmtiError.JVMTI_ERROR_NONE) {
                    Object holderName = getClassNameOr(jni, holderPtr.read(), null, null);
                    if (holderName != null) {
                        CCharPointerPointer namePtr = StackValue.get(CCharPointerPointer.class);
                        CCharPointerPointer signaturePtr = StackValue.get(CCharPointerPointer.class);
                        if (jvmtiFunctions().GetMethodName().invoke(jvmtiEnv(), enclosingID, namePtr, signaturePtr, nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
                            result = holderName + "." + fromCString(namePtr.read()) + fromCString(signaturePtr.read());
                            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), namePtr);
                            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), signaturePtr);
                        }
                    }
                }
            }
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result);
    }

    private static void newInstance(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        boolean result;
        try (CCharPointerHolder ctorName = toCString("<init>"); CCharPointerHolder ctorSignature = toCString("()V")) {
            result = nullHandle().notEqual(jniFunctions().getGetMethodID().invoke(jni, self, ctorName.get(), ctorSignature.get()));
        }
        if (clearException(jni)) {
            result = false;
        }
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, result);
    }

    private static void getResource(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetResources(jni, callerClass, bp, false);
    }

    private static void getResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetResources(jni, callerClass, bp, true);
    }

    private static void handleGetResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean returnsEnumeration) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        JNIObjectHandle returnValue = jniFunctions().<CallObjectMethod1FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, name);
        boolean result = returnValue.notEqual(nullHandle());
        if (clearException(jni)) {
            result = false;
        }
        if (result && returnsEnumeration) {
            result = hasEnumerationElements(jni, returnValue);
        }
        JNIObjectHandle selfClazz = nullHandle(); // self is java.lang.ClassLoader, get its class
        if (self.notEqual(nullHandle())) {
            selfClazz = jniFunctions().getGetObjectClass().invoke(jni, self);
            if (clearException(jni)) {
                selfClazz = nullHandle();
            }
        }
        traceBreakpoint(jni, selfClazz, nullHandle(), callerClass, bp.specification.methodName, result, fromJniString(jni, name));
    }

    private static boolean hasEnumerationElements(JNIEnvironment jni, JNIObjectHandle obj) {
        boolean hasElements = jniFunctions().<CallBooleanMethod0FunctionPointer> getCallBooleanMethod().invoke(jni, obj, handles().javaUtilEnumerationHasMoreElements);
        if (clearException(jni)) {
            hasElements = false;
        }
        return hasElements;
    }

    private static void getSystemResource(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetSystemResources(jni, callerClass, bp, false);
    }

    private static void getSystemResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        handleGetSystemResources(jni, callerClass, bp, true);
    }

    private static void handleGetSystemResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean returnsEnumeration) {
        JNIObjectHandle name = getObjectArgument(0);
        JNIObjectHandle returnValue = jniFunctions().<CallObjectMethod1FunctionPointer> getCallStaticObjectMethod().invoke(jni, bp.clazz, bp.method, name);
        boolean result = returnValue.notEqual(nullHandle());
        if (clearException(jni)) {
            result = false;
        }
        if (result && returnsEnumeration) {
            result = hasEnumerationElements(jni, returnValue);
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, fromJniString(jni, name));
    }

    private static void newProxyInstance(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        JNIObjectHandle invokeHandler = getObjectArgument(2);
        boolean result = nullHandle().notEqual(jniFunctions().<CallObjectMethod3FunctionPointer> getCallStaticObjectMethod()
                        .invoke(jni, bp.clazz, bp.method, classLoader, ifaces, invokeHandler));
        if (clearException(jni)) {
            result = false;
        }
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, TraceWriter.UNKNOWN_VALUE, ifaceNames, TraceWriter.UNKNOWN_VALUE);
    }

    private static void getProxyClass(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        boolean result = nullHandle().notEqual(jniFunctions().<CallObjectMethod2FunctionPointer> getCallStaticObjectMethod()
                        .invoke(jni, bp.clazz, bp.method, classLoader, ifaces));
        if (clearException(jni)) {
            result = false;
        }
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, TraceWriter.UNKNOWN_VALUE, ifaceNames, TraceWriter.UNKNOWN_VALUE);
    }

    private static Object getClassArrayNames(JNIEnvironment jni, JNIObjectHandle classArray) {
        Object classNames = TraceWriter.EXPLICIT_NULL;
        if (classArray.notEqual(nullHandle())) {
            classNames = TraceWriter.UNKNOWN_VALUE;
            int length = jniFunctions().getGetArrayLength().invoke(jni, classArray);
            if (!clearException(jni) && length >= 0) {
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    JNIObjectHandle clazz = jniFunctions().getGetObjectArrayElement().invoke(jni, classArray, i);
                    if (!clearException(jni)) {
                        list.add(getClassNameOr(jni, clazz, TraceWriter.EXPLICIT_NULL, TraceWriter.UNKNOWN_VALUE));
                    } else {
                        list.add(TraceWriter.UNKNOWN_VALUE);
                    }
                }
                classNames = list.toArray();
            }
        }
        return classNames;
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static void onBreakpoint(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni,
                    @SuppressWarnings("unused") JNIObjectHandle thread, JNIMethodId method, @SuppressWarnings("unused") long location) {

        if (recursive.get()) {
            return;
        }
        recursive.set(true);
        try {
            JNIObjectHandle callerClass = getCallerClass(1);
            Breakpoint bp = installedBreakpoints.get(method.rawValue());
            bp.specification.handler.dispatch(jni, callerClass, bp);
        } catch (Throwable t) {
            VMError.shouldNotReachHere(t);
        }
        guarantee(!testException(jni));
        recursive.set(false);
    }

    private static final CEntryPointLiteral<CFunctionPointer> onBreakpointLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onBreakpoint",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, long.class);

    public static void onLoad(JvmtiEnv jvmti, JvmtiEventCallbacks callbacks) {
        JvmtiCapabilities capabilities = UnmanagedMemory.calloc(SizeOf.get(JvmtiCapabilities.class));
        check(jvmti.getFunctions().GetCapabilities().invoke(jvmti, capabilities));
        capabilities.setCanGenerateBreakpointEvents(1);
        capabilities.setCanAccessLocalVariables(1);
        check(jvmti.getFunctions().AddCapabilities().invoke(jvmti, capabilities));
        UnmanagedMemory.free(capabilities);

        Support.check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullHandle()));

        callbacks.setBreakpoint(onBreakpointLiteral.getFunctionPointer());
    }

    public static void onVMInit(JvmtiEnv jvmti, JNIEnvironment jni) {
        Map<Long, Breakpoint> breakpoints = new HashMap<>(BREAKPOINT_SPECIFICATIONS.length);

        JNIObjectHandle lastClass = nullHandle();
        String lastClassName = null;
        for (BreakpointSpecification br : BREAKPOINT_SPECIFICATIONS) {
            JNIObjectHandle clazz;
            if (lastClassName != null && lastClassName.equals(br.className)) {
                clazz = lastClass;
            } else {
                try (CCharPointerHolder cname = toCString(br.className)) {
                    clazz = jniFunctions().getFindClass().invoke(jni, cname.get());
                    checkNoException(jni);
                }
                clazz = jniFunctions().getNewGlobalRef().invoke(jni, clazz);
                checkNoException(jni);
                lastClass = clazz;
                lastClassName = br.className;
            }
            guarantee(clazz.notEqual(nullHandle()));
            JNIMethodId method;
            try (CCharPointerHolder cname = toCString(br.methodName); CCharPointerHolder csignature = toCString(br.signature)) {
                method = jniFunctions().getGetMethodID().invoke(jni, clazz, cname.get(), csignature.get());
                if (method.isNull()) {
                    clearException(jni);
                    method = jniFunctions().getGetStaticMethodID().invoke(jni, clazz, cname.get(), csignature.get());
                }
                guarantee(!testException(jni) && method.isNonNull());
                check(jvmtiFunctions().SetBreakpoint().invoke(jvmti, method, 0L));
            }
            breakpoints.put(method.rawValue(), new Breakpoint(br, clazz, method));
        }
        installedBreakpoints = breakpoints;
    }

    public static void onVMStart(TraceWriter writer) {
        BreakpointInterceptor.traceWriter = writer;
    }

    public static void onUnload(JNIEnvironment env) {
        installedBreakpoints.values().stream().map(bp -> bp.clazz.rawValue()).distinct().forEach(
                        ref -> jniFunctions().getDeleteGlobalRef().invoke(env, WordFactory.pointer(ref)));
        installedBreakpoints = null;
        traceWriter = null;
    }

    private interface BreakpointHandler {
        void dispatch(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp);
    }

    private static final BreakpointSpecification[] BREAKPOINT_SPECIFICATIONS = {
                    brk("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", BreakpointInterceptor::forName),
                    brk("java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", BreakpointInterceptor::forName),

                    brk("java/lang/Class", "getFields", "()[Ljava/lang/reflect/Field;", BreakpointInterceptor::getMembers),
                    brk("java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getMembers),
                    brk("java/lang/Class", "getConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getMembers),
                    brk("java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;", BreakpointInterceptor::getMembers),
                    brk("java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getMembers),
                    brk("java/lang/Class", "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getMembers),

                    brk("java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", BreakpointInterceptor::getField),
                    brk("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", BreakpointInterceptor::getDeclaredField),
                    brk("java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getMethod),
                    brk("java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),
                    brk("java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getDeclaredMethod),
                    brk("java/lang/Class", "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),

                    brk("java/lang/Class", "getEnclosingMethod", "()Ljava/lang/reflect/Method;", BreakpointInterceptor::getEnclosingMethod),
                    brk("java/lang/Class", "getEnclosingConstructor", "()Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getEnclosingMethod),

                    brk("java/lang/Class", "newInstance", "()Ljava/lang/Object;", BreakpointInterceptor::newInstance),

                    brk("java/lang/ClassLoader", "getResource", "(Ljava/lang/String;)Ljava/net/URL;", BreakpointInterceptor::getResource),
                    brk("java/lang/ClassLoader", "getResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", BreakpointInterceptor::getResources),
                    brk("java/lang/ClassLoader", "getSystemResource", "(Ljava/lang/String;)Ljava/net/URL;", BreakpointInterceptor::getSystemResource),
                    brk("java/lang/ClassLoader", "getSystemResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", BreakpointInterceptor::getSystemResources),
                    /*
                     * NOTE: get(System)ResourceAsStream() generallys call get(System)Resource(), no
                     * additional breakpoints necessary
                     */

                    brk("java/lang/reflect/Proxy", "getProxyClass", "(Ljava/lang/ClassLoader;[Ljava/lang/Class;)Ljava/lang/Class;", BreakpointInterceptor::getProxyClass),
                    brk("java/lang/reflect/Proxy", "newProxyInstance",
                                    "(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;", BreakpointInterceptor::newProxyInstance),
    };

    private static BreakpointSpecification brk(String className, String methodName, String signature, BreakpointHandler handler) {
        return new BreakpointSpecification(className, methodName, signature, handler);
    }

    private static final class BreakpointSpecification {
        final String className;
        final String methodName;
        final String signature;
        final BreakpointHandler handler;

        BreakpointSpecification(String className, String methodName, String signature, BreakpointHandler handler) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
            this.handler = handler;
        }
    }

    private static final class Breakpoint {
        final BreakpointSpecification specification;
        final JNIObjectHandle clazz;
        final JNIMethodId method;

        Breakpoint(BreakpointSpecification specification, JNIObjectHandle clazz, JNIMethodId method) {
            this.specification = specification;
            this.clazz = clazz;
            this.method = method;
        }
    }

    private BreakpointInterceptor() {
    }
}
