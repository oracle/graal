/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import com.oracle.svm.core.jdk.Target_jdk_internal_reflect_Reflection;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols;

/**
 * Utility class for performing access checks on types loaded at run time.
 * <p>
 * This helper is used, for example, during symbolic resolution and when validating direct
 * superclasses and superinterfaces. It covers package and module visibility, member access,
 * nestmate access, {@code MagicAccessor} handling, and the array {@code Object.clone()} special
 * case.
 */
public final class AccessChecks {
    private AccessChecks() {
    }

    /**
     * Ensures that {@code accessingType} may access {@code type}.
     * <p>
     * Array types are checked using their elemental type. Primitive types are always accessible.
     * <p>
     *
     * @throws IllegalAccessError if {@code accessingType} is not allowed to access {@code type}
     */
    public static void ensureTypeAccess(InterpreterResolvedJavaType type, InterpreterResolvedJavaType accessingType) throws IllegalAccessError {
        if (!AccessChecks.checkTypeAccess(type, accessingType)) {
            throw new IllegalAccessError(buildFailedAccessMessage(type, accessingType));
        }
    }

    /**
     * Ensures that the class being defined may access {@code accessedType} as a direct superclass
     * or direct superinterface.
     * <p>
     * The {@code clsName}, {@code accessingLoader}, {@code accessingPkgName}, and
     * {@code accessingModule} arguments describe the class under definition and are also used when
     * constructing the {@link IllegalAccessError} message.
     * <p>
     *
     * @throws IllegalAccessError if {@code accessedType} is not accessible
     */
    public static void ensureTypeAccess(String clsName, ClassLoader accessingLoader, ByteSequence accessingPkgName, Module accessingModule, InterpreterResolvedJavaType accessedType)
                    throws IllegalAccessError {
        if (!AccessChecks.checkTypeAccess(accessingLoader, accessingPkgName, accessingModule, accessedType)) {
            throw new IllegalAccessError(buildFailedSuperAccessMessage(clsName, accessingLoader, accessingModule, accessedType));
        }
    }

    /**
     * Returns whether {@code accessingClass} may access {@code member}, with {@code holderClass} as
     * the symbolic holder for the member.
     * <p>
     * {@code holderClass} may differ from {@code member.getDeclaringClass()}. The check covers
     * protected access, including receiver constraints, package-private access, private nestmate
     * access, the array {@code Object.clone()} special case, and {@code MagicAccessor} handling.
     */
    public static boolean checkMemberAccess(CremaResolvedMember member, InterpreterResolvedJavaType accessingClass, InterpreterResolvedJavaType holderClass) {
        if (member.isPublic()) {
            return true;
        }

        InterpreterResolvedJavaType memberClass = member.getDeclaringClass();
        if (member.isProtected()) {
            if (member instanceof InterpreterResolvedJavaMethod method &&
                            !method.isStatic() &&
                            method.getSymbolicName() == ParserSymbols.ParserNames.clone &&
                            memberClass.isJavaLangObject() &&
                            holderClass.isArray()) {
                return true;
            }
            if (!member.isStatic()) {
                if (holderClass.isAssignableFrom(accessingClass) || accessingClass.isAssignableFrom(holderClass)) {
                    return true;
                }
            } else {
                if (memberClass.isAssignableFrom(accessingClass)) {
                    return true;
                }
            }
        }

        if ((member.isProtected() || member.isPackagePrivate()) && sameRuntimePackage(accessingClass, memberClass)) {
            return true;
        }

        if (member.isPrivate() && areNestMates(accessingClass, memberClass)) {
            return true;
        }

        return accessingClass.isMagicAccessor();
    }

    private static boolean checkTypeAccess(InterpreterResolvedJavaType type, InterpreterResolvedJavaType accessingType) {
        InterpreterResolvedJavaType klass = unwrapToElementalType(type);
        InterpreterResolvedJavaType accessingKlass = unwrapToElementalType(accessingType);
        if (klass.equals(accessingKlass)) {
            return true;
        }
        if (klass.isPrimitive()) {
            return true;
        }
        Module moduleFrom = accessingKlass.getJavaClass().getModule();
        return checkTypeAccess(accessingKlass.getClassLoader(), accessingKlass.getSymbolicRuntimePackage(), moduleFrom, klass);
    }

