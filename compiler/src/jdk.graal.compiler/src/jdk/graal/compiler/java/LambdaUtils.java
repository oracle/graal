/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.java;

import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.Digest;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class LambdaUtils {

    private static final Pattern LAMBDA_PATTERN = Pattern.compile("\\$\\$Lambda[/.][^/]+;");
    public static final String LAMBDA_SPLIT_PATTERN = "\\$\\$Lambda";
    public static final String LAMBDA_CLASS_NAME_SUBSTRING = "$$Lambda";
    public static final String SERIALIZATION_TEST_LAMBDA_CLASS_SUBSTRING = "$$Lambda";
    public static final String SERIALIZATION_TEST_LAMBDA_CLASS_SPLIT_PATTERN = "\\$\\$Lambda";
    public static final String ADDRESS_PREFIX = ".0x";

    private LambdaUtils() {
    }

    /**
     * Creates a stable name for a lambda by hashing all the invokes in the lambda. Lambda class
     * names are typically created based on an increasing atomic counter (e.g.
     * {@code Test$$Lambda$23}). A stable name is created by replacing the substring after
     * {@code "$$Lambda$"} with a hash of the method descriptor for each method invoked by the
     * lambda.
     *
     * Starting from JDK17, the lambda classes can have additional interfaces that lambda should
     * implement. This further means that lambda can have more than one public method (public and
     * not bridge).
     *
     * The scala lambda classes have by default one additional interface with one method. This
     * method has the same signature as the original one but with generalized parameters (all
     * parameters are Object types) and serves as a wrapper that casts parameters to specialized
     * types and calls an original method.
     *
     * @param lambdaType the lambda type to analyze
     * @return stable name for the lambda class
     */
    @SuppressWarnings("try")
    public static String findStableLambdaName(ResolvedJavaType lambdaType) {
        ResolvedJavaMethod[] lambdaProxyMethods = Arrays.stream(lambdaType.getDeclaredMethods(false)).filter(m -> !m.isBridge() && m.isPublic()).toArray(ResolvedJavaMethod[]::new);
        /*
         * Take only the first method to find invoked methods, because the result would be the same
         * for all other methods.
         */
        List<JavaMethod> invokedMethods = findInvokedMethods(lambdaProxyMethods[0]);
        if (invokedMethods.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Lambda without a target invoke: ").append(lambdaType.toClassName());
            for (ResolvedJavaMethod m : lambdaType.getDeclaredMethods(false)) {
                sb.append("\n  Method: ").append(m);
            }
            throw new JVMCIError(sb.toString());
        }
        return createStableLambdaName(lambdaType, invokedMethods);
    }

    /**
     * Finds the methods invoked in the bytecode of the provided method.
     *
     * @param method the method whose bytecode is parsed
     * @return the list of invoked methods
     */
    public static List<JavaMethod> findInvokedMethods(ResolvedJavaMethod method) {
        ConstantPool constantPool = method.getConstantPool();
        List<JavaMethod> invokedMethods = new ArrayList<>();
        for (BytecodeStream stream = new BytecodeStream(method.getCode()); stream.currentBCI() < stream.endBCI(); stream.next()) {
            int opcode = stream.currentBC();
            int cpi;
            switch (opcode) {
                case INVOKEVIRTUAL: // fall through
                case INVOKESPECIAL: // fall through
                case INVOKESTATIC: // fall through
                case INVOKEINTERFACE:
                    cpi = stream.readCPI();
                    invokedMethods.add(constantPool.lookupMethod(cpi, opcode, method));
                    break;
                case INVOKEDYNAMIC:
                    cpi = stream.readCPI4();
                    invokedMethods.add(constantPool.lookupMethod(cpi, opcode, method));
                    break;
                default:
                    break;
            }
        }
        return invokedMethods;
    }

    /**
     * Checks if the passed type is lambda class type based on set flags and the type name.
     *
     * @param type type to be checked
     * @return true if the passed type is lambda type, false otherwise
     */

    public static boolean isLambdaType(ResolvedJavaType type) {
        String typeName = type.getName();
        return type.isFinalFlagSet() && isLambdaName(typeName);
    }

    public static boolean isLambdaName(String name) {
        return isLambdaClassName(name) && lambdaMatcher(name).find();
    }

    private static String createStableLambdaName(ResolvedJavaType lambdaType, List<JavaMethod> invokedMethods) {
        final String lambdaName = lambdaType.getName();
        assert lambdaMatcher(lambdaName).find() : "Stable name should be created for lambda types: " + lambdaName;

        Matcher m = lambdaMatcher(lambdaName);
        /* Generate lambda signature by hashing its composing parts. */
        StringBuilder sb = new StringBuilder();
        /* Append invoked methods. */
        for (JavaMethod method : invokedMethods) {
            sb.append(method.format("%H.%n(%P)%R"));
        }
        /* Append constructor parameter types. */
        for (JavaMethod ctor : lambdaType.getDeclaredConstructors()) {
            sb.append(ctor.format("%P"));
        }
        /* Append implemented interfaces. */
        for (ResolvedJavaType iface : lambdaType.getInterfaces()) {
            sb.append(iface.toJavaName());
        }
        String signature = Digest.digestAsHex(sb.toString());
        GraalError.guarantee(signature.length() == 32, "Expecting a 32 digits long hex value.");
        return m.replaceFirst(Matcher.quoteReplacement(LAMBDA_CLASS_NAME_SUBSTRING + ADDRESS_PREFIX + signature + ";"));
    }

    private static Matcher lambdaMatcher(String value) {
        return LAMBDA_PATTERN.matcher(value);
    }

    /**
     * Extracts lambda capturing class name from the lambda class name.
     *
     * @param className name of the lambda class
     * @return name of the lambda capturing class
     */
    public static String capturingClass(String className) {
        return className.split(LambdaUtils.SERIALIZATION_TEST_LAMBDA_CLASS_SPLIT_PATTERN)[0];
    }

    /**
     * Checks if the passed class is lambda class.
     *
     * @param clazz class to be checked
     * @return true if the clazz is lambda class, false instead
     */
    public static boolean isLambdaClass(Class<?> clazz) {
        return isLambdaClassName(clazz.getName());
    }

    /**
     * Checks if the passed class name is lambda class name.
     *
     * @param className name of the class
     * @return true if the className is lambda class name, false instead
     */
    public static boolean isLambdaClassName(String className) {
        return className.contains(LAMBDA_CLASS_NAME_SUBSTRING);
    }
}
