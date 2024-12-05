package com.oracle.svm.core.debug;

import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;
import org.graalvm.word.WordBase;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.DirEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.FrameSizeChangeEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.LocalValueEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.StringTable;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.CallRange;
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
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.java.StableMethodNameFormatter;
import jdk.graal.compiler.util.Digest;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
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
import jdk.vm.ci.meta.Value;

/**
 * A shared base for providing debug info that can be processed by any debug info format specified
 * in ObjectFile.
 *
 * <p>
 * Debug Info is provided as Debug Entries, with each entry containing Debug Info for a logical
 * unit, i.e. Types, ClassLoaders, Methods, Fields, Compilations as well as the underlying file
 * system . Debug Entries model the hosted universe to provide a normalized view on debug info data
 * for all debug info implementations.
 * </p>
 *
 * <ul>
 * The Debug Info contains the following Debug Entries up as follows:
 * <li>DirEntry: Represents a parent directory for one or more FileEntries, based on the debug
 * sources directory. One root directory entry contains the path to the debug sources.</li>
 * <li>FileEntry: Represents a single source file, which may be used by the debugger to provide
 * source code. During native-image build, the SourceManager safes all the processed source files
 * and provides the debug sources directory.</li>
 * <li>LoaderEntry: Represents a class loader entry. Built-in class loaders and image classloaders
 * are not stored as LoaderEntries but implicitly inferred for types with no LoaderEntry.</li>
 * <li>TypeEntry: Represents one shared type. For native image build time debug info there exists
 * one TypeEntry per type in the HostedUniverse. TypeEntries are divided into following categories:
 * <ul>
 * <li>PrimitiveTypeEntry: Represents a primitive java type.</li>
 * <li>HeaderTypeEntry: A special TypeEntry that represents the object header information in the
 * native image heap, as sort of a super type to Object.</li>
 * <li>ArrayTypeEntry: Represents an array type.</li>
 * <li>ForeignTypeEntry: Represents a type that is not a java class, e.g. CStruct types, CPointer
 * types, ... .</li>
 * <li>EnumClassEntry: Represents an enumeration class.</li>
 * <li>InterfaceClassEntry: Represents an interface class, and stores references to all
 * implementors.</li>
 * <li>ClassEntry: Represents any other java class that is not already covered by other type entries
 * (Instance classes).</li>
 * </ul>
 * </li>
 * <li>MethodEntry: Represents a method declaration and holds a list of all parameters and locals
 * that are used within the method.</li>
 * <li>CompiledMethodEntry: Represents a compilation. Is composed of ranges, i.e. frame states and
 * location information of params and locals (where variables are stored). A CompiledMethodEntry
 * always has a PrimaryRange that spans the whole compilation, which is further composed of:
 * <ul>
 * <li>LeafRange: A leaf in the compilation tree.</li>
 * <li>CallRange: A CallNode in the compilation tree. Represents inlined calls and is therefore
 * itself composed of ranges.</li>
 * </ul>
 * </li>
 * </ul>
 */
public abstract class SharedDebugInfoProvider implements DebugInfoProvider {

    protected final RuntimeConfiguration runtimeConfiguration;
    protected final MetaAccessProvider metaAccess;
    protected final UniqueShortNameProvider nameProvider;

    protected final DebugContext debug;

    protected final boolean useHeapBase;
    protected final int compressionShift;
    protected final int referenceSize;
    protected final int pointerSize;
    protected final int objectAlignment;
    protected final int reservedBitsMask;

    protected final SharedType hubType;
    protected final SharedType wordBaseType;
    protected final SharedType voidType;

    private final ConcurrentHashMap<Path, DirEntry> dirIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, FileEntry> fileIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LoaderEntry> loaderIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SharedType, TypeEntry> typeIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SharedMethod, MethodEntry> methodIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CompilationIdentifier, CompiledMethodEntry> compiledMethodIndex = new ConcurrentHashMap<>();
    protected final StringTable stringTable = new StringTable();

    /* Ensure we have a null string and in the string section. */
    protected final String uniqueNullStringEntry = stringTable.uniqueDebugString("");
    protected final String cachePathEntry;

    private HeaderTypeEntry headerTypeEntry;

    /*
     * A prefix used to label indirect types used to ensure gdb performs oop reference --> raw
     * address translation
     */
    public static final String INDIRECT_PREFIX = "_z_.";
    /*
     * A prefix used for type signature generation to generate unique type signatures for type
     * layout type units
     */
    public static final String LAYOUT_PREFIX = "_layout_.";