    private static boolean checkTypeAccess(ClassLoader accessingLoader, ByteSequence accessingPkgName, Module accessingModule, InterpreterResolvedJavaType accessedType) {
        if (accessingLoader == accessedType.getClassLoader()) {
            /*
             * Note: `accessingPkgName` can be either a Symbol or a raw ByteSequence. It is
             * therefore important that it is the receiver of the `equals` call site, so the call
             * may select identity or content equality check as needed.
             */
            if (accessingPkgName.equals(accessedType.getSymbolicRuntimePackage())) {
                // Same package: success.
                return true;
            }
        }
        if (!accessedType.isPublic()) {
            // Only public types can be accessed outside of package
            return false;
        }
        // Establish readability
        Module moduleTo = accessedType.getJavaClass().getModule();
        if (!accessingModule.canRead(moduleTo)) {
            return false;
        }
        // Ensures the accessed module exports its package to the module we are accessing from.
        return Target_jdk_internal_reflect_Reflection.verifyModuleAccess(accessingModule, accessedType.getJavaClass());
    }

    private static String buildFailedSuperAccessMessage(String clsName, ClassLoader accessingLoader, Module accessingModule, InterpreterResolvedJavaType accessedType) {
        String superKind = accessedType.isInterface() ? "interface" : "class";
        StringBuilder sb = new StringBuilder().append("class ").append(clsName).append(" cannot access its super ").append(superKind).append(" ").append(accessedType.getJavaName());
        appendModuleAndLoadersDetails(
                        accessedType.getJavaName(), accessedType.getJavaClass().getModule(), accessedType.getClassLoader(),
                        clsName, accessingModule, accessingLoader,
                        sb);
        return sb.toString();
    }

    private static String buildFailedAccessMessage(InterpreterResolvedJavaType type, InterpreterResolvedJavaType accessingType) {
        StringBuilder sb = new StringBuilder().append("failed to access class ").append(type.getJavaName()).append(" from class ").append(accessingType.getJavaName());
        appendModuleAndLoadersDetails(
                        type.getJavaName(), type.getJavaClass().getModule(), type.getClassLoader(),
                        accessingType.getJavaName(), accessingType.getJavaClass().getModule(), accessingType.getClassLoader(),
                        sb);
        return sb.toString();
    }

    private static void appendModuleAndLoadersDetails(
                    String clsName1, Module clsModule1, ClassLoader clsLoader1,
                    String clsName2, Module clsModule2, ClassLoader clsLoader2,
                    StringBuilder sb) {
        sb.append(" (");
        if (clsModule1 == clsModule2) {
            sb.append(clsName1);
            sb.append(" and ");
            classInModuleOfLoader(clsName2, clsModule2, clsLoader2, true, sb);
        } else {
            classInModuleOfLoader(clsName1, clsModule1, clsLoader1, false, sb);
            sb.append("; ");
            classInModuleOfLoader(clsName2, clsModule2, clsLoader2, false, sb);
        }
        sb.append(")");
    }

    private static void classInModuleOfLoader(String clsName, Module clsModule, ClassLoader clsLoader, boolean plural, StringBuilder sb) {
        sb.append(clsName);
        if (plural) {
            sb.append(" are in ");
        } else {
            sb.append(" is in ");
        }
        if (clsModule.isNamed()) {
            sb.append("module ").append(clsModule.getName());
        } else {
            sb.append("unnamed module");
        }
        sb.append(" of loader ");
        sb.append(loaderDesc(clsLoader));
    }

    private static String loaderDesc(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        if (loader.getName() == null) {
            return loader.getClass().getName();
        } else {
            return loader.getName();
        }
    }

    private static InterpreterResolvedJavaType unwrapToElementalType(InterpreterResolvedJavaType type) {
        return type.isArray() ? (InterpreterResolvedJavaType) type.getElementalType() : type;
    }

    private static boolean sameRuntimePackage(InterpreterResolvedJavaType first, InterpreterResolvedJavaType second) {
        return first.getClassLoader() == second.getClassLoader() && first.getSymbolicRuntimePackage() == second.getSymbolicRuntimePackage();
    }

    private static boolean areNestMates(InterpreterResolvedJavaType first, InterpreterResolvedJavaType second) {
        return first.equals(second) || first.getJavaClass().isNestmateOf(second.getJavaClass());
    }
}
