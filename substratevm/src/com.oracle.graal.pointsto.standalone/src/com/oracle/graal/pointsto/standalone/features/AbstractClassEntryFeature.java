/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.features;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.standalone.StandaloneAnalysisClassLoader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;

/**
 * This feature supports taking abstract class methods(the non-abstract methods) and interface
 * default methods as analyzing entry point. It generates a stub concrete subclass for the abstract
 * class/interface at analyzing time to make sure the abstract class has at least one
 * implementation.
 */
public class AbstractClassEntryFeature implements Feature {

    private static final String STUB_CLASSNAME = "_StubConcrete";

    /**
     * Work is done during analysis instead of before analysis for 2 reasons:
     * <ol>
     * <li>The root method is register in other threads which may not be executed when
     * beforeAnalysis is called.</li>
     * <li>Concrete subclasses of the abstract class may be found during analysis, so we don't need
     * to stub them at the beginning.</li>
     * </ol>
     * 
     * @param access The supported operations that the feature can perform at this time
     *
     */
    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        StandaloneAnalysisFeatureImpl.DuringAnalysisAccessImpl a = (StandaloneAnalysisFeatureImpl.DuringAnalysisAccessImpl) access;
        StandaloneAnalysisClassLoader standaloneAnalysisClassLoader = (StandaloneAnalysisClassLoader) a.getApplicationClassLoader();
        // Find the entry method whose declaring class is abstract and has no concrete descendants
        List<AnalysisMethod> abstractEntries = a.getUniverse().getMethods().stream()
                        .filter(m -> m.isVirtualRootMethod() && standaloneAnalysisClassLoader.equals(m.getDeclaringClass().getJavaClass().getClassLoader()) && noConcreteSub(m.getDeclaringClass()))
                        .distinct().collect(Collectors.toList());
        if (!abstractEntries.isEmpty()) {
            BigBang bigBang = a.bb;
            Set<AnalysisType> definedTypes = new HashSet<>();
            for (AnalysisMethod abstractEntry : abstractEntries) {
                // Dynamically generate a stub concrete class
                AnalysisType declaringType = abstractEntry.getDeclaringClass();
                if (!definedTypes.contains(declaringType)) {
                    Class<?> stubConcreteSub = generateStubConcreteSub(declaringType.getJavaClass(), standaloneAnalysisClassLoader);
                    bigBang.getMetaAccess().lookupJavaType(stubConcreteSub).registerAsInHeap("Dummy concrete subclass of abstract class.");
                    definedTypes.add(declaringType);
                }
                bigBang.addRootMethod(abstractEntry, false, "Non-abstract method in abstract class");
            }
            definedTypes.clear();
        }
    }

    private static boolean noConcreteSub(AnalysisType t) {
        return t.getAllSubtypes().stream().allMatch(subtype -> subtype.isAbstract());
    }

    private static Class<?> generateStubConcreteSub(Class<?> abstractClass, StandaloneAnalysisClassLoader classLoader) {
        String abstractClassName = abstractClass.getName();
        String concreteStubSubclassName = abstractClassName + STUB_CLASSNAME;
        if (concreteStubSubclassName.contains("$")) {
            concreteStubSubclassName = concreteStubSubclassName.replace('$', '_');
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String internalConcreteName = concreteStubSubclassName.replace('.', '/');
        String internalAbstractName = abstractClassName.replace('.', '/');
        if (abstractClass.isInterface()) {
            cw.visit(Opcodes.V1_8, ACC_PUBLIC, internalConcreteName, null, "java/lang/Object", new String[]{internalAbstractName});
        } else {
            cw.visit(Opcodes.V1_8, ACC_PUBLIC, internalConcreteName, null, internalAbstractName, null);
        }

        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();
        return classLoader.defineClass(concreteStubSubclassName, classBytes);
    }
}
