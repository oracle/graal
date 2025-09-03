/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Policy used to determine which types, methods and fields need to be included in an image. The
 * decisions made by a policy are based on {@link ModifiersProvider#getModifiers()} which follows
 * the type, method and field flags in the JVM spec. In the case of an inner class, the
 * {@code InnerClasses} attribute ({@jvms 4.7.9}) is ignored. In contrast,
 * {@link Class#getModifiers()} reflects the flags from the {@code InnerClasses} attribute.
 */
public abstract class ClassInclusionPolicy {

    protected BigBang bb;
    protected final Object reason;

    public ClassInclusionPolicy(Object reason) {
        this.reason = reason;
    }

    public void setBigBang(BigBang bb) {
        this.bb = bb;
    }

    /**
     * Determines if {@code type} needs to be included in the image according to this policy.
     */
    public boolean isOriginalTypeIncluded(ResolvedJavaType type) {
        /* Delegate to host VM for additional checks. */
        return bb.getHostVM().isSupportedOriginalType(bb, type);
    }

    /**
     * Includes the given {@code type} in the image.
     */
    public void includeType(ResolvedJavaType type) {
        AnalysisType aType = asAnalysisType(type);
        if (type.isAbstract() || type.isInterface() || type.isPrimitive()) {
            /*
             * Those types cannot be instantiated. They are instead registered as reachable as they
             * can still have methods or fields that could be used by an extension image.
             */
            aType.registerAsReachable(reason);
        } else {
            aType.registerAsInstantiated(reason);
        }
    }

    /**
     * Determines if {@code method} needs to be included in the image according to this policy.
     */
    public boolean isAnalysisMethodIncluded(AnalysisMethod method) {
        /* Delegate to host VM for additional checks. */
        return bb.getHostVM().isSupportedAnalysisMethod(bb, method);
    }

    /**
     * Determines if {@code method} needs to be included in the image according to this policy.
     */
    public boolean isOriginalMethodIncluded(ResolvedJavaMethod method) {
        /* Delegate to host VM for additional checks. */
        return bb.getHostVM().isSupportedOriginalMethod(bb, method);
    }

    /**
     * Includes the given {@code method} in the image.
     */
    public void includeMethod(ResolvedJavaMethod method) {
        bb.postTask(debug -> bb.addRootMethod(asAnalysisMethod(method), method.isConstructor(), reason));
    }

    /**
     * Determines if {@code field} needs to be included in the image according to this policy.
     */
    public boolean isAnalysisFieldIncluded(AnalysisField field) {
        /* Delegate to host VM for additional checks. */
        return bb.getHostVM().isSupportedAnalysisField(bb, field);
    }

    /**
     * Determines if {@code field} needs to be included in the image according to this policy.
     */
    public boolean isOriginalFieldIncluded(ResolvedJavaField field) {
        /* Delegate to host VM for additional checks. */
        return bb.getHostVM().isSupportedOriginalField(bb, field);
    }

    /**
     * Includes the given {@code field} in the image.
     */
    public void includeField(ResolvedJavaField field) {
        bb.postTask(debug -> bb.addRootField(asAnalysisField(field)));
    }

    AnalysisType asAnalysisType(ResolvedJavaType type) {
        return type instanceof AnalysisType aType ? aType : bb.getUniverse().lookup(type);
    }

    AnalysisField asAnalysisField(ResolvedJavaField field) {
        return field instanceof AnalysisField aField ? aField : bb.getUniverse().lookup(field);
    }

    AnalysisMethod asAnalysisMethod(ResolvedJavaMethod method) {
        return method instanceof AnalysisMethod aMethod ? aMethod : bb.getUniverse().lookup(method);
    }

    /**
     * The inclusion policy is queried during the class path scanning to determine which code should
     * be included in a shared layer. The policy tries to limit the code that we eagerly include by
     * eliminating code that wouldn't be directly accessible from an extension layer.
     * <p>
     * For types and methods it follows the Java language access rules to determine accessibility.
     * For example, it includes all {@code public} methods and types, but it doesn't include
     * {@code private} methods, although a {@code private} method will still be included if
     * reachable from a root.
     * <p>
     * All methods directly accessible from extension layers <em>must be marked as root</em> to
     * ensure the correctness of the analysis. For the other methods, the inclusion policy is only a
     * heuristic and the layer in which a method is included will not affect correctness.
     * <p>
     * Contrastingly, all the fields of included types are also included disregarding access rules
     * since missing a field would cause inconsistencies in the object layout across layers.
     * <p>
     * Note: For a description of layers structure see {@code ImageLayerBuildingSupport}.
     */
    public static class SharedLayerImageInclusionPolicy extends ClassInclusionPolicy {
        public SharedLayerImageInclusionPolicy(Object reason) {
            super(reason);
        }

        /**
         * A type from a shared layer is included iff it is public, which at the Java source level
         * corresponds to {@code public} for top level types and either {@code public} or
         * {@code protected} for inner types.
         * <p>
         * The VM access semantics for inner classes and interfaces differ from the Java language
         * semantics. An inner type declared as {@code protected} in the source code has the
         * ACC_PUBLIC access flag in the class file. Similarly, an inner type declared as
         * {@code private} is treated as a package-private inner type and has no access modifiers
         * set in the class file. We follow the VM access semantics for inner types.
         */
        @Override
        public boolean isOriginalTypeIncluded(ResolvedJavaType type) {
            if (!super.isOriginalTypeIncluded(type)) {
                return false;
            }
            if (!canLinkType(type)) {
                return false;
            }
            ResolvedJavaType enclosingType = type.getEnclosingType();
            if (enclosingType != null && !isOriginalTypeIncluded(enclosingType)) {
                return false;
            }
            return type.isPublic();
        }

        /**
         * Try to link the type. Linking can fail for example if the class path is incomplete.
         * Methods of unlinked types cannot be parsed, so we exclude the type early. Linking would
         * be triggered later when {@link AnalysisType} is created anyway.
         */
        private static boolean canLinkType(ResolvedJavaType type) {
            try {
                type.link();
            } catch (LinkageError ex) {
                return false;
            }
            return true;
        }

        @Override
        public boolean isAnalysisMethodIncluded(AnalysisMethod method) {
            return super.isAnalysisMethodIncluded(method) && isMethodAccessible(method);
        }

        @Override
        public boolean isOriginalMethodIncluded(ResolvedJavaMethod method) {
            return super.isOriginalMethodIncluded(method) && isMethodAccessible(method);
        }

        /**
         * Determines if {@code method} can be called (without reflection) from an extension layer.
         * A public method can always be accessed. A private or package-private method can never be
         * accessed as packages are never split between layers. A protected method can only be
         * accessed from classes in the same package or subclasses. Since a final class cannot be
         * subclassed and the complete closed hierarchy of a sealed class must be in the same layer,
         * a protected method in a final or sealed class cannot be accessed from an extension layer.
         * In addition, abstract methods cannot be included in the image as they cannot be analyzed
         * or compiled.
         */
        private static boolean isMethodAccessible(ResolvedJavaMethod method) {
            if (method.isAbstract()) {
                return false;
            }
            if (method.isPublic()) {
                return true;
            }
            if (method.isPrivate() || method.isPackagePrivate()) {
                return false;
            }
            /* Protected methods from non-final non-sealed classes should be accessible. */
            AnalysisError.guarantee(method.isProtected());
            ResolvedJavaType declaringClass = method.getDeclaringClass();
            return !declaringClass.isFinalFlagSet() && !OriginalClassProvider.getJavaClass(declaringClass).isSealed();
        }

        @Override
        public void includeMethod(ResolvedJavaMethod method) {
            bb.postTask(debug -> bb.forcedAddRootMethod(asAnalysisMethod(method), method.isConstructor(), reason));
        }

        @Override
        public boolean isAnalysisFieldIncluded(AnalysisField field) {
            return super.isAnalysisFieldIncluded(field) && bb.getHostVM().isFieldIncludedInSharedLayer(field);
        }

        @Override
        public boolean isOriginalFieldIncluded(ResolvedJavaField field) {
            return super.isOriginalFieldIncluded(field) && bb.getHostVM().isFieldIncludedInSharedLayer(field);
        }
    }

    /**
     * The default inclusion policy. Includes all types, methods and fields.
     */
    public static class DefaultAllInclusionPolicy extends ClassInclusionPolicy {
        public DefaultAllInclusionPolicy(Object reason) {
            super(reason);
        }

        @Override
        public boolean isOriginalTypeIncluded(ResolvedJavaType type) {
            if (!super.isOriginalTypeIncluded(type)) {
                return false;
            }
            try {
                ResolvedJavaType enclosingType = type.getEnclosingType();
                return enclosingType == null || isOriginalTypeIncluded(enclosingType);
            } catch (LinkageError e) {
                /* Ignore missing type errors. */
                return true;
            }
        }
    }
}
