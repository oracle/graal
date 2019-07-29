/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.SubstrateUtil.mangleName;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.serviceprovider.BufferUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.Section;
import com.oracle.objectfile.SectionName;
import com.oracle.objectfile.macho.MachOObjectFile;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.CConst;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.c.CHeader;
import com.oracle.svm.core.c.CHeader.Header;
import com.oracle.svm.core.c.CTypedef;
import com.oracle.svm.core.c.CUnsigned;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.GraalIsolateHeader;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CSourceCodeWriter;
import com.oracle.svm.hosted.c.codegen.QueryCodeWriter;
import com.oracle.svm.hosted.code.CEntryPointCallStubMethod;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.image.NativeImageHeap.HeapPartition;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.image.RelocatableBuffer.Info;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.MethodPointer;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class NativeBootImage extends AbstractBootImage {

    public static final long RWDATA_CGLOBALS_PARTITION_OFFSET = 0;

    @Override
    public Section getTextSection() {
        assert textSection != null;
        return textSection;
    }

    @Override
    public abstract String[] makeLaunchCommand(NativeImageKind k, String imageName, Path binPath, Path workPath, java.lang.reflect.Method method);

    protected final void write(Path outputFile) {
        try {
            Path outFileParent = outputFile.normalize().getParent();
            if (outFileParent != null) {
                Files.createDirectories(outFileParent);
            }
            FileChannel channel = FileChannel.open(outputFile, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            objectFile.write(channel);
        } catch (Exception ex) {
            throw shouldNotReachHere(ex);
        }
        resultingImageSize = (int) outputFile.toFile().length();
        if (NativeImageOptions.PrintImageElementSizes.getValue()) {
            for (Element e : objectFile.getElements()) {
                System.out.printf("PrintImageElementSizes:  size: %15d  name: %s\n", e.getMemSize(objectFile.getDecisionsByElement()), e.getElementName());
            }
        }
    }

    void writeHeaderFiles(Path outputDir, String imageName, Map<ResolvedJavaMethod, String> symbolAliases, boolean dynamic) {
        /* Group methods by header files. */
        Map<? extends Class<? extends Header>, List<HostedMethod>> hostedMethods = uniqueEntryPoints.stream()
                        .filter(this::shouldWriteHeader)
                        .map(m -> Pair.create(cHeader(m), m))
                        .collect(Collectors.groupingBy(Pair::getLeft, Collectors.mapping(Pair::getRight, Collectors.toList())));

        hostedMethods.forEach((headerClass, methods) -> {
            methods.sort(NativeBootImage::sortMethodsByFileNameAndPosition);
            Header header = headerClass == Header.class ? defaultCHeaderAnnotation(imageName) : instantiateCHeader(headerClass);
            writeHeaderFile(outputDir, header, methods, symbolAliases, dynamic);
        });
    }

    private void writeHeaderFile(Path outDir, Header header, List<HostedMethod> methods, Map<ResolvedJavaMethod, String> symbolAliases, boolean dynamic) {
        CSourceCodeWriter writer = new CSourceCodeWriter(outDir.getParent());
        String imageHeaderGuard = "__" + header.name().toUpperCase().replaceAll("[^A-Z0-9]", "_") + "_H";
        String dynamicSuffix = dynamic ? "_dynamic.h" : ".h";

        writer.appendln("#ifndef " + imageHeaderGuard);
        writer.appendln("#define " + imageHeaderGuard);

        writer.appendln();

        QueryCodeWriter.writeCStandardHeaders(writer);

        List<String> dependencies = header.dependsOn().stream()
                        .map(NativeBootImage::instantiateCHeader)
                        .map(depHeader -> "<" + depHeader.name() + dynamicSuffix + ">").collect(Collectors.toList());
        writer.includeFiles(dependencies);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(baos);
        header.writePreamble(printWriter);
        printWriter.flush();
        for (String line : baos.toString().split("\\r?\\n")) {
            writer.appendln(line);
        }

        if (methods.size() > 0) {
            writer.appendln();
            writer.appendln("#if defined(__cplusplus)");
            writer.appendln("extern \"C\" {");
            writer.appendln("#endif");
            writer.appendln();

            methods.forEach(m -> writeMethodHeader(m, writer, symbolAliases, dynamic));

            writer.appendln("#if defined(__cplusplus)");
            writer.appendln("}");
            writer.appendln("#endif");
        }

        writer.appendln("#endif");
        Path fileNamePath = outDir.getFileName();
        if (fileNamePath == null) {
            throw UserError.abort("Cannot determine header file name for directory " + outDir);
        } else {
            String fileName = fileNamePath.resolve(header.name() + dynamicSuffix).toString();
            writer.writeFile(fileName, false);
        }
    }

    /**
     * Looks up the corresponding {@link CHeader} annotation for the {@link HostedMethod}. Returns
     * {@code null} if no annotation was found.
     */
    private static Class<? extends CHeader.Header> cHeader(HostedMethod entryPointStub) {
        /* check if method is annotated */
        AnalysisMethod entryPoint = CEntryPointCallStubSupport.singleton().getMethodForStub((CEntryPointCallStubMethod) entryPointStub.wrapped.wrapped);
        CHeader methodAnnotation = entryPoint.getDeclaredAnnotation(CHeader.class);
        if (methodAnnotation != null) {
            return methodAnnotation.value();
        }

        /* check if enclosing classes are annotated */
        AnalysisType enclosingType = entryPoint.getDeclaringClass();
        while (enclosingType != null) {
            CHeader enclosing = enclosingType.getDeclaredAnnotation(CHeader.class);
            if (enclosing != null) {
                return enclosing.value();
            }
            enclosingType = enclosingType.getEnclosingType();
        }

        return CHeader.Header.class;
    }

    private static Header instantiateCHeader(Class<? extends CHeader.Header> header) {
        try {
            return ReflectionUtil.newInstance(header);
        } catch (ReflectionUtilError ex) {
            throw UserError.abort("CHeader " + header.getName() + " cannot be instantiated. Please make sure that it has a nullary constructor and is not abstract.", ex.getCause());
        }
    }

    private static CHeader.Header defaultCHeaderAnnotation(String defaultHeaderName) {
        return new CHeader.Header() {
            @Override
            public String name() {
                return defaultHeaderName;
            }

            @Override
            public List<Class<? extends Header>> dependsOn() {
                return Collections.singletonList(GraalIsolateHeader.class);
            }
        };
    }

    private static int sortMethodsByFileNameAndPosition(HostedMethod stub1, HostedMethod stub2) {
        ResolvedJavaMethod rm1 = CEntryPointCallStubSupport.singleton().getMethodForStub((CEntryPointCallStubMethod) stub1.wrapped.wrapped).wrapped;
        ResolvedJavaMethod rm2 = CEntryPointCallStubSupport.singleton().getMethodForStub((CEntryPointCallStubMethod) stub2.wrapped.wrapped).wrapped;

        int fileComparison = rm1.getDeclaringClass().getSourceFileName().compareTo(rm2.getDeclaringClass().getSourceFileName());
        if (fileComparison != 0) {
            return fileComparison;
        } else if (rm1.getLineNumberTable() != null && rm2.getLineNumberTable() != null) {
            return rm1.getLineNumberTable().getLineNumber(0) - rm2.getLineNumberTable().getLineNumber(0);
        }
        return 0;
    }

    private void writeMethodHeader(HostedMethod m, CSourceCodeWriter writer, Map<ResolvedJavaMethod, String> symbolAliases, boolean dynamic) {
        assert Modifier.isStatic(m.getModifiers()) : "Published methods that go into the header must be static.";
        CEntryPointData cEntryPointData = (CEntryPointData) m.getWrapped().getEntryPointData();
        String docComment = cEntryPointData.getDocumentation();
        if (docComment != null && !docComment.isEmpty()) {
            writer.appendln("/*");
            Arrays.stream(docComment.split("\n")).forEach(l -> writer.appendln(" * " + l));
            writer.appendln(" */");
        }

        String symbolName = symbolAliases.get(m);
        if (symbolName == null || symbolName.isEmpty()) {
            symbolName = cEntryPointData.getSymbolName();
        }

        if (dynamic) {
            writer.append("typedef ");
        }

        AnnotatedType annotatedReturnType = getAnnotatedReturnType(m);
        writer.append(CSourceCodeWriter.toCTypeName(m,
                        (ResolvedJavaType) m.getSignature().getReturnType(m.getDeclaringClass()),
                        Optional.ofNullable(annotatedReturnType.getAnnotation(CTypedef.class)).map(CTypedef::name),
                        false,
                        annotatedReturnType.isAnnotationPresent(CUnsigned.class),
                        metaAccess, nativeLibs));
        writer.append(" ");

        assert !symbolName.isEmpty();
        if (dynamic) {
            writer.append("(*").append(symbolName).append("_fn_t)");
        } else {
            writer.append(symbolName);
        }
        writer.append("(");

        String sep = "";
        AnnotatedType[] annotatedParameterTypes = getAnnotatedParameterTypes(m);
        Parameter[] parameters = m.getParameters();
        assert parameters != null;
        for (int i = 0; i < m.getSignature().getParameterCount(false); i++) {
            writer.append(sep);
            sep = ", ";
            writer.append(CSourceCodeWriter.toCTypeName(m,
                            (ResolvedJavaType) m.getSignature().getParameterType(i, m.getDeclaringClass()),
                            Optional.ofNullable(annotatedParameterTypes[i].getAnnotation(CTypedef.class)).map(CTypedef::name),
                            annotatedParameterTypes[i].isAnnotationPresent(CConst.class),
                            annotatedParameterTypes[i].isAnnotationPresent(CUnsigned.class),
                            metaAccess, nativeLibs));
            if (parameters[i].isNamePresent()) {
                writer.append(" ");
                writer.append(parameters[i].getName());
            }
        }
        writer.appendln(");");
        writer.appendln();
    }

    /** Workaround for lack of `Method.getAnnotatedReturnType` in the JVMCI API (GR-9241). */
    private AnnotatedType getAnnotatedReturnType(HostedMethod hostedMethod) {
        return getMethod(hostedMethod).getAnnotatedReturnType();
    }

    /** Workaround for lack of `Method.getAnnotatedParameterTypes` in the JVMCI API (GR-9241). */
    private AnnotatedType[] getAnnotatedParameterTypes(HostedMethod hostedMethod) {
        return getMethod(hostedMethod).getAnnotatedParameterTypes();
    }

    private Method getMethod(HostedMethod hostedMethod) {
        AnalysisMethod entryPoint = CEntryPointCallStubSupport.singleton().getMethodForStub(((CEntryPointCallStubMethod) hostedMethod.wrapped.wrapped));
        Method method;
        try {
            method = entryPoint.getDeclaringClass().getJavaClass().getDeclaredMethod(entryPoint.getName(),
                            MethodType.fromMethodDescriptorString(entryPoint.getSignature().toMethodDescriptor(), imageClassLoader).parameterArray());
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere(e);
        }
        return method;
    }

    private boolean shouldWriteHeader(HostedMethod method) {
        Object data = method.getWrapped().getEntryPointData();
        return data instanceof CEntryPointData && ((CEntryPointData) data).getPublishAs() == Publish.SymbolAndHeader;
    }

    private ObjectFile.Symbol defineDataSymbol(String name, Element section, long position) {
        return objectFile.createDefinedSymbol(name, section, position, wordSize, false, true);
    }

    /**
     * Create the image sections for code, constants, and the heap.
     */
    @Override
    @SuppressWarnings("try")
    public void build(DebugContext debug) {
        try (DebugContext.Scope buildScope = debug.scope("NativeBootImage.build")) {

            final CGlobalDataFeature cGlobals = CGlobalDataFeature.singleton();

            final int textSectionSize = codeCache.getCodeCacheSize();
            final int roConstantsSize = codeCache.getAlignedConstantsSize();
            final int cglobalsSize = ConfigurationValues.getObjectLayout().alignUp(cGlobals.getSize());

            if (SubstrateOptions.SpawnIsolates.getValue()) {
                heap.alignRelocatablePartition(objectFile.getPageSize());
            }

            long roSectionSize = roConstantsSize;
            long rwSectionSize = cglobalsSize;
            if (!SubstrateOptions.SpawnIsolates.getValue()) {
                roSectionSize += heap.getReadOnlySectionSize();
                rwSectionSize += heap.getWritableSectionSize();
            }

            // Text section (code)
            final RelocatableBuffer textBuffer = RelocatableBuffer.factory("text", textSectionSize, objectFile.getByteOrder());
            final NativeTextSectionImpl textImpl = NativeTextSectionImpl.factory(textBuffer, objectFile, codeCache);
            final String textSectionName = SectionName.TEXT.getFormatDependentName(objectFile.getFormat());
            textSection = objectFile.newProgbitsSection(textSectionName, objectFile.getPageSize(), false, true, textImpl);

            // Read-only data section
            final RelocatableBuffer roDataBuffer = RelocatableBuffer.factory("roData", roSectionSize, objectFile.getByteOrder());
            final ProgbitsSectionImpl roDataImpl = new BasicProgbitsSectionImpl(roDataBuffer.getBytes());
            final String roDataSectionName = SectionName.RODATA.getFormatDependentName(objectFile.getFormat());
            roDataSection = objectFile.newProgbitsSection(roDataSectionName, objectFile.getPageSize(), false, false, roDataImpl);

            // Read-write data section
            final RelocatableBuffer rwDataBuffer = RelocatableBuffer.factory("rwData", rwSectionSize, objectFile.getByteOrder());
            final ProgbitsSectionImpl rwDataImpl = new BasicProgbitsSectionImpl(rwDataBuffer.getBytes());
            final String rwDataSectionName = SectionName.DATA.getFormatDependentName(objectFile.getFormat());
            rwDataSection = objectFile.newProgbitsSection(rwDataSectionName, objectFile.getPageSize(), true, false, rwDataImpl);

            // Define symbols for the sections.
            objectFile.createDefinedSymbol(textSection.getName(), textSection, 0, 0, false, false);
            objectFile.createDefinedSymbol("__svm_text_end", textSection, textSectionSize, 0, false, true);
            objectFile.createDefinedSymbol(roDataSection.getName(), roDataSection, 0, 0, false, false);
            objectFile.createDefinedSymbol(rwDataSection.getName(), rwDataSection, 0, 0, false, false);

            final long constantsPartitionOffset = 0L;
            final long roConstantsEndOffset = constantsPartitionOffset + roConstantsSize;
            final long rwGlobalsEndOffset = RWDATA_CGLOBALS_PARTITION_OFFSET + ConfigurationValues.getObjectLayout().alignUp(cGlobals.getSize());

            final RelocatableBuffer heapSectionBuffer;
            final ProgbitsSectionImpl heapSectionImpl;
            if (SubstrateOptions.SpawnIsolates.getValue()) {
                boolean writable = !SubstrateOptions.SpawnIsolates.getValue();
                final long heapSize = heap.getReadOnlySectionSize() + heap.getWritableSectionSize();

                heapSectionBuffer = RelocatableBuffer.factory("heap", heapSize, objectFile.getByteOrder());
                heapSectionImpl = new BasicProgbitsSectionImpl(heapSectionBuffer.getBytes());
                final String heapSectionName = SectionName.SVM_HEAP.getFormatDependentName(objectFile.getFormat());
                heapSection = objectFile.newProgbitsSection(heapSectionName, objectFile.getPageSize(), writable, false, heapSectionImpl);
                objectFile.createDefinedSymbol(heapSection.getName(), heapSection, 0, 0, false, false);

                heap.setReadOnlySection(heapSection.getName(), 0);
                long writableSectionOffset = heap.getReadOnlySectionSize();
                heap.setWritableSection(heapSection.getName(), writableSectionOffset);
                defineDataSymbol(Isolates.IMAGE_HEAP_BEGIN_SYMBOL_NAME, heapSection, 0);
                defineDataSymbol(Isolates.IMAGE_HEAP_END_SYMBOL_NAME, heapSection, heapSize);
                defineDataSymbol(Isolates.IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME, heapSection, writableSectionOffset);
                defineDataSymbol(Isolates.IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME, heapSection, writableSectionOffset + heap.getWritableSectionSize());

                final long relocatableOffset = heap.getReadOnlyRelocatablePartitionOffset();
                final long relocatableSize = heap.getReadOnlyRelocatablePartitionSize();
                defineDataSymbol(Isolates.IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME, heapSection, relocatableOffset);
                defineDataSymbol(Isolates.IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME, heapSection, relocatableOffset + relocatableSize);
            } else {
                heapSectionBuffer = null;
                heapSectionImpl = null;
                heap.setReadOnlySection(roDataSection.getName(), roConstantsEndOffset);
                heap.setWritableSection(rwDataSection.getName(), rwGlobalsEndOffset);
            }

            // Write the section contents and record relocations.
            // - The code goes in the text section, by itself.
            textImpl.writeTextSection(debug, textSection, entryPoints);
            // - The constants go at the beginning of the read-only data section.
            codeCache.writeConstants(roDataBuffer);
            // - Non-heap global data goes at the beginning of the read-write data section.
            cGlobals.writeData(rwDataBuffer, (offset, symbolName) -> defineDataSymbol(symbolName, rwDataSection, offset + RWDATA_CGLOBALS_PARTITION_OFFSET));
            defineDataSymbol(CGlobalDataInfo.CGLOBALDATA_BASE_SYMBOL_NAME, rwDataSection, RWDATA_CGLOBALS_PARTITION_OFFSET);

            // - Write the heap, either to its own section, or to the ro and rw data sections.
            if (SubstrateOptions.SpawnIsolates.getValue()) {
                heap.writeHeap(debug, heapSectionBuffer, heapSectionBuffer);

                long firstRelocOffset = heap.getFirstRelocatablePointerOffsetInSection();
                defineDataSymbol(Isolates.IMAGE_HEAP_RELOCATABLE_FIRST_RELOC_POINTER_NAME, heapSection, firstRelocOffset);
                assert castToByteBuffer(heapSectionBuffer).getLong((int) firstRelocOffset) == 0;
            } else {
                assert heapSectionBuffer == null;
                heap.writeHeap(debug, roDataBuffer, rwDataBuffer);
            }

            // Mark the sections with the relocations from the maps.
            // - "null" as the objectMap is because relocations from text are always to constants.
            markRelocationSitesFromMaps(textBuffer, textImpl, heap.objects);
            markRelocationSitesFromMaps(roDataBuffer, roDataImpl, heap.objects);
            markRelocationSitesFromMaps(rwDataBuffer, rwDataImpl, heap.objects);
            if (heapSectionBuffer != null) {
                markRelocationSitesFromMaps(heapSectionBuffer, heapSectionImpl, heap.objects);
            }
        }

        // [Footnote 1]
        //
        // Subject: Re: Do you know why text references can only be to constants?
        // Date: Fri, 09 Jan 2015 12:51:15 -0800
        // From: Christian Wimmer <christian.wimmer@oracle.com>
        // To: Peter B. Kessler <Peter.B.Kessler@Oracle.COM>
        //
        // Code (i.e. the text section) needs to load the address of objects. So
        // the read-only section contains a 8-byte slot with the address of the
        // object that you actually want to load. A RIP-relative move instruction
        // is used to load this 8-byte slot. The relocation for the move ensures
        // the offset of the move is patched. And then a relocation from the
        // read-only section to the actual native image heap ensures the 8-byte slot
        // contains the actual address of the object to be loaded.
        //
        // Therefore, relocations in .text go only to things in .rodata; and
        // relocations in .rodata go to .data in the current implementation
        //
        // It might be possible to have a RIP-relative load-effective-address (LEA)
        // instruction to go directly from .text to .data, eliminating the memory
        // access to load the address of an object. So I agree that allowing
        // relocation from .text only to .rodata is an arbitrary restriction that
        // could prevent future optimizations.
        //
        // -Christian
    }

    /**
     * Covariant return type overrides added by https://bugs.openjdk.java.net/browse/JDK-4774077
     * make the cast below unnecessary as of JDK 11.
     */
    @SuppressWarnings("cast")
    private static ByteBuffer castToByteBuffer(final RelocatableBuffer heapSectionBuffer) {
        return (ByteBuffer) BufferUtil.asBaseBuffer(heapSectionBuffer.getBuffer().asReadOnlyBuffer()).position(0);
    }

    private void markRelocationSitesFromMaps(RelocatableBuffer relocationMap, ProgbitsSectionImpl sectionImpl, Map<Object, NativeImageHeap.ObjectInfo> objectMap) {
        // Create relocation records from a map.
        // TODO: Should this be a visitor to the map entries,
        // TODO: so I don't have to expose the entrySet() method?
        for (Map.Entry<Integer, RelocatableBuffer.Info> entry : relocationMap.entrySet()) {
            final int offset = entry.getKey();
            final RelocatableBuffer.Info info = entry.getValue();

            assert GraalAccess.getOriginalTarget().arch instanceof AArch64 || checkEmbeddedOffset(sectionImpl, offset, info);

            // Figure out what kind of relocation site it is.
            if (info.getTargetObject() instanceof CFunctionPointer) {
                // References to functions are via relocations to the symbol for the function.
                markFunctionRelocationSite(sectionImpl, offset, info);
            } else {
                // A data relocation.
                if (sectionImpl.getElement() == textSection) {
                    // A wrinkle on relocations *from* the text section: they are *always* to
                    // constants (in the "constant partition" of the roDataSection).
                    markDataRelocationSiteFromText(relocationMap, sectionImpl, offset, info, objectMap);
                } else {
                    // Relocations from other sections go to the section containing the target.
                    // Pass along the information about the target.
                    final Object targetObject = info.getTargetObject();
                    final NativeImageHeap.ObjectInfo targetObjectInfo = objectMap.get(targetObject);
                    markDataRelocationSite(sectionImpl, offset, info, targetObjectInfo);
                }
            }
        }
    }

    private static boolean checkEmbeddedOffset(ProgbitsSectionImpl sectionImpl, final int offset, final RelocatableBuffer.Info info) {
        final ByteBuffer dataBuf = ByteBuffer.wrap(sectionImpl.getContent()).order(sectionImpl.getElement().getOwner().getByteOrder());
        if (info.getRelocationSize() == Long.BYTES) {
            long value = dataBuf.getLong(offset);
            assert value == 0 || value == 0xDEADDEADDEADDEADL : String.format("unexpected embedded offset: 0x%x, info: %s", value, info);
        } else if (info.getRelocationSize() == Integer.BYTES) {
            int value = dataBuf.getInt(offset);
            assert value == 0 || value == 0xDEADDEAD : "unexpected embedded offset";
        } else {
            shouldNotReachHere("unsupported relocation size: " + info.getRelocationSize());
        }
        return true;
    }

    private static void markFunctionRelocationSite(final ProgbitsSectionImpl sectionImpl, final int offset, final RelocatableBuffer.Info info) {
        assert info.getTargetObject() instanceof CFunctionPointer : "Wrong type for FunctionPointer relocation: " + info.getTargetObject().toString();
        final int functionPointerRelocationSize = 8;
        assert info.getRelocationSize() == functionPointerRelocationSize : "Function relocation: " + info.getRelocationSize() + " should be " + functionPointerRelocationSize + " bytes.";
        // References to functions are via relocations to the symbol for the function.
        ResolvedJavaMethod method = ((MethodPointer) info.getTargetObject()).getMethod();
        // A reference to a method. Mark the relocation site using the symbol name.
        sectionImpl.markRelocationSite(offset, functionPointerRelocationSize, RelocationKind.DIRECT, localSymbolNameForMethod(method), false, 0L);
    }

    // TODO: These two methods for marking data relocations might have to be merged if text sections
    // TODO: ever have relocations to some where other than constants at the beginning of the
    // TODO: read-only data section.

    // A reference to data. Mark the relocation using the section and addend in the relocation info.
    private static void markDataRelocationSite(final ProgbitsSectionImpl sectionImpl, final int offset, final RelocatableBuffer.Info info, final NativeImageHeap.ObjectInfo targetObjectInfo) {
        // References to objects are via relocations to offsets from the symbol
        // for the section the symbol is in.
        // Use the target object to find the partition and offset, and from the
        // partition the section and the partition offset.
        assert ((info.getRelocationSize() == 4) || (info.getRelocationSize() == 8)) : "Data relocation size should be 4 or 8 bytes.";
        assert targetObjectInfo != null;
        // Gather information about the target object.
        HeapPartition partition = targetObjectInfo.getPartition();
        assert partition != null;
        final String targetSectionName = partition.getSectionName();
        final long targetOffsetInSection = targetObjectInfo.getOffsetInSection();
        final long relocationInfoAddend = info.hasExplicitAddend() ? info.getExplicitAddend().longValue() : 0L;
        final long relocationAddend = targetOffsetInSection + relocationInfoAddend;
        sectionImpl.markRelocationSite(offset, info.getRelocationSize(), info.getRelocationKind(), targetSectionName, false, relocationAddend);
    }

    private void markDataRelocationSiteFromText(RelocatableBuffer buffer, final ProgbitsSectionImpl sectionImpl, final int offset, final Info info, final Map<Object, ObjectInfo> objectMap) {
        assert ConfigurationValues.getTarget().arch instanceof AArch64 ||
                        ((info.getRelocationSize() == 4) || (info.getRelocationSize() == 8)) : "Data relocation size should be 4 or 8 bytes. Got size: " + info.getRelocationSize();
        Object target = info.getTargetObject();
        if (target instanceof DataSectionReference) {
            long addend = ((DataSectionReference) target).getOffset() - info.getExplicitAddend();
            sectionImpl.markRelocationSite(offset, info.getRelocationSize(), info.getRelocationKind(), roDataSection.getName(), false, addend);
        } else if (target instanceof CGlobalDataReference) {
            CGlobalDataReference ref = (CGlobalDataReference) target;
            CGlobalDataInfo dataInfo = ref.getDataInfo();
            CGlobalDataImpl<?> data = dataInfo.getData();
            long addend = RWDATA_CGLOBALS_PARTITION_OFFSET + dataInfo.getOffset() - info.getExplicitAddend();
            sectionImpl.markRelocationSite(offset, info.getRelocationSize(), info.getRelocationKind(), rwDataSection.getName(), false, addend);
            if (dataInfo.isSymbolReference()) { // create relocation for referenced symbol
                if (objectFile.getSymbolTable().getSymbol(data.symbolName) == null) {
                    objectFile.createUndefinedSymbol(data.symbolName, 0, true);
                }
                ProgbitsSectionImpl baseSectionImpl = (ProgbitsSectionImpl) rwDataSection.getImpl();
                int offsetInSection = Math.toIntExact(RWDATA_CGLOBALS_PARTITION_OFFSET + dataInfo.getOffset());
                baseSectionImpl.markRelocationSite(offsetInSection, wordSize, RelocationKind.DIRECT, data.symbolName, false, 0L);
            }
        } else if (target instanceof ConstantReference) {
            // Direct object reference in code that must be patched (not a linker relocation)
            assert info.getRelocationKind() == RelocationKind.DIRECT;
            Object object = SubstrateObjectConstant.asObject(((ConstantReference) target).getConstant());
            long targetOffset = objectMap.get(object).getOffsetInSection();
            int encShift = ImageSingletons.lookup(CompressEncoding.class).getShift();
            long targetValue = targetOffset >>> encShift;
            assert (targetValue << encShift) == targetOffset : "Reference compression shift discards non-zero bits: " + Long.toHexString(targetOffset);
            Architecture arch = GraalAccess.getOriginalTarget().arch;
            if (arch instanceof AMD64) {
                if (info.getRelocationSize() == Long.BYTES) {
                    buffer.getBuffer().putLong(offset, targetValue);
                } else if (info.getRelocationSize() == Integer.BYTES) {
                    buffer.getBuffer().putInt(offset, NumUtil.safeToInt(targetValue));
                } else {
                    new Exception().printStackTrace();
                    shouldNotReachHere("Unsupported object reference size: " + info.getRelocationSize());
                }
            } else if (arch instanceof AArch64) {
                int numInstrs = info.getRelocationSize() / 2;
                long curValue = targetValue;

                for (int i = 0; i < numInstrs; ++i) {
                    int instrValue = (int) (curValue & 0xFFFF);
                    instrValue = instrValue << 5;
                    int prevValue = buffer.getBuffer().getInt(offset + (4 * i));
                    int newValue = (prevValue & (~(0xFFFF << 5))) | instrValue;
                    buffer.getBuffer().putInt(offset + (4 * i), 0xFFFFFFFF & newValue);
                    curValue = curValue >> 16;
                }
            }
        } else {
            throw shouldNotReachHere("Unsupported target object for relocation in text section");
        }
    }

    /**
     * Given a java.lang.reflect.Method, compute the symbol name of its start address (if any) in
     * the image. The symbol name returned is the one that would be used for local references (e.g.
     * for relocation), so is guaranteed to exist if the method is in the image. However, it is not
     * necessarily visible for linking from other objects.
     *
     * @param m a java.lang.reflect.Method
     * @return its symbol name as it would appear in the image (regardless of whether it actually
     *         does)
     */
    public static String localSymbolNameForMethod(java.lang.reflect.Method m) {
        /* We don't mangle local symbols, because they never need be referenced by an assembler. */
        return SubstrateUtil.uniqueShortName(m);
    }

    /**
     * Given a {@link ResolvedJavaMethod}, compute what symbol name of its start address (if any) in
     * the image. The symbol name returned is the one that would be used for local references (e.g.
     * for relocation), so is guaranteed to exist if the method is in the image. However, it is not
     * necessarily visible for linking from other objects.
     *
     * @param sm a SubstrateMethod
     * @return its symbol name as it would appear in the image (regardless of whether it actually
     *         does)
     */
    public static String localSymbolNameForMethod(ResolvedJavaMethod sm) {
        /* We don't mangle local symbols, because they never need be referenced by an assembler. */
        return SubstrateUtil.uniqueShortName(sm);
    }

    /**
     * Given a java.lang.reflect.Method, compute the symbol name of its entry point (if any) in the
     * image. The symbol name returned is one that would be used for external references (e.g. for
     * linking) and for method lookup by signature. If multiple methods with the same signature are
     * present in the image, the returned symbol name is not guaranteed to resolve to the method
     * being passed.
     *
     * @param m a java.lang.reflect.Method
     * @return its symbol name as it would appear in the image (regardless of whether it actually
     *         does)
     */
    public static String globalSymbolNameForMethod(java.lang.reflect.Method m) {
        return mangleName(SubstrateUtil.uniqueShortName(m));
    }

    /**
     * Given a {@link ResolvedJavaMethod}, compute what symbol name of its entry point (if any) in
     * the image. The symbol name returned is one that would be used for external references (e.g.
     * for linking) and for method lookup by signature. If multiple methods with the same signature
     * are present in the image, the returned symbol name is not guaranteed to resolve to the method
     * being passed.
     *
     * @param sm a SubstrateMethod
     * @return its symbol name as it would appear in the image (regardless of whether it actually
     *         does)
     */
    public static String globalSymbolNameForMethod(ResolvedJavaMethod sm) {
        return mangleName(SubstrateUtil.uniqueShortName(sm));
    }

    @Override
    public ObjectFile getOrCreateDebugObjectFile() {
        assert objectFile != null;
        /*
         * FIXME: use ObjectFile.getOrCreateDebugObject, which knows how/whether to split (but is
         * somewhat unimplemented right now, i.e. doesn't actually implement splitting, even on
         * Mach-O where this is customary).
         */
        return objectFile;
    }

    public NativeBootImage(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap, NativeImageCodeCache codeCache,
                    List<HostedMethod> entryPoints, HostedMethod mainEntryPoint, ClassLoader imageClassLoader) {
        super(k, universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, imageClassLoader);

        uniqueEntryPoints.addAll(entryPoints);

        if (NativeImageOptions.MachODebugInfoTesting.getValue()) {
            objectFile = new MachOObjectFile();
        } else {
            objectFile = ObjectFile.getNativeObjectFile();
            if (objectFile == null) {
                throw new Error("Unsupported objectfile format: " + ObjectFile.getNativeFormat());
            }
        }

        if (mainEntryPoint != null) {
            objectFile.setMainEntryPoint(globalSymbolNameForMethod(mainEntryPoint));
        }

        objectFile.setByteOrder(ConfigurationValues.getTarget().arch.getByteOrder());
        int pageSize = NativeImageOptions.PageSize.getValue();
        if (pageSize > 0) {
            objectFile.setPageSize(pageSize);
        }
        wordSize = FrameAccess.wordSize();
        assert objectFile.getWordSizeInBytes() == wordSize;
    }

    private final ObjectFile objectFile;
    private final int wordSize;
    private final Set<HostedMethod> uniqueEntryPoints = new HashSet<>();

    // The sections of the native image.
    private Section textSection;
    private Section roDataSection;
    private Section rwDataSection;
    private Section heapSection;

    public abstract static class NativeTextSectionImpl extends BasicProgbitsSectionImpl {

        public static NativeTextSectionImpl factory(RelocatableBuffer relocatableBuffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
            return codeCache.getTextSectionImpl(relocatableBuffer, objectFile, codeCache);
        }

        private Element getRodataSection() {
            return getElement().getOwner().elementForName(SectionName.RODATA.getFormatDependentName(getElement().getOwner().getFormat()));
        }

        @Override
        public Set<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, getElement());
            LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision ourVaddr = decisions.get(getElement()).getDecision(LayoutDecision.Kind.VADDR);
            LayoutDecision rodataVaddr = decisions.get(getRodataSection()).getDecision(LayoutDecision.Kind.VADDR);
            deps.add(BuildDependency.createOrGet(ourContent, ourVaddr));
            deps.add(BuildDependency.createOrGet(ourContent, rodataVaddr));

            return deps;
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            return getContent();
        }

        protected abstract void defineMethodSymbol(String name, boolean global, Element section, HostedMethod method, CompilationResult result);

        @SuppressWarnings("try")
        protected void writeTextSection(DebugContext debug, final Section textSection, final List<HostedMethod> entryPoints) {
            try (Indent indent = debug.logAndIndent("TextImpl.writeTextSection")) {
                /*
                 * Write the text content. For slightly complicated reasons, we now call
                 * patchMethods in two places -- but it only happens once for any given image build.
                 *
                 * - If we're generating relocatable code, we do it now, and generate relocation
                 * records from the RelocationMap data that we get out. These relocation records
                 * stay in the output file.
                 *
                 * - If we are generating a shared library, we don't need these relocation records,
                 * because once we have fixed vaddrs, we can use rip-relative addressing and we
                 * won't need to do load-time relocation for these references. In this case, we
                 * instead call patchMethods *during* write-out, to fix up these cross-section
                 * PC-relative references.
                 *
                 * This late fix-up of text references is the only reason why we need a custom
                 * implementation for the text section. We can save a lot of load-time relocation by
                 * exploiting PC-relative addressing in this way.
                 */

                /*
                 * Symbols for defined functions: for all methods in our image, define a symbol. In
                 * fact, we define multiple symbols per method.
                 *
                 * 1. the fully-qualified mangled name method name
                 *
                 * 2. the same, but omitting the return type, for the "canonical" method of that
                 * signature (noting that covariant return types cause us to emit multiple methods
                 * with the same signature; we choose the covariant version, i.e. the more
                 * specific).
                 *
                 * 3. the linkage names given by @CEntryPoint
                 */

                final Map<String, HostedMethod> methodsBySignature = new HashMap<>();
                // 1. fq with return type
                for (Map.Entry<HostedMethod, CompilationResult> ent : codeCache.getCompilations().entrySet()) {
                    final String symName = localSymbolNameForMethod(ent.getKey());
                    final String signatureString = SubstrateUtil.uniqueShortName(ent.getKey());
                    final HostedMethod existing = methodsBySignature.get(signatureString);
                    HostedMethod current = ent.getKey();
                    if (existing != null) {
                        /*
                         * We've hit a signature with multiple methods. Choose the "more specific"
                         * of the two methods, i.e. the overriding covariant signature.
                         */
                        final ResolvedJavaType existingReturnType = existing.getSignature().getReturnType(null).resolve(existing.getDeclaringClass());
                        final ResolvedJavaType currentReturnType = current.getSignature().getReturnType(null).resolve(current.getDeclaringClass());
                        if (existingReturnType.isAssignableFrom(currentReturnType)) {
                            /* current is more specific than existing */
                            final HostedMethod replaced = methodsBySignature.put(signatureString, current);
                            assert replaced.equals(existing);
                        }
                    } else {
                        methodsBySignature.put(signatureString, current);
                    }
                    defineMethodSymbol(symName, entryPoints.contains(current), textSection, current, ent.getValue());
                }
                // 2. fq without return type -- only for entry points!
                for (Map.Entry<String, HostedMethod> ent : methodsBySignature.entrySet()) {
                    HostedMethod method = ent.getValue();
                    Object data = method.getWrapped().getEntryPointData();
                    CEntryPointData cEntryData = (data instanceof CEntryPointData) ? (CEntryPointData) data : null;
                    if (cEntryData != null && cEntryData.getPublishAs() == Publish.NotPublished) {
                        continue;
                    }

                    final int entryPointIndex = entryPoints.indexOf(method);
                    if (entryPointIndex != -1) {
                        final String mangledSignature = mangleName(ent.getKey());
                        assert mangledSignature.equals(globalSymbolNameForMethod(method));
                        defineMethodSymbol(mangledSignature, true, textSection, method, null);

                        // 3. Also create @CEntryPoint linkage names in this case
                        if (cEntryData != null) {
                            assert !cEntryData.getSymbolName().isEmpty();
                            // no need for mangling: name must already be a valid external name
                            defineMethodSymbol(cEntryData.getSymbolName(), true, textSection, method, codeCache.getCompilations().get(method));
                        }
                    }
                }

                // Write the text contents.
                // -- what did we embed in the bytes? currently nothing
                // -- to what symbol are we referring? always .rodata + something

                // the map starts out empty...
                assert textBuffer.mapSize() == 0;
                codeCache.patchMethods(debug, textBuffer, objectFile);
                // but now may be populated

                /*
                 * Blat the text-reference-patched (but rodata-reference-unpatched) code cache into
                 * our byte array.
                 */
                codeCache.writeCode(textBuffer);
            }
        }

        protected NativeTextSectionImpl(RelocatableBuffer relocatableBuffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
            // TODO: Do not separate the byte[] from the RelocatableBuffer.
            super(relocatableBuffer.getBytes());
            this.textBuffer = relocatableBuffer;
            this.objectFile = objectFile;
            this.codeCache = codeCache;
        }

        protected final RelocatableBuffer textBuffer;
        protected final ObjectFile objectFile;
        protected final NativeImageCodeCache codeCache;
    }
}
