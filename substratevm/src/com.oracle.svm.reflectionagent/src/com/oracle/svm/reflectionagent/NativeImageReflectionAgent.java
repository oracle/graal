/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflectionagent;

import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIJavaVM;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.AgentIsolate;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;
import com.oracle.svm.jvmtiagentbase.JvmtiAgentBase;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventMode;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiInterface;
import com.oracle.svm.reflectionagent.analyzers.AnalyzerSuite;
import com.oracle.svm.reflectionagent.analyzers.ConstantArrayAnalyzer;
import com.oracle.svm.reflectionagent.analyzers.ConstantBooleanAnalyzer;
import com.oracle.svm.reflectionagent.analyzers.ConstantClassAnalyzer;
import com.oracle.svm.reflectionagent.analyzers.ConstantMethodHandlesLookupAnalyzer;
import com.oracle.svm.reflectionagent.analyzers.ConstantMethodTypeAnalyzer;
import com.oracle.svm.reflectionagent.analyzers.ConstantStringAnalyzer;
import com.oracle.svm.reflectionagent.cfg.ControlFlowGraphAnalyzer;
import com.oracle.svm.reflectionagent.cfg.ControlFlowGraphNode;
import com.oracle.svm.shaded.org.objectweb.asm.ClassReader;
import com.oracle.svm.shaded.org.objectweb.asm.ClassWriter;
import com.oracle.svm.shaded.org.objectweb.asm.tree.AbstractInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.ClassNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.MethodInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.MethodNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.Analyzer;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.AnalyzerException;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.SourceInterpreter;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.SourceValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.reflectiontags.ConstantTags;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.check;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_CLASS_FILE_LOAD_HOOK;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * A JVMTI agent which analyzes user provided classes and tags reflective method calls which can be
 * proven constant.
 * <p>
 * This way of marking reflective calls as constant decouples the analysis and image runtime
 * behaviour w.r.t. reflection from various optimizations executed on IR graphs.
 */
@SuppressWarnings("unused")
public class NativeImageReflectionAgent extends JvmtiAgentBase<NativeImageReflectionAgentJNIHandleSet> {

    private static final Class<?> CONSTANT_TAGS_CLASS = ConstantTags.class;

    /*
     * Mapping from method signature to indices of arguments which must be constant in order for the
     * call to that method to be considered constant. In case a method is not static, a 0 index
     * corresponds to the method caller.
     */
    private static final Map<MethodCallUtils.Signature, int[]> REFLECTIVE_CALL_CONSTANT_DEFINITIONS = createReflectiveCallConstantDefinitions();
    private static final Map<MethodCallUtils.Signature, int[]> NON_REFLECTIVE_CALL_CONSTANT_DEFINITIONS = createNonReflectiveCallConstantDefinitions();