    public static final String CLASS_CONSTANT_SUFFIX = ".class";

    static final Path EMPTY_PATH = Paths.get("");

    public SharedDebugInfoProvider(DebugContext debug, RuntimeConfiguration runtimeConfiguration, MetaAccessProvider metaAccess, UniqueShortNameProvider nameProvider, Path cachePath) {
        this.runtimeConfiguration = runtimeConfiguration;
        this.metaAccess = metaAccess;
        this.nameProvider = nameProvider;
        this.debug = debug.isLogEnabled() ? debug : DebugContext.disabled(null);
        this.cachePathEntry = stringTable.uniqueDebugString(cachePath.toString());
        this.hubType = (SharedType) metaAccess.lookupJavaType(Class.class);
        this.wordBaseType = (SharedType) metaAccess.lookupJavaType(WordBase.class);
        this.voidType = (SharedType) metaAccess.lookupJavaType(Void.class);
        this.pointerSize = ConfigurationValues.getTarget().wordSize;
        ObjectHeader objectHeader = Heap.getHeap().getObjectHeader();
        this.reservedBitsMask = objectHeader.getReservedBitsMask();
        this.useHeapBase = ReferenceAccess.singleton().haveCompressedReferences() && ReferenceAccess.singleton().getCompressEncoding().hasBase();
        this.compressionShift = ReferenceAccess.singleton().getCompressionShift();
        this.referenceSize = getObjectLayout().getReferenceSize();
        this.objectAlignment = getObjectLayout().getAlignment();
    }

    protected abstract Stream<SharedType> typeInfo();

    protected abstract Stream<Pair<SharedMethod, CompilationResult>> codeInfo();

    protected abstract Stream<Object> dataInfo();

    @Override
    public boolean useHeapBase() {
        return useHeapBase;
    }

    @Override
    public boolean isRuntimeCompilation() {
        return false;
    }

