/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.common.meta.GuaranteeFolded;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.HostedSubstrateUtil;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.BreakpointNode;
import jdk.graal.compiler.util.Digest;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class SubstrateUtil {

    /**
     * Field that is true during native image generation, but false at run time.
     */
    @GuaranteeFolded public static final boolean HOSTED;

    static {
        /*
         * Static initializer runs on the hosting VM, setting field value to true during native
         * image generation. At run time, the substituted value from below is used, setting the
         * field value to false at run time.
         */
        HOSTED = true;
    }

    public static String getArchitectureName() {
        String arch = System.getProperty("os.arch");
        return switch (arch) {
            case "x86_64" -> "amd64";
            case "arm64" -> "aarch64";
            default -> arch;
        };
    }

    private static boolean isTTY() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        return console.isTerminal();
    }

    public static boolean isNonInteractiveTerminal() {
        return isCISetToTrue() || !isTTY();
    }

    public static boolean isCISetToTrue() {
        return Boolean.parseBoolean(System.getenv("CI"));
    }

    /**
     * Pattern for a single shell command argument that does not need to be quoted.
     */
    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-+=:,./]+");

    /**
     * Reliably quote a string as a single shell command argument.
     */
    public static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "''";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    public static String getShellCommandString(List<String> cmd, boolean multiLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.size(); i++) {
            if (i > 0) {
                sb.append(multiLine ? " \\\n" : " ");
            }
            sb.append(quoteShellArg(cmd.get(i)));
        }
        return sb.toString();
    }

    @TargetClass(com.oracle.svm.core.SubstrateUtil.class)
    static final class Target_com_oracle_svm_core_SubstrateUtil {
        @Alias @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true)//
        private static boolean HOSTED = false;
    }

    @TargetClass(java.io.FileOutputStream.class)
    static final class Target_java_io_FileOutputStream {
        @Alias//
        FileDescriptor fd;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static FileDescriptor getFileDescriptor(FileOutputStream out) {
        return SubstrateUtil.cast(out, Target_java_io_FileOutputStream.class).fd;
    }

    /**
     * Convert C-style to Java-style command line arguments. The first C-style argument, which is
     * always the executable file name, is ignored.
     *
     * @param argc the number of arguments in the {@code argv} array.
     * @param argv a C {@code char**}.
     *
     * @return the command line argument strings in a Java string array.
     */
    public static String[] convertCToJavaArgs(int argc, CCharPointerPointer argv) {
        String[] args = new String[argc - 1];
        for (int i = 1; i < argc; ++i) {
            args[i - 1] = CTypeConversion.toJavaString(argv.read(i));
        }
        return args;
    }

    /**
     * Returns the length of a C {@code char*} string.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord strlen(CCharPointer str) {
        UnsignedWord n = Word.zero();
        while (((Pointer) str).readByte(n) != 0) {
            n = n.add(1);
        }
        return n;
    }

    /**
     * Returns a pointer to the matched character or NULL if the character is not found.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CCharPointer strchr(CCharPointer str, int c) {
        int index = 0;
        while (true) {
            byte b = str.read(index);
            if (b == c) {
                return str.addressOf(index);
            }
            if (b == 0) {
                return Word.zero();
            }
            index += 1;
        }
    }

    /**
     * The same as {@link Class#cast}. This method is available for use in places where either the
     * Java compiler or static analysis tools would complain about a cast because the cast appears
     * to violate the Java type system rules.
     *
     * The most prominent example are casts between a {@link TargetClass} and the original class,
     * i.e., two classes that appear to be unrelated from the Java type system point of view, but
     * are actually the same class.
     */
    @SuppressWarnings({"unused", "unchecked"})
    @AlwaysInline("Some callers rely on this never becoming an actual method call.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> T cast(Object obj, Class<T> toType) {
        return (T) obj;
    }

    /**
     * Checks whether assertions are enabled in the VM.
     *
     * @return true if assertions are enabled.
     */
    @SuppressWarnings("all")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }

    /**
     * Emits a node that triggers a breakpoint in debuggers.
     *
     * @param arg0 value to inspect when the breakpoint hits
     * @see BreakpointNode how to use breakpoints and inspect breakpoint values in the debugger
     */
    @NodeIntrinsic(BreakpointNode.class)
    public static native void breakpoint(Object arg0);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isPowerOf2(long value) {
        return (value & (value - 1)) == 0;
    }

    /** The functional interface for a "thunk". */
    @FunctionalInterface
    public interface Thunk {

        /** The method to be supplied by the implementor. */
        void invoke();
    }

    /**
     * Similar to {@link String#split(String)} but with a fixed separator string instead of a
     * regular expression. This avoids making regular expression code reachable.
     */
    public static String[] split(String value, String separator) {
        return split(value, separator, 0);
    }

    /**
     * Similar to {@link String#split(String, int)} but with a fixed separator string instead of a
     * regular expression. This avoids making regular expression code reachable.
     */
    public static String[] split(String value, String separator, int limit) {
        return StringUtil.split(value, separator, limit);
    }

    /**
     * Convenience method that unwraps the method details and delegates to the currently registered
     * UniqueShortNameProvider image singleton with the significant exception that it always passes
     * null for the class loader.
     *
     * @param m a method whose unique short name is required
     * @return a unique short name for the method
     */
    public static String uniqueShortName(ResolvedJavaMethod m) {
        return UniqueShortNameProvider.singleton().uniqueShortName(null, m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
    }

    /**
     * Delegate to the corresponding method of the currently registered UniqueShortNameProvider
     * image singleton.
     *
     * @param loader the class loader for the method's owning class
     * @param declaringClass the method's declaring class
     * @param methodName the method's name
     * @param methodSignature the method's signature
     * @param isConstructor true if the method is a constructor otherwise false
     * @return a unique short name for the method
     */
    public static String uniqueShortName(ClassLoader loader, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        return UniqueShortNameProvider.singleton().uniqueShortName(loader, declaringClass, methodName, methodSignature, isConstructor);
    }

    /**
     * Delegate to the corresponding method of the currently registered UniqueShortNameProvider
     * image singleton.
     *
     * @param m a member whose unique short name is required
     * @return a unique short name for the member
     */
    public static String uniqueShortName(Member m) {
        return UniqueShortNameProvider.singleton().uniqueShortName(m);
    }

    /**
     * Generate a unique short name to be used as the selector for a stub method which invokes the
     * supplied target method. Note that the returned name must be derived using the name and class
     * of the target method even though the stub method will be owned to another class. This ensures
     * that any two stubs which target corresponding methods whose selector name is identical will
     * end up with different stub names.
     *
     * @param m a stub target method for which a unique stub method selector name is required
     * @return a unique stub name for the method
     */
    public static String uniqueStubName(ResolvedJavaMethod m) {
        return defaultUniqueShortName("", m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
    }

    public static String defaultUniqueShortName(String loaderNameAndId, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        StringBuilder sb = new StringBuilder(loaderNameAndId);
        sb.append(declaringClass.toClassName()).append(".").append(methodName).append("(");
        for (int i = 0; i < methodSignature.getParameterCount(false); i++) {
            sb.append(methodSignature.getParameterType(i, null).toClassName()).append(",");
        }
        sb.append(')');
        if (!isConstructor) {
            sb.append(methodSignature.getReturnType(null).toClassName());
        }

        return shortenClassName(stripPackage(declaringClass.toJavaName())) + "_" +
                        (isConstructor ? "" : stripExistingDigest(methodName) + "_") +
                        Digest.digest(sb.toString());
    }

    public static String defaultUniqueShortName(Member m) {
        StringBuilder fullName = new StringBuilder();
        fullName.append(m.getDeclaringClass().getName()).append(".");
        if (m instanceof Constructor) {
            fullName.append("<init>");
        } else {
            fullName.append(m.getName());
        }
        if (m instanceof Executable) {
            fullName.append("(");
            for (Class<?> c : ((Executable) m).getParameterTypes()) {
                fullName.append(c.getName()).append(",");
            }
            fullName.append(')');
            if (m instanceof Method) {
                fullName.append(((Method) m).getReturnType().getName());
            }
        }

        return shortenClassName(stripPackage(m.getDeclaringClass().getTypeName())) + "_" +
                        (m instanceof Constructor ? "" : stripExistingDigest(m.getName()) + "_") +
                        Digest.digest(fullName.toString());
    }

    /**
     * Returns a unique identifier for a class loader that can be folded into the unique short name
     * of methods where needed in order to disambiguate name collisions that can arise when the same
     * class bytecode is loaded by more than one loader.
     *
     * @param loader The loader whose identifier is to be returned.
     * @return A unique identifier for the classloader or the empty string when the loader is one of
     *         the special set whose method names do not need qualification.
     */
    public static String runtimeClassLoaderNameAndId(ClassLoader loader) {
        ClassLoader runtimeClassLoader = SubstrateUtil.HOSTED ? HostedSubstrateUtil.getRuntimeClassLoader(loader) : loader;

        if (runtimeClassLoader == null) {
            return "";
        }
        try {
            return (String) classLoaderNameAndId.get(runtimeClassLoader);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere("Cannot reflectively access ClassLoader.nameAndId");
        }
    }

    private static Field classLoaderNameAndId = ReflectionUtil.lookupField(ClassLoader.class, "nameAndId");

    /**
     * Mangle the given method name according to our image's (default) mangling convention. A rough
     * requirement is that symbol names are valid symbol name tokens for the assembler. (This is
     * necessary to use them in linker command lines, which we currently do in
     * NativeImageGenerator.) These are of the form '[a-zA-Z\._\$][a-zA-Z0-9\$_]*'. We use the
     * underscore sign as an escape character. It is always followed by four hex digits representing
     * the escaped character in natural (big-endian) order. We do not allow the dollar sign, even
     * though it is legal, because it has special meaning in some shells and disturbs command lines.
     *
     * @param methodName a string to mangle
     * @return a mangled version of methodName
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static String mangleName(String methodName) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < methodName.length(); ++i) {
            char c = methodName.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (i == 0 && c == '.') || (i > 0 && c >= '0' && c <= '9')) {
                // it's legal in this position
                out.append(c);
            } else if (c == '_') {
                out.append("__");
            } else {
                out.append('_');
                out.append(String.format("%04x", (int) c));
            }
        }
        String mangled = out.toString();
        assert mangled.matches("[a-zA-Z\\._][a-zA-Z0-9_]*") : mangled;
        /*-
         * To demangle, the following pipeline works for me (assuming no multi-byte characters):
         *
         * sed -r 's/\_([0-9a-f]{4})/\n\1\n/g' | sed -r 's#^[0-9a-f]{2}([0-9a-f]{2})#/usr/bin/printf "\\x\1"#e' | tr -d '\n'
         *
         * It's not strictly correct if the first characters after an escape sequence
         * happen to match ^[0-9a-f]{2}, but hey....
         */
        return mangled;
    }

    public static int arrayTypeDimension(Class<?> clazz) {
        int dimension = 0;
        Class<?> componentType = clazz;
        while (componentType.isArray()) {
            componentType = componentType.getComponentType();
            dimension++;
        }
        return dimension;
    }

    public static int arrayTypeDimension(ResolvedJavaType arrayType) {
        int dimension = 0;
        ResolvedJavaType componentType = arrayType;
        while (componentType.isArray()) {
            componentType = componentType.getComponentType();
            dimension++;
        }
        return dimension;
    }

    public static String stripPackage(String qualifiedClassName) {
        /* Anonymous classes can contain a '/' which can lead to an invalid binary name. */
        return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1).replace("/", "");
    }

    public static UUID getUUIDFromString(String value) {
        return Digest.digestAsUUID(value);
    }

    /**
     * Shorten lambda class names, as well as excessively long class names that can happen with
     * deeply nested inner classes. We keep the end of the class name, because the innermost classes
     * are the most interesting part of the name.
     */
    private static String shortenClassName(String className) {
        String result = className;

        /*
         * Lambda classes have a 32-byte digest (because hex encoding is required), so with the
         * prefix just the Lambda part is already longer than our desired maximum name. We keep only
         * the first part of the digest, which is sufficent to distinguish multiple lambdas defined
         * by the same holder class.
         */
        int lambdaStart = result.indexOf(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING);
        if (lambdaStart != -1) {
            int start = lambdaStart + LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING.length() + LambdaUtils.ADDRESS_PREFIX.length();
            int keepHashLen = 8;
            if (result.length() > start + keepHashLen) {
                result = result.substring(0, lambdaStart) + "$$L" + result.substring(start, start + keepHashLen);
            }
        }

        int maxLen = 40;
        if (result.length() > maxLen) {
            result = result.substring(result.length() - maxLen, result.length());
        }
        return result;
    }

    /**
     * Strip off a potential {@link Digest} from the end of the name. Note that this is a heuristic
     * only, and can remove the tail of a name on accident if a separator char happens to be at the
     * same place where usually the digest separator character is expected. That is OK because the
     * shorter name does not need to be unique.
     */
    private static String stripExistingDigest(String name) {
        int digestLength = Digest.DIGEST_SIZE + 1;
        if (name.length() > digestLength && name.charAt(name.length() - digestLength) == '_') {
            return name.substring(0, name.length() - digestLength);
        }
        return name;
    }

    public static Class<?> toUnboxedClass(Class<?> clazz) {
        return toUnboxedClassWithDefault(clazz, clazz);
    }

    public static Class<?> toUnboxedClassWithDefault(Class<?> clazz, Class<?> defaultClass) {
        if (clazz == Boolean.class) {
            return boolean.class;
        } else if (clazz == Byte.class) {
            return byte.class;
        } else if (clazz == Short.class) {
            return short.class;
        } else if (clazz == Character.class) {
            return char.class;
        } else if (clazz == Integer.class) {
            return int.class;
        } else if (clazz == Long.class) {
            return long.class;
        } else if (clazz == Float.class) {
            return float.class;
        } else if (clazz == Double.class) {
            return double.class;
        } else {
            return defaultClass;
        }
    }

    /** Sanitizes a name to be used in a file name. Special characters are replaced with '_'. */
    public static String sanitizeForFileName(String name) {
        StringBuilder buf = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.' || (c >= '0' && c <= '9')) {
                buf.append(c);
            } else {
                buf.append('_');
            }
        }
        return buf.toString();
    }
}
