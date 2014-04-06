/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import static java.lang.reflect.Modifier.*;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.ProfilingInfo.TriState;

/**
 * Miscellaneous collection of utility methods used by {@code com.oracle.graal.api.meta} and its
 * clients.
 */
public class MetaUtil {

    private static class ClassInfo {
        public long totalSize;
        public long instanceCount;

        @Override
        public String toString() {
            return "totalSize=" + totalSize + ", instanceCount=" + instanceCount;
        }
    }

    /**
     * Returns the number of bytes occupied by this constant value or constant object and
     * recursively all values reachable from this value.
     * 
     * @param constant the constant whose bytes should be measured
     * @param printTopN print total size and instance count of the top n classes is desired
     * @return the number of bytes occupied by this constant
     */
    public static long getMemorySizeRecursive(MetaAccessProvider access, Constant constant, PrintStream out, int printTopN) {
        IdentityHashMap<Object, Boolean> marked = new IdentityHashMap<>();
        Stack<Constant> stack = new Stack<>();
        if (constant.getKind() == Kind.Object && constant.isNonNull()) {
            marked.put(constant.asObject(), Boolean.TRUE);
        }
        final HashMap<Class, ClassInfo> histogram = new HashMap<>();
        stack.push(constant);
        long sum = 0;
        while (!stack.isEmpty()) {
            Constant c = stack.pop();
            long memorySize = access.getMemorySize(constant);
            sum += memorySize;
            if (c.getKind() == Kind.Object && c.isNonNull()) {
                Class<?> clazz = c.asObject().getClass();
                if (!histogram.containsKey(clazz)) {
                    histogram.put(clazz, new ClassInfo());
                }
                ClassInfo info = histogram.get(clazz);
                info.instanceCount++;
                info.totalSize += memorySize;
                ResolvedJavaType type = access.lookupJavaType(c);
                if (type.isArray()) {
                    if (!type.getComponentType().isPrimitive()) {
                        Object[] array = (Object[]) c.asObject();
                        for (Object value : array) {
                            Constant forObject = Constant.forObject(value);
                            pushConstant(marked, stack, forObject);
                        }
                    }
                } else {
                    ResolvedJavaField[] instanceFields = type.getInstanceFields(true);
                    for (ResolvedJavaField f : instanceFields) {
                        if (f.getKind() == Kind.Object) {
                            Constant value = f.readValue(c);
                            pushConstant(marked, stack, value);
                        }
                    }
                }
            }
        }
        ArrayList<Class> clazzes = new ArrayList<>();
        clazzes.addAll(histogram.keySet());
        Collections.sort(clazzes, new Comparator<Class>() {

            @Override
            public int compare(Class o1, Class o2) {
                long l1 = histogram.get(o1).totalSize;
                long l2 = histogram.get(o2).totalSize;
                if (l1 > l2) {
                    return -1;
                } else if (l1 == l2) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        int z = 0;
        for (Class c : clazzes) {
            if (z > printTopN) {
                break;
            }
            out.println("Class " + c + ", " + histogram.get(c));
            ++z;
        }

        return sum;
    }

    private static void pushConstant(IdentityHashMap<Object, Boolean> marked, Stack<Constant> stack, Constant value) {
        if (value.isNonNull()) {
            if (!marked.containsKey(value.asObject())) {
                marked.put(value.asObject(), Boolean.TRUE);
                stack.push(value);
            }
        }
    }

    /**
     * Returns true if the specified typed is exactly the type {@link java.lang.Object}.
     */
    public static boolean isJavaLangObject(ResolvedJavaType type) {
        boolean result = type.getSuperclass() == null && !type.isInterface() && type.getKind() == Kind.Object;
        assert result == type.getName().equals("Ljava/lang/Object;") : type.getName();
        return result;
    }

    /**
     * Calls {@link JavaType#resolve(ResolvedJavaType)} on an array of types.
     */
    public static ResolvedJavaType[] resolveJavaTypes(JavaType[] types, ResolvedJavaType accessingClass) {
        ResolvedJavaType[] result = new ResolvedJavaType[types.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = types[i].resolve(accessingClass);
        }
        return result;
    }

    /**
     * Calls {@link MetaAccessProvider#lookupJavaType(Class)} on an array of classes.
     */
    public static ResolvedJavaType[] lookupJavaTypes(MetaAccessProvider metaAccess, Class[] classes) {
        ResolvedJavaType[] result = new ResolvedJavaType[classes.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = metaAccess.lookupJavaType(classes[i]);
        }
        return result;
    }

    /**
     * Gets the elemental type for a given type. The elemental type of an array type is the
     * corresponding zero dimensional (e.g., the elemental type of {@code int[][][]} is {@code int}
     * ). A non-array type is its own elemental type.
     */
    public static ResolvedJavaType getElementalType(ResolvedJavaType type) {
        ResolvedJavaType t = type;
        while (t.getComponentType() != null) {
            t = t.getComponentType();
        }
        return t;
    }

    /**
     * Extends the functionality of {@link Class#getSimpleName()} to include a non-empty string for
     * anonymous and local classes.
     * 
     * @param clazz the class for which the simple name is being requested
     * @param withEnclosingClass specifies if the returned name should be qualified with the name(s)
     *            of the enclosing class/classes of {@code clazz} (if any). This option is ignored
     *            if {@code clazz} denotes an anonymous or local class.
     * @return the simple name
     */
    public static String getSimpleName(Class<?> clazz, boolean withEnclosingClass) {
        final String simpleName = clazz.getSimpleName();
        if (simpleName.length() != 0) {
            if (withEnclosingClass) {
                String prefix = "";
                Class<?> enclosingClass = clazz;
                while ((enclosingClass = enclosingClass.getEnclosingClass()) != null) {
                    prefix = enclosingClass.getSimpleName() + "." + prefix;
                }
                return prefix + simpleName;
            }
            return simpleName;
        }
        // Must be an anonymous or local class
        final String name = clazz.getName();
        int index = name.indexOf('$');
        if (index == -1) {
            return name;
        }
        index = name.lastIndexOf('.', index);
        if (index == -1) {
            return name;
        }
        return name.substring(index + 1);
    }

    /**
     * Converts a given type to its Java programming language name. The following are examples of
     * strings returned by this method:
     * 
     * <pre>
     *     qualified == true:
     *         java.lang.Object
     *         int
     *         boolean[][]
     *     qualified == false:
     *         Object
     *         int
     *         boolean[][]
     * </pre>
     * 
     * @param type the type to be converted to a Java name
     * @param qualified specifies if the package prefix of the type should be included in the
     *            returned name
     * @return the Java name corresponding to {@code type}
     */
    public static String toJavaName(JavaType type, boolean qualified) {
        Kind kind = type.getKind();
        if (kind == Kind.Object) {
            return internalNameToJava(type.getName(), qualified, false);
        }
        return type.getKind().getJavaName();
    }

    /**
     * Converts a given type to its Java programming language name. The following are examples of
     * strings returned by this method:
     * 
     * <pre>
     *      java.lang.Object
     *      int
     *      boolean[][]
     * </pre>
     * 
     * @param type the type to be converted to a Java name
     * @return the Java name corresponding to {@code type}
     */
    public static String toJavaName(JavaType type) {
        return (type == null) ? null : internalNameToJava(type.getName(), true, false);
    }

    /**
     * Returns the type name in the same format as {@link Class#getName()}.
     */
    public static String toClassName(JavaType type) {
        return internalNameToJava(type.getName(), true, true);
    }

    private static String internalNameToJava(String name, boolean qualified, boolean classForNameCompatible) {
        switch (name.charAt(0)) {
            case 'L': {
                String result = name.substring(1, name.length() - 1).replace('/', '.');
                if (!qualified) {
                    final int lastDot = result.lastIndexOf('.');
                    if (lastDot != -1) {
                        result = result.substring(lastDot + 1);
                    }
                }
                return result;
            }
            case '[':
                return classForNameCompatible ? name.replace('/', '.') : internalNameToJava(name.substring(1), qualified, classForNameCompatible) + "[]";
            default:
                if (name.length() != 1) {
                    throw new IllegalArgumentException("Illegal internal name: " + name);
                }
                return Kind.fromPrimitiveOrVoidTypeChar(name.charAt(0)).getJavaName();
        }
    }

    /**
     * Turns an class name in internal format into a resolved Java type.
     */
    public static ResolvedJavaType classForName(String internal, MetaAccessProvider metaAccess, ClassLoader cl) {
        Kind k = Kind.fromTypeString(internal);
        try {
            String n = internalNameToJava(internal, true, true);
            return metaAccess.lookupJavaType(k.isPrimitive() ? k.toJavaClass() : Class.forName(n, true, cl));
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("could not instantiate class described by " + internal, cnfe);
        }
    }

    /**
     * Gets a string for a given method formatted according to a given format specification. A
     * format specification is composed of characters that are to be copied verbatim to the result
     * and specifiers that denote an attribute of the method that is to be copied to the result. A
     * specifier is a single character preceded by a '%' character. The accepted specifiers and the
     * method attributes they denote are described below:
     * 
     * <pre>
     *     Specifier | Description                                          | Example(s)
     *     ----------+------------------------------------------------------------------------------------------
     *     'R'       | Qualified return type                                | "int" "java.lang.String"
     *     'r'       | Unqualified return type                              | "int" "String"
     *     'H'       | Qualified holder                                     | "java.util.Map.Entry"
     *     'h'       | Unqualified holder                                   | "Entry"
     *     'n'       | Method name                                          | "add"
     *     'P'       | Qualified parameter types, separated by ', '         | "int, java.lang.String"
     *     'p'       | Unqualified parameter types, separated by ', '       | "int, String"
     *     'f'       | Indicator if method is unresolved, static or virtual | "unresolved" "static" "virtual"
     *     '%'       | A '%' character                                      | "%"
     * </pre>
     * 
     * @param format a format specification
     * @param method the method to be formatted
     * @return the result of formatting this method according to {@code format}
     * @throws IllegalFormatException if an illegal specifier is encountered in {@code format}
     */
    public static String format(String format, JavaMethod method) throws IllegalFormatException {
        final StringBuilder sb = new StringBuilder();
        int index = 0;
        Signature sig = null;
        while (index < format.length()) {
            final char ch = format.charAt(index++);
            if (ch == '%') {
                if (index >= format.length()) {
                    throw new UnknownFormatConversionException("An unquoted '%' character cannot terminate a method format specification");
                }
                final char specifier = format.charAt(index++);
                boolean qualified = false;
                switch (specifier) {
                    case 'R':
                        qualified = true;
                        // fall through
                    case 'r': {
                        if (sig == null) {
                            sig = method.getSignature();
                        }
                        sb.append(toJavaName(sig.getReturnType(null), qualified));
                        break;
                    }
                    case 'H':
                        qualified = true;
                        // fall through
                    case 'h': {
                        sb.append(toJavaName(method.getDeclaringClass(), qualified));
                        break;
                    }
                    case 'n': {
                        sb.append(method.getName());
                        break;
                    }
                    case 'P':
                        qualified = true;
                        // fall through
                    case 'p': {
                        if (sig == null) {
                            sig = method.getSignature();
                        }
                        for (int i = 0; i < sig.getParameterCount(false); i++) {
                            if (i != 0) {
                                sb.append(", ");
                            }
                            sb.append(toJavaName(sig.getParameterType(i, null), qualified));
                        }
                        break;
                    }
                    case 'f': {
                        sb.append(!(method instanceof ResolvedJavaMethod) ? "unresolved" : isStatic(((ResolvedJavaMethod) method).getModifiers()) ? "static" : "virtual");
                        break;
                    }
                    case '%': {
                        sb.append('%');
                        break;
                    }
                    default: {
                        throw new UnknownFormatConversionException(String.valueOf(specifier));
                    }
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Gets a string for a given field formatted according to a given format specification. A format
     * specification is composed of characters that are to be copied verbatim to the result and
     * specifiers that denote an attribute of the field that is to be copied to the result. A
     * specifier is a single character preceded by a '%' character. The accepted specifiers and the
     * field attributes they denote are described below:
     * 
     * <pre>
     *     Specifier | Description                                          | Example(s)
     *     ----------+------------------------------------------------------------------------------------------
     *     'T'       | Qualified type                                       | "int" "java.lang.String"
     *     't'       | Unqualified type                                     | "int" "String"
     *     'H'       | Qualified holder                                     | "java.util.Map.Entry"
     *     'h'       | Unqualified holder                                   | "Entry"
     *     'n'       | Field name                                           | "age"
     *     'f'       | Indicator if field is unresolved, static or instance | "unresolved" "static" "instance"
     *     '%'       | A '%' character                                      | "%"
     * </pre>
     * 
     * @param format a format specification
     * @param field the field to be formatted
     * @return the result of formatting this field according to {@code format}
     * @throws IllegalFormatException if an illegal specifier is encountered in {@code format}
     */
    public static String format(String format, JavaField field) throws IllegalFormatException {
        final StringBuilder sb = new StringBuilder();
        int index = 0;
        JavaType type = field.getType();
        while (index < format.length()) {
            final char ch = format.charAt(index++);
            if (ch == '%') {
                if (index >= format.length()) {
                    throw new UnknownFormatConversionException("An unquoted '%' character cannot terminate a field format specification");
                }
                final char specifier = format.charAt(index++);
                boolean qualified = false;
                switch (specifier) {
                    case 'T':
                        qualified = true;
                        // fall through
                    case 't': {
                        sb.append(toJavaName(type, qualified));
                        break;
                    }
                    case 'H':
                        qualified = true;
                        // fall through
                    case 'h': {
                        sb.append(toJavaName(field.getDeclaringClass(), qualified));
                        break;
                    }
                    case 'n': {
                        sb.append(field.getName());
                        break;
                    }
                    case 'f': {
                        sb.append(!(field instanceof ResolvedJavaField) ? "unresolved" : isStatic(((ResolvedJavaField) field).getModifiers()) ? "static" : "instance");
                        break;
                    }
                    case '%': {
                        sb.append('%');
                        break;
                    }
                    default: {
                        throw new UnknownFormatConversionException(String.valueOf(specifier));
                    }
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Gets the annotations of a particular type for the formal parameters of a given method.
     * 
     * @param annotationClass the Class object corresponding to the annotation type
     * @param method the method for which a parameter annotations are being requested
     * @return the annotation of type {@code annotationClass} (if any) for each formal parameter
     *         present
     */
    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T[] getParameterAnnotations(Class<T> annotationClass, ResolvedJavaMethod method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        T[] result = (T[]) Array.newInstance(annotationClass, parameterAnnotations.length);
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation a : parameterAnnotations[i]) {
                if (a.annotationType() == annotationClass) {
                    result[i] = annotationClass.cast(a);
                }
            }
        }
        return result;
    }

    /**
     * Gets the annotation of a particular type for a formal parameter of a given method.
     * 
     * @param annotationClass the Class object corresponding to the annotation type
     * @param parameterIndex the index of a formal parameter of {@code method}
     * @param method the method for which a parameter annotation is being requested
     * @return the annotation of type {@code annotationClass} for the formal parameter present, else
     *         null
     * @throws IndexOutOfBoundsException if {@code parameterIndex} does not denote a formal
     *             parameter
     */
    public static <T extends Annotation> T getParameterAnnotation(Class<T> annotationClass, int parameterIndex, ResolvedJavaMethod method) {
        if (parameterIndex >= 0) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (Annotation a : parameterAnnotations[parameterIndex]) {
                if (a.annotationType() == annotationClass) {
                    return annotationClass.cast(a);
                }
            }
        }
        return null;
    }

    /**
     * Convenient shortcut for calling
     * {@link #appendLocation(StringBuilder, ResolvedJavaMethod, int)} without having to supply a a
     * {@link StringBuilder} instance and convert the result to a string.
     */
    public static String toLocation(ResolvedJavaMethod method, int bci) {
        return appendLocation(new StringBuilder(), method, bci).toString();
    }

    /**
     * Appends a string representation of a location specified by a given method and bci to a given
     * {@link StringBuilder}. If a stack trace element with a non-null file name and non-negative
     * line number is {@linkplain ResolvedJavaMethod#asStackTraceElement(int) available} for the
     * given method, then the string returned is the {@link StackTraceElement#toString()} value of
     * the stack trace element, suffixed by the bci location. For example:
     * 
     * <pre>
     *     java.lang.String.valueOf(String.java:2930) [bci: 12]
     * </pre>
     * 
     * Otherwise, the string returned is the value of applying {@link #format(String, JavaMethod)}
     * with the format string {@code "%H.%n(%p)"}, suffixed by the bci location. For example:
     * 
     * <pre>
     *     java.lang.String.valueOf(int) [bci: 12]
     * </pre>
     * 
     * @param sb
     * @param method
     * @param bci
     */
    public static StringBuilder appendLocation(StringBuilder sb, ResolvedJavaMethod method, int bci) {
        if (method != null) {
            StackTraceElement ste = method.asStackTraceElement(bci);
            if (ste.getFileName() != null && ste.getLineNumber() > 0) {
                sb.append(ste);
            } else {
                sb.append(format("%H.%n(%p)", method));
            }
        } else {
            sb.append("Null method");
        }
        return sb.append(" [bci: ").append(bci).append(']');
    }

    public static JavaType[] signatureToTypes(ResolvedJavaMethod method) {
        JavaType receiver = isStatic(method.getModifiers()) ? null : method.getDeclaringClass();
        return signatureToTypes(method.getSignature(), receiver);
    }

    public static JavaType[] signatureToTypes(Signature signature, JavaType receiverType) {
        int args = signature.getParameterCount(false);
        JavaType[] result;
        int i = 0;
        if (receiverType != null) {
            result = new JavaType[args + 1];
            result[0] = receiverType;
            i = 1;
        } else {
            result = new JavaType[args];
        }
        for (int j = 0; j < args; j++) {
            result[i + j] = signature.getParameterType(j, null);
        }
        return result;
    }

    /**
     * Gets the <a
     * href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">method
     * descriptor</a> corresponding to this signature. For example:
     * 
     * <pre>
     * (ILjava/lang/String;D)V
     * </pre>
     * 
     * .
     * 
     * @param sig the {@link Signature} to be converted.
     * @return the signature as a string
     */
    public static String signatureToMethodDescriptor(Signature sig) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < sig.getParameterCount(false); ++i) {
            sb.append(sig.getParameterType(i, null).getName());
        }
        sb.append(')').append(sig.getReturnType(null).getName());
        return sb.toString();
    }

    /**
     * Formats some profiling information associated as a string.
     * 
     * @param info the profiling info to format
     * @param method an optional method that augments the profile string returned
     * @param sep the separator to use for each separate profile record
     */
    public static String profileToString(ProfilingInfo info, ResolvedJavaMethod method, String sep) {
        StringBuilder buf = new StringBuilder(100);
        if (method != null) {
            buf.append(String.format("canBeStaticallyBound: %b%s", method.canBeStaticallyBound(), sep));
        }
        for (int i = 0; i < info.getCodeSize(); i++) {
            if (info.getExecutionCount(i) != -1) {
                buf.append(String.format("executionCount@%d: %d%s", i, info.getExecutionCount(i), sep));
            }

            if (info.getBranchTakenProbability(i) != -1) {
                buf.append(String.format("branchProbability@%d: %.6f%s", i, info.getBranchTakenProbability(i), sep));
            }

            double[] switchProbabilities = info.getSwitchProbabilities(i);
            if (switchProbabilities != null) {
                buf.append(String.format("switchProbabilities@%d:", i));
                for (int j = 0; j < switchProbabilities.length; j++) {
                    buf.append(String.format(" %.6f", switchProbabilities[j]));
                }
                buf.append(sep);
            }

            if (info.getExceptionSeen(i) != TriState.UNKNOWN) {
                buf.append(String.format("exceptionSeen@%d: %s%s", i, info.getExceptionSeen(i).name(), sep));
            }

            if (info.getNullSeen(i) != TriState.UNKNOWN) {
                buf.append(String.format("nullSeen@%d: %s%s", i, info.getNullSeen(i).name(), sep));
            }

            JavaTypeProfile typeProfile = info.getTypeProfile(i);
            appendProfile(buf, typeProfile, i, "types", sep);

            JavaMethodProfile methodProfile = info.getMethodProfile(i);
            appendProfile(buf, methodProfile, i, "methods", sep);
        }

        boolean firstDeoptReason = true;
        for (DeoptimizationReason reason : DeoptimizationReason.values()) {
            int count = info.getDeoptimizationCount(reason);
            if (count > 0) {
                if (firstDeoptReason) {
                    buf.append("deoptimization history").append(sep);
                    firstDeoptReason = false;
                }
                buf.append(String.format(" %s: %d%s", reason.name(), count, sep));
            }
        }
        if (buf.length() == 0) {
            return "";
        }
        String s = buf.toString();
        assert s.endsWith(sep);
        return s.substring(0, s.length() - sep.length());
    }

    private static void appendProfile(StringBuilder buf, AbstractJavaProfile profile, int bci, String type, String sep) {
        if (profile != null) {
            AbstractProfiledItem[] pitems = profile.getItems();
            if (pitems != null) {
                buf.append(String.format("%s@%d:", type, bci));
                for (int j = 0; j < pitems.length; j++) {
                    AbstractProfiledItem pitem = pitems[j];
                    buf.append(String.format(" %.6f (%s)%s", pitem.getProbability(), pitem.getItem(), sep));
                }
                if (profile.getNotRecordedProbability() != 0) {
                    buf.append(String.format(" %.6f <other %s>%s", profile.getNotRecordedProbability(), type, sep));
                } else {
                    buf.append(String.format(" <no other %s>%s", type, sep));
                }
            }
        }
    }

    /**
     * Converts a Java source-language class name into the internal form.
     * 
     * @param className the class name
     * @return the internal name form of the class name
     */
    public static String toInternalName(String className) {
        String prefix = "";
        String base = className;
        while (base.endsWith("[]")) {
            prefix += "[";
            base = base.substring(base.length() - 2);
        }

        if (className.equals("boolean")) {
            return prefix + "Z";
        }
        if (className.equals("byte")) {
            return prefix + "B";
        }
        if (className.equals("short")) {
            return prefix + "S";
        }
        if (className.equals("char")) {
            return prefix + "C";
        }
        if (className.equals("int")) {
            return prefix + "I";
        }
        if (className.equals("float")) {
            return prefix + "F";
        }
        if (className.equals("long")) {
            return prefix + "J";
        }
        if (className.equals("double")) {
            return prefix + "D";
        }
        if (className.equals("void")) {
            return prefix + "V";
        }
        return prefix + "L" + className.replace('.', '/') + ";";
    }

    /**
     * Prepends the String {@code indentation} to every line in String {@code lines}, including a
     * possibly non-empty line following the final newline.
     */
    public static String indent(String lines, String indentation) {
        if (lines.length() == 0) {
            return lines;
        }
        final String newLine = "\n";
        if (lines.endsWith(newLine)) {
            return indentation + (lines.substring(0, lines.length() - 1)).replace(newLine, newLine + indentation) + newLine;
        }
        return indentation + lines.replace(newLine, newLine + indentation);
    }
}
