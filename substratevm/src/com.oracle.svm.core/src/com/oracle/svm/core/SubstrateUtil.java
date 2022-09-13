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
import java.util.ArrayList;
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
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.StringUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
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
        return uniqueShortName("", m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
    }

    public static String uniqueShortName(String loaderNameAndId, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        StringBuilder sb = new StringBuilder(loaderNameAndId);
        sb.append(declaringClass.toClassName()).append(".").append(methodName).append("(");
        for (int i = 0; i < methodSignature.getParameterCount(false); i++) {
            sb.append(methodSignature.getParameterType(i, null).toClassName()).append(",");
        }
        sb.append(')');
        if (!isConstructor) {
            sb.append(methodSignature.getReturnType(null).toClassName());
        }

        return stripPackage(declaringClass.toJavaName()) + "_" +
                        (isConstructor ? "constructor" : methodName) + "_" +
                        SubstrateUtil.digest(sb.toString());
    }

    public static String toolFriendlyMangle(ResolvedJavaMethod m) {
        /*-
         *
        if (false) {
            return elfMangle(m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
        }
         */
        return bfdMangle(m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
    }

    public static String classLoaderNameAndId(ClassLoader loader) {
        if (loader == null) {
            return "";
        }
        try {
            return (String) classLoaderNameAndId.get(loader);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere("Cannot reflectively access ClassLoader.nameAndId");
        }
    }

    private static final Field classLoaderNameAndId = ReflectionUtil.lookupField(ClassLoader.class, "nameAndId");

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

    /**
     * mangle a method name in a format the Linux/ELF demangler will understand. This will allow
     * some tools to translate mangled symbol names to recognisable Java names in the same format as
     * derived from the DWARF info, i.e. fully qualified classname using '.' separator, method name
     * separated using '::' and parameter/return types printed either using the Java primitive name
     * or, for oops, as a pointer to a struct whose name is that of the Java type.
     *
     * Unfortunately, this encoding scheme does not help with tools that rely on the underlying
     * binutils (bfd) demangle API, most critically including the disassembly tool. Since, the ELF
     * demangling implementation always punts to the bfd implementation before resorting to its own
     * scheme it is better to rely on a scheme that bfd can understand.
     *
     * @param declaringClass the class owning the method implementation
     * @param methodName the simple name of the method
     * @param methodSignature the signature of the method
     * @param isConstructor true if this method is a constructor otherwise false
     * @return a unique mangled name for the method
     */
    @SuppressWarnings("unused")
    public static String elfMangle(ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        // elf library expects java symbols in bytecode internal format
        StringBuilder sb = new StringBuilder(declaringClass.getName());
        sb.append(methodName).append("(");
        for (int i = 0; i < methodSignature.getParameterCount(false); i++) {
            sb.append(methodSignature.getParameterType(i, null).getName());
        }
        sb.append(')');
        if (!isConstructor) {
            sb.append(methodSignature.getReturnType(null).getName());
        }
        return sb.toString();
    }

    /**
     * mangle a method name in a format the binutils demangler will understand. This should allow
     * all Linux tools to translate mangled symbol names to recognisable Java names in the same
     * format as derived from the DWARF info, i.e. fully qualified classname using '.' separator,
     * method name separated using '::' and parameter/return types printed either using the Java
     * primitive name or, for oops, as a pointer to a struct whose name is that of the Java type.
     *
     * @param declaringClass the class owning the method implementation
     * @param methodName the simple name of the method
     * @param methodSignature the signature of the method
     * @param isConstructor true if this method is a constructor otherwise false
     * @return a unique mangled name for the method
     */
    public static String bfdMangle(ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        /*-
         * The bfd library demangle API currnetly supports decoding of Java names if they are
         * using a scheme similar to that used for C++. Unfortunately, none of the tools which
         * reply on this API pass on the details that the mangeld name in question is for a
         * Java method. However, it is still possible to pass a name to the demangler that the
         * C++ demangle algorithm will correctly demangle to a Java name. The scheme used mirrors
         * the one used to generate names for DWRAF. It assumes that the linker can tolerate '.',
         * '[' and ']' characters in an ELF symbol name, which is not a problem on Linux.
         *
         * The BFD Demangle Encoding and Algorithm:
         *
         * The bfd Java encoding expects java symbols in variant of C++ format.
         *
         * A mangled name starts with "_Z"
         *
         * Base Symbols:
         *
         * Base symbols encode with a decimal length prefix followed by the relevant characters:
         *
         *   Foo -> 3Foo
         *   org.my.Foo -> 10org.my.Foo
         *
         * n.b. The latter encoding is acceptable by the demangler -- characters in the encoded
         * symbol text are opaque as far as it is concerned.
         *
         * Qualified Class Name Symbols:
         *
         * The standard Java encoding assumes that the package separator '.' will be elided from
         * symbol text by encoding a package qualified class name as a (possibly recursive)
         * namespace encoding:
         *
         *   org.my.Foo -> N3org2my3FooE
         *
         * The bfd Java demangle algorithm understands that the leading base symbols in this
         * encoding are package elements and will successfully demangle this latter encoding to
         * "org.my.Foo". However, the default C++ demangle algorithm will demangle it to
         * "org::my::Foo" since it regards the leading base symbols as naming namespaces.
         *
         * In consequence, the Graal mangler chooses to encode Java package qualified class names
         * in the format preceding the last one i.e. as a single base symbol with embedded '.'
         * characters.
         *
         * Qualified Method Name Symbols:
         *
         * A method name is encoded by concatenating the class name and base method name
         * as elements of a namespace encoding:
         *
         *   org.my.Foo.doAFoo -> N10org.my.Foo6doAFooE
         *   org.my.Foo.doAFoo -> N3org2my3Foo6doAFooE
         *
         * Note again that although the Java demangle algorithm expects the second of the above
         * two formats the Graal mangler employs the preceding format where the package and class
         * name are presented as a single base name with embedded '.' characters.
         *
         * Qualified Method Name With Signature Symbols:
         *
         * A full encoding for a method name requires concatenating encodings for the return
         * type and parameter types to the method name encoding.
         *
         * <rType> <method> '(' <paramType>+ ')' -> <methodencoding> 'J' (<rTypeencoding> <paramencoding>+ | 'v')
         *
         * Retyurn Types:
         *
         * The encoding for the return type is appended first. It is preceded with a 'J', to
         * mark it as a return type rather than the first parameter type, and a 'P', to mark
         * it as a pointer to the class type:
         *
         *   java.lang.String doAFoo(...) -> <methodencoding> JP16java.lang.String <paramencoding>+
         *
         * Note that a pointer type is employed for consistency with the DWARF type scheme.
         * That scheme also models oops as pointers to a struct whose name is taken from the
         * Java class.
         *
         * Void Signatures:
         *
         * If the method has no parameters then this is represented by encoding the single type
         * void using the standard primitive encoding for that type:
         *
         *   void -> v
         *
         * The void encoding can also be used to encode the return type of a void method:
         *
         *   void doAFoobar(...) <methodencoding> Jv <paramencoding>+
         *
         * Parameter type encodings are simply appended in order. The demangle algorithm detects
         * the parameter count by decoding each successive type encoding. Parameters have either
         * primitive type, non-array object type or array type.
         *
         * Primitive Parameter Types
         *
         * Primitive parameter types (or return types) are encoded using a single letter as follows:
         *
         *   bool -> b
         *   byte -> a
         *   short -> s
         *   char -> t
         *   int -> i
         *   long -> l
         *   float -> f
         *   double -> d
         *
         * Object parameter types (whcih included interfaces and enums) are encoded using the class
         * name encoding described above preceded by  prefix 'P'.
         *
         * java.lang.String doAFoo(int, java.lang.String) ->  <methodencoding> JP16java.lang.StringiP16java.lang.String
         *
         * Note that no type is emitted for the implicit 'this' argument
         *
         * Array Parameter Types:
         *
         * Array parameter types are encoded as bare symbols with the relevant number of square bracket pairs
         * for their dimension:
         *
         *   Foo[] -> 5Foo[]
         *   java.lang.Object[][] -> 20java.lang.Object[][]
         *
         * Note that the Java bfd encoding expects arrays to be encoded as template type instances
         * using a pseudo-template named JArray, with 'I' and 'E' appended as start and end delimiters
         * for the embedded template argument lists:
         *
         * Foo[] ->  P6JArrayI3FooE
         * Foo[][] ->  P6JArrayIP6JArrayI3FooEE
         *
         * The bfd Java demangler recognises this special pseudo template and translates it back into
         * the expected Java form i.e. decode(JArray<XXX>) -> concatenate(decode(XXX), "[]"). However,
         * the default bfd C++ demangler wil not perform this translation.
         *
         * Substitutions:
         *
         * Namespace prefix elements (i.e. the leading elements of an N...E encoding) are indexed and
         * can be referenced using a shorthand index, saving space in the symbol encoding. In the
         * standard Java encoding this means that common package and class name prefixes can be
         * independently substituted (also template prefixes).
         *
         * For example, when encoding the first parameter type, the standard Java encoding of method
         * startsWith of class java.lang.String can refer to the common prefixes java, lang and String
         * established by the namespace encoding of the method name:
         *
         *   boolean startsWith(java.lang.String, int) -> _ZN4java4lang6String10startsWithEJbPN4java4lang6StringEi
         *      -> _ZN4java4lang6String10startsWithEJbPN$_$0_$1_Ei
         *
         * The namespace prefixes 4java, 4lang and 6String establish successive bindings for the
         * indexed substitution variables $_, $0_ and $1_.
         *
         * The Graal encoding makes much more limited use of namespace prefixes but it can still profit
         * from them to produce more concise encodings:
         *
         *   boolean startsWith(java.lang.String, int) -> _ZN16java.lang.String10startsWithEJbP16java.lang.Stringi
         *      -> _ZN16java.lang.String10startsWithEJbP$_i
         *
         * Indexed prefix references are encoded as "S_", "S0_", ... "S9_", "SA_", ... "SZ_", "S10_", ...
         * i.e. after "$_" for index 0, successive encodings for index i embded the base 36 digits for
         * (i - 1) between "S" and "_".
         */
        return new BFDMangler().mangle(declaringClass, methodName, methodSignature, isConstructor);
    }

    private static class BFDMangler {
        final StringBuilder sb;
        final List<String> prefixes;

        BFDMangler() {
            sb = new StringBuilder("_Z");
            prefixes = new ArrayList<>();
        }

        public String mangle(ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
            String fqn = declaringClass.toJavaName();
            if (isConstructor) {
                assert methodName.equals("<init>");
                int index = fqn.lastIndexOf('.');
                String constructorName;
                if (index >= 0) {
                    constructorName = fqn.substring(index);
                } else {
                    constructorName = fqn;
                }
                mangleClassAndMethodName(fqn, constructorName);
            } else {
                mangleClassAndMethodName(fqn, methodName);
            }
            mangleReturnType(methodSignature);
            mangleParams(methodSignature);
            return sb.toString();
        }

        private void mangleSimpleName(String s) {
            sb.append(s.length());
            sb.append(s);
        }

        private void manglePrefix(String prefix) {
            int index = prefixIdx(prefix);
            if (index >= 0) {
                writePrefix(index);
            } else {
                mangleSimpleName(prefix);
                recordPrefix(prefix);
            }
        }

        private void mangleClassAndMethodName(String name, String methodName) {
            sb.append('N');
            mangleClassName(name);
            mangleMethodName(methodName);
            sb.append('E');
        }

        private void mangleClassName(String name) {
            /*
             * This code generates the FQN of the class including '.' separators as a prefix meaning
             * we only see the '::' separator between class FQN and method name
             */
            manglePrefix(name);
        }

        private void mangleMethodName(String name) {
            mangleSimpleName(name);
        }

        private void mangleReturnType(Signature methodSignature) {
            ResolvedJavaType type = (ResolvedJavaType) methodSignature.getReturnType(null);
            sb.append('J');
            mangleType(type);
        }

        private void mangleParams(Signature methodSignature) {
            int count = methodSignature.getParameterCount(false);
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) methodSignature.getParameterType(i, null);
                mangleType(type);
            }
            if (count == 0) {
                mangleTypeChar('V');
            }
        }

        private void mangleType(ResolvedJavaType type) {
            if (type.isPrimitive()) {
                manglePrimitiveType(type);
            } else if (type.isArray()) {
                sb.append('P');
                mangleArrayType(type);
            } else {
                sb.append('P');
                mangleClassName(type.toJavaName());
            }
        }

        private void mangleArrayType(ResolvedJavaType arrayType) {
            /*
             * This code mangles the array name as a symbol using the array base type and required
             * number of '[]' pairs.
             */
            int count = 1;
            ResolvedJavaType baseType = arrayType.getComponentType();
            while (baseType.isArray()) {
                count++;
                baseType = baseType.getComponentType();
            }
            String name = baseType.toJavaName();
            int len = name.length() + (count * 2);
            sb.append(len);
            sb.append(name);
            for (int i = 0; i < count; i++) {
                sb.append("[]");
            }
        }

        private void manglePrimitiveType(ResolvedJavaType type) {
            char c = type.getJavaKind().getTypeChar();
            mangleTypeChar(c);
        }

        private void mangleTypeChar(char c) {
            switch (c) {
                case 'Z':
                    sb.append('b');
                    return;
                case 'B':
                    sb.append('a');
                    return;
                case 'S':
                    sb.append('s');
                    return;
                case 'C':
                    sb.append('t');
                    return;
                case 'I':
                    sb.append('i');
                    return;
                case 'J':
                    sb.append('l');
                    return;
                case 'F':
                    sb.append('f');
                    return;
                case 'D':
                    sb.append('d');
                    return;
                case 'V':
                    sb.append('v');
                    return;
                default:
                    // should never reach here
                    assert false : "invalid kind for primitive type " + c;
            }
        }

        private void writePrefix(int i) {
            sb.append('S');
            // i = 0 has no digits, i = 1 -> 0, ... i = 10 -> 9, i = 11 -> A, ... i = 36 -> Z, i =
            // 37 -> 10, ...
            // allow for at most up 2 base 36 digits
            if (i > 36) {
                sb.append(b36((i - 1) / 36));
                sb.append(b36((i - 1) % 36));
            } else if (i > 0) {
                sb.append(b36(i - 1));
            }
            sb.append('_');
        }

        private static char b36(int i) {
            if (i < 10) {
                return (char) ('0' + i);
            } else {
                return (char) ('A' + (i - 10));
            }
        }

        private void recordPrefix(String prefix) {
            prefixes.add(prefix);
        }

        private int prefixIdx(String prefix) {
            return prefixes.indexOf(prefix);
        }
    }
}
