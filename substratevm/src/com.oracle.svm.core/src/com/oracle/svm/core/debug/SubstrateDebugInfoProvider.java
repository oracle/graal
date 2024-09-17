package com.oracle.svm.core.debug;

import com.oracle.objectfile.BasicNobitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CompilationResultFrameTree;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.NumUtil;
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
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.Value;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.CONTRACT;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.EXTEND;


public class SubstrateDebugInfoProvider implements RuntimeDebugInfoProvider {

    private final DebugContext debugContext;
    private final ResolvedJavaMethod method;
    private final CompilationResult compilation;
    private final long codeAddress;
    private final int codeSize;

    private final RuntimeConfiguration runtimeConfiguration;
    private final int pointerSize;
    private final int tagsMask;
    private final boolean useHeapBase;
    private final int compressShift;
    private final int referenceSize;
    private final int referenceAlignment;

    private final ObjectFile objectFile;
    private final List<ObjectFile.Element> sortedObjectFileElements;
    private final int debugInfoSize;


    public SubstrateDebugInfoProvider(DebugContext debugContext, ResolvedJavaMethod method, CompilationResult compilation, RuntimeConfiguration runtimeConfiguration, long codeAddress, int codeSize) {
        this.debugContext = debugContext;
        this.method = method;
        this.compilation = compilation;
        this.codeAddress = codeAddress;
        this.codeSize = codeSize;

        this.runtimeConfiguration = runtimeConfiguration;
        this.pointerSize = ConfigurationValues.getTarget().wordSize;
        ObjectHeader objectHeader = Heap.getHeap().getObjectHeader();
        this.tagsMask = objectHeader.getReservedBitsMask();
        CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
        this.useHeapBase = compressEncoding.hasBase();
        this.compressShift = (compressEncoding.hasShift() ? compressEncoding.getShift() : 0);
        this.referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        this.referenceAlignment = ConfigurationValues.getObjectLayout().getAlignment();

        int pageSize = NumUtil.safeToInt(ImageSingletons.lookup(VirtualMemoryProvider.class).getGranularity().rawValue());
        objectFile = ObjectFile.createRuntimeDebugInfo(pageSize);
        objectFile.newNobitsSection(SectionName.TEXT.getFormatDependentName(objectFile.getFormat()), new BasicNobitsSectionImpl(codeSize));

        objectFile.installRuntimeDebugInfo(this);

        sortedObjectFileElements = new ArrayList<>();
        debugInfoSize = objectFile.bake(sortedObjectFileElements);
        dumpObjectFile();
    }

    public NonmovableArray<Byte> writeDebugInfoData() {
        NonmovableArray<Byte> array = NonmovableArrays.createByteArray(debugInfoSize, NmtCategory.Code);
        objectFile.writeBuffer(sortedObjectFileElements, NonmovableArrays.asByteBuffer(array));
        return array;
    }

    @Override
    public String getCompilationUnitName() {
        String name = (compilation != null) ? compilation.getName() : null;
        if (name == null && method != null) {
            name = method.format("%H.%n(%p)");
        }
        if (name == null || name.isEmpty()) {
            name = "UnnamedCU";
        }
        name += " at 0x" + Long.toHexString(codeAddress);
        return name;
    }

