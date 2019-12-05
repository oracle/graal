/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Builtin;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.DefaultNameTransformation;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeBootImage;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class CEntryPointData {

    public static final String DEFAULT_NAME = "";
    public static final Class<? extends Function<String, String>> DEFAULT_NAME_TRANSFORMATION = DefaultNameTransformation.class;
    public static final CEntryPoint.Builtin DEFAULT_BUILTIN = CEntryPoint.Builtin.NO_BUILTIN;
    public static final Class<?> DEFAULT_PROLOGUE = CEntryPointOptions.AutomaticPrologue.class;
    public static final Class<?> DEFAULT_EPILOGUE = CEntryPointSetup.LeaveEpilogue.class;
    public static final Class<?> DEFAULT_EXCEPTION_HANDLER = CEntryPoint.FatalExceptionHandler.class;

    public static CEntryPointData create(ResolvedJavaMethod method) {
        return create(method.getAnnotation(CEntryPoint.class), method.getAnnotation(CEntryPointOptions.class),
                        () -> NativeBootImage.globalSymbolNameForMethod(method));
    }

    public static CEntryPointData create(ResolvedJavaMethod method, String name, Class<? extends Function<String, String>> nameTransformation,
                    String documentation, Class<?> prologue, Class<?> epilogue, Class<?> exceptionHandler, Publish publishAs) {

        return create(name, () -> NativeBootImage.globalSymbolNameForMethod(method), nameTransformation, documentation, Builtin.NO_BUILTIN, prologue, epilogue, exceptionHandler, publishAs);
    }

    public static CEntryPointData create(Method method) {
        return create(method, DEFAULT_NAME);
    }

    public static CEntryPointData create(Method method, String name) {
        assert method.getAnnotation(CEntryPoint.class).name().isEmpty() || name.isEmpty();
        return create(method.getAnnotation(CEntryPoint.class), method.getAnnotation(CEntryPointOptions.class),
                        () -> !name.isEmpty() ? name : NativeBootImage.globalSymbolNameForMethod(method));
    }

    public static CEntryPointData create(Method method, String name, Class<? extends Function<String, String>> nameTransformation,
                    String documentation, Class<?> prologue, Class<?> epilogue, Class<?> exceptionHandler, Publish publishAs) {

        return create(name, () -> NativeBootImage.globalSymbolNameForMethod(method), nameTransformation, documentation, Builtin.NO_BUILTIN, prologue, epilogue, exceptionHandler, publishAs);
    }

    public static CEntryPointData createCustomUnpublished() {
        CEntryPointData unpublished = new CEntryPointData(null, DEFAULT_NAME, "", Builtin.NO_BUILTIN, CEntryPointOptions.NoPrologue.class, CEntryPointOptions.NoEpilogue.class,
                        DEFAULT_EXCEPTION_HANDLER, Publish.NotPublished);
        unpublished.symbolName = DEFAULT_NAME;
        return unpublished;
    }

    @SuppressWarnings("deprecation")
    private static CEntryPointData create(CEntryPoint annotation, CEntryPointOptions options, Supplier<String> alternativeNameSupplier) {
        String annotatedName = annotation.name();
        Class<? extends Function<String, String>> nameTransformation = DEFAULT_NAME_TRANSFORMATION;
        String documentation = String.join(System.lineSeparator(), annotation.documentation());
        CEntryPoint.Builtin builtin = annotation.builtin();
        Class<?> prologue = DEFAULT_PROLOGUE;
        Class<?> epilogue = DEFAULT_EPILOGUE;
        Class<?> exceptionHandler = annotation.exceptionHandler();
        Publish publishAs = Publish.SymbolAndHeader;
        if (options != null) {
            nameTransformation = options.nameTransformation();
            prologue = options.prologue();
            epilogue = options.epilogue();
            publishAs = options.publishAs();
        }
        return create(annotatedName, alternativeNameSupplier, nameTransformation, documentation, builtin, prologue, epilogue, exceptionHandler, publishAs);
    }

    private static CEntryPointData create(String providedName, Supplier<String> alternativeNameSupplier, Class<? extends Function<String, String>> nameTransformation,
                    String documentation, Builtin builtin, Class<?> prologue, Class<?> epilogue, Class<?> exceptionHandler, Publish publishAs) {

        // Delay generating the final symbol name because this method may be called early at a time
        // where some of the environment (such as ImageSingletons) is incomplete
        Supplier<String> symbolNameSupplier = () -> {
            String symbolName = !providedName.isEmpty() ? providedName : alternativeNameSupplier.get();
            if (nameTransformation != null) {
                try {
                    Function<String, String> instance = nameTransformation.getDeclaredConstructor().newInstance();
                    symbolName = instance.apply(symbolName);
                } catch (Exception e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }
            return symbolName;
        };
        return new CEntryPointData(symbolNameSupplier, providedName, documentation, builtin, prologue, epilogue, exceptionHandler, publishAs);
    }

    private String symbolName;
    private final Supplier<String> symbolNameSupplier;
    private final String providedName;
    private final String documentation;
    private final Builtin builtin;
    private final Class<?> prologue;
    private final Class<?> epilogue;
    private final Class<?> exceptionHandler;
    private final Publish publishAs;

    private CEntryPointData(Supplier<String> symbolNameSupplier, String providedName, String documentation, Builtin builtin,
                    Class<?> prologue, Class<?> epilogue, Class<?> exceptionHandler, Publish publishAs) {

        this.symbolNameSupplier = symbolNameSupplier;
        this.providedName = providedName;
        this.documentation = documentation;
        this.builtin = builtin;
        this.prologue = prologue;
        this.epilogue = epilogue;
        this.exceptionHandler = exceptionHandler;
        this.publishAs = publishAs;
    }

    public CEntryPointData copyWithPublishAs(Publish customPublishAs) {
        return new CEntryPointData(symbolNameSupplier, providedName, documentation, builtin, prologue, epilogue, exceptionHandler, customPublishAs);
    }

    public String getSymbolName() {
        if (symbolName == null) {
            symbolName = symbolNameSupplier.get();
            assert symbolName != null;
        }
        return symbolName;
    }

    public String getProvidedName() {
        return providedName;
    }

    public String getDocumentation() {
        return documentation;
    }

    public Builtin getBuiltin() {
        return builtin;
    }

    public Class<?> getPrologue() {
        return prologue;
    }

    public Class<?> getEpilogue() {
        return epilogue;
    }

    public Class<?> getExceptionHandler() {
        return exceptionHandler;
    }

    public Publish getPublishAs() {
        return publishAs;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof CEntryPointData) {
            CEntryPointData other = (CEntryPointData) obj;
            return Objects.equals(getSymbolName(), other.getSymbolName()) &&
                            Objects.equals(providedName, other.providedName) &&
                            Objects.equals(documentation, other.documentation) &&
                            Objects.equals(builtin, other.builtin) &&
                            Objects.equals(prologue, other.prologue) &&
                            Objects.equals(epilogue, other.epilogue) &&
                            Objects.equals(exceptionHandler, other.exceptionHandler) &&
                            publishAs == other.publishAs;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = Objects.hashCode(getSymbolName());
        h = h * 31 + Objects.hashCode(providedName);
        h = h * 31 + Objects.hashCode(documentation);
        h = h * 31 + Objects.hashCode(builtin);
        h = h * 31 + Objects.hashCode(prologue);
        h = h * 31 + Objects.hashCode(epilogue);
        h = h * 31 + Objects.hashCode(exceptionHandler);
        h = h * 31 + publishAs.hashCode();
        return h;
    }
}
