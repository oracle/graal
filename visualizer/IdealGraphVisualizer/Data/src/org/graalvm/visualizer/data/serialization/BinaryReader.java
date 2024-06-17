/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.data.serialization;

import org.graalvm.visualizer.data.impl.DataSrcApiAccessor;
import org.graalvm.visualizer.data.src.LocationStackFrame;
import org.graalvm.visualizer.data.src.LocationStratum;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.serialization.Builder.TypedPort;
import org.graalvm.visualizer.data.serialization.Builder.Length;
import org.graalvm.visualizer.data.serialization.Builder.LengthToString;
import org.graalvm.visualizer.data.serialization.Builder.NodeClass;
import org.graalvm.visualizer.data.serialization.Builder.Port;
import static org.graalvm.visualizer.data.serialization.BinaryStreamDefs.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.graalvm.visualizer.data.serialization.Builder.ModelControl;
import org.graalvm.visualizer.data.serialization.Builder.Node;
import org.openide.util.NbBundle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The class reads the Graal binary dump format. All model object creation or
 * property value computation / logic is delegated to the {@link ModelBuilder}
 * class. While the BinaryReader should change seldom, together with Graal
 * runtime, the ModelBuilder can be adapted or subclassed to provide different
 * ways how to process the binary data.
 * <p/>
 * Uses {@link BinarySource} to actually read the underlying stream. The Source
 * can report positions to the Builder.
 * <p/>
 * The Reader obtains initial {@link ConstantPool} from the builder; it also
 * allows the Builder to replace ConstantPool in the reader (useful for partial
 * reading).
 */
public class BinaryReader implements GraphParser, ModelControl {
    private static final boolean POOL_STATS = Boolean.getBoolean(BinaryReader.class.getName() + ".poolStats");
    private static final Logger LOG = Logger.getLogger(BinaryReader.class.getName());

    private final Logger instLog;

    private final DataSource dataSource;
    private BinaryMap binaryMap;

    private final Deque<byte[]> hashStack;
    private int folderLevel;

    private ConstantPool constantPool;

    private final Builder builder;
    // diagnostics only
    private int constantPoolSize;
    private int graphReadCount;

    /**
     * Accumulates names of objects as they are parsed out of stream Used to
     * report context for irrecoverable errors which happen during reading BGV
     * stream.
     */
    private List<Supplier<String>> namesStack = new ArrayList<>();

    /**
     * The last name pop-ed off the {@link #nameStack}. Used as context to
     * report an error at the start or in the prologue of a next object
     */
    private Supplier<String> lastName;

    /**
     * Part of object being read. Will be printed as a context information
     */
    private Supplier<String> part;

    /**
     * Debugging information String concatenation postponing to avoid performance issues.
     */
    private void parsedPart(Function<Object, String> messageFactory, Object messageDetail) {
        part = () -> messageFactory.apply(messageDetail);
    }

    /**
     * Debugging information String concatenation postponing to avoid performance issues.
     */
    private void parsedPart(Supplier<String> messageFactory) {
        part = () -> messageFactory.get();
    }

    /**
     * Debugging information String concatenation postponing to avoid performance issues.
     */
    private void parsedPart(String message) {
        part = () -> message;
    }

    private static abstract class Member implements LengthToString {
        public final Klass holder;
        public final int accessFlags;
        public final String name;

