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
package com.oracle.svm.hosted.image;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.UniqueShortNameProvider;
import com.oracle.svm.hosted.meta.HostedType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * A unique name provider employed when debug info generation is enabled on Linux. Names are
 * generated in the C++ mangled format that is understood by the Linux binutils BFD library. An ELF
 * symbol defined using a BFD mangled name is linked to a corresponding DWARF method or field info
 * declaration (DIE) by inserting it as the value of the the <code>linkage_name</code> attribute. In
 * order for this linkage to be correctly recognised by the debugger and Linux tools the
 * <code>name</code> attributes of the associated class, method, field, parameter types and return
 * type must be the same as the corresponding names encoded in the mangled name.
 * <p>
 * Note that when the class component of a mangled name needs to be qualified with a class loader id
 * the corresponding DWARF class record must embed the class in a namespace whose name matches the
 * classloader id otherwise the mangled name will not be recognised and demangled successfully.
 * TODO: Namespace embedding is not yet implemented.
 */
class NativeImageBFDNameProvider implements UniqueShortNameProvider {

    NativeImageBFDNameProvider(List<ClassLoader> ignore) {
        this.ignoredLoaders = ignore;
    }

    @Override
    public String uniqueShortName(ClassLoader loader, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        String loaderName = uniqueShortLoaderName(loader);
        return bfdMangle(loaderName, declaringClass, methodName, methodSignature, isConstructor);
    }

    @Override
    public String uniqueShortName(Member m) {
        return bfdMangle(m);
    }

    @Override
    public String uniqueShortLoaderName(ClassLoader loader) {
        // no need to qualify classes loaded by a builtin loader
        if (isBuiltinLoader(loader)) {
            return "";
        }
        if (isGraalImageLoader(loader)) {
            return "";
        }
        String name = SubstrateUtil.classLoaderNameAndId(loader);
        // name will look like "org.foo.bar.FooBarClassLoader @1234"
        // trim it down to something more manageable
        name = SubstrateUtil.stripPackage(name);
        name = stripOuterClass(name);
        name = name.replace(" @", "_");
        return name;
    }

    private String classLoaderNameAndId(ResolvedJavaType type) {
        return uniqueShortLoaderName(getClassLoader(type));
    }

    private static String stripOuterClass(String name) {
        return name.substring(name.lastIndexOf('$') + 1);
    }

    private static ClassLoader getClassLoader(ResolvedJavaType type) {
        if (type.isPrimitive()) {
            return null;
        }
        if (type.isArray()) {
            return getClassLoader(type.getElementalType());
        }
        if (type instanceof HostedType) {
            HostedType hostedType = (HostedType) type;
            return hostedType.getJavaClass().getClassLoader();
        }
        return null;
    }

    private final List<ClassLoader> ignoredLoaders;

    private static final String BUILTIN_CLASSLOADER_NAME = "jdk.internal.loader.BuiltinClassLoader";

    private static boolean isBuiltinLoader(ClassLoader loader) {
        if (loader == null) {
            return true;
        }
        // built in loaders are all subclasses of jdk.internal.loader.BuiltinClassLoader
        Class<?> loaderClazz = loader.getClass();
        do {
            if (loaderClazz.getName().equals(BUILTIN_CLASSLOADER_NAME)) {
                return true;
            }
            loaderClazz = loaderClazz.getSuperclass();
        } while (loaderClazz != Object.class);

        return false;
    }

    private boolean isGraalImageLoader(ClassLoader loader) {
        // Graal installs its own system loader and loaders for application and image classes whose
        // classes do not need qualifying with a loader id
        if (ignoredLoaders.contains(loader)) {
            return true;
        }
        return false;
    }

