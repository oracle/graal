/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Builtin;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.DefaultNameTransformation;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.GraalAccess;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Encapsulates info from {@link CEntryPoint} and {@link CEntryPointOptions} annotations.
 */
public final class CEntryPointData {

    /**
     * @see CEntryPoint#name()
     */
    private static final String EMPTY_NAME = "";

    /**
     * @see CEntryPoint#documentation()
     */
    public static final String EMPTY_DOCUMENTATION = "";

    /**
     * @see CEntryPointOptions#nameTransformation()
     */
    public static final ResolvedJavaType DEFAULT_NAME_TRANSFORMATION = GraalAccess.get().lookupType(DefaultNameTransformation.class);

    /**
     * @see CEntryPoint#builtin()
     */
    public static final CEntryPoint.Builtin NO_BUILTIN = CEntryPoint.Builtin.NO_BUILTIN;

    /**
     * @see CEntryPointOptions#prologue()
     */
    public static final ResolvedJavaType AUTOMATIC_PROLOGUE = GraalAccess.get().lookupType(CEntryPointOptions.AutomaticPrologue.class);
    public static final ResolvedJavaType NO_PROLOGUE = GraalAccess.get().lookupType(CEntryPointOptions.NoPrologue.class);

    /**
     * @see CEntryPointOptions#prologueBailout()
     */
    public static final ResolvedJavaType AUTOMATIC_PROLOGUE_BAILOUT = GraalAccess.get().lookupType(CEntryPointOptions.AutomaticPrologueBailout.class);

    /**
     * @see CEntryPointOptions#epilogue()
     */
    public static final ResolvedJavaType DEFAULT_EPILOGUE = GraalAccess.get().lookupType(CEntryPointSetup.LeaveEpilogue.class);
    public static final ResolvedJavaType NO_EPILOGUE = GraalAccess.get().lookupType(CEntryPointOptions.NoEpilogue.class);

    /**
     * @see CEntryPoint#exceptionHandler()
     */
    public static final ResolvedJavaType FATAL_EXCEPTION_HANDLER = GraalAccess.get().lookupType(CEntryPoint.FatalExceptionHandler.class);

    public static CEntryPointData create(ResolvedJavaMethod method, String name) {
        CEntryPointGuestValue cEntryPoint = CEntryPointGuestValue.from(AnnotationUtil.getAnnotationValue(method, CEntryPoint.class));
        CEntryPointOptionsGuestValue cEntryPointOptions = CEntryPointOptionsGuestValue.from(AnnotationUtil.getAnnotationValue(method, CEntryPointOptions.class));
        assert cEntryPoint.name().isEmpty() || name.isEmpty();
        return create(cEntryPoint, cEntryPointOptions,
                        () -> !name.isEmpty() ? name : NativeImage.globalSymbolNameForMethod(method));
    }

    public static CEntryPointData create(ResolvedJavaMethod method) {
        return create(method, EMPTY_NAME);
    }

    public static CEntryPointData create(ResolvedJavaMethod method,
                    String name,
                    ResolvedJavaType nameTransformation,
                    String documentation,
                    ResolvedJavaType prologue,
                    ResolvedJavaType prologueBailout,
                    ResolvedJavaType epilogue,
                    ResolvedJavaType exceptionHandler,
                    Publish publishAs) {

        return create(name,
                        () -> NativeImage.globalSymbolNameForMethod(method),
                        nameTransformation,
                        documentation,
                        Builtin.NO_BUILTIN,
                        prologue,
                        prologueBailout,
                        epilogue,
                        exceptionHandler,
                        publishAs);
    }

    public static CEntryPointData createCustomUnpublished() {
        CEntryPointData unpublished = new CEntryPointData(null,
                        EMPTY_NAME,
                        EMPTY_DOCUMENTATION,
                        Builtin.NO_BUILTIN,
                        NO_PROLOGUE,
                        null,
                        NO_EPILOGUE,
                        FATAL_EXCEPTION_HANDLER,
                        Publish.NotPublished);
        unpublished.symbolName = EMPTY_NAME;
        return unpublished;
    }

