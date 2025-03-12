package com.oracle.svm.hosted.reflectionanalysis;

import com.oracle.svm.hosted.reflectionanalysis.analyzers.AnalyzerSuite;
import com.oracle.svm.hosted.reflectionanalysis.analyzers.ConstantArrayAnalyzer;
import com.oracle.svm.hosted.reflectionanalysis.analyzers.ConstantBooleanAnalyzer;
import com.oracle.svm.hosted.reflectionanalysis.analyzers.ConstantClassAnalyzer;
import com.oracle.svm.hosted.reflectionanalysis.analyzers.ConstantMethodHandlesLookupAnalyzer;
import com.oracle.svm.hosted.reflectionanalysis.analyzers.ConstantMethodTypeAnalyzer;
import com.oracle.svm.hosted.reflectionanalysis.analyzers.ConstantStringAnalyzer;
import com.oracle.svm.hosted.reflectionanalysis.cfg.ControlFlowGraphAnalyzer;
import com.oracle.svm.hosted.reflectionanalysis.cfg.ControlFlowGraphNode;
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
import org.graalvm.nativeimage.impl.reflectiontags.ConstantTags;

import java.lang.instrument.ClassFileTransformer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.INVOKESTATIC;

public class ReflectionAnalysisTransformer implements ClassFileTransformer {

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

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) {
        Constructor<ClassReader> classReaderConstructor;
        try {
            classReaderConstructor = ClassReader.class.getDeclaredConstructor(byte[].class, int.class, boolean.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        classReaderConstructor.setAccessible(true);
        ClassReader reader;
        try {
            reader = classReaderConstructor.newInstance(classFileBuffer, 0, false);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        for (MethodNode methodNode : classNode.methods) {
            try {
                instrumentMethod(methodNode, classNode);
            } catch (AnalyzerException e) {
                throw new RuntimeException(e);
            }
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
                .forEach(ReflectionAnalysisTransformer::tagAsConstant);
    }

    private static void tagAsConstant(MethodInsnNode methodCall) {
        if (methodCall.getOpcode() != INVOKESTATIC) {
            methodCall.setOpcode(INVOKESTATIC);
            methodCall.desc = "(L" + methodCall.owner + ";" + methodCall.desc.substring(1);
        }
        methodCall.owner = CONSTANT_TAGS_CLASS.getName().replace('.', '/');
    }
}
