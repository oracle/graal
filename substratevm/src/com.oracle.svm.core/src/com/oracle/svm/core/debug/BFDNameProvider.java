/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.UniqueShortNameProvider;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * A unique name provider employed when debug info generation is enabled on Linux. Names are
 * generated in the C++ mangled format that is understood by the Linux binutils BFD library. An ELF
 * symbol defined using a BFD mangled name is linked to a corresponding DWARF method or field info
 * declaration (DIE) by inserting it as the value of the <code>linkage_name</code> attribute. In
 * order for this linkage to be correctly recognised by the debugger and Linux tools the
 * <code>name</code> attributes of the associated class, method, field, parameter types and return
 * type must be the same as the corresponding names encoded in the mangled name.
 * <p>
 * Note that when the class component of a mangled name needs to be qualified with a class loader id
 * the corresponding DWARF class record must embed the class in a namespace whose name matches the
 * classloader id otherwise the mangled name will not be recognised and demangled successfully.
 */
public class BFDNameProvider implements UniqueShortNameProvider {

    public BFDNameProvider(List<ClassLoader> ignore) {
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
        String name = SubstrateUtil.runtimeClassLoaderNameAndId(loader);
        // name will look like "org.foo.bar.FooBarClassLoader @1234"
        // trim it down to something more manageable
        // escaping quotes in the classlaoder name does not work in GDB
        // but the name is still unique without quotes
        name = SubstrateUtil.stripPackage(name);
        name = stripOuterClass(name);
        name = name.replace(" @", "_").replace("'", "").replace("\"", "");
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
        if (type instanceof SharedType sharedType && sharedType.getHub().isLoaded()) {
            return sharedType.getHub().getClassLoader();
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
         * Note also that a foreign pointer type -- i.e. a Java interface or, occasionally, class used to
         * model a native pointer -- is encoded without the P prefix. That is because for such cases the
         * DWARF encoding uses the Java name as a typedef to the relevant pointer type.
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
         * Primitive parameter types (or return types) may be encoded using a single letter
         * where the symbol encodes a C++ primitive type with the same name and bit layout and
         * interpretation as the Java type:
         *
         *   short -> s
         *   int -> i
         *   long -> l
         *   float -> f
         *   double -> d
         *   void -> v
         *
         * Other primitive types need to be encoded as symbols
         *
         *   boolean -> 7boolean
         *   byte -> 4byte
         *   char -> 4char
         *
         * In these latter cases the single letter encodings that identifies a C++ type with the
         * same bit layout and interpretation (respectively, b, c and t) encode for differently
         * named C++ type (respectively "bool", "char", and unsigned short). Their use would cause
         * method signatures to be printed with a wrong and potentially misleading name e.g. if
         * method void Foo::foo(boolean, byte, char) were encoded as _ZN3Foo3fooEJvbct it would
         * then demangle as Foo::foo(bool, char, unsigned short).
         *
         * It would be possible to encode the name "char" using symbol c. However, that would
         * suggest that the type is a signed 8 bit value rather than a 16 bit unsigned value.
         * Note that the info section includes appropriate definitions of these three primitive
         * types with the appropriate names so tools which attempt to resolve the symbol names to
         * type info should work correctly.
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
         * the default bfd C++ demangler will not perform this translation.
         *
         * Foreign pointer parameter types (which may be modeled as interfaces and enums) are encoded using the
         * class name encoding but without the prefix 'P'.
         *
         * com.oracle.svm.core.posix.PosixUtils.dlsym(org.graalvm.word.PointerBase, java.lang.String)
         *   ->
         * <method-encoding>  28org.graalvm.word.PointerBaseP16java.lang.String
         *
         *
         * Note that no type is emitted for the implicit 'this' argument
         *
         * Substitutions:
         *
         * Symbol elements are indexed and can be referenced using a shorthand index, saving
         * space in the symbol encoding.
         *
         * For example, consider C++ equivalent of String.compareTo and its corresponding encoding
         *
         *   int java::lang::String::compareTo(java::lang::String)
         *      -> _ZN4java4lang6String9compareToEJiPS1_
         *
         * The symbol encodings 4java, 4lang and 6String and 9compareTo establish
         * successive bindings for the indexed substitution variables S_, S0_, S1_ and S2_ to
         * the respective names java, java::lang, java::lang::String and java::lang::String::compareTo
         * (note that bindings accumulate preceding elements when symbols occur inside a namespace).
         * The encoding of the argument list uses S1_ references the 3rd binding i.e. the class
         * name. The problem with this, as seen before when using namespaces is that the demangler
         * accumulates names using a '::' separator between the namespace components rather than the
         * desired  '.' separator.
         *
         * The Graal encoding can work around the namespace separator as shown earlier and still employ
         * symbols as prefixes in order to produce more concise encodings. The full encoding for the
         *
         *   int String.compareTo(java.lang.String)
         *      -> _ZN16java.lang.String9compareToEJiP16java.lang.String
         *
         * However, it is also possible to encode this more concisely as
         *
         *   int String.compareTo(java.lang.String)
         *      -> _ZN16java.lang.String9compareToEJiPS_
         *
         * S_ translates to the first symbol introduced in the namespace i.e. java.lang.String.
         *
         * Substitutions can also occur when parameter types are repeated e.g.
         *
         * int Arrays.NaturalOrder.compare(Object first, Object second)
         *      -> _ZN19Arrays$NaturalOrder7compareEJiPP16java.lang.ObjectPS_2
         * 
         * In this case the class name symbol 19Arrays$NaturalOrder binds $_ to Arrays$NaturalOrder,
         * the method name symbol 7compare binds $1_ to Arrays$NaturalOrder::compareTo and the
         * first parameter type name symbol 16java.lang.Object binds $2_ to java.lang.Object.
         *
         * Indexed symbol references are encoded as "S_", "S0_", ... "S9_", "SA_", ... "SZ_", "S10_", ...
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

        /*
         * The mangler tracks certain elements as they are inserted into the mangled name and bind
         * short symbol name to use as substitutions. If the same element needs to be re-inserted
         * the mangler can embed the short symbol instead of generating the previously mangled full
         * text. Short symbol names are bound in a well-defined sequence at well-defined points
         * during mangling. This allows the demangler to identify exactly which text in the previous
         * input must be used to replace a short symbol name.
         *
         * A binding occurs whenever a simple name is mangled outside of a namespace. For example, a
         * top level class name mangled as 3Hello will bind the next available short name with the
         * result that it will demangle to Hello and can be used to replace later occurrences of the
         * string Hello.
         *
         * When a sequence of simple names is mangled inside a namespace substitution bindings are
         * recorded for each successive composite namespace prefix but not for the final symbol
         * itself. For example, when method name Hello::main is mangled to N5Hello4main4 a single
         * binding is recorded which demangles to Hello. If a class has been loaded by an
         * application loader and, hence, has a name which includes a loader namespace prefix (to
         * avoid the possibility of the same named class being loaded by two different loaders) then
         * the method name, say AppCL504::Hello::main, would mangle to a namespace encoding with 3
         * elements N8AppCL5045Hello4mainE which would introduce two bindings, the first demangling
         * to AppCL504 and the second to AppCL504::Hello.
         *
         * A binding is also recorded whenever a pointer type is mangled. The bound symbol demangles
         * to whatever the text decoded from the scope of the P followed by a '*' to translate it to
         * a pointer type. For example, when the type in parameter list (Foo*) is encoded as P3Foo a
         * binding is recorded for the pointer as well as for the type Foo. The first binding
         * demangles to Foo. The second binding demangles to Foo*.
         *
         * n.b. repeated use of the pointer operator results in repeated bindings. So, if a
         * parameter with type Foo** were to be encoded as PP3Foo three bindings would recorded
         * which demangle to Foo, Foo* and Foo**. However, multiple indirections do not occur in
         * Java signatures.
         *
         * n.b.b. repeated mentions of the same symbol would redundantly record a binding. For
         * example if int Hello::compareTo(Hello*) were mangled to _ZN5Hello9compareToEJiP5Hello the
         * resulting bindings would be S_ ==> Hello, S0_ ==> Hello::compareTo, S1_ ==> Hello and S2_
         * ==> Hello* i.e. both S_ and S1_ would demangle to Hello. This situation should never
         * arise. A name can always be correctly encoded without repeats. In the above example that
         * would be _ZN5Hello9compareToEJiPS_.
         */
        final BFDNameProvider nameProvider;
        final StringBuilder sb;

        // A list of lookup names identifying substituted elements. A prospective name for an
        // element that is about to be encoded can be looked up in this list. If a match is found
        // the list index can be used to identify the relevant short symbol. If it is not found
        // then inserting the name serves to allocate the short name associated with the inserted
        // element's result index.
        /**
         * A map relating lookup names to the index for the corresponding short name with which it
         * can be substituted. A prospective name for an element that is about to be encoded can be
         * looked up in this list. If a match is found the list index can be used to identify the
         * relevant short symbol. If it is not found then it can be inserted to allocate the next
         * short name, using the current size of the map to identify the next available index.
         */
        EconomicMap<LookupName, Integer> bindings;

        /**
         * A lookup name is used as a key to record and subsequently lookup a short symbol that can
         * replace one or more elements of a mangled name. A lookup name is usually just a simple
         * string, i.e. some text that is to be mangled and to which the corresponding short symbol
         * should decode. However, substitutions for namespace prefixes (e.g. AppLoader506::Foo)
         * require a composite lookup name that composes a namespace prefix (AppLoader506) with a
         * trailing simple name (Foo). The corresponding short symbol will demangle to the string
         * produced by composing the prefix and simple name with a :: separator (i.e. restoring
         * AppLoader506::Foo). Short symbols for pointer types (e.g. Foo* or AppLoader506::Foo*)
         * require a pointer lookup name that identifies the substituted text as a pointer to some
         * underlying type (e.g. Foo or AppLoader506::Foo). The corresponding short symbol will
         * demangle to the string produced by demangling the underlying type with a * suffix. In
         * theory the prefix for a pointer type lookup name might be defined by another pointer type
         * lookup name (if, say, we needed to encode type Foo**). In practice, that case should not
         * arise with Java method signatures.
         */
        private sealed interface LookupName permits SimpleLookupName, CompositeLookupName {
        }

        private sealed interface CompositeLookupName extends LookupName permits NamespaceLookupName, PointerLookupName {
        }

        private record SimpleLookupName(String value) implements LookupName {
            @Override
            public String toString() {
                return value;
            }
        }

        private record NamespaceLookupName(String prefix, LookupName tail) implements CompositeLookupName {
            @Override
            public String toString() {
                return prefix + "::" + tail.toString();
            }
        }

        private record PointerLookupName(LookupName tail) implements CompositeLookupName {
            @Override
            public String toString() {
                return tail.toString() + "*";
            }
        }

        BFDMangler(BFDNameProvider provider) {
            nameProvider = provider;
            sb = new StringBuilder("_Z");
            bindings = EconomicMap.create();
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

        private void mangleWriteSimpleName(String s) {
            // a simple name starting with a digit would invalidate the C++ mangled encoding scheme
            assert !s.startsWith("[0-9]");
            sb.append(s.length());
            sb.append(s);
        }

        private void mangleWriteSubstitutableNameRecord(String name) {
            LookupName lookupName = new SimpleLookupName(name);
            if (!substituteName(new SimpleLookupName(name))) {
                // failed so mangle the name and create a binding to track it
                mangleWriteSimpleName(name);
                recordName(lookupName);
            }
        }

        private void mangleWriteSubstitutableNameNoRecord(String name) {
            LookupName lookupName = new SimpleLookupName(name);
            if (!substituteName(lookupName)) {
                // failed so mangle the name
                mangleWriteSimpleName(name);
            }
        }

        private void mangleWriteSubstitutablePrefixedName(String prefix, String name) {
            // this should only be called when inserting a sequence into a namespace
            assert sb.charAt(sb.length() - 1) == 'N';
            // we can substitute both symbols
            mangleWriteSubstitutableNameRecord(prefix);
            // In theory the trailing name at the end of the namespace ought
            // to be able to be encoded with a short name i.e. we ought to be
            // able to call mangleWriteSubstitutableNameNoRecord(name) at this
            // point. Indeed, the demangler *will* translate a substitution when it
            // is inserted at the end of a namespace e.g.
            //
            // c++filt _ZN3foo3barS_E --> foo::bar::foo
            //
            // However, gdb will barf on the $_ and refuse to translate the symbol
            // So, in order to keep gdb happy we have to avoid translating this
            // final name ane emit it as a simple name e.g. with the above example
            // we would generate _ZN3foo3bar3fooE.

            mangleWriteSimpleName(name);
        }

        private void mangleWriteSubstitutablePrefixedName(String prefix1, String prefix2, String name) {
            // this should only be called when inserting a sequence into a namespace
            assert sb.charAt(sb.length() - 1) == 'N';
            // we can substitute the composed prefix followed by the name
            // or we can substitute all three individual symbols
            LookupName simpleLookupName = new SimpleLookupName(prefix2);
            LookupName namespaceLookupName = new NamespaceLookupName(prefix1, simpleLookupName);
            // try substituting the combined prefix
            if (!substituteName(namespaceLookupName)) {
                // we may still be able to establish a binding for the first prefix
                mangleWriteSubstitutableNameRecord(prefix1);
                // we cannot establish a binding for the trailing prefix
                mangleWriteSubstitutableNameNoRecord(prefix2);
                recordName(namespaceLookupName);
            }
            // see comment in previous method as to why we call mangleWriteSimpleName
            // instead of mangleWriteSubstitutableNameNoRecord(name)
            mangleWriteSimpleName(name);
        }

        private boolean substituteName(LookupName name) {
            Integer index = bindings.get(name);
            if (index != null) {
                writeSubstitution(index.intValue());
                return true;
            }
            return false;
        }

        private static boolean encodeLoaderName(String loaderName) {
            return loaderName != null && !loaderName.isEmpty();
        }

        private void mangleClassAndMemberName(String loaderName, String className, String methodName) {
            sb.append('N');
            if (encodeLoaderName(loaderName)) {
                mangleWriteSubstitutablePrefixedName(loaderName, className, methodName);
            } else {
                mangleWriteSubstitutablePrefixedName(className, methodName);
            }
            sb.append('E');
        }

        private void mangleClassName(String loaderName, String className) {
            boolean encodeLoaderName = encodeLoaderName(loaderName);
            if (encodeLoaderName) {
                sb.append('N');
                // only leading elements of namespace encoding may be recorded as substitutions
                mangleWriteSubstitutablePrefixedName(loaderName, className);
                sb.append('E');
            } else {
                mangleWriteSubstitutableNameRecord(className);
            }
        }

        private void mangleClassPointer(String loaderName, String className) {
            boolean encodeLoaderName = encodeLoaderName(loaderName);
            LookupName classLookup = new SimpleLookupName(className);
            LookupName lookup = classLookup;
            if (encodeLoaderName) {
                lookup = new NamespaceLookupName(loaderName, classLookup);
            }
            PointerLookupName pointerLookup = new PointerLookupName(lookup);
            // see if we can use a short name for the pointer
            if (!substituteName(pointerLookup)) {
                // failed - so we need to mark this as a pointer,
                // encode the class name and (only) then record a
                // binding for the pointer, ensuring that bindings
                // for the pointer type and its referent appear in
                // the expected order
                sb.append("P");
                mangleClassName(loaderName, className);
                recordName(pointerLookup);
            }
        }

        private void mangleReturnType(Signature methodSignature, ResolvedJavaType owner) {
            sb.append('J');
            mangleType(methodSignature.getReturnType(owner));
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
                    mangleType(methodSignature.getParameterType(i, owner));
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

        private void mangleType(JavaType type) {
            if (type instanceof ResolvedJavaType) {
                mangleType((ResolvedJavaType) type);
            } else if (type instanceof UnresolvedJavaType) {
                mangleType((UnresolvedJavaType) type);
            } else {
                throw VMError.shouldNotReachHere("Unexpected JavaType for " + type);
            }
        }

        private void mangleType(ResolvedJavaType type) {
            if (type.isPrimitive()) {
                manglePrimitiveType(type);
            } else if (type.isArray()) {
                mangleArrayType(type);
            } else {
                String loaderName = nameProvider.classLoaderNameAndId(type);
                String className = type.toJavaName();
                if (needsPointerPrefix(type)) {
                    mangleClassPointer(loaderName, className);
                } else {
                    mangleClassName(loaderName, className);
                }
            }
        }

        private void mangleType(UnresolvedJavaType type) {
            if (type.isArray()) {
                mangleArrayType(type);
            } else {
                mangleClassPointer("", type.toJavaName());
            }
        }

        private void mangleType(Class<?> type) {
            if (type.isPrimitive()) {
                manglePrimitiveType(type);
            } else if (type.isArray()) {
                mangleArrayType(type);
            } else {
                mangleClassPointer(nameProvider.uniqueShortLoaderName(type.getClassLoader()), type.getName());
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
            mangleArrayPointer(loaderName, baseType.toJavaName(), count);
        }

        private void mangleArrayType(UnresolvedJavaType arrayType) {
            int count = 1;
            JavaType baseType = arrayType.getComponentType();
            while (baseType.isArray()) {
                count++;
                baseType = baseType.getComponentType();
            }
            mangleArrayPointer("", baseType.toJavaName(), count);
        }

        private void mangleArrayType(Class<?> arrayType) {
            // See above for why we consider placing array names in a namespace.
            int count = 1;
            Class<?> baseType = arrayType.getComponentType();
            while (baseType.isArray()) {
                baseType = baseType.getComponentType();
            }
            String loaderName = nameProvider.uniqueShortLoaderName(baseType.getClassLoader());
            mangleArrayPointer(loaderName, baseType.getName(), count);
        }

        private void mangleArrayPointer(String loaderName, String baseName, int dims) {
            // an array is just a class with a name that includes trailing [] pairs
            mangleClassPointer(loaderName, makeArrayName(baseName, dims));
        }

        private static String makeArrayName(String baseName, int dims) {
            StringBuilder sb1 = new StringBuilder();
            sb1.append(baseName);
            for (int i = 0; i < dims; i++) {
                sb1.append("[]");
            }
            return sb1.toString();
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
            // we can use single char encodings for most primitive types
            // but we need to encode boolean, byte and char specially
            switch (c) {
                case 'Z':
                    mangleWriteSubstitutableNameRecord("boolean");
                    return;
                case 'B':
                    mangleWriteSubstitutableNameRecord("byte");
                    return;
                case 'S':
                    sb.append("s");
                    return;
                case 'C':
                    mangleWriteSubstitutableNameRecord("char");
                    return;
                case 'I':
                    sb.append("i");
                    return;
                case 'J':
                    sb.append("l");
                    return;
                case 'F':
                    sb.append("f");
                    return;
                case 'D':
                    sb.append("d");
                    return;
                case 'V':
                    sb.append("v");
                    return;
                default:
                    // should never reach here
                    assert false : "invalid kind for primitive type " + c;
            }
        }

        private void writeSubstitution(int i) {
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

        private void recordName(LookupName name) {
            bindings.put(name, bindings.size());
        }
    }

    /**
     * Determine whether a type modeled as a Java object type needs to be encoded using pointer
     * prefix P.
     *
     * @param type The type to be checked.
     * @return true if the type needs to be encoded using pointer prefix P otherwise false.
     */
    private static boolean needsPointerPrefix(ResolvedJavaType type) {
        if (type instanceof SharedType sharedType) {
            /* Word types have the kind Object, but a primitive storageKind. */
            return sharedType.getJavaKind() == JavaKind.Object && sharedType.getStorageKind() == sharedType.getJavaKind();
        }
        return false;
    }
}