    @Override
    public int reservedBitsMask() {
        return reservedBitsMask;
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

    @Override
    public int compiledCodeMax() {
        return 0;
    }

    @Override
    public String cachePath() {
        return cachePathEntry;
    }

    @Override
    public StringTable getStringTable() {
        return stringTable;
    }

    @Override
    public List<TypeEntry> typeEntries() {
        // we will always create the headerTypeEntry, as it will be used as superclass for object
        return Stream.concat(Stream.of(headerTypeEntry), typeIndex.values().stream()).toList();
    }

    @Override
    public List<CompiledMethodEntry> compiledMethodEntries() {
        return compiledMethodIndex.values().stream().toList();
    }

    /* Functions for installing debug info into the index maps. */
    @Override
    public void installDebugInfo() {
        // we can only meaningfully provide logging if debug info is produced sequentially
        Stream<SharedType> typeStream = debug.isLogEnabled() ? typeInfo() : typeInfo().parallel();
        Stream<Pair<SharedMethod, CompilationResult>> codeStream = debug.isLogEnabled() ? codeInfo() : codeInfo().parallel();
        Stream<Object> dataStream = debug.isLogEnabled() ? dataInfo() : dataInfo().parallel();

        try (DebugContext.Scope s = debug.scope("DebugInfoProvider")) {
            // create and index an empty dir with index 0 for null paths.
            lookupDirEntry(EMPTY_PATH);

            // handle types, compilations and data
            // code info needs to be handled first as it contains source file infos of compilations
            // which are collected in the class entry
            codeStream.forEach(pair -> handleCodeInfo(pair.getLeft(), pair.getRight()));
            typeStream.forEach(this::handleTypeInfo);
            dataStream.forEach(this::handleDataInfo);

            // create the header type
            handleTypeInfo(null);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected void handleDataInfo(Object data) {
    }

    private void handleTypeInfo(SharedType type) {
        TypeEntry typeEntry = lookupTypeEntry(type);

        // collect all files and directories referenced by each classEntry
        if (typeEntry instanceof ClassEntry classEntry) {
            classEntry.collectFilesAndDirs();
        }
    }

    private void handleCodeInfo(SharedMethod method, CompilationResult compilation) {
        // First make sure the underlying MethodEntry exists
        MethodEntry methodEntry = lookupMethodEntry(method);
        // Then process the compilation for frame states from infopoints/sourcemappings
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

    protected CompiledMethodEntry installCompilationInfo(MethodEntry methodEntry, SharedMethod method, CompilationResult compilation) {
        try (DebugContext.Scope s = debug.scope("DebugInfoCompilation")) {
            debug.log("test");

            methodEntry.setInRange();

            int primaryLine = methodEntry.getLine();
            int frameSize = compilation.getTotalFrameSize();
            List<FrameSizeChangeEntry> frameSizeChanges = getFrameSizeChanges(compilation);
            ClassEntry ownerType = methodEntry.getOwnerType();

            PrimaryRange primaryRange = Range.createPrimary(methodEntry, 0, compilation.getTargetCodeSize(), primaryLine, getCodeOffset(method));
            debug.log(DebugContext.INFO_LEVEL, "PrimaryRange %s.%s %s %s:%d [0x%x, 0x%x]", ownerType.getTypeName(), methodEntry.getMethodName(), primaryRange.getFileEntry().getPathName(),
                            primaryRange.getFileName(), primaryLine, primaryRange.getLo(), primaryRange.getHi());

            CompiledMethodEntry compiledMethodEntry = new CompiledMethodEntry(primaryRange, frameSizeChanges, frameSize, ownerType);
            if (compiledMethodIndex.putIfAbsent(compilation.getCompilationId(), compiledMethodEntry) == null) {
                // can we still provide locals if we have no file name?
                if (methodEntry.getFileName().isEmpty()) {
                    return compiledMethodEntry;
                }

                boolean omitInline = omitInline();
                int maxDepth = debugCodeInfoMaxDepth();
                boolean useSourceMappings = debugCodeInfoUseSourceMappings();
                if (omitInline) {
                    /* TopLevelVisitor will not go deeper than level 2 */
                    maxDepth = 2;
                }
                final CompilationResultFrameTree.CallNode root = new CompilationResultFrameTree.Builder(debug, compilation.getTargetCodeSize(), maxDepth, useSourceMappings,
                                true)
                                .build(compilation);
                if (root == null) {
                    return compiledMethodEntry;
                }
                final List<Range> subRanges = new ArrayList<>();
                final CompilationResultFrameTree.Visitor visitor = omitInline ? new TopLevelVisitor(subRanges, frameSize, primaryRange) : new MultiLevelVisitor(subRanges, frameSize, primaryRange);
                // arguments passed by visitor to apply are
                // NativeImageDebugLocationInfo caller location info
                // CallNode nodeToEmbed parent call node to convert to entry code leaf
                // NativeImageDebugLocationInfo leaf into which current leaf may be merged
                root.visitChildren(visitor, primaryRange, null, null);
                // try to add a location record for offset zero
                updateInitialLocation(primaryRange, subRanges, compilation, method);

                ownerType.addCompiledMethod(compiledMethodEntry);
            }

            return compiledMethodEntry;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

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
        return frameSizeChanges;
    }

    protected abstract long getCodeOffset(SharedMethod method);

    protected MethodEntry installMethodEntry(SharedMethod method) {
        try (DebugContext.Scope s = debug.scope("DebugInfoMethod")) {
            debug.log("test");
            FileEntry fileEntry = lookupFileEntry(method);

            LineNumberTable lineNumberTable = method.getLineNumberTable();
            int line = lineNumberTable == null ? 0 : lineNumberTable.getLineNumber(0);

            String methodName = getMethodName(method); // stringTable.uniqueDebugString(getMethodName(method));
            StructureTypeEntry ownerType = (StructureTypeEntry) lookupTypeEntry((SharedType) method.getDeclaringClass());
            assert ownerType instanceof ClassEntry;
            TypeEntry valueType = lookupTypeEntry((SharedType) method.getSignature().getReturnType(null));
            int modifiers = getModifiers(method);

            // check the local variable table for parameters
            // if the params are not in the table, we create synthetic ones from the method
            // signature
            List<LocalEntry> paramInfos = getParamEntries(method, line);
            int firstLocalSlot = paramInfos.isEmpty() ? 0 : paramInfos.getLast().slot() + paramInfos.getLast().kind().getSlotCount();
            LocalEntry thisParam = Modifier.isStatic(modifiers) ? null : paramInfos.removeFirst();

            // look for locals in the methods local variable table
            List<LocalEntry> locals = getLocalEntries(method, firstLocalSlot);

            String symbolName = getSymbolName(method); // stringTable.uniqueDebugString(getSymbolName(method));
            int vTableOffset = getVTableOffset(method);

            boolean isDeopt = isDeopt(method, methodName);
            boolean isOverride = isOverride(method);
            boolean isConstructor = isConstructor(method);

            return methodIndex.computeIfAbsent(method, m -> new MethodEntry(fileEntry, line, methodName, ownerType,
                            valueType, modifiers, paramInfos, thisParam, symbolName, isDeopt, isOverride, isConstructor,
                            vTableOffset, firstLocalSlot, locals));
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private TypeEntry installTypeEntry(SharedType type) {
        try (DebugContext.Scope s = debug.scope("DebugInfoType")) {
            debug.log(DebugContext.INFO_LEVEL, "Register type %s ", type.getName());

            TypeEntry typeEntry = createTypeEntry(type);
            if (typeIndex.putIfAbsent(type, typeEntry) == null) {
                /* Add class constant name to string table. */
                if (typeEntry.getClassOffset() != -1) {
                    // stringTable.uniqueDebugString(typeEntry.getTypeName() +
                    // CLASS_CONSTANT_SUFFIX);
                }
                // typeEntry was added to the type index, now we need to process the type
                debug.log(DebugContext.INFO_LEVEL, "Process type %s ", type.getName());
                processTypeEntry(type, typeEntry);
            }
            return typeEntry;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected abstract TypeEntry createTypeEntry(SharedType type);

    protected abstract void processTypeEntry(SharedType type, TypeEntry typeEntry);

    protected void installHeaderTypeEntry() {
        ObjectLayout ol = getObjectLayout();

        int hubOffset = ol.getHubOffset();

        String typeName = "_objhdr"; // stringTable.uniqueDebugString("_objhdr");
        long typeSignature = getTypeSignature(typeName);

        headerTypeEntry = new HeaderTypeEntry(typeName, ol.getFirstFieldOffset(), typeSignature);

        headerTypeEntry.addField(createSyntheticFieldEntry("hub", headerTypeEntry, hubType, hubOffset, referenceSize));
        if (ol.isIdentityHashFieldInObjectHeader()) {
            int idHashSize = ol.sizeInBytes(JavaKind.Int);
            headerTypeEntry.addField(createSyntheticFieldEntry("idHash", headerTypeEntry, (SharedType) metaAccess.lookupJavaType(JavaKind.Int.toJavaClass()), ol.getObjectHeaderIdentityHashOffset(),
                            idHashSize));
        }
    }

    protected FieldEntry createSyntheticFieldEntry(String name, StructureTypeEntry ownerType, SharedType type, int offset, int size) {
        // stringTable.uniqueDebugString(name);
        TypeEntry typeEntry = lookupTypeEntry(type);
        debug.log("typename %s adding synthetic (public) field %s type %s size %d at offset 0x%x%n",
                        ownerType.getTypeName(), name, typeEntry.getTypeName(), size, offset);
        return new FieldEntry(null, name, ownerType, typeEntry, size, offset, false, Modifier.PUBLIC);
    }

    protected FieldEntry createFieldEntry(FileEntry fileEntry, String name, StructureTypeEntry ownerType, SharedType type, int offset, int size, boolean isEmbedded, int modifier) {
        // stringTable.uniqueDebugString(name);
        TypeEntry typeEntry = lookupTypeEntry(type);
        debug.log("typename %s adding %s field %s type %s%s size %d at offset 0x%x%n",
                        ownerType.getTypeName(), ownerType.getModifiersString(modifier), name, typeEntry.getTypeName(), (isEmbedded ? "(embedded)" : ""), size, offset);
        return new FieldEntry(fileEntry, name, ownerType, typeEntry, size, offset, isEmbedded, modifier);
    }

    public long getTypeSignature(String typeName) {
        return Digest.digestAsUUID(typeName).getLeastSignificantBits();
    }

    public String getMethodName(SharedMethod method) {
        String name = method.getName();
        if (name.equals("<init>")) {
            name = method.format("%h");
            if (name.indexOf('$') >= 0) {
                name = name.substring(name.lastIndexOf('$') + 1);
            }
        }
        return name;
    }

    public List<LocalEntry> getLocalEntries(SharedMethod method, int firstLocalSlot) {
        ArrayList<LocalEntry> localEntries = new ArrayList<>();

        LineNumberTable lnt = method.getLineNumberTable();
        LocalVariableTable lvt = method.getLocalVariableTable();

        // we do not have any information on local variables
        if (lvt == null) {
            return localEntries;
        }

        SharedType ownerType = (SharedType) method.getDeclaringClass();
        for (Local local : lvt.getLocals()) {
            // check if this is a local (slot is after last param slot)
            if (local != null && local.getSlot() >= firstLocalSlot) {
                // we have a local with a known name, type and slot
                String name = local.getName(); // stringTable.uniqueDebugString(l.getName());
                SharedType type = (SharedType) local.getType().resolve(ownerType);
                int slot = local.getSlot();
                JavaKind storageKind = type.getStorageKind();
                int bciStart = local.getStartBCI();
                int line = lnt == null ? 0 : lnt.getLineNumber(bciStart);
                TypeEntry typeEntry = lookupTypeEntry(type);

                Optional<LocalEntry> existingEntry = localEntries.stream()
                                .filter(le -> le.slot() == slot && le.type() == typeEntry && le.name().equals(name))
                                .findFirst();

                LocalEntry localEntry = new LocalEntry(name, typeEntry, storageKind, slot, line);
                if (existingEntry.isEmpty()) {
                    localEntries.add(localEntry);
                } else if (existingEntry.get().line() > line) {
                    localEntries.remove(existingEntry.get());
                    localEntries.add(localEntry);
                }
            }
        }

        return localEntries;
    }

    public List<LocalEntry> getParamEntries(SharedMethod method, int line) {
        Signature signature = method.getSignature();
        int parameterCount = signature.getParameterCount(false);
        List<LocalEntry> paramInfos = new ArrayList<>(parameterCount);
        LocalVariableTable table = method.getLocalVariableTable();
        int slot = 0;
        SharedType ownerType = (SharedType) method.getDeclaringClass();
        if (!method.isStatic()) {
            JavaKind kind = ownerType.getJavaKind();
            JavaKind storageKind = ownerType.getStorageKind(); // isForeignWordType(ownerType,
                                                               // ownerType) ? JavaKind.Long : kind;
            assert kind == JavaKind.Object : "must be an object";
            paramInfos.add(new LocalEntry("this", lookupTypeEntry(ownerType), storageKind, slot, line));
            slot += kind.getSlotCount();
        }
        for (int i = 0; i < parameterCount; i++) {
            Local local = table == null ? null : table.getLocal(slot, 0);
            String name = local != null ? local.getName() : "__" + i; // stringTable.uniqueDebugString(local
                                                                      // != null ? local.getName() :
                                                                      // "__" + i);
            SharedType paramType = (SharedType) signature.getParameterType(i, null);
            JavaKind kind = paramType.getJavaKind();
            JavaKind storageKind = paramType.getStorageKind(); // isForeignWordType(paramType,
                                                               // ownerType) ? JavaKind.Long : kind;
            paramInfos.add(new LocalEntry(name, lookupTypeEntry(paramType), storageKind, slot, line));
            slot += kind.getSlotCount();
        }
        return paramInfos;
    }

    public int getModifiers(SharedMethod method) {
        return method.getModifiers();
    }

    public String getSymbolName(SharedMethod method) {
        return nameProvider.uniqueShortName(null, method.getDeclaringClass(), method.getName(), method.getSignature(), method.isConstructor());
    }

    public boolean isDeopt(SharedMethod method, String methodName) {
        if (method instanceof SharedMethod sharedMethod) {
            return sharedMethod.isDeoptTarget();
        }
        return methodName.endsWith(StableMethodNameFormatter.MULTI_METHOD_KEY_SEPARATOR);
    }

    public boolean isOverride(SharedMethod method) {
        return false;
    }

    public boolean isConstructor(SharedMethod method) {
        return method.isConstructor();
    }

    public boolean isVirtual(SharedMethod method) {
        return false;
    }

    public int getVTableOffset(SharedMethod method) {
        return isVirtual(method) ? method.getVTableIndex() : -1;
    }

    /* Lookup functions for indexed debug info entries. */

    public HeaderTypeEntry lookupHeaderTypeEntry() {
        if (headerTypeEntry == null) {
            installHeaderTypeEntry();
        }
        return headerTypeEntry;
    }

    public MethodEntry lookupMethodEntry(SharedMethod method) {
        if (method == null) {
            return null;
        }
        MethodEntry methodEntry = methodIndex.get(method);
        if (methodEntry == null) {
            methodEntry = installMethodEntry(method);
            methodEntry.getOwnerType().addMethod(methodEntry);
        }
        return methodEntry;

    }

    public CompiledMethodEntry lookupCompiledMethodEntry(MethodEntry methodEntry, SharedMethod method, CompilationResult compilation) {
        if (method == null) {
            return null;
        }
        CompiledMethodEntry compiledMethodEntry = compiledMethodIndex.get(compilation.getCompilationId());
        if (compiledMethodEntry == null) {
            compiledMethodEntry = installCompilationInfo(methodEntry, method, compilation);
        }
        return compiledMethodEntry;
    }

    public TypeEntry lookupTypeEntry(SharedType type) {
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

    public LoaderEntry lookupLoaderEntry(SharedType type) {
        if (type.isArray()) {
            type = (SharedType) type.getElementalType();
        }
        return type.getHub().isLoaded() ? lookupLoaderEntry(nameProvider.uniqueShortLoaderName(type.getHub().getClassLoader())) : null;
    }

    public LoaderEntry lookupLoaderEntry(String loaderName) {
        if (loaderName == null || loaderName.isEmpty()) {
            return null;
        }
        return loaderIndex.computeIfAbsent(loaderName, LoaderEntry::new);
    }

    public FileEntry lookupFileEntry(ResolvedJavaType type) {
        // conjure up an appropriate, unique file name to keep tools happy
        // even though we cannot find a corresponding source
        return lookupFileEntry(fullFilePathFromClassName(type));
    }

    public FileEntry lookupFileEntry(ResolvedJavaMethod method) {
        return lookupFileEntry(method.getDeclaringClass());
    }

    public FileEntry lookupFileEntry(ResolvedJavaField field) {
        return lookupFileEntry(field.getDeclaringClass());
    }

    public FileEntry lookupFileEntry(Path fullFilePath) {
        if (fullFilePath == null) {
            return null;
        }

        String fileName = fullFilePath.getFileName().toString(); // stringTable.uniqueDebugString(fullFilePath.getFileName().toString());
        Path dirPath = fullFilePath.getParent();

        DirEntry dirEntry = lookupDirEntry(dirPath);

        /* Reuse any existing entry if available. */
        FileEntry fileEntry = fileIndex.computeIfAbsent(fullFilePath, path -> new FileEntry(fileName, dirEntry));
        assert dirPath == null || fileEntry.dirEntry() != null && fileEntry.dirEntry().path().equals(dirPath);
        return fileEntry;
    }

    public DirEntry lookupDirEntry(Path dirPath) {
        if (dirPath == null) {
            dirPath = EMPTY_PATH;
        }

        // stringTable.uniqueDebugString(dirPath.toString());
        return dirIndex.computeIfAbsent(dirPath, DirEntry::new);
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

    private int findMarkOffset(SubstrateBackend.SubstrateMarkId markId, CompilationResult compilation) {
        for (CompilationResult.CodeMark mark : compilation.getMarks()) {
            if (mark.id.equals(markId)) {
                return mark.pcOffset;
            }
        }
        return -1;
    }

    private void updateInitialLocation(PrimaryRange primary, List<Range> locationInfos, CompilationResult compilation, SharedMethod method) {
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
        // If there are any location info records then the first one will be for
        // a nop which follows the stack decrement, stack range check and pushes
        // of arguments into the stack frame.
        //
        // We can construct synthetic location info covering the first instruction
        // based on the method arguments and the calling convention and that will
        // normally be valid right up to the nop. In exceptional cases a call
        // might pass arguments on the stack, in which case the stack decrement will
        // invalidate the original stack locations. Providing location info for that
        // case requires adding two locations, one for initial instruction that does
        // the stack decrement and another for the range up to the nop. They will
        // be essentially the same but the stack locations will be adjusted to account
        // for the different value of the stack pointer.

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
        debug.log(DebugContext.DETAILED_LEVEL, "Add synthetic Location Info : %s (0, %d)", method.getName(), firstLocationOffset - 1);
        MethodEntry methodEntry = lookupMethodEntry(method);
        Range locationInfo = Range.createSubrange(primary, methodEntry, 0, firstLocationOffset, methodEntry.getLine(), primary, true, true);

        locationInfo.setLocalValueInfo(initSyntheticInfoList(locProducer, methodEntry));

        // if the prologue extends beyond the stack extend and uses the stack then the info
        // needs
        // splitting at the extent point with the stack offsets adjusted in the new info
        if (locProducer.usesStack() && firstLocationOffset > stackDecrement) {
            Range splitLocationInfo = locationInfo.split(stackDecrement, compilation.getTotalFrameSize(), PRE_EXTEND_FRAME_SIZE);
            debug.log(DebugContext.DETAILED_LEVEL, "Split synthetic Location Info : %s (0, %d) (%d, %d)", method.getName(),
                            locationInfo.getLoOffset() - 1, locationInfo.getLoOffset(), locationInfo.getHiOffset() - 1);
            locationInfos.addFirst(splitLocationInfo);
        }
        locationInfos.addFirst(locationInfo);
    }

    private List<LocalValueEntry> initSyntheticInfoList(ParamLocationProducer locProducer, MethodEntry methodEntry) {
        ArrayList<LocalValueEntry> localValueInfos = new ArrayList<>();
        // Create synthetic this param info
        if (methodEntry.getThisParam() != null) {
            JavaValue value = locProducer.thisLocation();
            LocalEntry thisParam = methodEntry.getThisParam();
            debug.log(DebugContext.DETAILED_LEVEL, "local[0] %s type %s slot %d", thisParam.name(), thisParam.type().getTypeName(), thisParam.slot());
            debug.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, thisParam.kind());
            localValueInfos.add(createLocalValueEntry(value, PRE_EXTEND_FRAME_SIZE, thisParam));
        }
        // Iterate over all params and create synthetic param info for each
        int paramIdx = 0;
        for (LocalEntry param : methodEntry.getParams()) {
            JavaValue value = locProducer.paramLocation(paramIdx);
            debug.log(DebugContext.DETAILED_LEVEL, "local[%d] %s type %s slot %d", paramIdx + 1, param.name(), param.type().getTypeName(), param.slot());
            debug.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, param.kind());
            localValueInfos.add(createLocalValueEntry(value, PRE_EXTEND_FRAME_SIZE, param));
            paramIdx++;
        }
        return localValueInfos;
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

        JavaValue thisLocation() {
            assert !method.isStatic();
            return unpack(callingConvention.getArgument(0));
        }

        JavaValue paramLocation(int paramIdx) {
            assert paramIdx < method.getSignature().getParameterCount(false);
            int idx = paramIdx;
            if (!method.isStatic()) {
                idx++;
            }
            return unpack(callingConvention.getArgument(idx));
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
            // Visits all nodes at this level and handles call nodes depth first (by default do
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
                    } else {
                        debug.log(DebugContext.DETAILED_LEVEL, "Merge leaf Location Info : %s depth %d (%d, %d) into (%d, %d)", lastLeaf.getMethodName(), lastLeaf.getDepth(), lastLeaf.getLoOffset(),
                                        lastLeaf.getHiOffset() - 1, locationInfo.getLoOffset(), locationInfo.getHiOffset() - 1);
                    }
                }
            }
        }

        protected void handleCallNode(CompilationResultFrameTree.CallNode callNode, CallRange locationInfo) {
            // do nothing by default
        }

        protected void handleNodeToEmbed(CompilationResultFrameTree.CallNode nodeToEmbed, CompilationResultFrameTree.FrameNode node, CallRange callerInfo, Object... args) {
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

            Range locationInfo = Range.createSubrange(primary, methodEntry, node.getStartPos(), node.getEndPos() + 1, line, callerInfo, isLeaf);

            debug.log(DebugContext.DETAILED_LEVEL, "Create %s Location Info : %s depth %d (%d, %d)", isLeaf ? "leaf" : "call", method.getName(), locationInfo.getDepth(), locationInfo.getLoOffset(),
                            locationInfo.getHiOffset() - 1);

            locationInfo.setLocalValueInfo(initLocalInfoList(pos, methodEntry, frameSize));

            return locationInfo;
        }
    }

    protected List<LocalValueEntry> initLocalInfoList(BytecodePosition pos, MethodEntry method, int frameSize) {
        List<LocalValueEntry> localInfos = new ArrayList<>();

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
            LocalVariableTable lvt = pos.getMethod().getLocalVariableTable();
            LineNumberTable lnt = pos.getMethod().getLineNumberTable();
            int line = lnt == null ? 0 : lnt.getLineNumber(pos.getBCI());

            // the owner type to resolve the local types against
            SharedType ownerType = (SharedType) pos.getMethod().getDeclaringClass();

            for (int slot = 0; slot < frame.numLocals; slot++) {
                // Read locals from frame by slot - this might be an Illegal value
                JavaValue value = frame.getLocalValue(slot);
                JavaKind storageKind = frame.getLocalValueKind(slot);

                if (value == Value.ILLEGAL) {
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
                 * local at this slot in the local variable table.
                 */
                Local local = lvt == null ? null : lvt.getLocal(slot, pos.getBCI());

                String name;
                SharedType type;
                if (local == null) {
                    /*
                     * We don't have a corresponding local in the local variable table. Collect some
                     * usable information for this local from the frame local kind.
                     */
                    name = storageKind.getTypeChar() + "_" + slot;
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

                debug.log(DebugContext.DETAILED_LEVEL, "local %s type %s slot %d", name, typeEntry.getTypeName(), slot);
                debug.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, storageKind);

                // Double-check the kind from the frame local value with the kind from the local
                // variable table.
                if (storageKind == kind || isIntegralKindPromotion(storageKind, kind) || (isForeignWordType(type, ownerType) && kind == JavaKind.Object && storageKind == JavaKind.Long)) {
                    /*
                     * Lookup a LocalEntry from the MethodEntry. If the LocalEntry was already read
                     * upfront from the local variable table, the LocalEntry already exists.
                     */
                    LocalEntry localEntry = method.lookupLocalEntry(name, slot, typeEntry, kind, line);
                    localInfos.add(createLocalValueEntry(value, frameSize, localEntry));
                } else {
                    debug.log(DebugContext.DETAILED_LEVEL, "  value kind incompatible with var kind %s!", kind);
                }

                // No need to check the next slot if the current value needs two slots (next slot
                // contains an illegal value which is skipped anyway).
                if (storageKind.needsTwoSlots()) {
                    slot++;
                }
            }
        }

        return localInfos;
    }

    private LocalValueEntry createLocalValueEntry(JavaValue value, int frameSize, LocalEntry local) {
        LocalValueKind localKind;
        int regIndex = -1;
        int stackSlot = -1;
        long heapOffset = -1;
        JavaConstant constant = null;

        switch (value) {
            case RegisterValue registerValue -> {
                localKind = LocalValueKind.REGISTER;
                regIndex = registerValue.getRegister().number;
            }
            case StackSlot stackValue -> {
                localKind = LocalValueKind.STACK;
                stackSlot = frameSize == 0 ? stackValue.getRawOffset() : stackValue.getOffset(frameSize);
            }
            case JavaConstant constantValue -> {
                if (constantValue instanceof PrimitiveConstant || constantValue.isNull()) {
                    localKind = LocalValueKind.CONSTANT;
                    constant = constantValue;
                } else {
                    heapOffset = objectOffset(constantValue);
                    if (heapOffset >= 0) {
                        localKind = LocalValueKind.CONSTANT;
                        constant = constantValue;
                    } else {
                        localKind = LocalValueKind.UNDEFINED;
                    }
                }
            }
            case null, default -> localKind = LocalValueKind.UNDEFINED;
        }

        return new LocalValueEntry(regIndex, stackSlot, heapOffset, constant, localKind, local);
    }

    public abstract long objectOffset(JavaConstant constant);

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
                // a call node may include an initial leaf range for the call that must
                // be
                // embedded under the newly created location info so pass it as an
                // argument
                callNode.visitChildren(this, locationInfo, callNode, null);
            } else {
                // we need to embed a leaf node for the whole call range
                locationInfos.add(createEmbeddedParentLocationInfo(primary, callNode, null, locationInfo, frameSize));
            }
        }

        @Override
        protected void handleNodeToEmbed(CompilationResultFrameTree.CallNode nodeToEmbed, CompilationResultFrameTree.FrameNode node, CallRange callerInfo, Object... args) {
            if (nodeToEmbed != null) {
                // we only need to insert a range for the caller if it fills a gap
                // at the start of the caller range before the first child
                if (nodeToEmbed.getStartPos() < node.getStartPos()) {
                    // embed a leaf range for the method start that was included in the
                    // parent CallNode
                    // its end range is determined by the start of the first node at this
                    // level
                    Range embeddedLocationInfo = createEmbeddedParentLocationInfo(primary, nodeToEmbed, node, callerInfo, frameSize);
                    locationInfos.add(embeddedLocationInfo);
                    // since this is a leaf node we can merge later leafs into it
                    args[LAST_LEAF_INFO] = embeddedLocationInfo;
                }
                // reset args so we only embed the parent node before the first node at
                // this level
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

        Range locationInfo = Range.createSubrange(primary, methodEntry, startPos, endPos, line, callerLocation, true);

        debug.log(DebugContext.DETAILED_LEVEL, "Embed leaf Location Info : %s depth %d (%d, %d)", locationInfo.getMethodName(), locationInfo.getDepth(), locationInfo.getLoOffset(),
                        locationInfo.getHiOffset() - 1);

        locationInfo.setLocalValueInfo(initLocalInfoList(pos, methodEntry, frameSize));

        return locationInfo;
    }

    private static boolean isBadLeaf(CompilationResultFrameTree.FrameNode node, CallRange callerLocation) {
        // Sometimes we see a leaf node marked as belonging to an inlined method
        // that sits directly under the root method rather than under a call node.
        // It needs replacing with a location info for the root method that covers
        // the relevant code range.
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
}
