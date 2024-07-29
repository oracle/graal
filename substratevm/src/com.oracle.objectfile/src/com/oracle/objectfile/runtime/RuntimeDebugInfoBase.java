package com.oracle.objectfile.runtime;

import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugCodeInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugFileInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocationInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugMethodInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import com.oracle.objectfile.runtime.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.runtime.debugentry.DirEntry;
import com.oracle.objectfile.runtime.debugentry.FileEntry;
import com.oracle.objectfile.runtime.debugentry.MethodEntry;
import com.oracle.objectfile.runtime.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.runtime.debugentry.StringTable;
import com.oracle.objectfile.runtime.debugentry.TypeEntry;
import com.oracle.objectfile.runtime.debugentry.TypedefEntry;
import com.oracle.objectfile.runtime.debugentry.range.PrimaryRange;
import com.oracle.objectfile.runtime.debugentry.range.Range;
import com.oracle.objectfile.runtime.debugentry.range.SubRange;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.nio.ByteOrder;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RuntimeDebugInfoBase {

    protected ByteOrder byteOrder;
    /**
     * A table listing all known strings, some of which may be marked for insertion into the
     * debug_str section.
     */
    private final StringTable stringTable = new StringTable();
    /**
     * List of dirs in which files are found to reside.
     */
    private final List<DirEntry> dirs = new ArrayList<>();
    /**
     * Index of all dirs in which files are found to reside either as part of substrate/compiler or
     * user code.
     */
    private final HashMap<Path, DirEntry> dirsIndex = new HashMap<>();

    /**
     * List of all types present in the native image including instance classes, array classes,
     * primitive types and the one-off Java header struct.
     */
    private final List<TypeEntry> types = new ArrayList<>();
    /**
     * Index of already seen types keyed by the unique, associated, identifying ResolvedJavaType or,
     * in the single special case of the TypeEntry for the Java header structure, by key null.
     */
    private final HashMap<ResolvedJavaType, TypeEntry> typesIndex = new HashMap<>();
    /**
     * List of all types present in the native image including instance classes, array classes,
     * primitive types and the one-off Java header struct.
     */
    private final List<TypedefEntry> typedefs = new ArrayList<>();
    /**
     * Index of already seen types keyed by the unique, associated, identifying ResolvedJavaType or,
     * in the single special case of the TypeEntry for the Java header structure, by key null.
     */
    private final HashMap<ResolvedJavaType, TypedefEntry> typedefsIndex = new HashMap<>();
    /**
     * Handle on runtime compiled method found in debug info.
     */
    private CompiledMethodEntry compiledMethod;
    /**
     * List of files which contain primary or secondary ranges.
     */
    private final List<FileEntry> files = new ArrayList<>();
    /**
     * Index of files which contain primary or secondary ranges keyed by path.
     */
    private final HashMap<Path, FileEntry> filesIndex = new HashMap<>();

    /**
     * Flag set to true if heap references are stored as addresses relative to a heap base register
     * otherwise false.
     */
    private boolean useHeapBase;

    private String cuName;
    /**
     * Number of bits oops are left shifted by when using compressed oops.
     */
    private int oopCompressShift;
    /**
     * Number of low order bits used for tagging oops.
     */
    private int oopTagsCount;
    /**
     * Number of bytes used to store an oop reference.
     */
    private int oopReferenceSize;
    /**
     * Number of bytes used to store a raw pointer.
     */
    private int pointerSize;
    /**
     * Alignment of object memory area (and, therefore, of any oop) in bytes.
     */
    private int oopAlignment;
    /**
     * Number of bits in oop which are guaranteed 0 by virtue of alignment.
     */
    private int oopAlignShift;
    /**
     * The compilation directory in which to look for source files as a {@link String}.
     */
    private String cachePath;

    /**
     * The offset of the first byte beyond the end of the Java compiled code address range.
     */
    private int compiledCodeMax;

    @SuppressWarnings("this-escape")
    public RuntimeDebugInfoBase(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        this.useHeapBase = true;
        this.oopTagsCount = 0;
        this.oopCompressShift = 0;
        this.oopReferenceSize = 0;
        this.pointerSize = 0;
        this.oopAlignment = 0;
        this.oopAlignShift = 0;
        this.compiledCodeMax = 0;
        // create and index an empty dir with index 0.
        ensureDirEntry(EMPTY_PATH);
    }

    public int compiledCodeMax() {
        return compiledCodeMax;
    }

    /**
     * Entry point allowing ELFObjectFile to pass on information about types, code and heap data.
     *
     * @param debugInfoProvider provider instance passed by ObjectFile client.
     */
    @SuppressWarnings("try")
    public void installDebugInfo(RuntimeDebugInfoProvider debugInfoProvider) {
        /*
         * Track whether we need to use a heap base register.
         */
        useHeapBase = debugInfoProvider.useHeapBase();

        cuName = debugInfoProvider.getCompilationUnitName();

        /*
         * Save count of low order tag bits that may appear in references.
         */
        int oopTagsMask = debugInfoProvider.oopTagsMask();

        /* Tag bits must be between 0 and 32 for us to emit as DW_OP_lit<n>. */
        assert oopTagsMask >= 0 && oopTagsMask < 32;
        /* Mask must be contiguous from bit 0. */
        assert ((oopTagsMask + 1) & oopTagsMask) == 0;

        oopTagsCount = Integer.bitCount(oopTagsMask);

        /* Save amount we need to shift references by when loading from an object field. */
        oopCompressShift = debugInfoProvider.oopCompressShift();

        /* shift bit count must be either 0 or 3 */
        assert (oopCompressShift == 0 || oopCompressShift == 3);

        /* Save number of bytes in a reference field. */
        oopReferenceSize = debugInfoProvider.oopReferenceSize();

        /* Save pointer size of current target. */
        pointerSize = debugInfoProvider.pointerSize();

        /* Save alignment of a reference. */
        oopAlignment = debugInfoProvider.oopAlignment();

        /* Save alignment of a reference. */
        oopAlignShift = Integer.bitCount(oopAlignment - 1);

        /* Reference alignment must be 8 bytes. */
        assert oopAlignment == 8;

        /* retrieve limit for Java code address range */
        compiledCodeMax = debugInfoProvider.compiledCodeMax();

        /* Ensure we have a null string and cachePath in the string section. */
        String uniqueNullString = stringTable.uniqueDebugString("");
        if (debugInfoProvider.getCachePath() != null) {
            cachePath = stringTable.uniqueDebugString(debugInfoProvider.getCachePath().toString());
        } else {
            cachePath = uniqueNullString; // fall back to null string
        }

        // TODO: handle method and required typedefs / primitives
        DebugCodeInfo debugCodeInfo = debugInfoProvider.codeInfoProvider();
        String fileName = debugCodeInfo.fileName();
        Path filePath = debugCodeInfo.filePath();
        ResolvedJavaType ownerType = debugCodeInfo.ownerType();
        String methodName = debugCodeInfo.name();
        long lo = debugCodeInfo.addressLo();
        long hi = debugCodeInfo.addressHi();
        int primaryLine = debugCodeInfo.line();

        TypedefEntry typedefEntry = lookupTypedefEntry(ownerType);
        MethodEntry methodEntry = processMethod(debugCodeInfo, typedefEntry);
        PrimaryRange primaryRange = Range.createPrimary(methodEntry, lo, hi, primaryLine);
        compiledMethod = new CompiledMethodEntry(primaryRange, debugCodeInfo.getFrameSizeChanges(), debugCodeInfo.getFrameSize(), typedefEntry);
        HashMap<DebugLocationInfo, SubRange> subRangeIndex = new HashMap<>();
        for(DebugLocationInfo debugLocationInfo : debugCodeInfo.locationInfoProvider()) {
            addSubrange(debugLocationInfo, primaryRange, subRangeIndex);
        }
    }

    private MethodEntry processMethod(DebugMethodInfo debugMethodInfo, TypedefEntry ownerType) {
        String methodName = debugMethodInfo.name();
        int line = debugMethodInfo.line();
        ResolvedJavaType resultType = debugMethodInfo.valueType();
        List<DebugLocalInfo> paramInfos = debugMethodInfo.getParamInfo();
        DebugLocalInfo thisParam = debugMethodInfo.getThisParamInfo();
        int paramCount = paramInfos.size();
        TypeEntry resultTypeEntry = lookupTypeEntry(resultType);
        TypeEntry[] typeEntries = new TypeEntry[paramCount];
        for (int i = 0; i < paramCount; i++) {
            typeEntries[i] = lookupTypeEntry(paramInfos.get(i).valueType());
        }
        FileEntry methodFileEntry = ensureFileEntry(debugMethodInfo);
        MethodEntry methodEntry = new MethodEntry(this, debugMethodInfo, methodFileEntry, line, methodName, ownerType, resultTypeEntry, typeEntries, paramInfos, thisParam);
        // indexMethodEntry(methodEntry, debugMethodInfo.idMethod());
        return methodEntry;
    }

    private TypeEntry createTypeEntry(String typeName, String fileName, Path filePath, int size, DebugTypeKind typeKind) {
        TypeEntry typeEntry = null;
        switch (typeKind) {
            case TYPEDEF:
                typeEntry = new TypedefEntry(typeName, size);
                break;
            case PRIMITIVE:
                assert fileName.length() == 0;
                assert filePath == null;
                typeEntry = new PrimitiveTypeEntry(typeName, size);
                break;
        }
        return typeEntry;
    }

    private TypeEntry addTypeEntry(ResolvedJavaType idType, String typeName, String fileName, Path filePath, int size, DebugTypeKind typeKind) {
        TypeEntry typeEntry = (idType != null ? typesIndex.get(idType) : null);
        if (typeEntry == null) {
            typeEntry = createTypeEntry(typeName, fileName, filePath, size, typeKind);
            types.add(typeEntry);
            if (idType != null) {
                typesIndex.put(idType, typeEntry);
            }
            if (typeEntry instanceof TypedefEntry) {
                indexTypedef(idType, (TypedefEntry) typeEntry);
            }
        }
        return typeEntry;
    }

    public TypeEntry lookupTypeEntry(ResolvedJavaType type) {
        TypeEntry typeEntry = typesIndex.get(type);
        if (typeEntry == null) {
            if (type.isPrimitive()) {
                typeEntry = addTypeEntry(type, type.toJavaName(), "", null, (type.getJavaKind() == JavaKind.Void ? 0 : type.getJavaKind().getBitCount()), DebugTypeKind.PRIMITIVE);
            } else {
                String fileName = type.getSourceFileName();
                Path filePath = fullFilePathFromClassName(type);
                typeEntry = addTypeEntry(type, type.toJavaName(),fileName, filePath, pointerSize, DebugTypeKind.TYPEDEF);
            }
        }
        return typeEntry;
    }

    TypedefEntry lookupTypedefEntry(ResolvedJavaType type) {
        TypedefEntry typedefEntry = typedefsIndex.get(type);
        if (typedefEntry == null) {
            String fileName = type.getSourceFileName();
            Path filePath = fullFilePathFromClassName(type);
            typedefEntry = (TypedefEntry) addTypeEntry(type, type.toJavaName(), fileName, filePath, pointerSize, DebugTypeKind.TYPEDEF);
        }
        return typedefEntry;
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
        if (name.equals("")) {
            name = "_nofile_";
        }
        elements[count - 1] = name + ".java";
        return FileSystems.getDefault().getPath("", elements);
    }

    /**
     * Recursively creates subranges based on DebugLocationInfo including, and appropriately
     * linking, nested inline subranges.
     *
     * @param locationInfo
     * @param primaryRange
     * @param subRangeIndex
     * @return the subrange for {@code locationInfo} linked with all its caller subranges up to the
     *         primaryRange
     */
    @SuppressWarnings("try")
    private Range addSubrange(DebugLocationInfo locationInfo, PrimaryRange primaryRange, HashMap<DebugLocationInfo, SubRange> subRangeIndex) {
        /*
         * We still insert subranges for the primary method but they don't actually count as inline.
         * we only need a range so that subranges for inline code can refer to the top level line
         * number.
         */
        DebugLocationInfo callerLocationInfo = locationInfo.getCaller();
        boolean isTopLevel = callerLocationInfo == null;
        assert (!isTopLevel || (locationInfo.name().equals(primaryRange.getMethodName()) &&
                locationInfo.ownerType().toJavaName().equals(primaryRange.getClassName())));
        Range caller = (isTopLevel ? primaryRange : subRangeIndex.get(callerLocationInfo));
        // the frame tree is walked topdown so inline ranges should always have a caller range
        assert caller != null;

        final String fileName = locationInfo.fileName();
        final Path filePath = locationInfo.filePath();
        final String fullPath = (filePath == null ? "" : filePath.toString() + "/") + fileName;
        final ResolvedJavaType ownerType = locationInfo.ownerType();
        final String methodName = locationInfo.name();
        final long loOff = locationInfo.addressLo();
        final long hiOff = locationInfo.addressHi() - 1;
        final long lo = primaryRange.getLo() + locationInfo.addressLo();
        final long hi = primaryRange.getLo() + locationInfo.addressHi();
        final int line = locationInfo.line();
        TypedefEntry subRangeTypedefEntry = lookupTypedefEntry(ownerType);
        MethodEntry subRangeMethodEntry = processMethod(locationInfo, subRangeTypedefEntry);
        SubRange subRange = Range.createSubrange(subRangeMethodEntry, lo, hi, line, primaryRange, caller, locationInfo.isLeaf());
        //classEntry.indexSubRange(subRange);
        subRangeIndex.put(locationInfo, subRange);
        /*if (debugContext.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
            debugContext.log(DebugContext.DETAILED_LEVEL, "SubRange %s.%s %d %s:%d [0x%x, 0x%x] (%d, %d)",
                    ownerType.toJavaName(), methodName, subRange.getDepth(), fullPath, line, lo, hi, loOff, hiOff);
        }*/
        assert (callerLocationInfo == null || (callerLocationInfo.addressLo() <= loOff && callerLocationInfo.addressHi() >= hiOff)) : "parent range should enclose subrange!";
        List<DebugLocalValueInfo> localValueInfos = locationInfo.getLocalValueInfo();
        /*for (int i = 0; i < localValueInfos.length; i++) {
            DebugLocalValueInfo localValueInfo = localValueInfos[i];
            if (debugContext.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                debugContext.log(DebugContext.DETAILED_LEVEL, "  locals[%d] %s:%s = %s", localValueInfo.slot(), localValueInfo.name(), localValueInfo.typeName(), localValueInfo);
            }
        }*/
        subRange.setLocalValueInfo(localValueInfos);
        return subRange;
    }

    private void indexTypedef(ResolvedJavaType idType, TypedefEntry typedefEntry) {
        typedefs.add(typedefEntry);
        typedefsIndex.put(idType, typedefEntry);
    }

    static final Path EMPTY_PATH = Paths.get("");

    private FileEntry addFileEntry(String fileName, Path filePath) {
        assert fileName != null;
        Path dirPath = filePath;
        Path fileAsPath;
        if (filePath != null) {
            fileAsPath = dirPath.resolve(fileName);
        } else {
            fileAsPath = Paths.get(fileName);
            dirPath = EMPTY_PATH;
        }
        FileEntry fileEntry = filesIndex.get(fileAsPath);
        if (fileEntry == null) {
            DirEntry dirEntry = ensureDirEntry(dirPath);
            /* Ensure file and cachepath are added to the debug_str section. */
            uniqueDebugString(fileName);
            uniqueDebugString(cachePath);
            fileEntry = new FileEntry(fileName, dirEntry);
            files.add(fileEntry);
            /* Index the file entry by file path. */
            filesIndex.put(fileAsPath, fileEntry);
        } else {
            assert fileEntry.getDirEntry().getPath().equals(dirPath);
        }
        return fileEntry;
    }

    public FileEntry ensureFileEntry(DebugFileInfo debugFileInfo) {
        String fileName = debugFileInfo.fileName();
        if (fileName == null || fileName.length() == 0) {
            return null;
        }
        Path filePath = debugFileInfo.filePath();
        Path fileAsPath;
        if (filePath == null) {
            fileAsPath = Paths.get(fileName);
        } else {
            fileAsPath = filePath.resolve(fileName);
        }
        /* Reuse any existing entry. */
        FileEntry fileEntry = findFile(fileAsPath);
        if (fileEntry == null) {
            fileEntry = addFileEntry(fileName, filePath);
        }
        return fileEntry;
    }

    private DirEntry ensureDirEntry(Path filePath) {
        if (filePath == null) {
            return null;
        }
        DirEntry dirEntry = dirsIndex.get(filePath);
        if (dirEntry == null) {
            /* Ensure dir path is entered into the debug_str section. */
            uniqueDebugString(filePath.toString());
            dirEntry = new DirEntry(filePath);
            dirsIndex.put(filePath, dirEntry);
            dirs.add(dirEntry);
        }
        return dirEntry;
    }

    /* Accessors to query the debug info model. */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public List<TypeEntry> getTypes() {
        return types;
    }
    public List<TypedefEntry> getTypedefs() {
        return typedefs;
    }

    public List<FileEntry> getFiles() {
        return files;
    }

    public List<DirEntry> getDirs() {
        return dirs;
    }

    @SuppressWarnings("unused")
    public FileEntry findFile(Path fullFileName) {
        return filesIndex.get(fullFileName);
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     */
    public String uniqueDebugString(String string) {
        return stringTable.uniqueDebugString(string);
    }

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     * @return the offset of the string in the .debug_str section.
     */
    public int debugStringIndex(String string) {
        return stringTable.debugStringIndex(string);
    }

    public boolean useHeapBase() {
        return useHeapBase;
    }

    public String cuName() {
        return cuName;
    }

    public byte oopTagsMask() {
        return (byte) ((1 << oopTagsCount) - 1);
    }

    public byte oopTagsShift() {
        return (byte) oopTagsCount;
    }

    public int oopCompressShift() {
        return oopCompressShift;
    }

    public int oopReferenceSize() {
        return oopReferenceSize;
    }

    public int pointerSize() {
        return pointerSize;
    }

    public int oopAlignment() {
        return oopAlignment;
    }

    public int oopAlignShift() {
        return oopAlignShift;
    }

    public String getCachePath() {
        return cachePath;
    }

    public CompiledMethodEntry getCompiledMethod() {
        return compiledMethod;
    }

    public Iterable<PrimitiveTypeEntry> getPrimitiveTypes() {
        List<PrimitiveTypeEntry> primitiveTypes = new ArrayList<>();
        for (TypeEntry typeEntry : types) {
            if (typeEntry instanceof PrimitiveTypeEntry primitiveTypeEntry) {
                primitiveTypes.add(primitiveTypeEntry);
            }
        }
        return primitiveTypes;
    }


    /*
    private static void collectFilesAndDirs(TypedefEntry classEntry) {
        // track files and dirs we have already seen so that we only add them once
        EconomicSet<FileEntry> visitedFiles = EconomicSet.create();
        EconomicSet<DirEntry> visitedDirs = EconomicSet.create();
        // add the class's file and dir
        includeOnce(classEntry, classEntry.getFileEntry(), visitedFiles, visitedDirs);
        // add files for fields (may differ from class file if we have a substitution)
        for (FieldEntry fieldEntry : classEntry.fields) {
            includeOnce(classEntry, fieldEntry.getFileEntry(), visitedFiles, visitedDirs);
        }
        // add files for declared methods (may differ from class file if we have a substitution)
        for (MethodEntry methodEntry : classEntry.getMethods()) {
            includeOnce(classEntry, methodEntry.getFileEntry(), visitedFiles, visitedDirs);
        }
        // add files for top level compiled and inline methods
        classEntry.compiledEntries().forEachOrdered(compiledMethodEntry -> {
            includeOnce(classEntry, compiledMethodEntry.getPrimary().getFileEntry(), visitedFiles, visitedDirs);
            // we need files for leaf ranges and for inline caller ranges
            //
            // add leaf range files first because they get searched for linearly
            // during line info processing
            compiledMethodEntry.leafRangeIterator().forEachRemaining(subRange -> {
                includeOnce(classEntry, subRange.getFileEntry(), visitedFiles, visitedDirs);
            });
            // now the non-leaf range files
            compiledMethodEntry.topDownRangeIterator().forEachRemaining(subRange -> {
                if (!subRange.isLeaf()) {
                    includeOnce(classEntry, subRange.getFileEntry(), visitedFiles, visitedDirs);
                }
            });
        });
        // now all files and dirs are known build an index for them
        classEntry.buildFileAndDirIndexes();
    }*/

    /**
     * Ensure the supplied file entry and associated directory entry are included, but only once, in
     * a class entry's file and dir list.
     *
     * @param classEntry the class entry whose file and dir list may need to be updated
     * @param fileEntry a file entry which may need to be added to the class entry's file list or
     *            whose dir may need adding to the class entry's dir list
     * @param visitedFiles a set tracking current file list entries, updated if a file is added
     * @param visitedDirs a set tracking current dir list entries, updated if a dir is added
     */
    /*
    private static void includeOnce(ClassEntry classEntry, FileEntry fileEntry, EconomicSet<FileEntry> visitedFiles, EconomicSet<DirEntry> visitedDirs) {
        if (fileEntry != null && !visitedFiles.contains(fileEntry)) {
            visitedFiles.add(fileEntry);
            classEntry.includeFile(fileEntry);
            DirEntry dirEntry = fileEntry.getDirEntry();
            if (dirEntry != null && !dirEntry.getPathString().isEmpty() && !visitedDirs.contains(dirEntry)) {
                visitedDirs.add(dirEntry);
                classEntry.includeDir(dirEntry);
            }
        }
    }*/
}
