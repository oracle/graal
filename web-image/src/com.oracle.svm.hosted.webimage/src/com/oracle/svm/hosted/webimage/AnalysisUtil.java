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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.webimage.codegen.JSIntrinsifyFile;
import com.oracle.svm.hosted.webimage.codegen.JSIntrinsifyFile.FileData;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ResolvedJavaField;

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
            if (i instanceof JSIntrinsifyFile.MethodIntrinsification) {
                JSIntrinsifyFile.MethodIntrinsification mi = (JSIntrinsifyFile.MethodIntrinsification) i;
                Class<?> clazz = access.findClassByName(mi.precedingType.name);

                if (clazz == null) {
                    continue;
                }

                /*
                 * We need to register the method as accessed in this class and all subclasses
                 * because we don't know which of the methods may actually be invoked at runtime.
                 */
                for (Class<?> c : access.reachableSubtypes(clazz)) {
                    if (registerAccessToMethods(access, c, mi)) {
                        didRegister = true;
                    }
                }
            } else if (i instanceof JSIntrinsifyFile.TypeIntrinsification) {
                JSIntrinsifyFile.TypeIntrinsification ti = (JSIntrinsifyFile.TypeIntrinsification) i;

                Class<?> clazz = access.findClassByName(ti.name);
                AnalysisType t = access.getMetaAccess().lookupJavaType(clazz);
                if (t.registerAsReachable("is used by TypeIntrinsifications in Web Image")) {
                    didRegister = true;
                }
            } else if (i instanceof JSIntrinsifyFile.FieldIntrinsification) {
                JSIntrinsifyFile.FieldIntrinsification fi = (JSIntrinsifyFile.FieldIntrinsification) i;

                Class<?> clazz = access.findClassByName(fi.precedingType.name);
                AnalysisType t = access.getMetaAccess().lookupJavaType(clazz);

                ResolvedJavaField[] instanceFields = t.getInstanceFields(false);
                ResolvedJavaField[] staticFields = t.getStaticFields();

                List<ResolvedJavaField> fields = Stream.concat(Arrays.stream(instanceFields), Arrays.stream(staticFields)).collect(Collectors.toList());

                for (ResolvedJavaField field : fields) {
                    if (field.getName().equals(fi.name)) {
                        if (((AnalysisField) field).registerAsAccessed("is used by FieldIntrinsification in Web Image")) {
                            didRegister = true;
                        }
                        break;
                    }
                }
            } else {
                GraalError.shouldNotReachHere(i.toString()); // ExcludeFromJacocoGeneratedReport
            }
        }

        return didRegister;
    }

    private static boolean registerAccessToMethods(BeforeAnalysisAccessImpl access, Class<?> clazz, JSIntrinsifyFile.MethodIntrinsification mi) {
        AnalysisMetaAccess meta = access.getMetaAccess();

        boolean didRegister = false;

        if (mi.name.equals("<init>")) {
            assert mi.sig != null;
            // only register the constructor that matches the signature
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> constructor : constructors) {
                String signature = getSignatureString(constructor);
                if (signature.equals(mi.sig)) {
                    AnalysisMethod analysisMethod = meta.lookupJavaMethod(constructor);
                    if (!analysisMethod.isInvoked()) {
                        access.registerAsRoot(constructor, false, "Constructor accessed reflectively from JS, registered in " + AnalysisUtil.class);
                        didRegister = true;
                    }
                    break;
                }
            }
        } else {
            for (Method candidate : clazz.getDeclaredMethods()) {
                if (candidate.getName().equals(mi.name)) {
                    AnalysisMethod aMethod = meta.lookupJavaMethod(candidate);

                    if (Modifier.isAbstract(aMethod.getModifiers()) || aMethod.isInvoked()) {
                        continue;
                    }

                    access.registerAsRoot(aMethod, false, "Methods accessed reflectively from JS, registered in " + AnalysisUtil.class);
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
    private static String getSignatureString(Constructor<?> constructor) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] parameters = constructor.getParameterTypes();
        for (Class<?> parameter : parameters) {
            sb.append(parameter.getName());
        }
        return sb.toString();
    }
}