    /**
     * mangle a resolved method name in a format the binutils demangler will understand. This should
     * allow all Linux tools to translate mangled symbol names to recognisable Java names in the
     * same format as derived from the DWARF info, i.e. fully qualified classname using '.'
     * separator, member name separated using '::' and parameter/return types printed either using
     * the Java primitive name or, for oops, as a pointer to a struct whose name is that of the Java
     * type.
     *
     * @param declaringClass the class owning the method implementation
     * @param memberName the simple name of the method
     * @param methodSignature the signature of the method
     * @param isConstructor true if this method is a constructor otherwise false
     * @return a unique mangled name for the method
     */
    public String bfdMangle(String loaderName, ResolvedJavaType declaringClass, String memberName, Signature methodSignature, boolean isConstructor) {
        /*-
         * The bfd library demangle API currently supports decoding of Java names if they are
         * using a scheme similar to that used for C++. Unfortunately, none of the tools which
         * reply on this API pass on the details that the mangled name in question is for a
         * Java method. However, it is still possible to pass a name to the demangler that the
         * C++ demangle algorithm will correctly demangle to a Java name. The scheme used mirrors
         * the one used to generate names for DWARF. It assumes that the linker can tolerate '.',
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
         * characters:
         *
         *   org.my.Foo -> 10org.my.Foo
         *
         * Qualified Member Name Symbols:
         *
         * A member name is encoded by concatenating the class name and member selector
         * as elements of a hierarchical (namespace) encoding:
         *
         *   org.my.Foo.doAFoo -> N10org.my.Foo6doAFooE
         *   org.my.Foo.doAFoo -> N3org2my3Foo6doAFooE
         *
         * Note again that although the Java demangle algorithm expects the second of the above
         * two formats the Graal mangler employs the preceding format where the package and class
         * name are presented as a single base name with embedded '.' characters.
         *
         * Loader Qualified Class and Member Name Symbols:
         *
         * The above encoding scheme can fail to guarantee uniqueness of the encoding of a class
         * name or the corresponding class component of a qualified member name. Duplication can
         * occur when the same class bytecode is loaded by more than one class loader. Not all
         * loaders are susceptible to this problem. However, for those that are susceptible a unique
         * id String for the class loader needs to be composed with the class id String:
         *
         * {loader AppLoader @1234}org.my.Foo -> N14AppLoader_123410org.my.FooE
         * {loader AppLoader @1234}org.my.Foo.doAFoo -> N14AppLoader_123410org.my.Foo6doAFooE
         *
         * Note that in order for the debugger and Linux tools to be able to link the demangled name
         * to the corresponding DWARF records the same namespace identifier must be used to qualify the
         * DWARF declaration of the class.
         *
         * Qualified Method Name With Signature Symbols:
         *
         * A full encoding for a method name requires concatenating encodings for the return
         * type and parameter types to the method name encoding.
         *
         * <rType> <method> '(' <paramType>+ ')' -> <methodencoding> 'J' (<rTypeencoding> <paramencoding>+ | 'v')
         *
         * Return Types:
         *
         * The encoding for the return type is appended first. It is preceded with a 'J', to
         * mark it as a return type rather than the first parameter type. A primitive return type
         * is encoded using a single letter encoding (see below). An object return type employs the
         * qualified class encoding described with a preceding 'P', to mark it as a pointer type:
         *
         *   java.lang.String doAFoo(...) -> <methodencoding> JP16java.lang.String <paramencoding>+
         *
         * An array return type also requires a J and P prefix. Details of the array type encoding
         * are provided below.
         *
         * Note that a pointer type is employed for consistency with the DWARF type scheme.
         * That scheme also models oops as pointers to a struct whose name is taken from the
         * Java class.
         *
         * Void Signatures and Void Return Types:
         *
         * If the method has no parameters then this is represented by encoding the single type
         * void using the standard primitive encoding for that type:
         *
         *   void -> v
         *
         * The void encoding is also used to encode the return type of a void method:
         *
         *   void doAFoobar(...) <methodencoding> Jv <paramencoding>+
         *
         * Non-void Signatures
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
         * Object parameter types (which includes interfaces and enums) are encoded using the class
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
        return new BFDMangler(this).mangle(loaderName, declaringClass, memberName, methodSignature, isConstructor);
    }

    /**
     * mangle a reflective member name in a format the binutils demangler will understand. This
     * should allow all Linux tools to translate mangled symbol names to recognisable Java names in
     * the same format as derived from the DWARF info, i.e. fully qualified classname using '.'
     * separator, member name separated using '::' and parameter/return types printed either using
     * the Java primitive name or, for oops, as a pointer to a struct whose name is that of the Java
     * type.
     *
     * @param m the reflective member whose name is to be mangled
     * @return a unique mangled name for the method
     */
    public String bfdMangle(Member m) {
        return new BFDMangler(this).mangle(m);
    }

    private static class BFDMangler {
        final NativeImageBFDNameProvider nameProvider;
        final StringBuilder sb;
        final List<String> prefixes;

        BFDMangler(NativeImageBFDNameProvider provider) {
            nameProvider = provider;
            sb = new StringBuilder("_Z");
            prefixes = new ArrayList<>();
        }

        public String mangle(String loaderName, ResolvedJavaType declaringClass, String memberName, Signature methodSignature, boolean isConstructor) {
            String fqn = declaringClass.toJavaName();
            String selector = memberName;
            if (isConstructor) {
                assert methodSignature != null;
                // replace <init> with the class name n.b. it may include a disambiguating suffix
                assert selector.startsWith("<init>");
                String replacement = fqn;
                int index = replacement.lastIndexOf('.');
                if (index >= 0) {
                    replacement = fqn.substring(index + 1);
                }
                selector = selector.replace("<init>", replacement);
            }
            mangleClassAndMemberName(loaderName, fqn, selector);
            if (methodSignature != null) {
                if (!isConstructor) {
                    mangleReturnType(methodSignature, declaringClass);
                }
                mangleParams(methodSignature, declaringClass);
            }
            return sb.toString();
        }

        public String mangle(Member member) {
            Class<?> declaringClass = member.getDeclaringClass();
            String loaderName = nameProvider.uniqueShortLoaderName(declaringClass.getClassLoader());
            String className = declaringClass.getName();
            String selector = member.getName();
            boolean isConstructor = member instanceof Constructor<?>;
            boolean isMethod = member instanceof Method;

            if (isConstructor) {
                assert selector.equals("<init>");
                selector = SubstrateUtil.stripPackage(className);
            }
            mangleClassAndMemberName(loaderName, className, selector);
            if (isMethod) {
                Method method = (Method) member;
                mangleReturnType(method.getReturnType());
            }
            if (isConstructor || isMethod) {
                Executable executable = (Executable) member;
                mangleParams(executable.getParameters());
            }
            return sb.toString();
        }

        private void mangleSimpleName(String s) {
            // a simple name starting with a digit would invalidate the C++ mangled encoding scheme
            assert !s.startsWith("[0-9]");
            sb.append(s.length());
            sb.append(s);
        }

        private void mangleRecordPrefix(String prefix) {
            if (!substitutePrefix(prefix)) {
                mangleSimpleName(prefix);
                recordPrefix(prefix);
            }
        }

        private void manglePrefix(String prefix) {
            if (!substitutePrefix(prefix)) {
                mangleSimpleName(prefix);
            }
        }

        private boolean substitutePrefix(String prefix) {
            int index = prefixIdx(prefix);
            if (index >= 0) {
                writePrefix(index);
                return true;
            }
            return false;
        }

        private static boolean encodeLoaderName(String loaderName) {
            return loaderName != null && !loaderName.isEmpty();
        }

        private void mangleClassAndMemberName(String loaderName, String className, String methodName) {
            sb.append('N');
            // only leading elements of namespace encoding may be recorded as substitutions
            if (encodeLoaderName(loaderName)) {
                mangleRecordPrefix(loaderName);
            }
            mangleRecordPrefix(className);
            mangleSimpleName(methodName);
            sb.append('E');
        }

        private void mangleClassName(String loaderName, String className) {
            boolean encodeLoaderName = encodeLoaderName(loaderName);
            if (encodeLoaderName) {
                sb.append('N');
                // only leading elements of namespace encoding may be recorded as substitutions
                mangleRecordPrefix(loaderName);
            }
            manglePrefix(className);
            if (encodeLoaderName) {
                sb.append('E');
            }
        }

        private void mangleReturnType(Signature methodSignature, ResolvedJavaType owner) {
            ResolvedJavaType type = (ResolvedJavaType) methodSignature.getReturnType(owner);
            sb.append('J');
            mangleType(type);
        }

        private void mangleReturnType(Class<?> type) {
            sb.append('J');
            mangleType(type);
        }

        private void mangleParams(Signature methodSignature, ResolvedJavaType owner) {
            int count = methodSignature.getParameterCount(false);
            if (count == 0) {
                mangleTypeChar('V');
            } else {
                for (int i = 0; i < count; i++) {
                    ResolvedJavaType type = (ResolvedJavaType) methodSignature.getParameterType(i, owner);
                    mangleType(type);
                }
            }
        }

        private void mangleParams(Parameter[] params) {
            if (params.length == 0) {
                mangleTypeChar('V');
            } else {
                for (int i = 0; i < params.length; i++) {
                    mangleType(params[i].getType());
                }
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
                mangleClassName(nameProvider.classLoaderNameAndId(type), type.toJavaName());
            }
        }

        private void mangleType(Class<?> type) {
            if (type.isPrimitive()) {
                manglePrimitiveType(type);
            } else if (type.isArray()) {
                sb.append('P');
                mangleArrayType(type);
            } else {
                sb.append('P');
                mangleClassName(nameProvider.uniqueShortLoaderName(type.getClassLoader()), type.getName());
            }
        }

        private void mangleArrayType(ResolvedJavaType arrayType) {
            // It may seem odd that we consider placing array names in a namespace.
            // However, note that we are faking arrays with classes that embed the
            // actual array data and identifying the array class with a name derived by
            // appending [] pairs to the base element type. If the base name may need a
            // loader namespace prefix to disambiguate repeated loads of the same class
            // then so may this fake array name.
            int count = 1;
            ResolvedJavaType baseType = arrayType.getComponentType();
            while (baseType.isArray()) {
                count++;
                baseType = baseType.getComponentType();
            }
            String loaderName = nameProvider.classLoaderNameAndId(baseType);
            mangleArrayName(loaderName, baseType.toJavaName(), count);
        }

        private void mangleArrayType(Class<?> arrayType) {
            // See above for why we consider placing array names in a namespace.
            int count = 1;
            Class<?> baseType = arrayType.getComponentType();
            while (baseType.isArray()) {
                baseType = baseType.getComponentType();
            }
            String loaderName = nameProvider.uniqueShortLoaderName(baseType.getClassLoader());
            mangleArrayName(loaderName, baseType.getName(), count);
        }

        private void mangleArrayName(String loaderName, String baseName, int dims) {
            /*
             * This code mangles the array name as a symbol using the array base type and required
             * number of '[]' pairs.
             */
            int len = baseName.length() + (dims * 2);
            boolean encodeLoaderName = encodeLoaderName(loaderName);

            if (encodeLoaderName) {
                sb.append("N");
                // only leading elements of namespace encoding may be recorded as substitutions
                mangleRecordPrefix(loaderName);
            }
            sb.append(len);
            sb.append(baseName);
            for (int i = 0; i < dims; i++) {
                sb.append("[]");
            }
            if (encodeLoaderName) {
                sb.append("E");
            }
        }

        private void manglePrimitiveType(ResolvedJavaType type) {
            char c = type.getJavaKind().getTypeChar();
            mangleTypeChar(c);
        }

        private void manglePrimitiveType(Class<?> type) {
            char c = JavaKind.fromJavaClass(type).getTypeChar();
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
