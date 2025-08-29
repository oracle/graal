/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.word.WordBase;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.ConstantValueEntry;
import com.oracle.objectfile.debugentry.DirEntry;
import com.oracle.objectfile.debugentry.EnumClassEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.ForeignStructTypeEntry;
import com.oracle.objectfile.debugentry.FrameSizeChangeEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.LocalValueEntry;
import com.oracle.objectfile.debugentry.MemberEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PointerToTypeEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.RegisterValueEntry;
import com.oracle.objectfile.debugentry.StackValueEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.CallRange;
import com.oracle.objectfile.debugentry.range.LeafRange;
import com.oracle.objectfile.debugentry.range.PrimaryRange;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UniqueShortNameProvider;
import com.oracle.svm.core.code.CompilationResultFrameTree;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.util.Digest;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A shared base for providing debug info that can be processed by any debug info format specified
 * in {@link ObjectFile}. Implements most of the {@link DebugInfoProvider} interface.
 *
 * <p>
 * Debug Info is provided as debug entries, with each entry containing debug info for a logical
 * unit, i.e. Types, ClassLoaders, Methods, Fields, Compilations as well as the underlying file
 * system. Debug entries model the hosted universe to provide a normalized view on debug info data
 * for all debug info implementations.
 * </p>
 *
 * <ul>
 * The Debug Info primarily consists of the following debug entries:
 * <li>{@link DirEntry}: Represents a parent directory for one or more file entries, based on the
 * debug sources directory. One root directory entry contains the path to the debug sources.</li>
 * <li>{@link FileEntry}: Represents a single source file, which may be used by the debugger to
 * provide source code. During native-image build, the
 * {@code com.oracle.svm.hosted.image.sources.SourceManager} safes all the processed source files
 * and provides the debug sources directory.</li>
 * <li>{@link LoaderEntry}: Represents a {@link ClassLoader}. Built-in class loaders and image
 * classloaders are not stored as loader entries, because they are implicitly inferred for types
 * with no {@code LoaderEntry}.</li>
 * <li>{@link TypeEntry}: Represents a {@link SharedType}. For native image build time debug info
 * there exists one {@code TypeEntry} per type in the hosted universe. Type entries are divided into
 * following categories:
 * <ul>
 * <li>{@link PrimitiveTypeEntry}: Represents a primitive java type or any other word type.</li>
 * <li>{@link HeaderTypeEntry}: A special {@code TypeEntry} that represents the object header
 * information in the native image heap, as sort of a super type to {@link Object}.</li>
 * <li>{@link ArrayTypeEntry}: Represents an array type.</li>
 * <li>{@link ForeignStructTypeEntry}: Represents a structured type that is not a java class, e.g.
 * {@link org.graalvm.nativeimage.c.struct.CStruct CStruct},
 * <li>{@link PointerToTypeEntry}: Represents a pointer type, e.g.
 * {@link org.graalvm.nativeimage.c.struct.CPointerTo CPointerTo}, ... .</li>
 * <li>{@link EnumClassEntry}: Represents an {@link Enum} class.</li>
 * <li>{@link InterfaceClassEntry}: Represents an interface, and stores references to all
 * implementors known at the time of debug info generation.</li>
 * <li>{@link ClassEntry}: Represents any other java class that is not already covered by other type
 * entries (Instance classes).</li>
 * </ul>
 * </li>
 * <li>{@link MethodEntry}: Represents the method declaration of a {@link SharedMethod} and holds a
 * list of all parameters and locals that are used within the method. Initially the list of locals
 * in a {@code MethodEntry} contains locals from the method's {@code LocalVariableTable} and is
 * extended if other locals are found when processing a compilation for the method.</li>
 * <li>{@link CompiledMethodEntry}: Represents a {@link CompilationResult}. Is composed of ranges,
 * i.e. frame states and location information of params and locals (where variables are stored). A
 * {@code CompiledMethodEntry} always has a {@link PrimaryRange} that spans the whole code range of
 * the compilation, which is further composed of:
 * <ul>
 * <li>{@link LeafRange}: A leaf in the {@link CompilationResultFrameTree}.</li>
 * <li>{@link CallRange}: A {@code CallNode} in the {@link CompilationResultFrameTree}. Represents
 * inlined calls and is therefore itself composed of ranges.</li>
 * </ul>
 * </li>
 * </ul>
 */
public abstract class SharedDebugInfoProvider implements DebugInfoProvider {

    protected final RuntimeConfiguration runtimeConfiguration;

    protected final MetaAccessProvider metaAccess;

    protected final DebugContext debug;

    protected final boolean useHeapBase;
    protected final int compressionShift;
    protected final int referenceSize;
    protected final int pointerSize;
    protected final int objectAlignment;
    protected final int reservedHubBitsMask;

    /**
     * The {@code SharedType} for {@link Class}. This is the type that represents a dynamic hub in
     * the native image.
     */
    protected final SharedType hubType;

    /**
     * The {@code SharedType} for {@link WordBase}. This is used to check for foreign types.
     */
    protected final SharedType wordBaseType;

    /**
     * The {@code SharedType} for {@code void}. This is used as fallback for foreign pointer types,
     * if there is no type it points to.
     */
    protected final SharedType voidType;

    /**
     * An index map that holds all unique dir entries used for file entries in the
     * {@link #fileIndex}.
     */
    private final EconomicMap<Path, DirEntry> dirIndex = EconomicMap.create();

    /**
     * An index map that holds all unique file entries used for type entries and method entries in
     * the {@link #typeIndex} and {@link #methodIndex}.
     */
    private final EconomicMap<Path, FileEntry> fileIndex = EconomicMap.create();

    /**
     * An index map that holds all unique loader entries used for type entries in the
     * {@link #typeIndex}.
     */
    private final EconomicMap<String, LoaderEntry> loaderIndex = EconomicMap.create();

    /**
     * An index map that holds all unique type entries except the {@link #headerTypeEntry}.
     * <p>
     * This is a {@code ConcurrentHashMap} instead of a {@code EconomicMap} because it is
     * performance critical.
     */
    private final ConcurrentHashMap<SharedType, TypeEntry> typeIndex = new ConcurrentHashMap<>();

    /**
     * An index map that holds all unique method entries used for type entries compiled method
     * entries in the {@link #typeIndex} and {@link #compiledMethodIndex}.
     */
    private final EconomicMap<SharedMethod, MethodEntry> methodIndex = EconomicMap.create();

    /**
     * An index map that holds all unique compiled method entries.
     */
    private final EconomicMap<CompilationIdentifier, CompiledMethodEntry> compiledMethodIndex = EconomicMap.create();

    /**
     * An index map that holds all unique local entries.
     * <p>
     * This is a {@code ConcurrentHashMap} instead of a {@code EconomicMap} because it is
     * performance critical.
     */
    private final ConcurrentHashMap<LocalEntry, LocalEntry> localEntryIndex = new ConcurrentHashMap<>();

    /**
     * An index map that holds all unique local value entries.
     * <p>
     * This is a {@code ConcurrentHashMap} instead of a {@code EconomicMap} because it is
     * performance critical.
     */
    private final ConcurrentHashMap<LocalValueEntry, LocalValueEntry> localValueEntryIndex = new ConcurrentHashMap<>();

    /**
     * The header type entry which is used as a super class of {@link Object} in the debug info. It
     * describes the object header of an object in the native image.
     */
    private HeaderTypeEntry headerTypeEntry;

    /**
     * A prefix used to label indirect types used to ensure gdb performs oop reference to raw
     * address translation.
     */
    public static final String INDIRECT_PREFIX = "_z_.";

    /**
     * A prefix used for type signature generation with {@link #getTypeSignature} to generate unique
     * type signatures for layout type units.
     */
    public static final String LAYOUT_PREFIX = "_layout_.";

    /**
     * A prefix used for type signature generation with {@link #getTypeSignature} to generate unique
     * type signatures for foreign primitive type units.
     */
    public static final String FOREIGN_PREFIX = "_foreign_.";

    public static final String FOREIGN_METHOD_LIST_TYPE = "Foreign$Method$List";

    static final Path EMPTY_PATH = Paths.get("");

    static final LoaderEntry NULL_LOADER_ENTRY = new LoaderEntry("");

    /**
     * A class entry that holds all compilations for function pointers.
     */
    private final ClassEntry foreignMethodListClassEntry = new ClassEntry(FOREIGN_METHOD_LIST_TYPE, -1, -1, -1, -1, -1, null, null, NULL_LOADER_ENTRY);

    public SharedDebugInfoProvider(DebugContext debug, RuntimeConfiguration runtimeConfiguration, MetaAccessProvider metaAccess) {
        this.runtimeConfiguration = runtimeConfiguration;
        this.metaAccess = metaAccess;

        /*
         * Use a disabled DebugContext if log is disabled here. We need to make sure the log stays
         * disabled, as we use parallel streams if it is disabled
         */
        this.debug = debug.isLogEnabledForMethod() ? debug : DebugContext.disabled(null);

        // Fetch special types that have special use cases.
        // hubType: type of the 'hub' field in the object header.
        // wordBaseType: for checking for foreign types.
        // voidType: fallback type to point to for foreign pointer types
        this.hubType = (SharedType) metaAccess.lookupJavaType(Class.class);
        this.wordBaseType = (SharedType) metaAccess.lookupJavaType(WordBase.class);
        this.voidType = (SharedType) metaAccess.lookupJavaType(void.class);

        // Get some information on heap layout and object/object header layout
        this.useHeapBase = ReferenceAccess.singleton().haveCompressedReferences() && ReferenceAccess.singleton().getCompressEncoding().hasBase();
        this.compressionShift = ReferenceAccess.singleton().getCompressionShift();
        this.pointerSize = ConfigurationValues.getTarget().wordSize;
        this.referenceSize = getObjectLayout().getReferenceSize();
        this.objectAlignment = getObjectLayout().getAlignment();
        this.reservedHubBitsMask = Heap.getHeap().getObjectHeader().getReservedHubBitsMask();
    }

