/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared.util;

import java.io.Console;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.meta.GuaranteeFolded;

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

    /**
     * Guarantees that the code is only executed at run time and not at image build time. The call
     * should always be at the beginning of a method. It is not supposed to mark conditional code
     * branches. It always indicates that the whole containing method is run-time only.
     */
    @AlwaysInline("Should be eliminated")
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void guaranteeRuntimeOnly() {
        if (HOSTED) {
            throw VMError.shouldNotReachHere("Should only be called at run time");
        }
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isPowerOf2(long value) {
        return (value & (value - 1)) == 0;
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

    public static String stripPackage(String qualifiedClassName) {
        /* Anonymous classes can contain a '/' which can lead to an invalid binary name. */
        return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1).replace("/", "");
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
