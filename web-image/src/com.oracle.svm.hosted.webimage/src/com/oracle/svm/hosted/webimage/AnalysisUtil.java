/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.webimage.codegen.JSIntrinsifyFile;
import com.oracle.svm.hosted.webimage.codegen.JSIntrinsifyFile.FileData;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Utility class for code that influences the points-to-analysis (e.g. beforeAnalysis and
 * duringAnalysis in features).
 */
public class AnalysisUtil {

    /**
     * Goes through {@link FileData} and marks types, methods, and fields as reachable, invoked, or
     * accessed respectively.
     *
     * {@link BeforeAnalysisAccessImpl} is a supertype of
     * {@link com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl} and thus this can be
     * called both during and before analysis.
     *
     * @return Whether any new types were registered.
     */
    public static boolean processFileData(BeforeAnalysisAccessImpl access, FileData data) {
        boolean didRegister = false;
        for (JSIntrinsifyFile.JSIntrinsification i : data.intrinsics) {
            switch (i) {
                case JSIntrinsifyFile.MethodIntrinsification mi -> {
                    AnalysisType type = mi.precedingType.analysisType;

                    GraalError.guarantee(type != null, "Found method intrinsification with uninitialized preceding type");

                    /*
                     * We need to register the method as accessed in this class and all subclasses
                     * because we don't know which of the methods may actually be invoked at
                     * runtime.
                     */
                    for (AnalysisType subType : AnalysisUniverse.reachableSubtypes(type)) {
                        if (registerAccessToMethods(access, subType, mi)) {
                            didRegister = true;
                        }
                    }
                }
                case JSIntrinsifyFile.TypeIntrinsification ti -> {
                    ti.analysisType = access.findTypeByName(ti.name);
                    if (ti.analysisType.registerAsReachable("is used by TypeIntrinsifications in Web Image")) {
                        didRegister = true;
                    }
                }
                case JSIntrinsifyFile.FieldIntrinsification fi -> {
                    AnalysisType t = fi.precedingType.analysisType;

                    GraalError.guarantee(t != null, "Found field intrinsification with uninitialized preceding type");

                    ResolvedJavaField[] instanceFields = t.getInstanceFields(false);
                    ResolvedJavaField[] staticFields = t.getStaticFields();

                    List<ResolvedJavaField> fields = Stream.concat(Arrays.stream(instanceFields), Arrays.stream(staticFields)).toList();

                    for (ResolvedJavaField field : fields) {
                        if (field.getName().equals(fi.name)) {
                            if (((AnalysisField) field).registerAsAccessed("is used by FieldIntrinsification in Web Image")) {
                                didRegister = true;
                            }
                            break;
                        }
                    }
                }
                default -> {
                    GraalError.shouldNotReachHere(i.toString()); // ExcludeFromJacocoGeneratedReport
                }
            }
        }

        return didRegister;
    }

    private static boolean registerAccessToMethods(BeforeAnalysisAccessImpl access, AnalysisType type, JSIntrinsifyFile.MethodIntrinsification mi) {
        boolean didRegister = false;

        if (mi.name.equals("<init>")) {
            assert mi.sig != null;
            // only register the constructor that matches the signature
            ResolvedJavaMethod[] constructors = JVMCIReflectionUtil.getConstructors(type);
            for (ResolvedJavaMethod c : constructors) {
                AnalysisMethod constructor = (AnalysisMethod) c;
                String signature = getSignatureString(constructor);
                if (signature.equals(mi.sig)) {
                    if (!constructor.isInvoked()) {
                        access.registerAsRoot(constructor, false, "Constructor accessed reflectively from JS, registered in " + AnalysisUtil.class);
                        didRegister = true;
                    }
                    break;
                }
            }
        } else {
            for (AnalysisMethod candidate : type.getDeclaredMethods(false)) {
                if (candidate.getName().equals(mi.name)) {
                    if (candidate.isAbstract() || candidate.isInvoked()) {
                        continue;
                    }

                    access.registerAsRoot(candidate, false, "Methods accessed reflectively from JS, registered in " + AnalysisUtil.class);
                    didRegister = true;
                }
            }
        }

        return didRegister;
    }

    /**
     * Gets the signature string of a constructor. For example, for {@link String#String(char[])}
     * this returns {@code [C}
     */
    private static String getSignatureString(ResolvedJavaMethod constructor) {
        StringBuilder sb = new StringBuilder();
        ResolvedJavaMethod.Parameter[] parameters = constructor.getParameters();
        for (var parameter : parameters) {
            sb.append(parameter.getType().toClassName());
        }
        return sb.toString();
    }
}
