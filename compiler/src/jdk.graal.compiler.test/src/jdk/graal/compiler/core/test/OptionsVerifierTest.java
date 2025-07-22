/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static java.lang.String.format;
import static java.lang.constant.ConstantDescs.CD_Boolean;
import static java.lang.constant.ConstantDescs.CD_Byte;
import static java.lang.constant.ConstantDescs.CD_Character;
import static java.lang.constant.ConstantDescs.CD_Double;
import static java.lang.constant.ConstantDescs.CD_Float;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_Short;
import static java.lang.constant.ConstantDescs.CLASS_INIT_NAME;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.util.EconomicHashSet;

/**
 * Verifies a class declaring one or more {@linkplain OptionKey options} has a class initializer
 * that only initializes the option(s). This sanity check mitigates the possibility of an option
 * value being used before being set.
 */
public class OptionsVerifierTest {

    private static final Set<String> ALLOWLIST = new TreeSet<>(List.of(//
                    "jdk.graal.compiler.truffle.TruffleCompilerOptions"));

    @Test
    public void verifyOptions() throws IOException, ReflectiveOperationException {
        Set<Class<?>> checked = new EconomicHashSet<>();
        for (OptionDescriptors opts : OptionsParser.getOptionsLoader()) {
            for (OptionDescriptor desc : opts) {
                Class<?> descDeclaringClass = desc.getDeclaringClass();
                if (!ALLOWLIST.contains(descDeclaringClass.getName())) {
                    OptionsVerifier.checkClass(descDeclaringClass, desc, checked);
                }
            }
        }
    }

    static final class OptionsVerifier {

        public static void checkClass(Class<?> cls, OptionDescriptor option, Set<Class<?>> checked) throws IOException, ReflectiveOperationException {
            if (!checked.contains(cls)) {
                checked.add(cls);
                Class<?> superclass = cls.getSuperclass();
                if (superclass != null && !superclass.equals(Object.class)) {
                    checkClass(superclass, option, checked);
                }

                new OptionsVerifier(cls, option).apply();
            }
        }

        /**
         * The option field context of the verification.
         */
        private final OptionDescriptor option;

        private final ClassDesc optionClassDesc;

        /**
         * The class in which {@link #option} is declared or a super-class of that class. This is
         * the class whose {@code <clinit>} method is being verified.
         */
        private final Class<?> cls;

        /**
         * Source file context for error reporting.
         */
        String sourceFile = "unknown";

        /**
         * Line number for error reporting.
         */
        int lineNo = -1;

        OptionsVerifier(Class<?> cls, OptionDescriptor desc) {
            this.cls = cls;
            this.option = desc;
            this.optionClassDesc = option.getDeclaringClass().describeConstable().orElseThrow();
        }

        void verify(boolean condition, String message) {
            if (!condition) {
                error(message);
            }
        }

        void error(String message) {
            String errorMessage = format("""
                            %s:%d: Illegal code in %s.<clinit> which may be executed when %s.%s is initialized:
                                %s
                            The recommended solution is to move %s into a separate class (e.g., %s.Options).
                            """, sourceFile, lineNo, cls.getSimpleName(), option.getDeclaringClass().getSimpleName(), option.getName(),
                            message, option.getName(), option.getDeclaringClass().getSimpleName());
            throw new InternalError(errorMessage);
        }

        private static Class<?> resolve(String name) {
            try {
                return Class.forName(name.replace('/', '.'));
            } catch (ClassNotFoundException e) {
                throw new InternalError(e);
            }
        }

        private static final List<ClassDesc> boxingTypes = List.of(CD_Boolean, CD_Byte, CD_Short, CD_Character, CD_Integer, CD_Float, CD_Long, CD_Double);

        /**
         * Checks whether a given method is allowed to be called.
         */
        private static boolean checkInvokeTarget(MemberRefEntry method) {
            ClassDesc owner = method.owner().asSymbol();
            String methodName = method.name().stringValue();

            if ("<init>".equals(methodName)) {
                return OptionKey.class.isAssignableFrom(resolve(method.owner().asInternalName()));
            } else if ("valueOf".equals(methodName)) {
                return boxingTypes.contains(owner);
            } else if ("desiredAssertionStatus".equals(methodName)) {
                return ConstantDescs.CD_Class.equals(owner);
            }
            return false;
        }

        public void apply() throws IOException {
            ClassModel cm = ClassFile.of().parse(GraalServices.getClassfileAsStream(cls).readAllBytes());
            cm.findAttribute(Attributes.sourceFile()).ifPresent(attr -> sourceFile = attr.sourceFile().stringValue());

            // @formatter:off
            for (MethodModel methodModel : cm.methods()) {
                if (CLASS_INIT_NAME.equals(methodModel.methodName().stringValue())) {
                    CodeModel code = methodModel.code().orElseThrow();
                    for (CodeElement instruction : code) {
                        switch (instruction) {
                            case LineNumber line -> lineNo = line.line();
                            case FieldInstruction fi -> {
                                if (fi.opcode() == Opcode.PUTFIELD) {
                                    error(format("store to non-static field %s.%s", fi.owner().asInternalName(), fi.name().stringValue()));
                                } else if (fi.opcode() == Opcode.PUTSTATIC) {
                                    verify(fi.owner().asSymbol().equals(optionClassDesc), format("store to static field %s.%s", fi.owner().asInternalName(), fi.name().stringValue()));
                                }
                            }
                            case InvokeInstruction invoke -> verify(checkInvokeTarget(invoke.method()), "invocation of " + invoke.method());
                            default -> {
                            }
                        }
                    }
                }
            }
            // @formatter:on
        }
    }
}