        public Member(Klass holder, String name, int accessFlags) {
            this.holder = holder;
            this.accessFlags = accessFlags;
            this.name = name;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.holder);
            hash = 29 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Member other = (Member) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return Objects.equals(this.holder, other.holder);
        }
    }

    /**
     * This type is public by mistake. Do not use outside Data module, it will
     * be refactored to a non-public package or changed.
     *
     * @deprecated do not use outside Data module
     */
    @Deprecated
    public final static class Method extends Member {
        public final Signature signature;
        public final byte[] code;

        public Method(String name, Signature signature, byte[] code, Klass holder, int accessFlags) {
            super(holder, name, accessFlags);
            this.signature = signature;
            this.code = code;
        }

        @Override
        public String toString() {
            int sz = 0;
            for (int i = 0; i < signature.argTypes.length; i++) {
                sz += 1 + signature.argTypes[i].length();
            }
            sz += name.length();
            StringBuilder sb = new StringBuilder(sz + 4);
            sb.append(holder).append('.').append(name).append('(');
            for (int i = 0; i < signature.argTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(signature.argTypes[i]);
            }
            sb.append(')');
            return sb.toString();
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case M:
                    return holder.toString(Length.L) + "." + name;
                case S:
                    return holder.toString(Length.S) + "." + name;
                default:
                case L:
                    return toString();
            }
        }

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = 79 * hash + Objects.hashCode(this.signature);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            final Method other = (Method) obj;
            if (!Objects.equals(this.signature, other.signature)) {
                return false;
            }
            return Arrays.equals(this.code, other.code);
        }
    }

    public static final class Signature {
        public final String returnType;
        public final String[] argTypes;
        private final int hash;

        public Signature(String returnType, String[] argTypes) {
            this.returnType = returnType;
            this.argTypes = argTypes;
            this.hash = toString().hashCode();
        }

        @Override
        public String toString() {
            return "Signature(" + returnType + ":" + Arrays.toString(argTypes) + ")";
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Signature other = (Signature) obj;
            if (!Objects.equals(this.returnType, other.returnType)) {
                return false;
            }
            return Arrays.equals(this.argTypes, other.argTypes);
        }
    }

    /**
     * This type is public by mistake. Do not use outside Data module, it will
     * be refactored to a non-public package or changed.
     *
     * @deprecated do not use outside Data module
     */
    @Deprecated
    public static class Field extends Member {
        public final String type;

        public Field(String type, Klass holder, String name, int accessFlags) {
            super(holder, name, accessFlags);
            this.type = type;
        }

        @Override
        public String toString() {
            return holder + "." + name;
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case M:
                    return holder.toString(Length.L) + "." + name;
                case S:
                    return holder.toString(Length.S) + "." + name;
                default:
                case L:
                    return toString();
            }
        }

    }

    /**
     * This type is public by mistake. Do not use outside Data module, it will
     * be refactored to a non-public package or changed.
     *
     * @deprecated do not use outside Data module
     */
    @Deprecated
    public static class Klass implements LengthToString {
        public final String name;
        public final String simpleName;
        private final int hash;

        public Klass(String name) {
            this.name = name;
            String simple;
            try {
                simple = name.substring(name.lastIndexOf('.') + 1);
            } catch (IndexOutOfBoundsException e) {
                simple = name;
            }
            this.simpleName = simple;
            this.hash = (simple + "#" + name).hashCode();
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case S:
                    return simpleName;
                default:
                case L:
                case M:
                    return toString();
            }
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Klass other = (Klass) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return Objects.equals(this.simpleName, other.simpleName);
        }

    }

    /**
     * This type is public by mistake. Do not use outside Data module, it will
     * be refactored to a non-public package or changed.
     *
     * @deprecated do not use outside Data module
     */
    @Deprecated
    public static class EnumKlass extends Klass {
        public final String[] values;

        public EnumKlass(String name, String[] values) {
            super(name);
            this.values = values;
        }

        @Override
        public int hashCode() {
            return super.hash * 31 + Arrays.hashCode(values);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            final EnumKlass other = (EnumKlass) obj;
            return Arrays.equals(values, other.values);
        }
    }

    /**
     * This type is public by mistake. Do not use outside Data module, it will
     * be refactored to a non-public package or changed.
     *
     * @deprecated do not use outside Data module
     */
    @Deprecated
    public static class EnumValue implements LengthToString {
        public EnumKlass enumKlass;
        public int ordinal;

        public EnumValue(EnumKlass enumKlass, int ordinal) {
            this.enumKlass = enumKlass;
            this.ordinal = ordinal;
        }

        @Override
        public String toString() {
            return enumKlass.simpleName + "." + enumKlass.values[ordinal];
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case S:
                    return enumKlass.values[ordinal];
                default:
                case M:
                case L:
                    return toString();
            }
        }

        @Override
        public int hashCode() {
            return (ordinal + 7) * 13 ^ enumKlass.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final EnumValue other = (EnumValue) obj;
            if (this.ordinal != other.ordinal) {
                return false;
            }
            return Objects.equals(this.enumKlass, other.enumKlass);
        }
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public BinaryReader(DataSource dataSource, Builder builder) {
        this.dataSource = dataSource;
        this.builder = builder;
        this.constantPool = builder.getConstantPool();
        // allow the builder to reconfigure the reader.
        builder.setModelControl(this);
        hashStack = new LinkedList<>();
        instLog = Logger.getLogger(LOG.getName() + "." + Integer.toHexString(System.identityHashCode(dataSource)));

        LocationStackFrame.init();
    }

    private Object[] readPoolObjects() throws IOException {
        int len = dataSource.readInt();
        if (len < 0) {
            return null;
        }
        Object[] props = new Object[len];
        for (int i = 0; i < len; i++) {
            props[i] = readPoolObject(Object.class);
        }
        return props;
    }

    @SuppressWarnings("unchecked")
    private <T> T readPoolObject(Class<T> klass) throws IOException {
        int type = dataSource.readByte();
        if (type == POOL_NULL) {
            return null;
        }
        if (type == POOL_NEW) {
            return (T) addPoolEntry(klass);
        }
        assert assertObjectType(klass, type) : "Wrong object type : " + klass + " != " + type;
        char index = dataSource.readShort();
        if (index >= constantPool.size()) {
            throw new IOException("Invalid constant pool index : " + index);
        }
        Object obj = getPoolData(index);
        return (T) obj;
    }

    private Object getPoolData(int index) {
        return constantPool.get(index, -1);
    }

    private boolean assertObjectType(Class<?> klass, int type) {
        switch (type) {
            case POOL_CLASS:
                return klass.isAssignableFrom(EnumKlass.class);
            case POOL_ENUM:
                return klass.isAssignableFrom(EnumValue.class);
            case POOL_METHOD:
                return klass.isAssignableFrom(Method.class);
            case POOL_STRING:
                return klass.isAssignableFrom(String.class);
            case POOL_NODE_CLASS:
                return klass.isAssignableFrom(NodeClass.class);
            case POOL_FIELD:
                return klass.isAssignableFrom(Field.class);
            case POOL_SIGNATURE:
                return klass.isAssignableFrom(Signature.class);
            case POOL_NODE_SOURCE_POSITION:
                return klass.isAssignableFrom(LocationStackFrame.class);
            case POOL_NODE:
                return klass.isAssignableFrom(Node.class);
            case POOL_NULL:
                return true;
            default:
                return false;
        }
    }

    private Object addPoolEntry(Class<?> klass) throws IOException {
        char index = dataSource.readShort();
        int type = dataSource.readByte();
        int size = 0;
        assert assertObjectType(klass, type) : "Wrong object type : " + klass + " != " + type;
        Object obj;
        switch (type) {
            case POOL_CLASS: {
                String name = dataSource.readString();
                BinaryMap map = binaryMap;
                String conv = map == null ? null : map.translate(name);
                if (conv != null) {
                    name = conv;
                }
                int klasstype = dataSource.readByte();
                if (klasstype == ENUM_KLASS) {
                    int len = dataSource.readInt();
                    String[] values = new String[len];
                    for (int i = 0; i < len; i++) {
                        values[i] = readPoolObject(String.class);
                    }
                    size = 2 + name.length();
                    obj = new EnumKlass(name, values);
                } else if (klasstype == KLASS) {
                    size = name.length();
                    obj = new Klass(name);
                } else {
                    throw new IOException("unknown klass type : " + klasstype);
                }
                break;
            }
            case POOL_ENUM: {
                EnumKlass enumClass = readPoolObject(EnumKlass.class);
                int ordinal = dataSource.readInt();
                obj = new EnumValue(enumClass, ordinal);
                size = 2;
                break;
            }
            case POOL_NODE_CLASS: {
                String className;
                if (dataSource.getMajorVersion() < 2) {
                    className = dataSource.readString();
                } else {
                    Klass nodeClass = readPoolObject(Klass.class);
                    className = nodeClass.toString();
                }
                String nameTemplate = dataSource.readString();
                size = nameTemplate.length();
                int inputCount = dataSource.readShort();
                List<TypedPort> inputs = new ArrayList<>(inputCount);
                for (int i = 0; i < inputCount; i++) {
                    boolean isList = dataSource.readByte() != 0;
                    String name = readPoolObject(String.class);
                    EnumValue inputType = readPoolObject(EnumValue.class);
                    inputs.add(new TypedPort(isList, name, inputType));
                }
                int suxCount = dataSource.readShort();
                List<Port> sux = new ArrayList<>(suxCount);
                for (int i = 0; i < suxCount; i++) {
                    boolean isList = dataSource.readByte() != 0;
                    String name = readPoolObject(String.class);
                    sux.add(new Port(isList, name));
                }
                obj = new NodeClass(className, nameTemplate, inputs, sux);
                break;
            }
            case POOL_METHOD: {
                Klass holder = readPoolObject(Klass.class);
                String name = readPoolObject(String.class);
                Signature sign = readPoolObject(Signature.class);
                int flags = dataSource.readInt();
                byte[] code = dataSource.readBytes();
                obj = new Method(name, sign, code, holder, flags);
                break;
            }
            case POOL_FIELD: {
                Klass holder = readPoolObject(Klass.class);
                String name = readPoolObject(String.class);
                String fType = readPoolObject(String.class);
                int flags = dataSource.readInt();
                obj = new Field(fType, holder, name, flags);
                break;
            }
            case POOL_SIGNATURE: {
                int argc = dataSource.readShort();
                String[] args = new String[argc];
                for (int i = 0; i < argc; i++) {
                    args[i] = readPoolObject(String.class);
                }
                String returnType = readPoolObject(String.class);
                obj = new Signature(returnType, args);
                break;
            }
            case POOL_NODE_SOURCE_POSITION: {
                Method method = readPoolObject(Method.class);
                int bci = dataSource.readInt();
                DataSrcApiAccessor accessor = DataSrcApiAccessor.getInstance();
                if (dataSource.getMajorVersion() < 6) {
                    String fileName = readPoolObject(String.class);
                    int line = -1; // in sync with GraalVM, 0 can be a valid line #
                    if (fileName != null) {
                        line = dataSource.readInt();
                    }
                    LocationStackFrame parent = readPoolObject(LocationStackFrame.class);
                    obj = accessor.createFrame(method, bci,
                                               accessor.fileLineStratum(fileName, line), parent);
                } else {
                    ArrayList<LocationStratum> infos = new ArrayList<>();
                    for (;;) {
                        String uri = readPoolObject(String.class);
                        if (uri == null) {
                            break;
                        }
                        String language = dataSource.readString();
                        int line = dataSource.readInt();
                        int startOffset = dataSource.readInt();
                        int endOffset = dataSource.readInt();

                        infos.add(accessor.createStratum(uri, null, language, line, startOffset, endOffset));
                    }
                    if (infos.isEmpty()) {
                        // no file information is available, must add at least one strata for "Java"
                        accessor.fileLineStratum(null, -1);
                    }
                    LocationStackFrame parent = readPoolObject(LocationStackFrame.class);
                    infos.trimToSize();
                    obj = accessor.createFrame(method, bci, infos, parent);
                }
                break;
            }
            case POOL_NODE: {
                int id = dataSource.readInt();
                NodeClass clazz = readPoolObject(NodeClass.class);
                obj = new Node(id, clazz);
                break;
            }
            case POOL_STRING: {
                obj = dataSource.readString();
                size = obj.toString().length();
                break;
            }
            default:
                throw new IOException("unknown pool type");
        }
        if (POOL_STATS) {
            recordNewEntry(obj, size);
        }
        this.constantPoolSize += size;
        return constantPool.addPoolEntry(index, obj, -1);
    }

    /**
     * Each value holds 2 ints - 0 is the approx size of the data, 1 is the
     * number of addPooLEntry calls for this value - the number of redundant
     * appearances in the constant pool.
     */
    private final Map<Object, int[]> poolEntries = new LinkedHashMap<>(100, 0.8f, true);

    private void recordNewEntry(Object data, int size) {
        // TODO: the stats can be compacted from time to time - e.g. if the number of objects goes
        // large,
        // entries with < N usages can be removed in a hope they are rare.
        int[] v = poolEntries.get(data);
        if (v == null) {
            v = new int[]{size, 0};
            poolEntries.put(data, v);
        }
        v[1]++;
    }

    public void dumpPoolStats() {
        if (poolEntries.isEmpty()) {
            return;
        }
        List<Map.Entry<Object, int[]>> entries = new ArrayList(poolEntries.entrySet());
        Collections.sort(entries, (o1, o2) -> {
             return o1.getValue()[0] * o1.getValue()[1] - o2.getValue()[0] * o2.getValue()[1];
         });
        int oneSize = 0;
        int totalSize = 0;
        for (Map.Entry<Object, int[]> e : poolEntries.entrySet()) {
            oneSize += e.getValue()[0];
            totalSize += e.getValue()[1] * e.getValue()[0];
        }
        // ignore small overhead
        if (totalSize < oneSize * 2) {
            return;
        }
        // ignore small # of duplications
        if (entries.get(entries.size() - 1).getValue()[1] < 10) {
            return;
        }
        instLog.log(Level.FINE, "Dumping cpool statistics");
        instLog.log(Level.FINE, "Total {0} values, {1} size of useful data, {2} size with redefinitions", new Object[]{poolEntries.size(), oneSize, totalSize});
        instLog.log(Level.FINE, "Dumping the most consuming entries:");
        int count = 0;
        for (int i = entries.size() - 1; count < 50 && i >= 0; i--, count++) {
            Map.Entry<Object, int[]> e = entries.get(i);
            instLog.log(Level.FINE, "#{0}\t: {1}, size {2}, redefinitions {3}", new Object[]{count, e.getKey(), e.getValue()[0] * e.getValue()[1], e.getValue()[1]});
        }
    }

    @NbBundle.Messages({
        "# {0} - property name",
        "MSG_ContextPropertyGraph=Property {0} graph"
    })
    private Object readPropertyObject(String key) throws IOException {
        int type = dataSource.readByte();
        switch (type) {
            case PROPERTY_INT:
                return dataSource.readInt();
            case PROPERTY_LONG:
                return dataSource.readLong();
            case PROPERTY_FLOAT:
                return dataSource.readFloat();
            case PROPERTY_DOUBLE:
                return dataSource.readDouble();
            case PROPERTY_TRUE:
                return Boolean.TRUE;
            case PROPERTY_FALSE:
                return Boolean.FALSE;
            case PROPERTY_POOL:
                return readPoolObject(Object.class);
            case PROPERTY_ARRAY:
                int subType = dataSource.readByte();
                switch (subType) {
                    case PROPERTY_INT:
                        return dataSource.readInts();
                    case PROPERTY_DOUBLE:
                        return dataSource.readDoubles();
                    case PROPERTY_POOL:
                        return readPoolObjects();
                    default:
                        throw new IOException("Unknown type");
                }
            case PROPERTY_SUBGRAPH:
                pushName(Bundle::MSG_ContextPropertyGraph, key); // NOI18N
                builder.startNestedProperty(key);
                // will pop the name in its finally
                return parseGraph("", false);
            default:
                throw new IOException("Unknown type");
        }
    }

    private void closeDanglingGroups() throws IOException {
        while (folderLevel > 0) {
            // the builder may need to record the root position to close the group's entry
            builder.startRoot();
            doCloseGroup();
        }
        builder.end();
    }

    @Override
    public GraphDocument parse() throws IOException {
        hashStack.push(null);

        boolean restart = false;
        try {
            while (true) {
                // allows to concatenate BGV files; at the top-level, either BIGV signature,
                // or 0x00-0x02 should be present.
                // Check for a version specification
                if (dataSource.readHeader() && restart) {
                    // if not at the start of the stream, reinitialize the constant pool.
                    closeDanglingGroups();
                    builder.resetStreamData();
                    constantPool = builder.getConstantPool();
                }
                restart = true;
                if (dataSource.getMajorVersion() < 1) {
                    throw new VersionMismatchException("File header is missing");
                }
                parseRoot();
            }
        } catch (EOFException e) {
            // ignore
        } finally {
            // also terminates the builder
            closeDanglingGroups();
            dumpPoolStats();
        }
        return builder.rootDocument();
    }

    protected void beginGroup() throws IOException {
        parseGroup();
        folderLevel++;
        hashStack.push(null);
        // note: startGroupContent MAY throw SkipRootException; but unlike graph
        // group contains root terminator byte, which will be read by parseRoot()
        // immediately after.
        builder.startGroupContent();
    }

    private void doCloseGroup() throws IOException {
        if (folderLevel-- == 0) {
            throw new IOException("Unbalanced groups");
        }
        builder.endGroup();
        hashStack.pop();
        popName();
    }

    @NbBundle.Messages({
        "# {0} - object type ID",
        "ERROR_LoaderError=Binary stream corrupted, unknown root object type: {0}"
    })
    protected void parseRoot() throws IOException {
        try {
            builder.startRoot();
            int type = dataSource.readByte();
            // startRoot may also throw SkipRootException
            switch (type) {
                case BEGIN_GRAPH: {
                    parseGraph();
                    break;
                }
                case BEGIN_GROUP: {
                    beginGroup();
                    break;
                }
                case CLOSE_GROUP: {
                    doCloseGroup();
                    break;
                }
                case STREAM_PROPERTIES: {
                    loadDocumentProperties();
                    break;
                }
                default:
                    reportLoadingError(Bundle.ERROR_LoaderError(type));
                    throw new IOException("unknown root : " + type);
            }
        } catch (SkipRootException ex) {
            long s = ex.getStart();
            long e = ex.getEnd();
            long pos = dataSource.getMark();
            ConstantPool pool = ex.getConstantPool();
            if (e == -1) {
                // skip rest of the stream
                throw new EOFException();
            }
            instLog.log(Level.FINE, "Skipping to offset {0}, {1} bytes skipped, using cpool", new Object[]{e, e - pos, Integer.toHexString(pool == null ? 0 : System.identityHashCode(pool))});

            assert s < pos && e >= pos;
            if (pos < e) {
                long count = e - pos;
                byte[] scratch = new byte[(int) Math.min(count, 1024 * 1024 * 50)];
                while (count > 0) {
                    int l = (int) Math.min(scratch.length, count);
                    dataSource.readBytes(scratch, l);
                    count -= l;
                }
            }
            if (pool != null) {
                setConstantPool(pool);
            }
        }
    }

    @NbBundle.Messages({
        "# {0} - the message",
        "# {1} - the current phase",
        "MSG_ErrorsWithContext={0} during {1}",
        "# {0} - the message",
        "# {1} - the previous entry name",
        "MSG_ErrorsWithPreceding={0}. Previous object: {1}"
    })
    private void reportLoadingError(String message) {
        List<String> parents = new ArrayList<>();
        try {
            for (Supplier<String> c : namesStack) {
                parents.add(c.get());
            }
            if (part != null) {
                message = Bundle.MSG_ErrorsWithContext(message, part.get());
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        if (lastName != null) {
            message = Bundle.MSG_ErrorsWithPreceding(message, lastName.get());
        }
        builder.reportLoadingError(message, parents);
    }

    private void popName() {
        Supplier<String> s = namesStack.remove(namesStack.size() - 1);
        lastName = s;
        part = null;
    }

    /**
     * Debugging information String concatenation postponing to avoid performance issues.
     */
    @NbBundle.Messages({
        "# {0} - the previously closed object name",
        "MSG_ContextAfterObject=< after {0} >"
    })
    private void pushInitialName(Supplier<String> init) {
        if (lastName == null) {
            pushName(init);
        } else {
            pushName(Bundle::MSG_ContextAfterObject, lastName.get());
            lastName = null;
        }
    }

    /**
     * Debugging information String concatenation postponing to avoid performance issues.
     */
    private void pushName(Function<Object, String> messageFactory, Object messageDetail) {
        pushName(() -> messageFactory.apply(messageDetail));
    }

    /**
     * Debugging information String concatenation postponing to avoid performance issues.
     */
    private void pushName(Supplier<String> n) {
        namesStack.add(n);
        part = null;
    }

    /**
     * Debugging information String concatenation postponing to avoid performance issues.
     */
    private void replaceRealName(String n) {
        namesStack.set(namesStack.size() - 1, () -> n);
        lastName = null;
    }

    @NbBundle.Messages({
        "MSG_ContextFirstGroup=<first group>"
    })
    protected Group parseGroup() throws IOException {
        pushInitialName(() -> Bundle.MSG_ContextFirstGroup()); // NOI18N
        Group group = builder.startGroup();
        String name = readPoolObject(String.class);
        String shortName = readPoolObject(String.class);
        replaceRealName(name);
        Method method = readPoolObject(Method.class);
        int bci = dataSource.readInt();
        builder.setGroupName(name, shortName);
        parseProperties();
        builder.setMethod(name, shortName, bci, method);
        return group;
    }

    @NbBundle.Messages({
        "MSG_ContextFirstGraph=<first graph>"
    })
    private InputGraph parseGraph() throws IOException {
        pushInitialName(() -> Bundle.MSG_ContextFirstGraph()); // NOI18N
        binaryMap = builder.prepareBinaryMap();
        if (dataSource.getMajorVersion() < 2) {
            String title = readPoolObject(String.class);
            replaceRealName(title);
            return parseGraph(title, true);
        }
        int dumpId = dataSource.readInt();
        String format = dataSource.readString();
        int argsCount = dataSource.readInt();
        Object[] args = new Object[argsCount];
        StringBuilder sb = new StringBuilder(format);
        sb.append("{");
        if (dataSource.getMajorVersion() == 2) {
            for (int i = 0; i < argsCount; i++) {
                args[i] = readPoolObject(Object.class);
                sb.append(args[i]);
                sb.append(", ");
            }
        } else {
            for (int i = 0; i < argsCount; i++) {
                args[i] = readPropertyObject(null);
                sb.append(args[i]);
                sb.append(", ");
            }
        }
        sb.append("}");
        replaceRealName(sb.toString());
        return parseGraph(dumpId, format, args, true);
    }

    @NbBundle.Messages({
        "MSG_ContextProperites=Properties",
        "# {0} - property name",
        "MSG_ContextInProperty=Property {0}",
        "# {0} - property name",
        "MSG_ContextAfterProperty=After property {0}",
        "MSG_ContextAfterProperites=After Properties",})
    private void parseProperties() throws IOException {
        parseProperties(() -> parsedPart(Bundle::MSG_ContextProperites),
                        key -> parsedPart(Bundle::MSG_ContextInProperty, key),
                        key -> parsedPart(Bundle::MSG_ContextAfterProperty, key),
                        () -> parsedPart(Bundle::MSG_ContextAfterProperites)); // NOI18N
    }

    private void parseProperties(Runnable beginLog, Consumer<String> beginKeyLog, Consumer<String> endKeyLog, Runnable afterLog) throws IOException {
        parseProperties(builder::setProperty,
                        builder::setPropertySize,
                        beginLog,
                        beginKeyLog,
                        endKeyLog,
                        afterLog);
    }

    private void parseProperties(BiConsumer<String, Object> propertyConsumer, Runnable beginLog, Consumer<String> beginKeyLog, Consumer<String> endKeyLog, Runnable afterLog) throws IOException {
        parseProperties(propertyConsumer,
                        builder::setPropertySize,
                        beginLog,
                        beginKeyLog,
                        endKeyLog,
                        afterLog);
    }

    private void parseProperties(BiConsumer<String, Object> propertyConsumer, Consumer<Integer> sizePrepare, Runnable beginLog, Consumer<String> beginKeyLog, Consumer<String> endKeyLog, Runnable afterLog) throws IOException {
        beginLog.run();
        int propCount = dataSource.readShort();
        if (dataSource.getMajorVersion() > 7) {
            propCount = (propCount == Character.MAX_VALUE ? dataSource.readInt() : propCount);
        }
        sizePrepare.accept(propCount);
        for (int j = 0; j < propCount; j++) {
            String key = readPoolObject(String.class);
            beginKeyLog.accept(key);
            Object value = readPropertyObject(key);
            propertyConsumer.accept(key, value);
            endKeyLog.accept(key);
        }
        afterLog.run();
    }

    private void computeGraphDigest() {
        byte[] d = dataSource.finishDigest();
        byte[] hash = hashStack.peek();
        builder.graphContentDigest(d);
        hashStack.pop();
        hashStack.push(d);
    }

    private InputGraph parseGraph(String title, boolean toplevel) throws IOException {
        int index = title.indexOf(":");
        if (index == -1) {
            return parseGraph(-1, title, new Object[0], toplevel);
        } else {
            return parseGraph(Integer.parseInt(title.substring(0, index)), title.substring(index + 1, title.length()).trim(), new Object[0], toplevel);
        }
    }

    @NbBundle.Messages({
        "# {0} - the message",
        "ERROR_LoadingGraph=Corrupted graph data: {0}, loading terminated",
        "MSG_ContextNodes=Nodes",
        "MSG_ContextBlocks=Blocks"
    })
    private InputGraph parseGraph(int dumpId, String format, Object[] args, boolean toplevel) throws IOException {
        boolean ok = false;
        graphReadCount++;
        InputGraph g = builder.startGraph(dumpId, format, args);
        try {
            parseProperties();
            parsedPart(Bundle::MSG_ContextNodes);
            builder.startGraphContents(g);
            dataSource.startDigest();
            parseNodes();
            parsedPart(Bundle::MSG_ContextBlocks);
            parseBlocks();
            if (toplevel) {
                computeGraphDigest();
            }
            ok = true;
        } catch (SkipRootException ex) {
            throw ex;
        } catch (IOException | RuntimeException | Error ex) {
            reportLoadingError(Bundle.ERROR_LoadingGraph(ex.getLocalizedMessage()));
            throw ex;
        } finally {
            // graph does NOT contain terminator byte, in case we encounter a SkipRootException or EOF
            // we have to finish the graph.
            popName();
            g = builder.endGraph();
        }
        return g;
    }

    @NbBundle.Messages({
        "# {0} Block ID",
        "MSG_ContextInBlock=Block {0}"
    })
    private void parseBlocks() throws IOException {
        try {
            int blockCount = dataSource.readInt();
            for (int i = 0; i < blockCount; i++) {
                int id = dataSource.readInt();
                pushName(Bundle::MSG_ContextInBlock, id); // NOI18N
                builder.startBlock(id);
                int nodeCount = dataSource.readInt();
                for (int j = 0; j < nodeCount; j++) {
                    int nodeId = dataSource.readInt();
                    if (nodeId < 0) {
                        continue;
                    }
                    builder.addNodeToBlock(nodeId);
                }
                builder.endBlock(id);
                int edgeCount = dataSource.readInt();
                for (int j = 0; j < edgeCount; j++) {
                    int to = dataSource.readInt();
                    builder.addBlockEdge(id, to);
                }
                popName();
            }
        } finally {
            // graph does NOT contain terminator byte, in case we encounter a SkipRootException or EOF
            // we have to finish the graph.
            builder.makeBlockEdges();
        }
    }

    protected final void createEdges(int id, int preds, List<? extends Port> portList,
                                     boolean dir,
                                     EdgeBuilder factory) throws IOException {
        int portNum = 0;
        for (Port p : portList) {
            if (p.isList) {
                int size = dataSource.readShort();
                for (int j = 0; j < size; j++) {
                    int in = dataSource.readInt();
                    p.ids.add(in);
                    if (in >= 0) {
                        factory.edge(p, in, id, (char) (preds + portNum), j);
                        portNum++;
                    }
                }
            } else {
                int in = dataSource.readInt();
                p.ids.add(in);
                if (in >= 0) {
                    factory.edge(p, in, id, (char) (preds + portNum), -1);
                    portNum++;
                }
            }
            p.ids = new ArrayList<>();
        }
    }

    interface EdgeBuilder {
        void edge(Port p, int from, int to, char num, int index);
    }

    @NbBundle.Messages({
        "# {0} - node ID",
        "MSG_ContextInNode=Node {0}",
        "MSG_ContextNodeProperties=Node properties",
        "MSG_ContextEdges=Edges"
    })
    private void parseNodes() throws IOException {
        try {
            int count = dataSource.readInt();
            for (int i = 0; i < count; i++) {
                int id = dataSource.readInt();
                pushName(Bundle::MSG_ContextInNode, id);
                NodeClass nodeClass = readPoolObject(NodeClass.class);
                int preds = dataSource.readByte();
                builder.startNode(id, preds > 0, nodeClass);
                parseProperties(builder::setNodeProperty,
                                () -> parsedPart(Bundle::MSG_ContextNodeProperties),
                                key -> pushName(Bundle::MSG_ContextInProperty, key),
                                key -> popName(),
                                () -> parsedPart(Bundle::MSG_ContextEdges));
                createEdges(id, preds, nodeClass.inputs, true, builder::inputEdge);
                createEdges(id, 0, nodeClass.sux, true, builder::successorEdge);
                builder.setNodeName(nodeClass);
                builder.endNode(id);
                popName();
            }
        } finally {
            // graph does NOT contain terminator byte, in case we encounter a SkipRootException or EOF
            // we have to finish the graph.
            builder.makeGraphEdges();
        }
    }

    protected void loadDocumentProperties() throws IOException {
        if (dataSource.getMajorVersion() < 7) {
            throw new IllegalStateException("Document properties unexpected in version < 7");
        }
        try {
            parseProperties(builder::startDocumentHeader,
                            key -> pushName(Bundle::MSG_ContextInProperty, key),
                            key -> popName(),
                            () -> {
                            });
        } finally {
            builder.endDocumentHeader();
        }
    }

    @Override
    public final ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * Used during reading, to compact, reset or change constant pool. Use with
     * great care, wrong constant pool may damage the rest of reading process.
     *
     * @param cp
     */
    @Override
    public void setConstantPool(ConstantPool cp) {
        this.constantPool = cp;
    }
}
