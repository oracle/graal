/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.java.LambdaUtils;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.StringUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

public class SubstrateUtil {

    /**
     * Field that is true during native image generation, but false at run time.
     */
    public static final boolean HOSTED;

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
        switch (arch) {
            case "x86_64":
                arch = "amd64";
                break;
            case "arm64":
                arch = "aarch64";
                break;
        }
        return arch;
    }

    /**
     * @return true if the standalone libgraal is being built instead of a normal SVM image.
     */
    public static boolean isBuildingLibgraal() {
        return Services.IS_BUILDING_NATIVE_IMAGE;
    }

    /**
     * @return true if running in the standalone libgraal image.
     */
    public static boolean isInLibgraal() {
        return Services.IS_IN_NATIVE_IMAGE;
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
    public static String[] getArgs(int argc, CCharPointerPointer argv) {
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
        UnsignedWord n = WordFactory.zero();
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
                return WordFactory.zero();
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

    public static String toHex(byte[] data) {
        return LambdaUtils.toHex(data);
    }

    public static String digest(String value) {
        return LambdaUtils.digest(value);
    }

    /**
     * Returns a short, reasonably descriptive, but still unique name for the provided method. The
     * name includes a digest of the fully qualified method name, which ensures uniqueness.
     */
    public static String uniqueShortName(ResolvedJavaMethod m) {
        StringBuilder fullName = new StringBuilder();
        fullName.append(m.getDeclaringClass().toClassName()).append(".").append(m.getName()).append("(");
        for (int i = 0; i < m.getSignature().getParameterCount(false); i++) {
            fullName.append(m.getSignature().getParameterType(i, null).toClassName()).append(",");
        }
        fullName.append(')');
        if (!m.isConstructor()) {
            fullName.append(m.getSignature().getReturnType(null).toClassName());
        }

        return stripPackage(m.getDeclaringClass().toJavaName()) + "_" +
                        (m.isConstructor() ? "constructor" : m.getName()) + "_" +
                        SubstrateUtil.digest(fullName.toString());
    }

    /**
     * Returns a short, reasonably descriptive, but still unique name for the provided
     * {@link Method}, {@link Constructor}, or {@link Field}. The name includes a digest of the
     * fully qualified method name, which ensures uniqueness.
     */
    public static String uniqueShortName(Member m) {
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

        return stripPackage(m.getDeclaringClass().getTypeName()) + "_" +
                        (m instanceof Constructor ? "constructor" : m.getName()) + "_" +
                        SubstrateUtil.digest(fullName.toString());
    }

    private static String stripPackage(String qualifiedClassName) {
        /* Anonymous classes can contain a '/' which can lead to an invalid binary name. */
        return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1).replace("/", "");
    }

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
        assert mangled.matches("[a-zA-Z\\._][a-zA-Z0-9_]*");
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

    private static final Method isHiddenMethod = JavaVersionUtil.JAVA_SPEC >= 17 ? ReflectionUtil.lookupMethod(Class.class, "isHidden") : null;

    public static boolean isHiddenClass(Class<?> javaClass) {
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            try {
                return (boolean) isHiddenMethod.invoke(javaClass);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
        return false;
    }
}