    /**
     * Provides a stream of shared types that are processed in {@link #installDebugInfo}.
     *
     * @return A stream of all {@code SharedType} objects to process
     */
    protected abstract Stream<SharedType> typeInfo();

    /**
     * Provides a stream of shared method and compilation pairs that are processed in
     * {@link #installDebugInfo}.
     *
     * @return A stream of all compilations to process.
     */
    protected abstract Stream<Pair<SharedMethod, CompilationResult>> codeInfo();

    /**
     * Provides a stream of data objects that are processed in {@link #installDebugInfo}.
     *
     * @return A stream of all data objects to process.
     */
    protected abstract Stream<Object> dataInfo();

    protected abstract long getCodeOffset(SharedMethod method);

    public abstract long objectOffset(JavaConstant constant);

    @Override
    public boolean useHeapBase() {
        return useHeapBase;
    }

    @Override
    public boolean isRuntimeCompilation() {
        return false;
    }

    @Override
    public int reservedHubBitsMask() {
        return reservedHubBitsMask;
    }

    @Override
    public int compressionShift() {
        return compressionShift;
    }

    @Override
    public int referenceSize() {
        return referenceSize;
    }

    @Override
    public int pointerSize() {
        return pointerSize;
    }

    @Override
    public int objectAlignment() {
        return objectAlignment;
    }

    /**
     * Collect all type entries in {@link #typeIndex} plus the {@link #headerTypeEntry} sorted by
     * type signature.
     * <p>
     * This method only gets called after all reachable types, fields, and methods are processed and
     * all type entries have been created.
     * <p>
     * This ensures that type entries are ordered when processed for the debug info object file.
     *
     * @return A {@code SortedSet} of all type entries found and registered in
     *         {@link #installDebugInfo}.
     */
    @Override
    public List<TypeEntry> typeEntries() {
        List<TypeEntry> typeEntries = new ArrayList<>();
        /*
         * The header type entry does not have an underlying HostedType, so we cant put it into the
         * type index and have to add it manually.
         */
        typeEntries.add(headerTypeEntry);
        typeEntries.add(foreignMethodListClassEntry);

        for (TypeEntry typeEntry : typeIndex.values()) {
            typeEntries.add(typeEntry);

            // types processed after analysis might be missed otherwise
            if (typeEntry instanceof PointerToTypeEntry pointerToTypeEntry) {
                typeEntries.add(pointerToTypeEntry.getPointerTo());
            } else if (typeEntry instanceof ForeignStructTypeEntry foreignStructTypeEntry && foreignStructTypeEntry.getParent() != null) {
                typeEntries.add(foreignStructTypeEntry.getParent());
            }
        }

        return typeEntries.stream().sorted(Comparator.comparingLong(TypeEntry::getTypeSignature)).toList();
    }

    /**
     * Collect all compiled method entries in {@link #compiledMethodIndex} sorted by address of
     * their primary range and the owner class' type signature.
     *
     * <p>
     * This method only gets called after all compilations are processed and all compiled method
     * entries have been created.
     *
     * <p>
     * This ensures that compiled method entries are ordered when processed for the debug info
     * object file.
     *
     * @return A {@code List} of all compiled method entries found and registered in
     *         {@link #installDebugInfo}.
     */
    @Override
    public List<CompiledMethodEntry> compiledMethodEntries() {
        return StreamSupport.stream(compiledMethodIndex.getValues().spliterator(), false)
                        .sorted(Comparator.comparing(CompiledMethodEntry::primary).thenComparingLong(compiledMethodEntry -> compiledMethodEntry.ownerType().getTypeSignature()))
                        .toList();
    }