    private static CEntryPointData create(CEntryPointGuestValue cEntryPoint, CEntryPointOptionsGuestValue options, Supplier<String> alternativeNameSupplier) {
        String annotatedName = cEntryPoint.name();
        ResolvedJavaType nameTransformation = DEFAULT_NAME_TRANSFORMATION;
        List<String> docLines = cEntryPoint.documentation();
        String documentation = docLines.isEmpty() ? "" : String.join(System.lineSeparator(), docLines);
        CEntryPoint.Builtin builtin = cEntryPoint.builtin();
        ResolvedJavaType prologue = AUTOMATIC_PROLOGUE;
        ResolvedJavaType prologueBailout = AUTOMATIC_PROLOGUE_BAILOUT;
        ResolvedJavaType epilogue = DEFAULT_EPILOGUE;
        ResolvedJavaType exceptionHandler = cEntryPoint.exceptionHandler();
        Publish publishAs = cEntryPoint.publishAs();
        if (options != null) {
            nameTransformation = options.nameTransformation();
            prologue = options.prologue();
            prologueBailout = options.prologueBailout();
            epilogue = options.epilogue();
        }
        return create(annotatedName, alternativeNameSupplier, nameTransformation, documentation, builtin, prologue, prologueBailout, epilogue, exceptionHandler, publishAs);
    }

    private static CEntryPointData create(String providedName,
                    Supplier<String> alternativeNameSupplier,
                    ResolvedJavaType nameTransformation,
                    String documentation,
                    Builtin builtin,
                    ResolvedJavaType prologue,
                    ResolvedJavaType prologueBailout,
                    ResolvedJavaType epilogue,
                    ResolvedJavaType exceptionHandler,
                    Publish publishAs) {

        // Delay generating the final symbol name because this method may be called before
        // some initialization (e.g., of ImageSingletons) has completed.
        Supplier<String> symbolNameSupplier = () -> {
            String symbolName = !providedName.isEmpty() ? providedName : alternativeNameSupplier.get();
            if (nameTransformation != null) {
                try {
                    GraalAccess h = GraalAccess.get();
                    symbolName = h.asHostString(h.callFunction(nameTransformation, h.asGuestString(symbolName)));
                } catch (Exception e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }
            return symbolName;
        };
        return new CEntryPointData(symbolNameSupplier, providedName, documentation, builtin, prologue, prologueBailout, epilogue, exceptionHandler, publishAs);
    }

    private String symbolName;
    private final Supplier<String> symbolNameSupplier;
    private final String providedName;
    private final String documentation;
    private final Builtin builtin;
    private final ResolvedJavaType prologue;
    private final ResolvedJavaType prologueBailout;
    private final ResolvedJavaType epilogue;
    private final ResolvedJavaType exceptionHandler;
    private final Publish publishAs;

    private CEntryPointData(Supplier<String> symbolNameSupplier, String providedName, String documentation, Builtin builtin,
                    ResolvedJavaType prologue, ResolvedJavaType prologueBailout, ResolvedJavaType epilogue, ResolvedJavaType exceptionHandler, Publish publishAs) {

        this.symbolNameSupplier = symbolNameSupplier;
        this.providedName = providedName;
        this.documentation = documentation;
        this.builtin = builtin;
        this.prologue = prologue;
        this.prologueBailout = prologueBailout;
        this.epilogue = epilogue;
        this.exceptionHandler = exceptionHandler;
        this.publishAs = publishAs;
    }

    public CEntryPointData copyWithPublishAs(Publish customPublishAs) {
        return new CEntryPointData(symbolNameSupplier, providedName, documentation, builtin, prologue, prologueBailout, epilogue, exceptionHandler, customPublishAs);
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

    public ResolvedJavaType getPrologue() {
        return prologue;
    }

    public ResolvedJavaType getPrologueBailout() {
        return prologueBailout;
    }

    public ResolvedJavaType getEpilogue() {
        return epilogue;
    }

    public ResolvedJavaType getExceptionHandler() {
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
        if (obj instanceof CEntryPointData other) {
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