    private void dumpObjectFile() {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName()).append('@').append(Long.toHexString(codeAddress)).append(".debug");
        try (FileChannel dumpFile = FileChannel.open(Paths.get(sb.toString()),
                StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE)) {
            ByteBuffer buffer = dumpFile.map(FileChannel.MapMode.READ_WRITE, 0, debugInfoSize);
            objectFile.writeBuffer(sortedObjectFileElements, buffer);
        } catch (IOException e) {
            debugContext.log("Failed to dump %s", sb);
        }
    }

    @Override
    public boolean useHeapBase() {
        return useHeapBase;
    }

    @Override
    public int oopCompressShift() {
        return compressShift;
    }

    @Override
    public int oopTagsMask() {
        return tagsMask;
    }

    @Override
    public int oopReferenceSize() {
        return referenceSize;
    }

    @Override
    public int pointerSize() {
        return pointerSize;
    }

    @Override
    public int oopAlignment() {
        return referenceAlignment;
    }

    @Override
    public int compiledCodeMax() {
        return 0;
    }

    @Override
    public DebugInfoProvider.DebugCodeInfo codeInfoProvider() {
        return new SubstrateDebugCodeInfo(method, compilation);
    }

    @Override
    public DebugInfoProvider.DebugTypeInfo createDebugTypeInfo(ResolvedJavaType javaType) {

        try (DebugContext.Scope s = debugContext.scope("DebugTypeInfo", javaType.toJavaName())) {
            if (javaType.isInstanceClass()) {
                return new SubstrateDebugInstanceTypeInfo(javaType);
            } else if (javaType.isInterface()) {
                return new SubstrateDebugInterfaceTypeInfo(javaType);
            } else if (javaType.isArray()) {
                return new SubstrateDebugArrayTypeInfo(javaType);
            } else if (javaType.isPrimitive()) {
                return new SubstrateDebugPrimitiveTypeInfo(javaType);
            } else {
                System.out.println(javaType.getName());
                return new SubstrateDebugForeignTypeInfo(javaType);
            }
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
    }

    @Override
    public Path getCachePath() {
        return SubstrateOptions.getRuntimeSourceDestDir();
    }

    @Override
    public void recordActivity() {

    }

    private class SubstrateDebugFileInfo implements DebugInfoProvider.DebugFileInfo {
        private final Path fullFilePath;
        private final String fileName;

        SubstrateDebugFileInfo(ResolvedJavaMethod method) {
            this(method.getDeclaringClass());
        }

        SubstrateDebugFileInfo(ResolvedJavaType type) {
            fullFilePath = fullFilePathFromClassName(type);
            fileName = type.getSourceFileName();
        }

        private Path fullFilePathFromClassName(ResolvedJavaType type) {
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

        @Override
        public String fileName() {
            return fileName;
        }

        @Override
        public Path filePath() {
            if (fullFilePath != null) {
                return fullFilePath.getParent();
            }
            return null;
        }
    }


    // actually unused currently
    private abstract class SubstrateDebugTypeInfo extends SubstrateDebugFileInfo implements DebugInfoProvider.DebugTypeInfo {
        protected final ResolvedJavaType type;

        SubstrateDebugTypeInfo(ResolvedJavaType type) {
            super(type);
            this.type = type;
        }

        @Override
        public long typeSignature(String prefix) {
            return Digest.digestAsUUID(prefix + typeName()).getLeastSignificantBits();
        }

        @Override
        public ResolvedJavaType idType() {
            return type;
        }

        @Override
        public void debugContext(Consumer<DebugContext> action) {
            try (DebugContext.Scope s = debugContext.scope("DebugTypeInfo", method)) {
                action.accept(debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @Override
        public String typeName() {
            return type.toJavaName();
        }

        @Override
        public long classOffset() {
            // we would need access to the heap/types on the heap
            return -1;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    private class SubstrateDebugEnumTypeInfo extends SubstrateDebugInstanceTypeInfo implements DebugInfoProvider.DebugEnumTypeInfo {

        SubstrateDebugEnumTypeInfo(ResolvedJavaType enumType) {
            super(enumType);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.ENUM;
        }
    }

    private class SubstrateDebugInstanceTypeInfo extends SubstrateDebugTypeInfo implements DebugInfoProvider.DebugInstanceTypeInfo {
        SubstrateDebugInstanceTypeInfo(ResolvedJavaType javaType) {
            super(javaType);
        }

        @Override
        public long typeSignature(String prefix) {
            return super.typeSignature(prefix + loaderName());
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.INSTANCE;
        }

        @Override
        public String loaderName() {
            // return UniqueShortNameProvider.singleton().uniqueShortLoaderName(type.getClass().getClassLoader());
            return ImageSingletons.lookup(SubstrateBFDNameProvider.class).uniqueShortLoaderName(type.getClass().getClassLoader());
        }

        @Override
        public Stream<DebugInfoProvider.DebugFieldInfo> fieldInfoProvider() {
            return Stream.empty();
        }

        @Override
        public Stream<DebugInfoProvider.DebugMethodInfo> methodInfoProvider() {
            return Stream.empty();
        }

        @Override
        public ResolvedJavaType superClass() {
            return type.getSuperclass();
        }

        @Override
        public Stream<ResolvedJavaType> interfaces() {
            // map through getOriginal so we can use the result as an id type
            return Arrays.stream(type.getInterfaces());
        }
    }

    private class SubstrateDebugInterfaceTypeInfo extends SubstrateDebugInstanceTypeInfo implements DebugInfoProvider.DebugInterfaceTypeInfo {

        SubstrateDebugInterfaceTypeInfo(ResolvedJavaType interfaceType) {
            super(interfaceType);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.INTERFACE;
        }
    }

    private class SubstrateDebugForeignTypeInfo extends SubstrateDebugInstanceTypeInfo implements DebugInfoProvider.DebugForeignTypeInfo {

        SubstrateDebugForeignTypeInfo(ResolvedJavaType foreignType) {
            super(foreignType);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.FOREIGN;
        }

        @Override
        public String typedefName() {
            return "";
        }

        @Override
        public boolean isWord() {
            return false;
        }

        @Override
        public boolean isStruct() {
            return false;
        }

        @Override
        public boolean isPointer() {
            return false;
        }

        @Override
        public boolean isIntegral() {
            return false;
        }

        @Override
        public boolean isFloat() {
            return false;
        }

        @Override
        public boolean isSigned() {
            return false;
        }

        @Override
        public ResolvedJavaType parent() {
            return null;
        }

        @Override
        public ResolvedJavaType pointerTo() {
            return null;
        }
    }

    private class SubstrateDebugArrayTypeInfo extends SubstrateDebugTypeInfo implements DebugInfoProvider.DebugArrayTypeInfo {

        SubstrateDebugArrayTypeInfo(ResolvedJavaType arrayClass) {
            super(arrayClass);
        }

        @Override
        public long typeSignature(String prefix) {
            ResolvedJavaType elementType = type.getComponentType();
            while (elementType.isArray()) {
                elementType = elementType.getComponentType();
            }
            String loaderId = "";
            if (elementType.isInstanceClass() || elementType.isInterface()) {
                // loaderId = UniqueShortNameProvider.singleton().uniqueShortLoaderName(elementType.getClass().getClassLoader());
                loaderId = ImageSingletons.lookup(SubstrateBFDNameProvider.class).uniqueShortLoaderName(elementType.getClass().getClassLoader());
            }
            return super.typeSignature(prefix + loaderId);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.ARRAY;
        }

        @Override
        public int baseSize() {
            return 0;
        }

        @Override
        public int lengthOffset() {
            return 0;
        }

        @Override
        public ResolvedJavaType elementType() {
            return type.getComponentType();
        }

        @Override
        public Stream<DebugInfoProvider.DebugFieldInfo> fieldInfoProvider() {
            return Stream.empty();
        }
    }

    private class SubstrateDebugPrimitiveTypeInfo extends SubstrateDebugTypeInfo implements DebugInfoProvider.DebugPrimitiveTypeInfo {

        SubstrateDebugPrimitiveTypeInfo(ResolvedJavaType type) {
            super(type);
            assert type.isPrimitive();
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.PRIMITIVE;
        }

        @Override
        public int bitCount() {
            JavaKind javaKind = type.getJavaKind();
            return (javaKind == JavaKind.Void ? 0 : javaKind.getBitCount());
        }

        @Override
        public char typeChar() {
            return type.getJavaKind().getTypeChar();
        }

        @Override
        public int flags() {
            char typeChar = typeChar();
            return switch (typeChar) {
                case 'B', 'S', 'I', 'J' -> FLAG_NUMERIC | FLAG_INTEGRAL | FLAG_SIGNED;
                case 'C' -> FLAG_NUMERIC | FLAG_INTEGRAL;
                case 'F', 'D' -> FLAG_NUMERIC;
                default -> {
                    assert typeChar == 'V' || typeChar == 'Z';
                    yield 0;
                }
            };
        }
    }

    private class SubstrateDebugMethodInfo extends SubstrateDebugFileInfo implements DebugInfoProvider.DebugMethodInfo {
        protected final ResolvedJavaMethod method;
        protected int line;
        protected final List<DebugInfoProvider.DebugLocalInfo> paramInfo;
        protected final DebugInfoProvider.DebugLocalInfo thisParamInfo;

        SubstrateDebugMethodInfo(ResolvedJavaMethod method) {
            super(method);
            this.method = method;
            LineNumberTable lineNumberTable = method.getLineNumberTable();
            this.line = (lineNumberTable != null ? lineNumberTable.getLineNumber(0) : 0);
            this.paramInfo = createParamInfo(method, line);

            // We use the target modifiers to decide where to install any first param
            // even though we may have added it according to whether method is static.
            // That's because in a few special cases method is static but the original
            // DebugFrameLocals
            // from which it is derived is an instance method. This appears to happen
            // when a C function pointer masquerades as a method. Whatever parameters
            // we pass through need to match the definition of the original.
            if (Modifier.isStatic(modifiers())) {
                this.thisParamInfo = null;
            } else {
                this.thisParamInfo = paramInfo.removeFirst();
            }
        }

        private List<DebugInfoProvider.DebugLocalInfo> createParamInfo(ResolvedJavaMethod method, int line) {
            Signature signature = method.getSignature();
            int parameterCount = signature.getParameterCount(false);
            List<DebugInfoProvider.DebugLocalInfo> paramInfos = new ArrayList<>(parameterCount);
            LocalVariableTable table = method.getLocalVariableTable();
            int slot = 0;
            ResolvedJavaType ownerType = method.getDeclaringClass();
            if (!method.isStatic()) {
                JavaKind kind = ownerType.getJavaKind();
                assert kind == JavaKind.Object : "must be an object";
                paramInfos.add(new SubstrateDebugLocalInfo("this", kind, ownerType, slot, line));
                slot += kind.getSlotCount();
            }
            for (int i = 0; i < parameterCount; i++) {
                Local local = (table == null ? null : table.getLocal(slot, 0));
                String name = (local != null ? local.getName() : "__" + i);
                ResolvedJavaType paramType = (ResolvedJavaType) signature.getParameterType(i, null);
                JavaKind kind = paramType.getJavaKind();
                paramInfos.add(new SubstrateDebugLocalInfo(name, kind, paramType, slot, line));
                slot += kind.getSlotCount();
            }
            return paramInfos;
        }

        @Override
        public String name() {
            String name = method.getName();
            if (name.equals("<init>")) {
                name = method.format("%h");
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            }
            return name;
        }

        @Override
        public ResolvedJavaType valueType() {
            return (ResolvedJavaType) method.getSignature().getReturnType(null);
        }

        @Override
        public int modifiers() {
            return method.getModifiers();
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public DebugInfoProvider.DebugLocalInfo[] getParamInfo() {
            return paramInfo.toArray(new DebugInfoProvider.DebugLocalInfo[]{});
        }

        @Override
        public DebugInfoProvider.DebugLocalInfo getThisParamInfo() {
            return thisParamInfo;
        }

        @Override
        public String symbolNameForMethod() {
            // SubstrateOptions.ImageSymbolsPrefix.getValue() + getUniqueShortName(sm);
            return ImageSingletons.lookup(SubstrateBFDNameProvider.class).uniqueShortName(null, method.getDeclaringClass(), method.getName(), method.getSignature(), method.isConstructor());
        }

        @Override
        public boolean isDeoptTarget() {
            return name().endsWith(StableMethodNameFormatter.MULTI_METHOD_KEY_SEPARATOR);
        }

        @Override
        public boolean isConstructor() {
            return method.isConstructor();
        }

        @Override
        public boolean isVirtual() {
            return false;
        }

        @Override
        public int vtableOffset() {
            // not virtual
            return -1;
        }

        @Override
        public boolean isOverride() {
            return false;
        }

        @Override
        public ResolvedJavaType ownerType() {
            return method.getDeclaringClass();
        }

        @Override
        public ResolvedJavaMethod idMethod() {
            return method;
        }
    }

    private class SubstrateDebugCodeInfo extends SubstrateDebugMethodInfo implements DebugInfoProvider.DebugCodeInfo {
        private final CompilationResult compilation;

        SubstrateDebugCodeInfo(ResolvedJavaMethod method, CompilationResult compilation) {
            super(method);
            this.compilation = compilation;
        }

        @SuppressWarnings("try")
        @Override
        public void debugContext(Consumer<DebugContext> action) {
            try (DebugContext.Scope s = debugContext.scope("DebugCodeInfo", method)) {
                action.accept(debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @Override
        public long addressLo() {
            return codeAddress;
        }

        @Override
        public long addressHi() {
            return codeAddress + compilation.getTargetCodeSize();
        }

        @Override
        public Stream<DebugInfoProvider.DebugLocationInfo> locationInfoProvider() {
            int maxDepth = Integer.MAX_VALUE; //SubstrateOptions.DebugCodeInfoMaxDepth.getValue();
            boolean useSourceMappings = false; //SubstrateOptions.DebugCodeInfoUseSourceMappings.getValue();
            final CompilationResultFrameTree.CallNode root = new CompilationResultFrameTree.Builder(debugContext, compilation.getTargetCodeSize(), maxDepth, useSourceMappings, true).build(compilation);
            if (root == null) {
                return Stream.empty();
            }
            final List<DebugInfoProvider.DebugLocationInfo> locationInfos = new ArrayList<>();
            int frameSize = getFrameSize();
            final CompilationResultFrameTree.Visitor visitor = new MultiLevelVisitor(locationInfos, frameSize);
            // arguments passed by visitor to apply are
            // NativeImageDebugLocationInfo caller location info
            // CallNode nodeToEmbed parent call node to convert to entry code leaf
            // NativeImageDebugLocationInfo leaf into which current leaf may be merged
            root.visitChildren(visitor, (Object) null, (Object) null, (Object) null);
            // try to add a location record for offset zero
            updateInitialLocation(locationInfos);
            return locationInfos.stream();
        }

        private int findMarkOffset(SubstrateBackend.SubstrateMarkId markId) {
            for (CompilationResult.CodeMark mark : compilation.getMarks()) {
                if (mark.id.equals(markId)) {
                    return mark.pcOffset;
                }
            }
            return -1;
        }

        private void updateInitialLocation(List<DebugInfoProvider.DebugLocationInfo> locationInfos) {
            int prologueEnd = findMarkOffset(SubstrateBackend.SubstrateMarkId.PROLOGUE_END);
            if (prologueEnd < 0) {
                // this is not a normal compiled method so give up
                return;
            }
            int stackDecrement = findMarkOffset(SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP);
            if (stackDecrement < 0) {
                // this is not a normal compiled method so give up
                return;
            }
            // if there are any location info records then the first one will be for
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
            SubstrateDebugLocationInfo firstLocation = (SubstrateDebugLocationInfo) locationInfos.get(0);
            long firstLocationOffset = firstLocation.addressLo();

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
            debugContext.log(DebugContext.DETAILED_LEVEL, "Add synthetic Location Info : %s (0, %d)", method.getName(), firstLocationOffset - 1);
            SubstrateDebugLocationInfo locationInfo = new SubstrateDebugLocationInfo(method, firstLocationOffset, locProducer);
            // if the prologue extends beyond the stack extend and uses the stack then the info
            // needs
            // splitting at the extend point with the stack offsets adjusted in the new info
            if (locProducer.usesStack() && firstLocationOffset > stackDecrement) {
                SubstrateDebugLocationInfo splitLocationInfo = locationInfo.split(stackDecrement, getFrameSize());
                debugContext.log(DebugContext.DETAILED_LEVEL, "Split synthetic Location Info : %s (%d, %d) (%d, %d)", locationInfo.name(), 0,
                        locationInfo.addressLo() - 1, locationInfo.addressLo(), locationInfo.addressHi() - 1);
                locationInfos.add(0, splitLocationInfo);
            }
            locationInfos.add(0, locationInfo);
        }

        // indices for arguments passed to SingleLevelVisitor::apply
        protected static final int CALLER_INFO = 0;
        protected static final int PARENT_NODE_TO_EMBED = 1;
        protected static final int LAST_LEAF_INFO = 2;

        private abstract class SingleLevelVisitor implements CompilationResultFrameTree.Visitor {

            protected final List<DebugInfoProvider.DebugLocationInfo> locationInfos;
            protected final int frameSize;

            SingleLevelVisitor(List<DebugInfoProvider.DebugLocationInfo> locationInfos, int frameSize) {
                this.locationInfos = locationInfos;
                this.frameSize = frameSize;
            }

            public SubstrateDebugLocationInfo process(CompilationResultFrameTree.FrameNode node, SubstrateDebugLocationInfo callerInfo) {
                SubstrateDebugLocationInfo locationInfo;
                if (node instanceof CompilationResultFrameTree.CallNode) {
                    // this node represents an inline call range so
                    // add a locationinfo to cover the range of the call
                    locationInfo = createCallLocationInfo((CompilationResultFrameTree.CallNode) node, callerInfo, frameSize);
                } else if (isBadLeaf(node, callerInfo)) {
                    locationInfo = createBadLeafLocationInfo(node, callerInfo, frameSize);
                } else {
                    // this is leaf method code so add details of its range
                    locationInfo = createLeafLocationInfo(node, callerInfo, frameSize);
                }
                return locationInfo;
            }
        }

        private class TopLevelVisitor extends SingleLevelVisitor {
            TopLevelVisitor(List<DebugInfoProvider.DebugLocationInfo> locationInfos, int frameSize) {
                super(locationInfos, frameSize);
            }

            @Override
            public void apply(CompilationResultFrameTree.FrameNode node, Object... args) {
                if (skipNode(node)) {
                    // this is a bogus wrapper so skip it and transform the wrapped node instead
                    node.visitChildren(this, args);
                } else {
                    SubstrateDebugLocationInfo locationInfo = process(node, null);
                    if (node instanceof CompilationResultFrameTree.CallNode) {
                        locationInfos.add(locationInfo);
                        // erase last leaf (if present) since there is an intervening call range
                        invalidateMerge(args);
                    } else {
                        locationInfo = tryMerge(locationInfo, args);
                        if (locationInfo != null) {
                            locationInfos.add(locationInfo);
                        }
                    }
                }
            }
        }

        public class MultiLevelVisitor extends SingleLevelVisitor {
            MultiLevelVisitor(List<DebugInfoProvider.DebugLocationInfo> locationInfos, int frameSize) {
                super(locationInfos, frameSize);
            }

            @Override
            public void apply(CompilationResultFrameTree.FrameNode node, Object... args) {
                if (skipNode(node)) {
                    // this is a bogus wrapper so skip it and transform the wrapped node instead
                    node.visitChildren(this, args);
                } else {
                    SubstrateDebugLocationInfo callerInfo = (SubstrateDebugLocationInfo) args[CALLER_INFO];
                    CompilationResultFrameTree.CallNode nodeToEmbed = (CompilationResultFrameTree.CallNode) args[PARENT_NODE_TO_EMBED];
                    if (nodeToEmbed != null) {
                        if (embedWithChildren(nodeToEmbed, node)) {
                            // embed a leaf range for the method start that was included in the
                            // parent CallNode
                            // its end range is determined by the start of the first node at this
                            // level
                            SubstrateDebugLocationInfo embeddedLocationInfo = createEmbeddedParentLocationInfo(nodeToEmbed, node, callerInfo, frameSize);
                            locationInfos.add(embeddedLocationInfo);
                            // since this is a leaf node we can merge later leafs into it
                            initMerge(embeddedLocationInfo, args);
                        }
                        // reset args so we only embed the parent node before the first node at
                        // this level
                        args[PARENT_NODE_TO_EMBED] = nodeToEmbed = null;
                    }
                    SubstrateDebugLocationInfo locationInfo = process(node, callerInfo);
                    if (node instanceof CompilationResultFrameTree.CallNode) {
                        CompilationResultFrameTree.CallNode callNode = (CompilationResultFrameTree.CallNode) node;
                        locationInfos.add(locationInfo);
                        // erase last leaf (if present) since there is an intervening call range
                        invalidateMerge(args);
                        if (hasChildren(callNode)) {
                            // a call node may include an initial leaf range for the call that must
                            // be
                            // embedded under the newly created location info so pass it as an
                            // argument
                            callNode.visitChildren(this, locationInfo, callNode, (Object) null);
                        } else {
                            // we need to embed a leaf node for the whole call range
                            locationInfo = createEmbeddedParentLocationInfo(callNode, null, locationInfo, frameSize);
                            locationInfos.add(locationInfo);
                        }
                    } else {
                        locationInfo = tryMerge(locationInfo, args);
                        if (locationInfo != null) {
                            locationInfos.add(locationInfo);
                        }
                    }
                }
            }
        }

        /**
         * Report whether a call node has any children.
         *
         * @param callNode the node to check
         * @return true if it has any children otherwise false.
         */
        private boolean hasChildren(CompilationResultFrameTree.CallNode callNode) {
            Object[] result = new Object[]{false};
            callNode.visitChildren(new CompilationResultFrameTree.Visitor() {
                @Override
                public void apply(CompilationResultFrameTree.FrameNode node, Object... args) {
                    args[0] = true;
                }
            }, result);
            return (boolean) result[0];
        }

        /**
         * Create a location info record for a leaf subrange.
         *
         * @param node is a simple FrameNode
         * @return the newly created location info record
         */
        private SubstrateDebugLocationInfo createLeafLocationInfo(CompilationResultFrameTree.FrameNode node, SubstrateDebugLocationInfo callerInfo, int framesize) {
            assert !(node instanceof CompilationResultFrameTree.CallNode);
            SubstrateDebugLocationInfo locationInfo = new SubstrateDebugLocationInfo(node, callerInfo, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Create leaf Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                    locationInfo.addressHi() - 1);
            return locationInfo;
        }

        /**
         * Create a location info record for a subrange that encloses an inline call.
         *
         * @param callNode is the top level inlined call frame
         * @return the newly created location info record
         */
        private SubstrateDebugLocationInfo createCallLocationInfo(CompilationResultFrameTree.CallNode callNode, SubstrateDebugLocationInfo callerInfo, int framesize) {
            BytecodePosition callerPos = realCaller(callNode);
            SubstrateDebugLocationInfo locationInfo = new SubstrateDebugLocationInfo(callerPos, callNode.getStartPos(), callNode.getEndPos() + 1, callerInfo, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Create call Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                    locationInfo.addressHi() - 1);
            return locationInfo;
        }

        /**
         * Create a location info record for the initial range associated with a parent call node
         * whose position and start are defined by that call node and whose end is determined by the
         * first child of the call node.
         *
         * @param parentToEmbed a parent call node which has already been processed to create the
         *            caller location info
         * @param firstChild the first child of the call node
         * @param callerLocation the location info created to represent the range for the call
         * @return a location info to be embedded as the first child range of the caller location.
         */
        private SubstrateDebugLocationInfo createEmbeddedParentLocationInfo(CompilationResultFrameTree.CallNode parentToEmbed, CompilationResultFrameTree.FrameNode firstChild, SubstrateDebugLocationInfo callerLocation, int framesize) {
            BytecodePosition pos = parentToEmbed.frame;
            int startPos = parentToEmbed.getStartPos();
            int endPos = (firstChild != null ? firstChild.getStartPos() : parentToEmbed.getEndPos() + 1);
            SubstrateDebugLocationInfo locationInfo = new SubstrateDebugLocationInfo(pos, startPos, endPos, callerLocation, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Embed leaf Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                    locationInfo.addressHi() - 1);
            return locationInfo;
        }

        private SubstrateDebugLocationInfo createBadLeafLocationInfo(CompilationResultFrameTree.FrameNode node, SubstrateDebugLocationInfo callerLocation, int framesize) {
            assert !(node instanceof CompilationResultFrameTree.CallNode) : "bad leaf location cannot be a call node!";
            assert callerLocation == null : "should only see bad leaf at top level!";
            BytecodePosition pos = node.frame;
            BytecodePosition callerPos = pos.getCaller();
            assert callerPos != null : "bad leaf must have a caller";
            assert callerPos.getCaller() == null : "bad leaf caller must be root method";
            int startPos = node.getStartPos();
            int endPos = node.getEndPos() + 1;
            SubstrateDebugLocationInfo locationInfo = new SubstrateDebugLocationInfo(callerPos, startPos, endPos, null, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Embed leaf Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                    locationInfo.addressHi() - 1);
            return locationInfo;
        }

        private boolean isBadLeaf(CompilationResultFrameTree.FrameNode node, SubstrateDebugLocationInfo callerLocation) {
            // Sometimes we see a leaf node marked as belonging to an inlined method
            // that sits directly under the root method rather than under a call node.
            // It needs replacing with a location info for the root method that covers
            // the relevant code range.
            if (callerLocation == null) {
                BytecodePosition pos = node.frame;
                BytecodePosition callerPos = pos.getCaller();
                if (callerPos != null && !callerPos.getMethod().equals(pos.getMethod())) {
                    if (callerPos.getCaller() == null) {
                        return true;
                    }
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
        private boolean skipPos(BytecodePosition pos) {
            return (pos.getBCI() == -1 && pos instanceof NodeSourcePosition && ((NodeSourcePosition) pos).isSubstitution());
        }

        /**
         * Skip caller nodes with bogus positions, as determined by
         * {@link #skipPos(BytecodePosition)}, returning first caller node position that is not
         * bogus.
         *
         * @param node the node whose callers are to be traversed
         * @return the first non-bogus position in the caller chain.
         */
        private BytecodePosition realCaller(CompilationResultFrameTree.CallNode node) {
            BytecodePosition pos = node.frame.getCaller();
            while (skipPos(pos)) {
                pos = pos.getCaller();
            }
            return pos;
        }

        /**
         * Test whether the position associated with a child node should result in an entry in the
         * inline tree. The test is for a call node with a bogus position as determined by
         * {@link #skipPos(BytecodePosition)}.
         *
         * @param node A node associated with a child frame in the compilation result frame tree.
         * @return True an entry should be included or false if it should be omitted.
         */
        private boolean skipNode(CompilationResultFrameTree.FrameNode node) {
            return node instanceof CompilationResultFrameTree.CallNode && skipPos(node.frame);
        }

        /**
         * Test whether the position associated with a call node frame should be embedded along with
         * the locations generated for the node's children. This is needed because call frames may
         * include a valid source position that precedes the first child position.
         *
         * @param parent The call node whose children are currently being visited
         * @param firstChild The first child of that call node
         * @return true if the node should be embedded otherwise false
         */
        private boolean embedWithChildren(CompilationResultFrameTree.CallNode parent, CompilationResultFrameTree.FrameNode firstChild) {
            // we only need to insert a range for the caller if it fills a gap
            // at the start of the caller range before the first child
            if (parent.getStartPos() < firstChild.getStartPos()) {
                return true;
            }
            return false;
        }

        /**
         * Try merging a new location info for a leaf range into the location info for the last leaf
         * range added at this level.
         *
         * @param newLeaf the new leaf location info
         * @param args the visitor argument vector used to pass parameters from one child visit to
         *            the next possibly including the last leaf
         * @return the new location info if it could not be merged or null to indicate that it was
         *         merged
         */
        private SubstrateDebugLocationInfo tryMerge(SubstrateDebugLocationInfo newLeaf, Object[] args) {
            // last leaf node added at this level is 3rd element of arg vector
            SubstrateDebugLocationInfo lastLeaf = (SubstrateDebugLocationInfo) args[LAST_LEAF_INFO];

            if (lastLeaf != null) {
                // try merging new leaf into last one
                lastLeaf = lastLeaf.merge(newLeaf);
                if (lastLeaf != null) {
                    // null return indicates new leaf has been merged into last leaf
                    return null;
                }
            }
            // update last leaf and return new leaf for addition to local info list
            args[LAST_LEAF_INFO] = newLeaf;
            return newLeaf;
        }

        /**
         * Set the last leaf node at the current level to the supplied leaf node.
         *
         * @param lastLeaf the last leaf node created at this level
         * @param args the visitor argument vector used to pass parameters from one child visit to
         *            the next
         */
        private void initMerge(SubstrateDebugLocationInfo lastLeaf, Object[] args) {
            args[LAST_LEAF_INFO] = lastLeaf;
        }

        /**
         * Clear the last leaf node at the current level from the visitor arguments by setting the
         * arg vector entry to null.
         *
         * @param args the visitor argument vector used to pass parameters from one child visit to
         *            the next
         */
        private void invalidateMerge(Object[] args) {
            args[LAST_LEAF_INFO] = null;
        }

        @Override
        public int getFrameSize() {
            return compilation.getTotalFrameSize();
        }

        @Override
        public List<DebugInfoProvider.DebugFrameSizeChange> getFrameSizeChanges() {
            List<DebugInfoProvider.DebugFrameSizeChange> frameSizeChanges = new LinkedList<>();
            for (CompilationResult.CodeMark mark : compilation.getMarks()) {
                /* We only need to observe stack increment or decrement points. */
                if (mark.id.equals(SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP)) {
                    SubstrateDebugFrameSizeChange sizeChange = new SubstrateDebugFrameSizeChange(mark.pcOffset, EXTEND);
                    frameSizeChanges.add(sizeChange);
                    // } else if (mark.id.equals("PROLOGUE_END")) {
                    // can ignore these
                    // } else if (mark.id.equals("EPILOGUE_START")) {
                    // can ignore these
                } else if (mark.id.equals(SubstrateBackend.SubstrateMarkId.EPILOGUE_INCD_RSP)) {
                    SubstrateDebugFrameSizeChange sizeChange = new SubstrateDebugFrameSizeChange(mark.pcOffset, CONTRACT);
                    frameSizeChanges.add(sizeChange);
                } else if (mark.id.equals(SubstrateBackend.SubstrateMarkId.EPILOGUE_END) && mark.pcOffset < compilation.getTargetCodeSize()) {
                    /* There is code after this return point so notify a stack extend again. */
                    SubstrateDebugFrameSizeChange sizeChange = new SubstrateDebugFrameSizeChange(mark.pcOffset, EXTEND);
                    frameSizeChanges.add(sizeChange);
                }
            }
            return frameSizeChanges;
        }
    }

    private class SubstrateDebugLocationInfo extends SubstrateDebugMethodInfo implements DebugInfoProvider.DebugLocationInfo {
        private final int bci;
        private long lo;
        private long hi;
        private DebugInfoProvider.DebugLocationInfo callersLocationInfo;
        private List<DebugInfoProvider.DebugLocalValueInfo> localInfoList;
        private boolean isLeaf;

        SubstrateDebugLocationInfo(CompilationResultFrameTree.FrameNode frameNode, SubstrateDebugLocationInfo callersLocationInfo, int framesize) {
            this(frameNode.frame, frameNode.getStartPos(), frameNode.getEndPos() + 1, callersLocationInfo, framesize);
        }

        SubstrateDebugLocationInfo(BytecodePosition bcpos, long lo, long hi, SubstrateDebugLocationInfo callersLocationInfo, int framesize) {
            super(bcpos.getMethod());
            this.bci = bcpos.getBCI();
            this.lo = lo;
            this.hi = hi;
            this.callersLocationInfo = callersLocationInfo;
            this.localInfoList = initLocalInfoList(bcpos, framesize);
            // assume this is a leaf until we find out otherwise
            this.isLeaf = true;
            // tag the caller as a non-leaf
            if (callersLocationInfo != null) {
                callersLocationInfo.isLeaf = false;
            }
        }

        // special constructor for synthetic location info added at start of method
        SubstrateDebugLocationInfo(ResolvedJavaMethod method, long hi, ParamLocationProducer locProducer) {
            super(method);
            // bci is always 0 and lo is always 0.
            this.bci = 0;
            this.lo = 0;
            this.hi = hi;
            // this is always going to be a top-level leaf range.
            this.callersLocationInfo = null;
            // location info is synthesized off the method signature
            this.localInfoList = initSyntheticInfoList(locProducer);
            // assume this is a leaf until we find out otherwise
            this.isLeaf = true;
        }

        // special constructor for synthetic location info which splits off the initial segment
        // of the first range to accommodate a stack access prior to the stack extend
        SubstrateDebugLocationInfo(SubstrateDebugLocationInfo toSplit, int stackDecrement, int frameSize) {
            super(toSplit.method);
            this.lo = stackDecrement;
            this.hi = toSplit.hi;
            toSplit.hi = this.lo;
            this.bci = toSplit.bci;
            this.callersLocationInfo = toSplit.callersLocationInfo;
            this.isLeaf = toSplit.isLeaf;
            toSplit.isLeaf = true;
            this.localInfoList = new ArrayList<>(toSplit.localInfoList.size());
            for (DebugInfoProvider.DebugLocalValueInfo localInfo : toSplit.localInfoList) {
                if (localInfo.localKind() == DebugInfoProvider.DebugLocalValueInfo.LocalKind.STACKSLOT) {
                    // need to redefine the value for this param using a stack slot value
                    // that allows for the stack being extended by framesize. however we
                    // also need to remove any adjustment that was made to allow for the
                    // difference between the caller SP and the pre-extend callee SP
                    // because of a stacked return address.
                    int adjustment = frameSize - PRE_EXTEND_FRAME_SIZE;
                    SubstrateDebugLocalValue value = SubstrateDebugStackValue.create(localInfo, adjustment);
                    SubstrateDebugLocalValueInfo nativeLocalInfo = (SubstrateDebugLocalValueInfo) localInfo;
                    SubstrateDebugLocalValueInfo newLocalinfo = new SubstrateDebugLocalValueInfo(nativeLocalInfo.name,
                            value,
                            nativeLocalInfo.kind,
                            nativeLocalInfo.type,
                            nativeLocalInfo.slot,
                            nativeLocalInfo.line);
                    localInfoList.add(newLocalinfo);
                } else {
                    localInfoList.add(localInfo);
                }
            }
        }

        private List<DebugInfoProvider.DebugLocalValueInfo> initLocalInfoList(BytecodePosition bcpos, int framesize) {
            if (!(bcpos instanceof BytecodeFrame)) {
                return null;
            }

            BytecodeFrame frame = (BytecodeFrame) bcpos;
            if (frame.numLocals == 0) {
                return null;
            }
            // deal with any inconsistencies in the layout of the frame locals
            // NativeImageDebugFrameInfo debugFrameInfo = new NativeImageDebugFrameInfo(frame);

            LineNumberTable lineNumberTable = frame.getMethod().getLineNumberTable();
            Local[] localsBySlot = getLocalsBySlot();
            if (localsBySlot == null) {
                return Collections.emptyList();
            }
            int count = Integer.min(localsBySlot.length, frame.numLocals);
            ArrayList<DebugInfoProvider.DebugLocalValueInfo> localInfos = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Local l = localsBySlot[i];
                if (l != null) {
                    // we have a local with a known name, type and slot
                    String name = l.getName();
                    ResolvedJavaType ownerType = method.getDeclaringClass();
                    ResolvedJavaType type = l.getType().resolve(ownerType);
                    JavaKind kind = type.getJavaKind();
                    int slot = l.getSlot();
                    debugContext.log(DebugContext.DETAILED_LEVEL, "locals[%d] %s type %s slot %d", i, name, type.getName(), slot);
                    JavaValue value = (slot < frame.numLocals ? frame.getLocalValue(slot) : Value.ILLEGAL);
                    JavaKind storageKind = (slot < frame.numLocals ? frame.getLocalValueKind(slot) : JavaKind.Illegal);
                    debugContext.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, storageKind);
                    int bciStart = l.getStartBCI();
                    int firstLine = (lineNumberTable != null ? lineNumberTable.getLineNumber(bciStart) : -1);
                    // only add the local if the kinds match
                    if ((storageKind == kind) ||
                            isIntegralKindPromotion(storageKind, kind) ||
                            (kind == JavaKind.Object && storageKind == JavaKind.Long)) {
                        localInfos.add(new SubstrateDebugLocalValueInfo(name, value, framesize, storageKind, type, slot, firstLine));
                    } else if (storageKind != JavaKind.Illegal) {
                        debugContext.log(DebugContext.DETAILED_LEVEL, "  value kind incompatible with var kind %s!", type.getJavaKind());
                    }
                }
            }
            return localInfos;
        }

        private List<DebugInfoProvider.DebugLocalValueInfo> initSyntheticInfoList(ParamLocationProducer locProducer) {
            Signature signature = method.getSignature();
            int parameterCount = signature.getParameterCount(false);
            ArrayList<DebugInfoProvider.DebugLocalValueInfo> localInfos = new ArrayList<>();
            LocalVariableTable table = method.getLocalVariableTable();
            LineNumberTable lineNumberTable = method.getLineNumberTable();
            int firstLine = (lineNumberTable != null ? lineNumberTable.getLineNumber(0) : -1);
            int slot = 0;
            int localIdx = 0;
            ResolvedJavaType ownerType = method.getDeclaringClass();
            if (!method.isStatic()) {
                String name = "this";
                JavaKind kind = ownerType.getJavaKind();
                assert kind == JavaKind.Object : "must be an object";
                SubstrateDebugLocalValue value = locProducer.thisLocation();
                debugContext.log(DebugContext.DETAILED_LEVEL, "locals[%d] %s type %s slot %d", localIdx, name, ownerType.getName(), slot);
                debugContext.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, kind);
                localInfos.add(new SubstrateDebugLocalValueInfo(name, value, kind, ownerType, slot, firstLine));
                slot += kind.getSlotCount();
                localIdx++;
            }
            for (int i = 0; i < parameterCount; i++) {
                Local local = (table == null ? null : table.getLocal(slot, 0));
                String name = (local != null ? local.getName() : "__" + i);
                ResolvedJavaType paramType = (ResolvedJavaType) signature.getParameterType(i, ownerType);
                JavaKind kind = paramType.getJavaKind();
                SubstrateDebugLocalValue value = locProducer.paramLocation(i);
                debugContext.log(DebugContext.DETAILED_LEVEL, "locals[%d] %s type %s slot %d", localIdx, name, ownerType.getName(), slot);
                debugContext.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, kind);
                localInfos.add(new SubstrateDebugLocalValueInfo(name, value, kind, paramType, slot, firstLine));
                slot += kind.getSlotCount();
                localIdx++;
            }
            return localInfos;
        }

        private static boolean isIntegralKindPromotion(JavaKind promoted, JavaKind original) {
            return (promoted == JavaKind.Int &&
                    (original == JavaKind.Boolean || original == JavaKind.Byte || original == JavaKind.Short || original == JavaKind.Char));
        }

        private Local[] getLocalsBySlot() {
            LocalVariableTable lvt = method.getLocalVariableTable();
            Local[] nonEmptySortedLocals = null;
            if (lvt != null) {
                Local[] locals = lvt.getLocalsAt(bci);
                if (locals != null && locals.length > 0) {
                    nonEmptySortedLocals = Arrays.copyOf(locals, locals.length);
                    Arrays.sort(nonEmptySortedLocals, (Local l1, Local l2) -> l1.getSlot() - l2.getSlot());
                }
            }
            return nonEmptySortedLocals;
        }

        @Override
        public long addressLo() {
            return lo;
        }

        @Override
        public long addressHi() {
            return hi;
        }

        @Override
        public int line() {
            LineNumberTable lineNumberTable = method.getLineNumberTable();
            if (lineNumberTable != null && bci >= 0) {
                return lineNumberTable.getLineNumber(bci);
            }
            return -1;
        }

        @Override
        public DebugInfoProvider.DebugLocationInfo getCaller() {
            return callersLocationInfo;
        }

        @Override
        public DebugInfoProvider.DebugLocalValueInfo[] getLocalValueInfo() {
            if (localInfoList != null) {
                return localInfoList.toArray(new DebugInfoProvider.DebugLocalValueInfo[]{});
            } else {
                return new DebugInfoProvider.DebugLocalValueInfo[]{};
            }
        }

        @Override
        public boolean isLeaf() {
            return isLeaf;
        }

        public int depth() {
            int depth = 1;
            DebugInfoProvider.DebugLocationInfo caller = getCaller();
            while (caller != null) {
                depth++;
                caller = caller.getCaller();
            }
            return depth;
        }

        private int localsSize() {
            if (localInfoList != null) {
                return localInfoList.size();
            } else {
                return 0;
            }
        }

        /**
         * Merge the supplied leaf location info into this leaf location info if they have
         * contiguous ranges, the same method and line number and the same live local variables with
         * the same values.
         *
         * @param that a leaf location info to be merged into this one
         * @return this leaf location info if the merge was performed otherwise null
         */
        SubstrateDebugLocationInfo merge(SubstrateDebugLocationInfo that) {
            assert callersLocationInfo == that.callersLocationInfo;
            assert isLeaf == that.isLeaf;
            assert depth() == that.depth() : "should only compare sibling ranges";
            assert this.hi <= that.lo : "later nodes should not overlap earlier ones";
            if (this.hi != that.lo) {
                return null;
            }
            if (!method.equals(that.method)) {
                return null;
            }
            if (line() != that.line()) {
                return null;
            }
            int size = localsSize();
            if (size != that.localsSize()) {
                return null;
            }
            for (int i = 0; i < size; i++) {
                SubstrateDebugLocalValueInfo thisLocal = (SubstrateDebugLocalValueInfo) localInfoList.get(i);
                SubstrateDebugLocalValueInfo thatLocal = (SubstrateDebugLocalValueInfo) that.localInfoList.get(i);
                if (!thisLocal.equals(thatLocal)) {
                    return null;
                }
            }
            debugContext.log(DebugContext.DETAILED_LEVEL, "Merge  leaf Location Info : %s depth %d (%d, %d) into (%d, %d)", that.name(), that.depth(), that.lo, that.hi - 1, this.lo, this.hi - 1);
            // merging just requires updating lo and hi range as everything else is equal
            this.hi = that.hi;

            return this;
        }

        public SubstrateDebugLocationInfo split(int stackDecrement, int frameSize) {
            // this should be for an initial range extending beyond the stack decrement
            assert lo == 0 && lo < stackDecrement && stackDecrement < hi : "invalid split request";
            return new SubstrateDebugLocationInfo(this, stackDecrement, frameSize);
        }
    }

    /**
     * Size in bytes of the frame at call entry before any stack extend. Essentially this accounts
     * for any automatically pushed return address whose presence depends upon the architecture.
     */
    static final int PRE_EXTEND_FRAME_SIZE = ConfigurationValues.getTarget().arch.getReturnAddressSize();

    class ParamLocationProducer {
        private final ResolvedJavaMethod method;
        private final CallingConvention callingConvention;
        private boolean usesStack;

        SubstrateDebugLocalValue thisLocation() {

            assert !method.isStatic();
            return unpack(callingConvention.getArgument(0));
        }

        ParamLocationProducer(ResolvedJavaMethod method) {
            assert method instanceof SharedMethod;
            this.method = method;
            this.callingConvention = getCallingConvention((SharedMethod) method);
            // assume no stack slots until we find out otherwise
            this.usesStack = false;
        }

        SubstrateDebugLocalValue paramLocation(int paramIdx) {
            assert paramIdx < method.getSignature().getParameterCount(false);
            int idx = paramIdx;
            if (!method.isStatic()) {
                idx++;
            }
            return unpack(callingConvention.getArgument(idx));
        }

        private SubstrateDebugLocalValue unpack(AllocatableValue value) {
            if (value instanceof RegisterValue) {
                RegisterValue registerValue = (RegisterValue) value;
                return SubstrateDebugRegisterValue.create(registerValue);
            } else {
                // call argument must be a stack slot if it is not a register
                StackSlot stackSlot = (StackSlot) value;
                this.usesStack = true;
                // the calling convention provides offsets from the SP relative to the current
                // frame size. At the point of call the frame may or may not include a return
                // address depending on the architecture.
                return SubstrateDebugStackValue.create(stackSlot, PRE_EXTEND_FRAME_SIZE);
            }
        }


        /**
         * Retrieve details of the native calling convention for a top level compiled method, including
         * details of which registers or stack slots are used to pass parameters.
         *
         * @param method The method whose calling convention is required.
         * @return The calling convention for the method.
         */
        private SubstrateCallingConvention getCallingConvention(SharedMethod method) {
            SubstrateCallingConventionKind callingConventionKind = method.getCallingConventionKind();
            ResolvedJavaType declaringClass = method.getDeclaringClass();
            ResolvedJavaType receiverType = method.isStatic() ? null : declaringClass;
            var signature = method.getSignature();
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

        public boolean usesStack() {
            return usesStack;
        }
    }

    private class SubstrateDebugLocalInfo implements DebugInfoProvider.DebugLocalInfo {
        protected final String name;
        protected ResolvedJavaType type;
        protected final JavaKind kind;
        protected int slot;
        protected int line;

        SubstrateDebugLocalInfo(String name, JavaKind kind, ResolvedJavaType resolvedType, int slot, int line) {
            this.name = name;
            this.kind = kind;
            this.slot = slot;
            this.line = line;
            // if we don't have a type default it for the JavaKind
            // it may still end up null when kind is Undefined.
            this.type = resolvedType;
        }

        @Override
        public ResolvedJavaType valueType() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String typeName() {
            ResolvedJavaType valueType = valueType();
            return (valueType == null ? "" : valueType().toJavaName());
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public int slotCount() {
            return kind.getSlotCount();
        }

        @Override
        public JavaKind javaKind() {
            return kind;
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubstrateDebugLocalInfo)) {
                return false;
            }
            SubstrateDebugLocalInfo that = (SubstrateDebugLocalInfo) o;
            // locals need to have the same name
            if (!name.equals(that.name)) {
                return false;
            }
            // locals need to have the same type
            if (!type.equals(that.type)) {
                return false;
            }
            // values need to be for the same line
            if (line != that.line) {
                return false;
            }
            // kinds must match
            if (kind != that.kind) {
                return false;
            }
            // slots must match
            return slot == that.slot;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name) * 31 + line;
        }

        @Override
        public String toString() {
            return typeName() + " " + name;
        }
    }

    private class SubstrateDebugLocalValueInfo extends SubstrateDebugLocalInfo implements DebugInfoProvider.DebugLocalValueInfo {
        private final SubstrateDebugLocalValue value;
        private DebugInfoProvider.DebugLocalValueInfo.LocalKind localKind;

        SubstrateDebugLocalValueInfo(String name, JavaValue value, int framesize, JavaKind kind, ResolvedJavaType resolvedType, int slot, int line) {
            super(name, kind, resolvedType, slot, line);
            if (value instanceof RegisterValue) {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.REGISTER;
                this.value = SubstrateDebugRegisterValue.create((RegisterValue) value);
            } else if (value instanceof StackSlot) {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.STACKSLOT;
                this.value = SubstrateDebugStackValue.create((StackSlot) value, framesize);
            } else if (value instanceof JavaConstant constant && (constant instanceof PrimitiveConstant || constant.isNull())) {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.CONSTANT;
                this.value = SubstrateDebugConstantValue.create(constant);
            } else {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.UNDEFINED;
                this.value = null;
            }
        }

        SubstrateDebugLocalValueInfo(String name, SubstrateDebugLocalValue value, JavaKind kind, ResolvedJavaType type, int slot, int line) {
            super(name, kind, type, slot, line);
            if (value == null) {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.UNDEFINED;
            } else if (value instanceof SubstrateDebugRegisterValue) {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.REGISTER;
            } else if (value instanceof SubstrateDebugStackValue) {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.STACKSLOT;
            } else if (value instanceof SubstrateDebugConstantValue) {
                this.localKind = DebugInfoProvider.DebugLocalValueInfo.LocalKind.CONSTANT;
            }
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubstrateDebugLocalValueInfo)) {
                return false;
            }
            SubstrateDebugLocalValueInfo that = (SubstrateDebugLocalValueInfo) o;
            // values need to have the same name
            if (!name().equals(that.name())) {
                return false;
            }
            // values need to be for the same line
            if (line != that.line) {
                return false;
            }
            // location kinds must match
            if (localKind != that.localKind) {
                return false;
            }
            // locations must match
            switch (localKind) {
                case REGISTER:
                case STACKSLOT:
                case CONSTANT:
                    return value.equals(that.value);
                default:
                    return true;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(name(), value) * 31 + line;
        }

        @Override
        public String toString() {
            switch (localKind) {
                case REGISTER:
                    return "reg[" + regIndex() + "]";
                case STACKSLOT:
                    return "stack[" + stackSlot() + "]";
                case CONSTANT:
                    return "constant[" + (constantValue() != null ? constantValue().toValueString() : "null") + "]";
                default:
                    return "-";
            }
        }

        @Override
        public DebugInfoProvider.DebugLocalValueInfo.LocalKind localKind() {
            return localKind;
        }

        @Override
        public int regIndex() {
            return ((SubstrateDebugRegisterValue) value).getNumber();
        }

        @Override
        public int stackSlot() {
            return ((SubstrateDebugStackValue) value).getOffset();
        }

        @Override
        public long heapOffset() {
            return ((SubstrateDebugConstantValue) value).getHeapOffset();
        }

        @Override
        public JavaConstant constantValue() {
            return ((SubstrateDebugConstantValue) value).getConstant();
        }
    }

    public abstract static class SubstrateDebugLocalValue {
    }

    public static final class SubstrateDebugRegisterValue extends SubstrateDebugLocalValue {
        private static HashMap<Integer, SubstrateDebugRegisterValue> registerValues = new HashMap<>();
        private int number;
        private String name;

        private SubstrateDebugRegisterValue(int number, String name) {
            this.number = number;
            this.name = "reg:" + name;
        }

        static SubstrateDebugRegisterValue create(RegisterValue value) {
            int number = value.getRegister().number;
            String name = value.getRegister().name;
            return memoizedCreate(number, name);
        }

        static SubstrateDebugRegisterValue memoizedCreate(int number, String name) {
            SubstrateDebugRegisterValue reg = registerValues.get(number);
            if (reg == null) {
                reg = new SubstrateDebugRegisterValue(number, name);
                registerValues.put(number, reg);
            }
            return reg;
        }

        public int getNumber() {
            return number;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubstrateDebugRegisterValue)) {
                return false;
            }
            SubstrateDebugRegisterValue that = (SubstrateDebugRegisterValue) o;
            return number == that.number;
        }

        @Override
        public int hashCode() {
            return number * 31;
        }
    }

    public static final class SubstrateDebugStackValue extends SubstrateDebugLocalValue {
        private static HashMap<Integer, SubstrateDebugStackValue> stackValues = new HashMap<>();
        private int offset;
        private String name;

        private SubstrateDebugStackValue(int offset) {
            this.offset = offset;
            this.name = "stack:" + offset;
        }

        static SubstrateDebugStackValue create(StackSlot value, int framesize) {
            // Work around a problem on AArch64 where StackSlot asserts if it is
            // passed a zero frame size, even though this is what is expected
            // for stack slot offsets provided at the point of entry (because,
            // unlike x86, lr has not been pushed).
            int offset = (framesize == 0 ? value.getRawOffset() : value.getOffset(framesize));
            return memoizedCreate(offset);
        }

        static SubstrateDebugStackValue create(DebugInfoProvider.DebugLocalValueInfo previous, int adjustment) {
            assert previous.localKind() == DebugInfoProvider.DebugLocalValueInfo.LocalKind.STACKSLOT;
            return memoizedCreate(previous.stackSlot() + adjustment);
        }

        private static SubstrateDebugStackValue memoizedCreate(int offset) {
            SubstrateDebugStackValue value = stackValues.get(offset);
            if (value == null) {
                value = new SubstrateDebugStackValue(offset);
                stackValues.put(offset, value);
            }
            return value;
        }

        public int getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubstrateDebugStackValue)) {
                return false;
            }
            SubstrateDebugStackValue that = (SubstrateDebugStackValue) o;
            return offset == that.offset;
        }

        @Override
        public int hashCode() {
            return offset * 31;
        }
    }

    public static final class SubstrateDebugConstantValue extends SubstrateDebugLocalValue {
        private static HashMap<JavaConstant, SubstrateDebugConstantValue> constantValues = new HashMap<>();
        private JavaConstant value;
        private long heapoffset;

        private SubstrateDebugConstantValue(JavaConstant value, long heapoffset) {
            this.value = value;
            this.heapoffset = heapoffset;
        }

        static SubstrateDebugConstantValue create(JavaConstant value) {
            return create(value, -1);
        }

        static SubstrateDebugConstantValue create(JavaConstant value, long heapoffset) {
            SubstrateDebugConstantValue c = constantValues.get(value);
            if (c == null) {
                c = new SubstrateDebugConstantValue(value, heapoffset);
                constantValues.put(value, c);
            }
            assert c.heapoffset == heapoffset;
            return c;
        }

        public JavaConstant getConstant() {
            return value;
        }

        public long getHeapOffset() {
            return heapoffset;
        }

        @Override
        public String toString() {
            return "constant:" + value.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubstrateDebugConstantValue)) {
                return false;
            }
            SubstrateDebugConstantValue that = (SubstrateDebugConstantValue) o;
            return heapoffset == that.heapoffset && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value) * 31 + (int) heapoffset;
        }
    }

    /**
     * Implementation of the DebugFrameSizeChange API interface that allows stack frame size change
     * info to be passed to an ObjectFile when generation of debug info is enabled.
     */
    private class SubstrateDebugFrameSizeChange implements DebugInfoProvider.DebugFrameSizeChange {
        private int offset;
        private Type type;

        SubstrateDebugFrameSizeChange(int offset, Type type) {
            this.offset = offset;
            this.type = type;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public Type getType() {
            return type;
        }
    }
}