    /**
     * Defines the reflective methods (which can potentially throw a
     * {@link org.graalvm.nativeimage.MissingReflectionRegistrationError}) which we want to tag for
     * folding in {@link com.oracle.svm.hosted.snippets.ReflectionPlugins}.
     * <p>
     * If proven as constant by our analysis, calls to these methods will be tagged by redirecting
     * their owner to {@link org.graalvm.nativeimage.impl.reflectiontags.ConstantTags} (making them
     * static invocations in the process if necessary).
     */
    private static Map<MethodCallUtils.Signature, int[]> createReflectiveCallConstantDefinitions() {
        Map<MethodCallUtils.Signature, int[]> definitions = new HashMap<>();

        definitions.put(new MethodCallUtils.Signature(Class.class, "forName", Class.class, String.class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "forName", Class.class, String.class, boolean.class, ClassLoader.class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getField", Field.class, String.class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getDeclaredField", Field.class, String.class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getConstructor", Constructor.class, Class[].class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getDeclaredConstructor", Constructor.class, Class[].class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getMethod", Method.class, String.class, Class[].class), new int[]{0, 1, 2});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getDeclaredMethod", Method.class, String.class, Class[].class), new int[]{0, 1, 2});

        definitions.put(new MethodCallUtils.Signature(Class.class, "getFields", Field[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getDeclaredFields", Field[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getConstructors", Constructor[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getDeclaredConstructors", Constructor[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getMethods", Method[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getDeclaredMethods", Method[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getClasses", Class[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getDeclaredClasses", Class[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getNestMembers", Class[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getPermittedSubclasses", Class[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getRecordComponents", RecordComponent[].class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(Class.class, "getSigners", Object[].class), new int[]{0});

        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findClass", Class.class, String.class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findVirtual", MethodHandle.class, Class.class, String.class, MethodType.class), new int[]{0, 1, 2, 3});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findStatic", MethodHandle.class, Class.class, String.class, MethodType.class), new int[]{0, 1, 2, 3});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findConstructor", MethodHandle.class, Class.class, MethodType.class), new int[]{0, 1, 2});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findGetter", MethodHandle.class, Class.class, String.class, Class.class), new int[]{0, 1, 2, 3});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findStaticGetter", MethodHandle.class, Class.class, String.class, Class.class), new int[]{0, 1, 2, 3});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findSetter", MethodHandle.class, Class.class, String.class, Class.class), new int[]{0, 1, 2, 3});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findStaticSetter", MethodHandle.class, Class.class, String.class, Class.class), new int[]{0, 1, 2, 3});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findVarHandle", VarHandle.class, Class.class, String.class, Class.class), new int[]{0, 1, 2, 3});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.Lookup.class, "findStaticVarHandle", VarHandle.class, Class.class, String.class, Class.class), new int[]{0, 1, 2, 3});

        return definitions;
    }

    /**
     * Defines methods which we still need to track, but not tag with
     * {@link org.graalvm.nativeimage.impl.reflectiontags.ConstantTags}. An example of this are
     * various methods for {@link java.lang.invoke.MethodType} construction.
     */
    private static Map<MethodCallUtils.Signature, int[]> createNonReflectiveCallConstantDefinitions() {
        Map<MethodCallUtils.Signature, int[]> definitions = new HashMap<>();

        definitions.put(new MethodCallUtils.Signature(MethodType.class, "methodType", MethodType.class, Class.class), new int[]{0});
        definitions.put(new MethodCallUtils.Signature(MethodType.class, "methodType", MethodType.class, Class.class, Class.class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(MethodType.class, "methodType", MethodType.class, Class.class, Class[].class), new int[]{0, 1});
        definitions.put(new MethodCallUtils.Signature(MethodType.class, "methodType", MethodType.class, Class.class, Class.class, Class[].class), new int[]{0, 1, 2});
        definitions.put(new MethodCallUtils.Signature(MethodType.class, "methodType", MethodType.class, Class.class, MethodType.class), new int[]{0, 1});

        definitions.put(new MethodCallUtils.Signature(MethodHandles.class, "lookup", MethodHandles.Lookup.class), new int[]{});
        definitions.put(new MethodCallUtils.Signature(MethodHandles.class, "privateLookupIn", MethodHandles.Lookup.class, Class.class, MethodHandles.Lookup.class), new int[]{0, 1});

        return definitions;
    }

    private static final CEntryPointLiteral<CFunctionPointer> ON_CLASS_FILE_LOAD_HOOK = CEntryPointLiteral.create(NativeImageReflectionAgent.class, "onClassFileLoadHook",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class, CCharPointer.class, JNIObjectHandle.class, int.class, CCharPointer.class, CIntPointer.class,
                    CCharPointerPointer.class);

    @Override
    protected JNIHandleSet constructJavaHandles(JNIEnvironment env) {
        return new NativeImageReflectionAgentJNIHandleSet(env);
    }

    @Override
    protected int onLoadCallback(JNIJavaVM vm, JvmtiEnv jvmti, JvmtiEventCallbacks callbacks, String options) {
        callbacks.setClassFileLoadHook(ON_CLASS_FILE_LOAD_HOOK.getFunctionPointer());
        return 0;
    }

    @Override
    protected void onVMInitCallback(JvmtiEnv jvmti, JNIEnvironment jni, JNIObjectHandle thread) {
        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullHandle()));
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    @SuppressWarnings("unused")
    private static void onClassFileLoadHook(JvmtiEnv jvmti, JNIEnvironment jni, JNIObjectHandle classBeingRedefined,
                    JNIObjectHandle loader, CCharPointer name, JNIObjectHandle protectionDomain, int classDataLen, CCharPointer classData,
                    CIntPointer newClassDataLen, CCharPointerPointer newClassData) {
        if (shouldIgnoreClassLoader(jni, loader)) {
            return;
        }

        String className = CTypeConversion.toJavaString(name);
        if (AccessAdvisor.PROXY_CLASS_NAME_PATTERN.matcher(className).matches()) {
            return;
        }

        byte[] clazzData = new byte[classDataLen];
        CTypeConversion.asByteBuffer(classData, classDataLen).get(clazzData);
        try {
            byte[] newClazzData = instrumentClass(clazzData);
            int newClazzDataLen = newClazzData.length;
            Support.check(jvmti.getFunctions().Allocate().invoke(jvmti, newClazzDataLen, newClassData));
            CTypeConversion.asByteBuffer(newClassData.read(), newClazzDataLen).put(newClazzData);
            newClassDataLen.write(newClazzDataLen);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * We're only interested in analyzing and instrumenting user provided classes, so we're handling
     * that by checking which class loader the class was loaded by. In case a class was loaded by a
     * builtin class loader, we ignore it.
     */
    private static boolean shouldIgnoreClassLoader(JNIEnvironment jni, JNIObjectHandle loader) {
        NativeImageReflectionAgent agent = singleton();
        JNIObjectHandle platformClassLoader = agent.handles().platformClassLoader;
        JNIObjectHandle builtinAppClassLoader = agent.handles().builtinAppClassLoader;
        JNIObjectHandle jdkInternalReflectDelegatingClassLoader = agent.handles().jdkInternalReflectDelegatingClassLoader;

        return loader.equal(nullHandle()) // Bootstrap class loader
                        || jniFunctions().getIsSameObject().invoke(jni, loader, agent.handles().systemClassLoader) ||
                        !platformClassLoader.equal(nullHandle()) && jniFunctions().getIsSameObject().invoke(jni, loader, platformClassLoader) ||
                        !builtinAppClassLoader.equal(nullHandle()) && jniFunctions().getIsSameObject().invoke(jni, loader, builtinAppClassLoader) ||
                        !jdkInternalReflectDelegatingClassLoader.equal(nullHandle()) && jniFunctions().getIsInstanceOf().invoke(jni, loader, jdkInternalReflectDelegatingClassLoader);
    }

    private static byte[] instrumentClass(byte[] classData) throws AnalyzerException {
        ClassReader reader = new ClassReader(classData);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        for (MethodNode methodNode : classNode.methods) {
            instrumentMethod(methodNode, classNode);
        }
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static void instrumentMethod(MethodNode methodNode, ClassNode classNode) throws AnalyzerException {
        Analyzer<SourceValue> analyzer = new ControlFlowGraphAnalyzer<>(new SourceInterpreter());

        AbstractInsnNode[] instructions = methodNode.instructions.toArray();
        @SuppressWarnings("unchecked")
        ControlFlowGraphNode<SourceValue>[] frames = Arrays.stream(analyzer.analyze(classNode.name, methodNode))
                        .map(frame -> (ControlFlowGraphNode<SourceValue>) frame).toArray(ControlFlowGraphNode[]::new);
        Set<MethodInsnNode> constantCalls = new HashSet<>();

        Set<MethodCallUtils.Signature> allCalls = new HashSet<>(REFLECTIVE_CALL_CONSTANT_DEFINITIONS.keySet());
        allCalls.addAll(NON_REFLECTIVE_CALL_CONSTANT_DEFINITIONS.keySet());

        AnalyzerSuite analyzerSuite = new AnalyzerSuite();
        analyzerSuite.registerAnalyzer(new ConstantStringAnalyzer(instructions, frames, constantCalls));
        analyzerSuite.registerAnalyzer(new ConstantBooleanAnalyzer(instructions, frames, constantCalls));
        analyzerSuite.registerAnalyzer(new ConstantClassAnalyzer(instructions, frames, constantCalls));
        analyzerSuite.registerAnalyzer(new ConstantArrayAnalyzer(instructions, frames, allCalls, new ConstantClassAnalyzer(instructions, frames, constantCalls)));
        analyzerSuite.registerAnalyzer(new ConstantMethodTypeAnalyzer(instructions, frames, constantCalls));
        analyzerSuite.registerAnalyzer(new ConstantMethodHandlesLookupAnalyzer(instructions, frames, constantCalls));

        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i] instanceof MethodInsnNode methodCall) {
                int[] mustBeConstantArgs = REFLECTIVE_CALL_CONSTANT_DEFINITIONS.get(new MethodCallUtils.Signature(methodCall));
                if (mustBeConstantArgs == null) {
                    mustBeConstantArgs = NON_REFLECTIVE_CALL_CONSTANT_DEFINITIONS.get(new MethodCallUtils.Signature(methodCall));
                }
                if (mustBeConstantArgs != null && analyzerSuite.isConstant(methodCall, frames[i], mustBeConstantArgs)) {
                    constantCalls.add(methodCall);
                }
            }
        }

        constantCalls.stream()
                        .filter(cc -> REFLECTIVE_CALL_CONSTANT_DEFINITIONS.containsKey(new MethodCallUtils.Signature(cc)))
                        .forEach(NativeImageReflectionAgent::tagAsConstant);
    }

    private static void tagAsConstant(MethodInsnNode methodCall) {
        if (methodCall.getOpcode() != INVOKESTATIC) {
            methodCall.setOpcode(INVOKESTATIC);
            methodCall.desc = "(L" + methodCall.owner + ";" + methodCall.desc.substring(1);
        }
        methodCall.owner = CONSTANT_TAGS_CLASS.getName().replace('.', '/');
    }

    @Override
    protected int onUnloadCallback(JNIJavaVM vm) {
        return 0;
    }

    @Override
    protected void onVMStartCallback(JvmtiEnv jvmti, JNIEnvironment jni) {

    }

    @Override
    protected void onVMDeathCallback(JvmtiEnv jvmti, JNIEnvironment jni) {

    }

    @Override
    protected int getRequiredJvmtiVersion() {
        return JvmtiInterface.JVMTI_VERSION_9;
    }

    @SuppressWarnings("unused")
    public static class RegistrationFeature implements Feature {

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            JvmtiAgentBase.registerAgent(new NativeImageReflectionAgent());
        }
    }
}