    /**
     * This installs debug info into the index maps for all entries in {@link #typeInfo},
     * {@link #codeInfo}, and {@link #dataInfo}. After all debug entries are produced the debug
     * entries are trimmed to save memory. Debug entries are only produced and linked up within this
     * function.
     * <p>
     * If logging with a {@link DebugContext} is enabled, this is done sequential, otherwise in
     * parallel.
     */
    @Override
    @SuppressWarnings("try")
    public final void installDebugInfo() {
        // we can only meaningfully provide logging if debug info is produced sequentially
        Stream<SharedType> typeStream = debug.isLogEnabledForMethod() ? typeInfo() : typeInfo().parallel();
        Stream<Pair<SharedMethod, CompilationResult>> codeStream = debug.isLogEnabledForMethod() ? codeInfo() : codeInfo().parallel();
        Stream<Object> dataStream = debug.isLogEnabledForMethod() ? dataInfo() : dataInfo().parallel();

        try (DebugContext.Scope s = debug.scope("DebugInfoProvider")) {
            // Create and index an empty dir with index 0 for null paths.
            lookupDirEntry(EMPTY_PATH);

            /*
             * Handle types, compilations and data. Code info needs to be handled first as it
             * contains source file infos of compilations which are collected in the class entry.
             */
            codeStream.forEach(pair -> handleCodeInfo(pair.getLeft(), pair.getRight()));
            typeStream.forEach(this::installTypeInfo);
            dataStream.forEach(this::installDataInfo);

            // Create the header type.
            installTypeInfo(null);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        /*
         * After producing debug info, trim all debug entries to save memory. All debug entries are
         * produced at this point and no more debug entries should be added later. It is enough to
         * trim all types as all other debug entries are reachable from them.
         */
        headerTypeEntry.seal();
        foreignMethodListClassEntry.seal();
        typeIndex.values().parallelStream().forEach(TypeEntry::seal);
    }

    /**
     * Installs debug info for any given data info. For AOT debug info generation this is used to
     * log information for objects in the native image heap.
     *
     * @param data The data info to process.
     */
    protected void installDataInfo(@SuppressWarnings("unused") Object data) {
        // by default, we do not use data info for installing debug info
    }

    /**
     * Installs debug info for a given type info. A type info must be a {@code SharedType}.
     *
     * <p>
     * This ensures that the type info is processed and a {@link TypeEntry} is put in the type
     * index.
     *
     * @param type The {@code SharedType} to process.
     */
    private void installTypeInfo(SharedType type) {
        /*
         * Looking up a type will either return the existing type in the index map or create and
         * process a new type entry.
         */
        lookupTypeEntry(type);
    }

    /**
     * Installs debug info for a {@code CompilationResult} and the underlying {@code SharedMethod}.
     *
     * <p>
     * This ensures that the compilation is processed and a {@link MethodEntry} and a
     * {@link CompiledMethodEntry} are put into the method index and compiled method index
     * respectively.
     *
     * <p>
     * A compilation is processed for its frame states from infopoints/sourcemappings. For
     * performance reasons we mostly only use infopoints for processing compilations.
     *
     * @param method The {@code SharedMethod} to process.
     * @param compilation The {@code CompilationResult} to process
     */
    private void handleCodeInfo(SharedMethod method, CompilationResult compilation) {
        // First make sure the underlying MethodEntry exists.
        MethodEntry methodEntry = lookupMethodEntry(method);
        // Then process the compilation.
        lookupCompiledMethodEntry(methodEntry, method, compilation);
    }

    @Fold
    static boolean omitInline() {
        return SubstrateOptions.OmitInlinedMethodDebugLineInfo.getValue();
    }

    @Fold
    static int debugCodeInfoMaxDepth() {
        return SubstrateOptions.DebugCodeInfoMaxDepth.getValue();
    }

    @Fold
    static boolean debugCodeInfoUseSourceMappings() {
        return SubstrateOptions.DebugCodeInfoUseSourceMappings.getValue();
    }

    /**
     * Creates a {@code CompiledMethodEntry} that holds a {@link PrimaryRange} and still needs to be
     * processed in {@link #processCompilationInfo}.
     *
     * @param methodEntry the {@code MethodEntry} of the method
     * @param method the {@code SharedMethod} of the given compilation
     * @param compilation the given {@code CompilationResult}.
     * @return an unprocessed {@code CompiledMethodEntry}.
     */
    protected CompiledMethodEntry createCompilationInfo(MethodEntry methodEntry, SharedMethod method, CompilationResult compilation) {
        int primaryLine = methodEntry.getLine();
        int frameSize = compilation.getTotalFrameSize();
        List<FrameSizeChangeEntry> frameSizeChanges = getFrameSizeChanges(compilation);
        ClassEntry ownerType = methodEntry.getOwnerType();

        // Create a primary range that spans over the compilation.
        // The primary range entry holds the code offset information for all its sub ranges.
        PrimaryRange primaryRange = Range.createPrimary(methodEntry, 0, compilation.getTargetCodeSize(), primaryLine, getCodeOffset(method));
        if (debug.isLogEnabled()) {
            debug.log(DebugContext.INFO_LEVEL, "PrimaryRange %s.%s %s %s:%d [0x%x, 0x%x]", ownerType.getTypeName(), methodEntry.getMethodName(), primaryRange.getFileEntry().getPathName(),
                            primaryRange.getFileName(), primaryLine, primaryRange.getLo(), primaryRange.getHi());
        }

        return new CompiledMethodEntry(primaryRange, frameSizeChanges, frameSize, ownerType);
    }

    /**
     * Processes a {@code CompiledMethodEntry} created in {@link #createCompilationInfo}.
     * 
     * @param methodEntry the {@code MethodEntry} of the method
     * @param method the {@code SharedMethod} of the given compilation
     * @param compilation the given {@code CompilationResult}
     * @param compiledMethodEntry the {@code CompiledMethodEntry} to process
     */
    protected void processCompilationInfo(MethodEntry methodEntry, SharedMethod method, CompilationResult compilation, CompiledMethodEntry compiledMethodEntry) {
        // Mark the method entry for the compilation.
        methodEntry.setInRange();

        // If the compiled method entry was added, we still need to check the frame states
        // for subranges.

        // Can we still provide locals if we have no file name?
        if (methodEntry.getFileName().isEmpty()) {
            return;
        }

        // Restrict the frame state traversal based on options.
        boolean omitInline = omitInline();
        int maxDepth = debugCodeInfoMaxDepth();
        boolean useSourceMappings = debugCodeInfoUseSourceMappings();
        if (omitInline) {
            /* TopLevelVisitor will not go deeper than level 2 */
            maxDepth = 2;
        }

        // The root node is represented by the primary range.
        // A call nodes in the frame tree will be stored as call ranges and leaf nodes as
        // leaf ranges
        final CompilationResultFrameTree.CallNode root = new CompilationResultFrameTree.Builder(debug, compilation.getTargetCodeSize(), maxDepth, useSourceMappings,
                        true)
                        .build(compilation);
        if (root == null) {
            return;
        }
        PrimaryRange primaryRange = compiledMethodEntry.primary();
        int frameSize = compiledMethodEntry.frameSize();
        final List<Range> subRanges = new ArrayList<>();
        // The top level visitor will only traverse the direct children of the primary
        // range. All sub call ranges will be treated as leaf ranges.
        final CompilationResultFrameTree.Visitor visitor = omitInline ? new TopLevelVisitor(subRanges, frameSize, primaryRange) : new MultiLevelVisitor(subRanges, frameSize, primaryRange);
        // arguments passed by visitor to apply are
        // NativeImageDebugLocationInfo caller location info
        // CallNode nodeToEmbed parent call node to convert to entry code leaf
        // NativeImageDebugLocationInfo leaf into which current leaf may be merged
        root.visitChildren(visitor, primaryRange, null, null);
        // try to add a location record for offset zero
        updateInitialLocation(primaryRange, subRanges, compilation, method, methodEntry);

        methodEntry.getOwnerType().addCompiledMethod(compiledMethodEntry);
    }

    /**
     * Installs a compilation info that was not found in {@link #lookupCompiledMethodEntry}.
     * 
     * @param methodEntry the {@code MethodEntry} of the method
     * @param method the {@code SharedMethod} of the given compilation
     * @param compilation the given {@code CompilationResult}
     * @return the fully processed {@code CompiledMethodEntry} for the compilation.
     */
    @SuppressWarnings("try")
    private CompiledMethodEntry installCompilationInfo(MethodEntry methodEntry, SharedMethod method, CompilationResult compilation) {
        try (DebugContext.Scope s = debug.scope("DebugInfoCompilation")) {
            if (debug.isLogEnabled()) {
                debug.log(DebugContext.INFO_LEVEL, "Register compilation %s ", compilation.getName());
            }

            CompiledMethodEntry compiledMethodEntry = createCompilationInfo(methodEntry, method, compilation);
            CompiledMethodEntry oldCompiledMethodEntry = synchronizedPutIfAbsent(compiledMethodIndex, compilation.getCompilationId(), compiledMethodEntry);
            if (oldCompiledMethodEntry == null) {
                // CompiledMethodEntry was added to the index, now we need to process the
                // compilation.
                if (debug.isLogEnabled()) {
                    debug.log(DebugContext.INFO_LEVEL, "Process compilation %s ", compilation.getName());
                }
                processCompilationInfo(methodEntry, method, compilation, compiledMethodEntry);
                return compiledMethodEntry;
            } else {
                // The compilation entry was created in the meantime, so we return the one unique
                // type.
                return oldCompiledMethodEntry;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    /**
     * Collect all frame size changes as list of {@code FrameSizeChangeEntry} for a compilation.
     * 
     * @param compilation the given {@code CompilationResult}
     * @return a list of relevant frame size changes.
     */
    public List<FrameSizeChangeEntry> getFrameSizeChanges(CompilationResult compilation) {
        List<FrameSizeChangeEntry> frameSizeChanges = new ArrayList<>();
        for (CompilationResult.CodeMark mark : compilation.getMarks()) {
            /* We only need to observe stack increment or decrement points. */
            if (mark.id.equals(SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP)) {
                FrameSizeChangeEntry sizeChange = new FrameSizeChangeEntry(mark.pcOffset, FrameSizeChangeType.EXTEND);
                frameSizeChanges.add(sizeChange);
                // } else if (mark.id.equals("PROLOGUE_END")) {
                // can ignore these
                // } else if (mark.id.equals("EPILOGUE_START")) {
                // can ignore these
            } else if (mark.id.equals(SubstrateBackend.SubstrateMarkId.EPILOGUE_INCD_RSP)) {
                FrameSizeChangeEntry sizeChange = new FrameSizeChangeEntry(mark.pcOffset, FrameSizeChangeType.CONTRACT);
                frameSizeChanges.add(sizeChange);
            } else if (mark.id.equals(SubstrateBackend.SubstrateMarkId.EPILOGUE_END) && mark.pcOffset < compilation.getTargetCodeSize()) {
                /* There is code after this return point so notify a stack extend again. */
                FrameSizeChangeEntry sizeChange = new FrameSizeChangeEntry(mark.pcOffset, FrameSizeChangeType.EXTEND);
                frameSizeChanges.add(sizeChange);
            }
        }
        // No more frame size changes will be added.
        return List.copyOf(frameSizeChanges);
    }

    /**
     * Creates a new {@code MethodEntry}.
     * 
     * @param method the {@code SharedMethod} to process
     * @return the corresponding {@code MethodEntry}
     */
    @SuppressWarnings("try")
    private MethodEntry createMethodEntry(SharedMethod method) {
        FileEntry fileEntry = lookupFileEntry(method);

        LineNumberTable lineNumberTable = method.getLineNumberTable();
        int line = lineNumberTable == null ? 0 : lineNumberTable.getLineNumber(0);

        String methodName = getMethodName(method);
        TypeEntry t = lookupTypeEntry((SharedType) method.getDeclaringClass());
        if (!(t instanceof StructureTypeEntry)) {
            // we can only install a foreign function pointer for a structured type
            // use a dummy type to process function pointers
            assert t instanceof PointerToTypeEntry;
            t = foreignMethodListClassEntry;
        }
        StructureTypeEntry ownerType = (StructureTypeEntry) t;
        TypeEntry valueType = lookupTypeEntry((SharedType) method.getSignature().getReturnType(null));
        int modifiers = method.getModifiers();

        /*
         * Check the local variable table for parameters. If the params are not in the table, we
         * create synthetic ones from the method signature.
         */
        List<LocalEntry> params = getParamEntries(method);
        int lastParamSlot = params.isEmpty() ? -1 : params.getLast().slot();

        String symbolName = getSymbolName(method);
        int vTableOffset = getVTableOffset(method);

        boolean isOverride = isOverride(method);
        boolean isDeopt = method.isDeoptTarget();
        boolean isConstructor = method.isConstructor();

        return new MethodEntry(fileEntry, line, methodName, ownerType,
                        valueType, modifiers, params, symbolName, isDeopt, isOverride, isConstructor,
                        vTableOffset, lastParamSlot);
    }

    /**
     * Processes a newly created {@code MethodEntry} created in {@link #createMethodEntry}.
     *
     * @param method the given method
     * @param methodEntry the {@code MethodEntry} to process
     */
    @SuppressWarnings("try")
    private void processMethodEntry(SharedMethod method, MethodEntry methodEntry) {
        methodEntry.getOwnerType().addMethod(methodEntry);
        // look for locals in the methods local variable table
        addLocalEntries(method, methodEntry);
    }

    /**
     * Installs a method info that was not found in {@link #lookupMethodEntry}.
     *
     * @param method the {@code SharedMethod} to process
     * @return the corresponding {@code MethodEntry}
     */
    @SuppressWarnings("try")
    private MethodEntry installMethodEntry(SharedMethod method) {
        try (DebugContext.Scope s = debug.scope("DebugInfoMethod")) {
            if (debug.isLogEnabled()) {
                debug.log(DebugContext.INFO_LEVEL, "Register method %s of class %s", getMethodName(method), method.getDeclaringClass().getName());
            }

            MethodEntry methodEntry = createMethodEntry(method);
            MethodEntry oldMethodEntry = synchronizedPutIfAbsent(methodIndex, method, methodEntry);
            if (oldMethodEntry == null) {
                // The method entry was added to the type index, now we need to process the method.
                if (debug.isLogEnabled()) {
                    debug.log(DebugContext.INFO_LEVEL, "Process method %s of class %s", getMethodName(method), method.getDeclaringClass().getName());
                }
                processMethodEntry(method, methodEntry);
                return methodEntry;
            } else {
                // The method entry was created in the meantime, so we return the one unique type.
                return oldMethodEntry;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    /**
     * Installs a type info that was not found in {@link #lookupTypeEntry}.
     *
     * @param type the {@code SharedType} to process
     * @return a fully processed {@code TypeEntry}
     */
    @SuppressWarnings("try")
    private TypeEntry installTypeEntry(SharedType type) {
        try (DebugContext.Scope s = debug.scope("DebugInfoType")) {
            if (debug.isLogEnabled()) {
                debug.log(DebugContext.INFO_LEVEL, "Register type %s ", type.getName());
            }

            TypeEntry typeEntry = createTypeEntry(type);
            TypeEntry oldTypeEntry = typeIndex.putIfAbsent(type, typeEntry);
            if (oldTypeEntry == null) {
                // TypeEntry was added to the type index, now we need to process the type.
                if (debug.isLogEnabled()) {
                    debug.log(DebugContext.INFO_LEVEL, "Process type %s ", type.getName());
                }
                processTypeEntry(type, typeEntry);
                return typeEntry;
            } else {
                // The type entry was created in the meantime, so we return the one unique type.
                return oldTypeEntry;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    /**
     * Creates a {@code TypeEntry} that needs to be processed with {@link #processTypeEntry}.
     *
     * @param type the {@code SharedType} to process
     * @return an unprocessed {@code TypeEntry}
     */
    protected abstract TypeEntry createTypeEntry(SharedType type);

    /**
     * Processes a {@code TypeEntry} created in {@link #createTypeEntry}.
     *
     * @param type the {@code SharedType} of the type entry
     * @param typeEntry the {@code TypeEntry} to process
     */
    protected abstract void processTypeEntry(SharedType type, TypeEntry typeEntry);

    /**
     * Installs the header type info. This is a made up {@link TypeEntry} that has no underlying
     * {@link SharedType} and represents the {@link ObjectLayout}.
     */
    private void installHeaderTypeEntry() {
        String typeName = "_objhdr";
        long typeSignature = getTypeSignature(typeName);

        // create the header type entry similar to a class entry without a super type
        ObjectLayout ol = getObjectLayout();
        int hubOffset = ol.getHubOffset();
        headerTypeEntry = new HeaderTypeEntry(typeName, ol.getFirstFieldOffset(), typeSignature);
        headerTypeEntry.setHubField(createSyntheticFieldEntry("hub", headerTypeEntry, hubType, hubOffset, ol.getHubSize()));

        if (hubOffset > 0) {
            assert hubOffset == Integer.BYTES || hubOffset == Long.BYTES;
            JavaKind kind = hubOffset == Integer.BYTES ? JavaKind.Int : JavaKind.Long;
            headerTypeEntry.addField(createSyntheticFieldEntry("reserved", headerTypeEntry, (SharedType) metaAccess.lookupJavaType(kind.toJavaClass()), 0, hubOffset));
        }
    }

    /**
     * Create a synthetic field for the debug info to add additional information as fields in the
     * debug info.
     * 
     * @param name name of the field
     * @param ownerType {@code StructureTypeEntry} that owns the field
     * @param type {@code TypeEntry} of the fields type
     * @param offset offset of the field
     * @param size size of the field
     * @return a {@code FieldEntry} that represents the synthetic field
     */
    protected FieldEntry createSyntheticFieldEntry(String name, StructureTypeEntry ownerType, SharedType type, int offset, int size) {
        TypeEntry typeEntry = lookupTypeEntry(type);
        if (debug.isLogEnabled()) {
            debug.log("typename %s adding synthetic (public) field %s type %s size %d at offset 0x%x%n",
                            ownerType.getTypeName(), name, typeEntry.getTypeName(), size, offset);
        }
        return new FieldEntry(null, name, ownerType, typeEntry, size, offset, false, Modifier.PUBLIC);
    }

    /**
     * Create a {@code FieldEntry} for a field of a structured type.
     * 
     * @param fileEntry {@code FileEntry} of the source file, where the field is declared
     * @param name name of the field
     * @param ownerType {@code StructureTypeEntry} that owns the field
     * @param type {@code TypeEntry} of the fields type
     * @param offset offset of the field
     * @param size size of the field
     * @return a {@code FieldEntry} that represents the synthetic field
     */
    protected FieldEntry createFieldEntry(FileEntry fileEntry, String name, StructureTypeEntry ownerType, SharedType type, int offset, int size, boolean isEmbedded, int modifier) {
        TypeEntry typeEntry = lookupTypeEntry(type);
        if (debug.isLogEnabled()) {
            debug.log("typename %s adding %s field %s type %s%s size %d at offset 0x%x%n",
                            ownerType.getTypeName(), MemberEntry.memberModifiers(modifier), name, typeEntry.getTypeName(), (isEmbedded ? "(embedded)" : ""), size, offset);
        }
        return new FieldEntry(fileEntry, name, ownerType, typeEntry, size, offset, isEmbedded, modifier);
    }

    /**
     * Produces a signature for a type name.
     * 
     * @param typeName name of a type
     * @return the signature for the type name
     */
    public static long getTypeSignature(String typeName) {
        return Digest.digestAsUUID(typeName).getLeastSignificantBits();
    }

    /**
     * Produce a method name of a {@code SharedMethod} for the debug info.
     * 
     * @param method method to produce a name for
     * @return method name for the debug info
     */
    protected String getMethodName(SharedMethod method) {
        String name = method.getName();
        // replace <init> (method name of a constructor) with the class name
        if (method.isConstructor()) {
            name = method.format("%h");
            if (name.indexOf('$') >= 0) {
                name = name.substring(name.lastIndexOf('$') + 1);
            }
        }
        return name;
    }

    /**
     * Fetches all locals that are no parameters from a methods {@link LocalVariableTable} and
     * processes them into {@code LocalEntry} objects.
     * 
     * @param method method to fetch locals from
     * @param methodEntry the {@code MethodEntry} to add the locals to
     */
    private void addLocalEntries(SharedMethod method, MethodEntry methodEntry) {
        LineNumberTable lnt = method.getLineNumberTable();
        LocalVariableTable lvt = method.getLocalVariableTable();

        // we do not have any information on local variables
        if (lvt == null) {
            return;
        }

        SharedType ownerType = (SharedType) method.getDeclaringClass();
        for (Local local : lvt.getLocals()) {
            // check if this is a local (slot is after last param slot)
            if (local != null && local.getSlot() > methodEntry.getLastParamSlot()) {
                // we have a local with a known name, type and slot
                String name = local.getName();
                SharedType type = (SharedType) local.getType().resolve(ownerType);
                int slot = local.getSlot();
                int bciStart = local.getStartBCI();
                int line = lnt == null ? 0 : lnt.getLineNumber(bciStart);
                TypeEntry typeEntry = lookupTypeEntry(type);
                methodEntry.getOrAddLocalEntry(lookupLocalEntry(name, typeEntry, slot), line);
            }
        }
    }

    /**
     * Fetches all parameters from a methods {@link Signature} and processes them into
     * {@link LocalEntry} objects. The parameters are sorted by slot number.
     *
     * <p>
     * If the parameter also exists in the methods {@link LocalVariableTable} we fetch the
     * parameters name from there, otherwise a name is generated.
     *
     * @param method method to fetch parameters from
     * @return a {@code Map} of parameters to source lines in the methods signature
     */
    private List<LocalEntry> getParamEntries(SharedMethod method) {
        Signature signature = method.getSignature();
        int parameterCount = signature.getParameterCount(false);
        List<LocalEntry> paramInfos = new ArrayList<>();
        LocalVariableTable lvt = method.getLocalVariableTable();
        int slot = 0;
        SharedType ownerType = (SharedType) method.getDeclaringClass();
        if (!method.isStatic()) {
            JavaKind kind = ownerType.getJavaKind();
            assert kind == JavaKind.Object : "must be an object";
            paramInfos.add(lookupLocalEntry("this", lookupTypeEntry(ownerType), slot));
            slot += kind.getSlotCount();
        }
        for (int i = 0; i < parameterCount; i++) {
            Local local = null;
            if (lvt != null) {
                try {
                    local = lvt.getLocal(slot, 0);
                } catch (IllegalStateException e) {
                    if (debug.isLogEnabled()) {
                        debug.log("Found invalid local variable table from method %s during debug info generation.", method.getName());
                    }
                }
            }
            SharedType paramType = (SharedType) signature.getParameterType(i, null);
            JavaKind kind = paramType.getJavaKind();
            JavaKind storageKind = paramType.getStorageKind();
            String name = local != null ? local.getName() : "__" + storageKind.getJavaName() + i;
            paramInfos.add(lookupLocalEntry(name, lookupTypeEntry(paramType), slot));
            slot += kind.getSlotCount();
        }
        return paramInfos.stream().sorted().toList();
    }

    /**
     * Fetch a methods symbol name from the {@link UniqueShortNameProvider}.
     * 
     * @param method method to get the symbol name for
     * @return symbol name of the method
     */
    public String getSymbolName(SharedMethod method) {
        return UniqueShortNameProvider.singleton().uniqueShortName(null, method.getDeclaringClass(), method.getName(), method.getSignature(), method.isConstructor());
    }

    public boolean isOverride(@SuppressWarnings("unused") SharedMethod method) {
        return false;
    }

    public boolean isVirtual(@SuppressWarnings("unused") SharedMethod method) {
        return false;
    }

    public int getVTableOffset(SharedMethod method) {
        return isVirtual(method) ? method.getVTableIndex() : -1;
    }

    /* Lookup functions for indexed debug info entries. */

    /**
     * Lookup the header type entry and installs it if it does not exist yet.
     * 
     * @return the header type entry
     */
    protected HeaderTypeEntry lookupHeaderTypeEntry() {
        if (headerTypeEntry == null) {
            installHeaderTypeEntry();
        }
        return headerTypeEntry;
    }

    /**
     * Lookup a {@code MethodEntry} for a {@code SharedMethod}. If it does not exist yet, this
     * installs the {@code MethodEntry} and updates the method list of the owner type.
     * 
     * @param method the given {@code SharedMethod}
     * @return the corresponding {@code MethodEntry}
     */
    protected MethodEntry lookupMethodEntry(SharedMethod method) {
        if (method == null) {
            return null;
        }
        MethodEntry methodEntry = synchronizedGet(methodIndex, method);
        if (methodEntry == null) {
            methodEntry = installMethodEntry(method);
        }
        return methodEntry;

    }

    /**
     * Lookup a {@code CompiledMethodEntry} for a {@code CompilationResult}. If the
     * {@code CompiledMethodEntry} does not exist yet, it is installed.
     *
     * @param methodEntry the {@code MethodEntry} of the method param
     * @param method the {@code SharedMethod} of this compilation
     * @param compilation the given {@code CompilationResult}
     * @return the corresponding {@code CompiledMethodEntry}
     */
    protected CompiledMethodEntry lookupCompiledMethodEntry(MethodEntry methodEntry, SharedMethod method, CompilationResult compilation) {
        if (method == null) {
            return null;
        }
        CompiledMethodEntry compiledMethodEntry = synchronizedGet(compiledMethodIndex, compilation.getCompilationId());
        if (compiledMethodEntry == null) {
            compiledMethodEntry = installCompilationInfo(methodEntry, method, compilation);
        }
        return compiledMethodEntry;
    }

    /**
     * Lookup a {@code TypeEntry} for a {@code SharedType}. If the {@code TypeEntry} does not exist
     * yet, it is installed.
     * 
     * @param type the given {@code SharedType}
     * @return the corresponding {@code TypeEntry}
     */
    protected TypeEntry lookupTypeEntry(SharedType type) {
        if (type == null) {
            // this must be the header type entry, as it is the only one with no underlying
            return lookupHeaderTypeEntry();
        }
        TypeEntry typeEntry = typeIndex.get(type);
        if (typeEntry == null) {
            typeEntry = installTypeEntry(type);
        }
        return typeEntry;
    }

    /**
     * Lookup a {@code LoaderEntry} for a {@code SharedType}. Extracts the loader name from the
     * {@code SharedType} and forwards to {@link #lookupLoaderEntry(String)}.
     *
     * @param type the given {@code SharedType}
     * @return the corresponding {@code LoaderEntry}
     */
    protected LoaderEntry lookupLoaderEntry(SharedType type) {
        SharedType targetType = type;
        if (type.isArray()) {
            targetType = (SharedType) type.getElementalType();
        }
        return targetType.getHub().isLoaded() ? lookupLoaderEntry(UniqueShortNameProvider.singleton().uniqueShortLoaderName(targetType.getHub().getClassLoader())) : NULL_LOADER_ENTRY;
    }

    /**
     * Lookup a {@code LoaderEntry} for a string. If the {@code LoaderEntry} does not exist yet, it
     * is added to the {@link #loaderIndex}.
     *
     * @param loaderName the given loader name string
     * @return the corresponding {@code LoaderEntry}
     */
    protected LoaderEntry lookupLoaderEntry(String loaderName) {
        if (loaderName == null || loaderName.isEmpty()) {
            return NULL_LOADER_ENTRY;
        }
        return synchronizedComputeIfAbsent(loaderIndex, loaderName, LoaderEntry::new);
    }

    /**
     * Lookup a {@code FileEntry} for a {@code ResolvedJavaType}.
     *
     * @param type the given {@code ResolvedJavaType}
     * @return the corresponding {@code FileEntry}
     */
    protected FileEntry lookupFileEntry(ResolvedJavaType type) {
        /*
         * Conjure up an appropriate, unique file name to keep tools happy even though we cannot
         * find a corresponding source.
         */
        return lookupFileEntry(fullFilePathFromClassName(type));
    }

    /**
     * Lookup a {@code FileEntry} for the declaring class of a {@code ResolvedJavaMethod}.
     *
     * @param method the given {@code ResolvedJavaMethod}
     * @return the corresponding {@code FileEntry}
     */
    protected FileEntry lookupFileEntry(ResolvedJavaMethod method) {
        return lookupFileEntry(method.getDeclaringClass());
    }

    /**
     * Lookup a {@code FileEntry} for the declaring class of a {@code ResolvedJavaField}.
     *
     * @param field the given {@code ResolvedJavaField}
     * @return the corresponding {@code FileEntry}
     */
    protected FileEntry lookupFileEntry(ResolvedJavaField field) {
        return lookupFileEntry(field.getDeclaringClass());
    }

    /**
     * Lookup a {@code FileEntry} for a file path. First extracts the files directory and the
     * corresponding {@link DirEntry} and adds a new {@code FileEntry} to {@link #fileIndex} if it
     * does not exist yet.
     *
     * @param fullFilePath the given file path
     * @return the corresponding {@code FileEntry}
     */
    protected FileEntry lookupFileEntry(Path fullFilePath) {
        if (fullFilePath == null) {
            return null;
        }
        Path fileName = fullFilePath.getFileName();
        if (fileName == null) {
            return null;
        }

        Path dirPath = fullFilePath.getParent();
        DirEntry dirEntry = lookupDirEntry(dirPath);

        /* Reuse any existing entry if available. */
        FileEntry fileEntry = synchronizedComputeIfAbsent(fileIndex, fullFilePath, path -> new FileEntry(fileName.toString(), dirEntry));
        assert dirPath == null || fileEntry.dirEntry() != null && fileEntry.dirEntry().path().equals(dirPath);
        return fileEntry;
    }

    /**
     * Lookup a {@code DirEntry} for a directory path. Adds a new {@code DirEntry} to
     * {@link #dirIndex} if it does not exist yet. A {@code null} path is represented by
     * {@link #EMPTY_PATH}.
     *
     * @param dirPath the given directory path
     * @return the corresponding {@code FileEntry}
     */
    protected DirEntry lookupDirEntry(Path dirPath) {
        Path path = dirPath == null ? EMPTY_PATH : dirPath;
        return synchronizedComputeIfAbsent(dirIndex, path, DirEntry::new);
    }

    /**
     * Lookup a {@code LocalEntry}.
     *
     * @param localEntry the {@code LocalEntry} to lookup
     * @return the {@code LocalEntry} from the lookup map
     */
    protected LocalEntry lookupLocalEntry(LocalEntry localEntry) {
        return localEntryIndex.computeIfAbsent(localEntry, le -> le);
    }

    /**
     * Lookup a {@code LocalEntry} by name, type, and slot.
     *
     * @param name the name of the local
     * @param typeEntry the type of the local
     * @param slot the slot of the local
     * @return the {@code LocalEntry} from the lookup map
     */
    protected LocalEntry lookupLocalEntry(String name, TypeEntry typeEntry, int slot) {
        return lookupLocalEntry(new LocalEntry(name, typeEntry, slot));
    }

    /**
     * Lookup a {@code LocalValueEntry}. This can either be a register, stack, constant value. This
     * allows to reuse the same entries for multiple ranges.
     * 
     * @param localValueEntry the {@code LocalValueEntry} to lookup
     * @return the {@code LocalValueEntry} from the lookup map
     */
    protected LocalValueEntry lookupLocalValueEntry(LocalValueEntry localValueEntry) {
        return localValueEntryIndex.computeIfAbsent(localValueEntry, lve -> lve);
    }

    /* Other helper functions. */
    protected static ObjectLayout getObjectLayout() {
        return ConfigurationValues.getObjectLayout();
    }

    protected static Path fullFilePathFromClassName(ResolvedJavaType type) {
        String[] elements = type.toJavaName().split("\\.");
        int count = elements.length;
        String name = elements[count - 1];
        while (name.startsWith("$")) {
            name = name.substring(1);
        }
        if (name.contains("$")) {
            name = name.substring(0, name.indexOf('$'));
        }
        if (name.isEmpty()) {
            name = "_nofile_";
        }
        elements[count - 1] = name + ".java";
        return FileSystems.getDefault().getPath("", elements);
    }

    /**
     * Identify a Java type which is being used to model a foreign memory word or pointer type.
     *
     * @param type the type to be tested
     * @param accessingType another type relative to which the first type may need to be resolved
     * @return true if the type models a foreign memory word or pointer type
     */
    protected boolean isForeignWordType(JavaType type, SharedType accessingType) {
        SharedType resolvedJavaType = (SharedType) type.resolve(accessingType);
        return isForeignWordType(resolvedJavaType);
    }

    /**
     * Identify a hosted type which is being used to model a foreign memory word or pointer type.
     *
     * @param type the type to be tested
     * @return true if the type models a foreign memory word or pointer type
     */
    protected boolean isForeignWordType(SharedType type) {
        return wordBaseType.isAssignableFrom(type);
    }

    private static int findMarkOffset(SubstrateBackend.SubstrateMarkId markId, CompilationResult compilation) {
        for (CompilationResult.CodeMark mark : compilation.getMarks()) {
            if (mark.id.equals(markId)) {
                return mark.pcOffset;
            }
        }
        return -1;
    }

    /**
     * If there are any location info records then the first one will be for a nop which follows the
     * stack decrement, stack range check and pushes of arguments into the stack frame.
     *
     * <p>
     * We can construct synthetic location info covering the first instruction based on the method
     * arguments and the calling convention and that will normally be valid right up to the nop. In
     * exceptional cases a call might pass arguments on the stack, in which case the stack decrement
     * will invalidate the original stack locations. Providing location info for that case requires
     * adding two locations, one for initial instruction that does the stack decrement and another
     * for the range up to the nop. They will be essentially the same but the stack locations will
     * be adjusted to account for the different value of the stack pointer.
     * 
     * @param primary the {@code PrimaryRange} of the compilation
     * @param locationInfos the location infos produced from the compilations frame states
     * @param compilation the {@code CompilationResult} of a method
     * @param method the given {@code SharedMethod}
     * @param methodEntry the methods {@code MethodEntry}
     */
    private void updateInitialLocation(PrimaryRange primary, List<Range> locationInfos, CompilationResult compilation, SharedMethod method, MethodEntry methodEntry) {
        int prologueEnd = findMarkOffset(SubstrateBackend.SubstrateMarkId.PROLOGUE_END, compilation);
        if (prologueEnd < 0) {
            // this is not a normal compiled method so give up
            return;
        }
        int stackDecrement = findMarkOffset(SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP, compilation);
        if (stackDecrement < 0) {
            // this is not a normal compiled method so give up
            return;
        }
        if (locationInfos.isEmpty()) {
            // this is not a normal compiled method so give up
            return;
        }

        Range firstLocation = locationInfos.getFirst();
        int firstLocationOffset = firstLocation.getLoOffset();

        if (firstLocationOffset == 0) {
            // this is not a normal compiled method so give up
            return;
        }
        if (firstLocationOffset < prologueEnd) {
            // this is not a normal compiled method so give up
            return;
        }

        // create a synthetic location record including details of passed arguments
        ParamLocationProducer locProducer = new ParamLocationProducer(method);
        if (debug.isLogEnabled()) {
            debug.log(DebugContext.DETAILED_LEVEL, "Add synthetic Location Info : %s (0, %d)", methodEntry.getMethodName(), firstLocationOffset - 1);
        }

        Map<LocalEntry, LocalValueEntry> localInfoList = initSyntheticInfoList(locProducer, methodEntry);
        Range locationInfo = Range.createSubrange(primary, methodEntry, localInfoList, 0, firstLocationOffset, methodEntry.getLine(), primary, true);

        /*
         * If the prologue extends beyond the stack extend and uses the stack then the info needs
         * splitting at the extent point with the stack offsets adjusted in the new info.
         */
        if (locProducer.usesStack() && firstLocationOffset > stackDecrement) {
            Range splitLocationInfo = splitLocationInfo(locationInfo, stackDecrement, compilation.getTotalFrameSize(), PRE_EXTEND_FRAME_SIZE);
            if (debug.isLogEnabled()) {
                debug.log(DebugContext.DETAILED_LEVEL, "Split synthetic Location Info : %s (0, %d) (%d, %d)", methodEntry.getMethodName(),
                                locationInfo.getLoOffset() - 1, locationInfo.getLoOffset(), locationInfo.getHiOffset() - 1);
            }
            locationInfos.addFirst(splitLocationInfo);
        }
        locationInfos.addFirst(locationInfo);
    }

    /**
     * Initiates splitting of an initial location information. Calculates the new local variable
     * information for the higher split and then splits off at the end of the range. The lower part
     * will stay as is except for a lowered high address to make room for the split off location
     * info.
     *
     * @param locationInfo the location info to split
     * @param stackDecrement the offset to split at
     * @param frameSize the frame size after the split
     * @param preExtendFrameSize the frame size before the split
     * @return the higher split, that has been split off the original location info
     */
    public Range splitLocationInfo(Range locationInfo, int stackDecrement, int frameSize, int preExtendFrameSize) {
        // This should be for an initial range extending beyond the stack decrement.
        assert locationInfo.getLoOffset() == 0 && locationInfo.getLoOffset() < stackDecrement && stackDecrement < locationInfo.getHiOffset() : "invalid split request";

        Map<LocalEntry, LocalValueEntry> splitLocalValueInfos = new HashMap<>();

        for (var localInfo : locationInfo.getLocalValueInfos().entrySet()) {
            if (localInfo.getValue() instanceof StackValueEntry stackValue) {
                /*
                 * Need to redefine the value for this param using a stack slot value that allows
                 * for the stack being extended by framesize. however we also need to remove any
                 * adjustment that was made to allow for the difference between the caller SP and
                 * the pre-extend callee SP because of a stacked return address.
                 */
                int adjustment = frameSize - preExtendFrameSize;
                splitLocalValueInfos.put(localInfo.getKey(), lookupLocalValueEntry(new StackValueEntry(stackValue.stackSlot() + adjustment)));
            } else {
                splitLocalValueInfos.put(localInfo.getKey(), localInfo.getValue());
            }
        }

        return locationInfo.split(Map.copyOf(splitLocalValueInfos), stackDecrement);
    }

    /**
     * Creates synthetic location infos for a methods parameters that spans to the first location
     * info from the compilations frame states.
     * 
     * @param locProducer the location info producer for the methods parameters
     * @param methodEntry the given {@code MethodEntry}
     * @return a mapping of {@code LocalEntry} to synthetic location info
     */
    private Map<LocalEntry, LocalValueEntry> initSyntheticInfoList(ParamLocationProducer locProducer, MethodEntry methodEntry) {
        HashMap<LocalEntry, LocalValueEntry> localValueInfos = new HashMap<>();
        // Iterate over all params and create synthetic param info for each
        int paramIdx = 0;
        for (LocalEntry param : methodEntry.getParams()) {
            JavaValue value = locProducer.paramLocation(paramIdx);
            if (debug.isLogEnabled()) {
                debug.log(DebugContext.DETAILED_LEVEL, "local[%d] %s type %s slot %d", paramIdx + 1, param.name(), param.type().getTypeName(), param.slot());
                debug.log(DebugContext.DETAILED_LEVEL, "  =>  %s", value);
            }
            LocalValueEntry localValueEntry = createLocalValueEntry(value, PRE_EXTEND_FRAME_SIZE);
            if (localValueEntry != null) {
                localValueInfos.put(param, localValueEntry);
            }
            paramIdx++;
        }
        return Map.copyOf(localValueInfos);
    }

    /**
     * Size in bytes of the frame at call entry before any stack extend. Essentially this accounts
     * for any automatically pushed return address whose presence depends upon the architecture.
     */
    static final int PRE_EXTEND_FRAME_SIZE = ConfigurationValues.getTarget().arch.getReturnAddressSize();

    /**
     * Retrieve details of the native calling convention for a top level compiled method, including
     * details of which registers or stack slots are used to pass parameters.
     *
     * @param method The method whose calling convention is required.
     * @return The calling convention for the method.
     */
    protected SubstrateCallingConvention getCallingConvention(SharedMethod method) {
        SubstrateCallingConventionKind callingConventionKind = method.getCallingConventionKind();
        ResolvedJavaType declaringClass = method.getDeclaringClass();
        ResolvedJavaType receiverType = method.isStatic() ? null : declaringClass;
        Signature signature = method.getSignature();
        final SubstrateCallingConventionType type;
        if (callingConventionKind.isCustom()) {
            type = method.getCustomCallingConventionType();
        } else {
            type = callingConventionKind.toType(false);
        }
        Backend backend = runtimeConfiguration.lookupBackend(method);
        RegisterConfig registerConfig = backend.getCodeCache().getRegisterConfig();
        assert registerConfig instanceof SubstrateRegisterConfig;
        return (SubstrateCallingConvention) registerConfig.getCallingConvention(type, signature.getReturnType(null), signature.toParameterTypes(receiverType), backend);
    }

    class ParamLocationProducer {
        private final SharedMethod method;
        private final CallingConvention callingConvention;
        private boolean usesStack;

        ParamLocationProducer(SharedMethod method) {
            this.method = method;
            this.callingConvention = getCallingConvention(method);
            // assume no stack slots until we find out otherwise
            this.usesStack = false;
        }

        JavaValue paramLocation(int paramIdx) {
            assert paramIdx < method.getSignature().getParameterCount(!method.isStatic());
            return unpack(callingConvention.getArgument(paramIdx));
        }

        private JavaValue unpack(AllocatableValue value) {
            if (value instanceof RegisterValue registerValue) {
                return registerValue;
            } else {
                // call argument must be a stack slot if it is not a register
                StackSlot stackSlot = (StackSlot) value;
                this.usesStack = true;
                // the calling convention provides offsets from the SP relative to the current
                // frame size. At the point of call the frame may or may not include a return
                // address depending on the architecture.
                return stackSlot;
            }
        }

        public boolean usesStack() {
            return usesStack;
        }
    }

    // indices for arguments passed to SingleLevelVisitor::apply
    protected static final int CALLER_INFO = 0;
    protected static final int PARENT_NODE_TO_EMBED = 1;
    protected static final int LAST_LEAF_INFO = 2;

    private abstract class SingleLevelVisitor implements CompilationResultFrameTree.Visitor {

        protected final List<Range> locationInfos;
        protected final int frameSize;

        protected final PrimaryRange primary;

        SingleLevelVisitor(List<Range> locationInfos, int frameSize, PrimaryRange primary) {
            this.locationInfos = locationInfos;
            this.frameSize = frameSize;
            this.primary = primary;
        }

        @Override
        public void apply(CompilationResultFrameTree.FrameNode node, Object... args) {
            // Visits all nodes at this level and handle call nodes depth first (by default do
            // nothing, just add the call nodes range info).
            if (node instanceof CompilationResultFrameTree.CallNode && skipPos(node.frame)) {
                node.visitChildren(this, args);
            } else {
                CallRange callerInfo = (CallRange) args[CALLER_INFO];
                CompilationResultFrameTree.CallNode nodeToEmbed = (CompilationResultFrameTree.CallNode) args[PARENT_NODE_TO_EMBED];
                handleNodeToEmbed(nodeToEmbed, node, callerInfo, args);
                Range locationInfo = process(node, callerInfo);
                if (node instanceof CompilationResultFrameTree.CallNode callNode) {
                    assert locationInfo instanceof CallRange;
                    locationInfos.add(locationInfo);
                    // erase last leaf (if present) since there is an intervening call range
                    args[LAST_LEAF_INFO] = null;
                    // handle inlined methods in implementors
                    handleCallNode(callNode, (CallRange) locationInfo);
                } else {
                    // last leaf node added at this level is 3rd element of arg vector
                    Range lastLeaf = (Range) args[LAST_LEAF_INFO];
                    if (lastLeaf == null || !lastLeaf.tryMerge(locationInfo)) {
                        // update last leaf and add new leaf to local info list
                        args[LAST_LEAF_INFO] = locationInfo;
                        locationInfos.add(locationInfo);
                    } else if (debug.isLogEnabled()) {
                        debug.log(DebugContext.DETAILED_LEVEL, "Merge leaf Location Info : %s depth %d (%d, %d) into (%d, %d)", lastLeaf.getMethodName(), lastLeaf.getDepth(), lastLeaf.getLoOffset(),
                                        lastLeaf.getHiOffset() - 1, locationInfo.getLoOffset(), locationInfo.getHiOffset() - 1);
                    }
                }
            }
        }

        @SuppressWarnings("unused")
        protected void handleCallNode(CompilationResultFrameTree.CallNode callNode, CallRange locationInfo) {
            // do nothing by default
        }

        @SuppressWarnings("unused")
        protected void handleNodeToEmbed(CompilationResultFrameTree.CallNode nodeToEmbed, CompilationResultFrameTree.FrameNode node,
                        CallRange callerInfo, Object... args) {
            // do nothing by default
        }

        public Range process(CompilationResultFrameTree.FrameNode node, CallRange callerInfo) {
            BytecodePosition pos;
            boolean isLeaf = true;
            if (node instanceof CompilationResultFrameTree.CallNode callNode) {
                // this node represents an inline call range so
                // add a location info to cover the range of the call
                pos = callNode.frame.getCaller();
                while (skipPos(pos)) {
                    pos = pos.getCaller();
                }
                isLeaf = false;
            } else if (isBadLeaf(node, callerInfo)) {
                pos = node.frame.getCaller();
                assert pos != null : "bad leaf must have a caller";
                assert pos.getCaller() == null : "bad leaf caller must be root method";
            } else {
                pos = node.frame;
            }

            SharedMethod method = (SharedMethod) pos.getMethod();
            MethodEntry methodEntry = lookupMethodEntry(method);

            LineNumberTable lineNumberTable = method.getLineNumberTable();
            int line = lineNumberTable == null ? -1 : lineNumberTable.getLineNumber(pos.getBCI());

            Map<LocalEntry, LocalValueEntry> localValueInfos = initLocalInfoList(pos, methodEntry, frameSize);
            Range locationInfo = Range.createSubrange(primary, methodEntry, localValueInfos, node.getStartPos(), node.getEndPos() + 1, line, callerInfo, isLeaf);

            if (debug.isLogEnabled()) {
                debug.log(DebugContext.DETAILED_LEVEL, "Create %s Location Info : %s depth %d (%d, %d)", isLeaf ? "leaf" : "call", method.getName(), locationInfo.getDepth(),
                                locationInfo.getLoOffset(), locationInfo.getHiOffset() - 1);
            }

            return locationInfo;
        }
    }

    /**
     * Generate local info list for a location info from the local values of the current frame.
     * Names and types of local variables are fetched from the methods local variable table. If we
     * cant find the local in the local variable table, we use the frame information.
     * 
     * @param pos the bytecode position of the location info
     * @param methodEntry the {@code MethodEntry} corresponding to the bytecode position
     * @param frameSize the current frame size
     * @return a mapping from {@code LocalEntry} to {@code LocalValueEntry}
     */
    protected Map<LocalEntry, LocalValueEntry> initLocalInfoList(BytecodePosition pos, MethodEntry methodEntry, int frameSize) {
        Map<LocalEntry, LocalValueEntry> localInfos = new HashMap<>();

        if (pos instanceof BytecodeFrame frame && frame.numLocals > 0) {
            /*
             * For each MethodEntry, initially local variables are loaded from the local variable
             * table The local variable table is used here to get some additional information about
             * locals and double-check the expected value kind
             *
             * A local variable that is not yet known to the method, will be added to the method
             * with a synthesized name, the type according to the JavaKind (Object for all
             * classes/array types) and the line of the current bytecode position
             */
            SharedMethod method = (SharedMethod) pos.getMethod();
            LocalVariableTable lvt = method.getLocalVariableTable();
            LineNumberTable lnt = method.getLineNumberTable();
            int line = lnt == null ? 0 : lnt.getLineNumber(pos.getBCI());

            // the owner type to resolve the local types against
            SharedType ownerType = (SharedType) method.getDeclaringClass();

            for (int slot = 0; slot < frame.numLocals; slot++) {
                // Read locals from frame by slot - this might be an Illegal value
                JavaValue value = frame.getLocalValue(slot);
                JavaKind storageKind = frame.getLocalValueKind(slot);

                if (ValueUtil.isIllegalJavaValue(value)) {
                    /*
                     * If we have an illegal value, also the storage kind must be Illegal. We don't
                     * have any value, so we have to continue with the next slot.
                     */
                    assert storageKind == JavaKind.Illegal;
                    continue;
                }

                /*
                 * We might not have a local variable table at all, which means we can only use the
                 * frame local value. Even if there is a local variable table, there might not be a
                 * local at this slot in the local variable table. We also need to check if the
                 * local variable table is malformed.
                 */
                Local local = null;
                if (lvt != null) {
                    try {
                        local = lvt.getLocal(slot, pos.getBCI());
                    } catch (IllegalStateException e) {
                        if (debug.isLogEnabled()) {
                            debug.log("Found invalid local variable table from method %s during debug info generation.", method.getName());
                        }
                    }
                }

                String name;
                SharedType type;
                if (local == null) {
                    if (methodEntry.getLastParamSlot() >= slot) {
                        /*
                         * If we e.g. get an int from the frame values can we really be sure that
                         * this is a param and not just any other local value that happens to be an
                         * int?
                         *
                         * Better just skip inferring params if we have no local in the local
                         * variable table.
                         */
                        continue;
                    }

                    /*
                     * We don't have a corresponding local in the local variable table. Collect some
                     * usable information for this local from the frame local kind.
                     */
                    name = "__" + storageKind.getJavaName() + (methodEntry.isStatic() ? slot : slot - 1);
                    Class<?> clazz = storageKind.isObject() ? Object.class : storageKind.toJavaClass();
                    type = (SharedType) metaAccess.lookupJavaType(clazz);
                } else {
                    /*
                     * Use the information from the local variable table. This allows us to match
                     * the local variables with the ones we already read for the method entry. In
                     * this case the information from the frame local kind is just used to
                     * double-check the type kind from the local variable table.
                     */
                    name = local.getName();
                    type = (SharedType) local.getType().resolve(ownerType);
                }

                TypeEntry typeEntry = lookupTypeEntry(type);
                JavaKind kind = type.getJavaKind();

                if (debug.isLogEnabled()) {
                    debug.log(DebugContext.DETAILED_LEVEL, "local %s type %s slot %d", name, typeEntry.getTypeName(), slot);
                    debug.log(DebugContext.DETAILED_LEVEL, "  =>  %s", value);
                }

                // Double-check the kind from the frame local value with the kind from the local
                // variable table.
                if (storageKind == kind || isIntegralKindPromotion(storageKind, kind) || (isForeignWordType(type, ownerType) && kind == JavaKind.Object && storageKind == JavaKind.Long)) {
                    /*
                     * Lookup a LocalEntry from the MethodEntry. If the LocalEntry was already read
                     * upfront from the local variable table, the LocalEntry already exists.
                     */
                    LocalEntry localEntry = methodEntry.getOrAddLocalEntry(lookupLocalEntry(name, typeEntry, slot), line);
                    LocalValueEntry localValueEntry = createLocalValueEntry(value, frameSize);
                    if (localEntry != null && localValueEntry != null) {
                        localInfos.put(localEntry, localValueEntry);
                    }
                } else if (debug.isLogEnabled()) {
                    debug.log(DebugContext.DETAILED_LEVEL, "  value kind incompatible with var kind %s!", kind);
                }
            }
        }

        return Map.copyOf(localInfos);
    }

    /**
     * Creates a {@code LocalValueEntry} for a given {@code JavaValue}. This processes register
     * values, stack values, primitive constants and constant in the heap.
     * 
     * @param value the given {@code JavaValue}
     * @param frameSize the frame size for stack values
     * @return the {@code LocalValueEntry} or {@code null} if the value can't be processed
     */
    private LocalValueEntry createLocalValueEntry(JavaValue value, int frameSize) {
        switch (value) {
            case RegisterValue registerValue -> {
                return lookupLocalValueEntry(new RegisterValueEntry(registerValue.getRegister().number));
            }
            case StackSlot stackValue -> {
                int stackSlot = frameSize == 0 ? stackValue.getRawOffset() : stackValue.getOffset(frameSize);
                return lookupLocalValueEntry(new StackValueEntry(stackSlot));
            }
            case JavaConstant constantValue -> {
                if (constantValue instanceof PrimitiveConstant || constantValue.isNull()) {
                    return lookupLocalValueEntry(new ConstantValueEntry(-1, constantValue));
                } else {
                    long heapOffset = objectOffset(constantValue);
                    if (heapOffset >= 0) {
                        return lookupLocalValueEntry(new ConstantValueEntry(heapOffset, constantValue));
                    }
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static boolean isIntegralKindPromotion(JavaKind promoted, JavaKind original) {
        return (promoted == JavaKind.Int &&
                        (original == JavaKind.Boolean || original == JavaKind.Byte || original == JavaKind.Short || original == JavaKind.Char));
    }

    // Top level visitor is just a single level visitor that starts at the primary range/root node
    private class TopLevelVisitor extends SingleLevelVisitor {
        TopLevelVisitor(List<Range> locationInfos, int frameSize, PrimaryRange primary) {
            super(locationInfos, frameSize, primary);
        }
    }

    // Multi level visitor starts at the primary range and defines behavior for stepping into call
    // nodes
    public class MultiLevelVisitor extends SingleLevelVisitor {
        MultiLevelVisitor(List<Range> locationInfos, int frameSize, PrimaryRange primary) {
            super(locationInfos, frameSize, primary);
        }

        @Override
        protected void handleCallNode(CompilationResultFrameTree.CallNode callNode, CallRange locationInfo) {
            if (hasChildren(callNode)) {
                /*
                 * A call node may include an initial leaf range for the call that must be embedded
                 * under the newly created location info so pass it as an argument
                 */
                callNode.visitChildren(this, locationInfo, callNode, null);
            } else {
                // We need to embed a leaf node for the whole call range
                locationInfos.add(createEmbeddedParentLocationInfo(primary, callNode, null, locationInfo, frameSize));
            }
        }

        @Override
        protected void handleNodeToEmbed(CompilationResultFrameTree.CallNode nodeToEmbed, CompilationResultFrameTree.FrameNode node, CallRange callerInfo, Object... args) {
            if (nodeToEmbed != null) {
                /*
                 * We only need to insert a range for the caller if it fills a gap at the start of
                 * the caller range before the first child.
                 */
                if (nodeToEmbed.getStartPos() < node.getStartPos()) {
                    /*
                     * Embed a leaf range for the method start that was included in the parent
                     * CallNode Its end range is determined by the start of the first node at this
                     * level.
                     */
                    Range embeddedLocationInfo = createEmbeddedParentLocationInfo(primary, nodeToEmbed, node, callerInfo, frameSize);
                    locationInfos.add(embeddedLocationInfo);
                    // Since this is a leaf node we can merge later leafs into it.
                    args[LAST_LEAF_INFO] = embeddedLocationInfo;
                }
                // Reset args so we only embed the parent node before the first node at this level.
                args[PARENT_NODE_TO_EMBED] = null;
            }
        }
    }

    /**
     * Report whether a call node has any children.
     *
     * @param callNode the node to check
     * @return true if it has any children otherwise false.
     */
    private static boolean hasChildren(CompilationResultFrameTree.CallNode callNode) {
        Object[] result = new Object[]{false};
        callNode.visitChildren((node, args) -> args[0] = true, result);
        return (boolean) result[0];
    }

    /**
     * Create a location info record for the initial range associated with a parent call node whose
     * position and start are defined by that call node and whose end is determined by the first
     * child of the call node.
     *
     * @param parentToEmbed a parent call node which has already been processed to create the caller
     *            location info
     * @param firstChild the first child of the call node
     * @param callerLocation the location info created to represent the range for the call
     * @return a location info to be embedded as the first child range of the caller location.
     */
    private Range createEmbeddedParentLocationInfo(PrimaryRange primary, CompilationResultFrameTree.CallNode parentToEmbed, CompilationResultFrameTree.FrameNode firstChild, CallRange callerLocation,
                    int frameSize) {
        BytecodePosition pos = parentToEmbed.frame;
        int startPos = parentToEmbed.getStartPos();
        int endPos = (firstChild != null ? firstChild.getStartPos() : parentToEmbed.getEndPos() + 1);

        SharedMethod method = (SharedMethod) pos.getMethod();
        MethodEntry methodEntry = lookupMethodEntry(method);

        LineNumberTable lineNumberTable = method.getLineNumberTable();
        int line = lineNumberTable == null ? -1 : lineNumberTable.getLineNumber(pos.getBCI());

        Map<LocalEntry, LocalValueEntry> localValueInfos = initLocalInfoList(pos, methodEntry, frameSize);
        Range locationInfo = Range.createSubrange(primary, methodEntry, localValueInfos, startPos, endPos, line, callerLocation, true);

        if (debug.isLogEnabled()) {
            debug.log(DebugContext.DETAILED_LEVEL, "Embed leaf Location Info : %s depth %d (%d, %d)", locationInfo.getMethodName(), locationInfo.getDepth(), locationInfo.getLoOffset(),
                            locationInfo.getHiOffset() - 1);
        }

        return locationInfo;
    }

    /**
     * Test whether a node is a bad leaf.
     *
     * <p>
     * Sometimes we see a leaf node marked as belonging to an inlined method that sits directly
     * under the root method rather than under a call node. It needs replacing with a location info
     * for the root method that covers the relevant code range.
     *
     * @param node the node to check
     * @param callerLocation the caller location info
     * @return true if the node is a bad leaf otherwise false
     */
    private static boolean isBadLeaf(CompilationResultFrameTree.FrameNode node, CallRange callerLocation) {
        if (callerLocation.isPrimary()) {
            BytecodePosition pos = node.frame;
            BytecodePosition callerPos = pos.getCaller();
            if (callerPos != null && !callerPos.getMethod().equals(pos.getMethod())) {
                return callerPos.getCaller() == null;
            }
        }
        return false;
    }

    /**
     * Test whether a bytecode position represents a bogus frame added by the compiler when a
     * substitution or snippet call is injected.
     *
     * @param pos the position to be tested
     * @return true if the frame is bogus otherwise false
     */
    private static boolean skipPos(BytecodePosition pos) {
        return pos.getBCI() == -1 && pos instanceof NodeSourcePosition sourcePos && sourcePos.isSubstitution();
    }

    /**
     * Perform a synchronized {@code computeIfAbsent} on an {@code EconomicMap}.
     * <p>
     * Compared to a {@code ConcurrentHashMap}, this allows the debug info generator to use a more
     * memory efficient {@code EconomicMap}. Therefore, EconomicHashMaps with synchronization are
     * preferred here over ConcurrentHashMaps to reduce the memory overhead of debug info generation
     * at native image build-time while still having comparable performance (for most of the index
     * maps). The most performance critical maps that see more traffic will use the less memory
     * efficient ConcurrentHashMaps instead.
     * 
     * @param map the {@code EconomicMap} to perform {@code computeIfAbsent} on
     * @param key the key to look for
     * @param mappingFunction the function producing the value for a given key
     * @return the value for the given key in the {@code EconomicMap}
     */
    private static <K, V> V synchronizedComputeIfAbsent(EconomicMap<K, V> map, K key, Function<? super K, ? extends V> mappingFunction) {
        V oldValue;
        V newValue;
        synchronized (map) {
            oldValue = map.get(key);
        }
        if (oldValue == null) {
            newValue = mappingFunction.apply(key);
            synchronized (map) {
                oldValue = map.putIfAbsent(key, newValue);
            }
            if (oldValue == null) {
                return newValue;
            }
        }
        return oldValue;
    }

    /**
     * Wraps {@link EconomicMap#get} in a synchronized context and returns the result.
     * <p>
     * Used for synchronized access to {@code EconomicMaps} for memory efficiency, see
     * {@link #synchronizedComputeIfAbsent}.
     *
     * @param map the {@code EconomicMap} to perform {@code get} on
     * @param key the key to look for
     * @return the result of {@link EconomicMap#get} on the given {@code EconomicMap}
     */
    private static <K, V> V synchronizedGet(EconomicMap<K, V> map, K key) {
        synchronized (map) {
            return map.get(key);
        }
    }

    /**
     * Wraps {@link EconomicMap#putIfAbsent} in a synchronized context and returns the result.
     * <p>
     * Used for synchronized access to {@code EconomicMaps} for memory efficiency, see
     * {@link #synchronizedComputeIfAbsent}.
     *
     * @param map the {@code EconomicMap} to perform {@code putIfAbsent} on
     * @param key the key to look for
     * @param value the value to add if the key is absent
     * @return the result of {@link EconomicMap#putIfAbsent} on the given {@code EconomicMap}
     */
    private static <K, V> V synchronizedPutIfAbsent(EconomicMap<K, V> map, K key, V value) {
        synchronized (map) {
            return map.putIfAbsent(key, value);
        }
    }
}
