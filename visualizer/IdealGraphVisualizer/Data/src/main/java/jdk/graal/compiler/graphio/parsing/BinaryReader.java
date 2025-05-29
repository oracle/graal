/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing;

import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.BEGIN_GRAPH;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.BEGIN_GROUP;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.CLOSE_GROUP;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.ENUM_KLASS;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.KLASS;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_CLASS;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_ENUM;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_FIELD;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_METHOD;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_NEW;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_NODE;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_NODE_CLASS;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_NODE_SOURCE_POSITION;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_NULL;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_SIGNATURE;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.POOL_STRING;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_ARRAY;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_DOUBLE;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_FALSE;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_FLOAT;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_INT;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_LONG;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_POOL;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_SUBGRAPH;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.PROPERTY_TRUE;
import static jdk.graal.compiler.graphio.parsing.BinaryStreamDefs.STREAM_PROPERTIES;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import jdk.graal.compiler.graphio.parsing.Builder.Length;
import jdk.graal.compiler.graphio.parsing.Builder.LengthToString;
import jdk.graal.compiler.graphio.parsing.Builder.ModelControl;
import jdk.graal.compiler.graphio.parsing.Builder.Node;
import jdk.graal.compiler.graphio.parsing.Builder.NodeClass;
import jdk.graal.compiler.graphio.parsing.Builder.Port;
import jdk.graal.compiler.graphio.parsing.Builder.TypedPort;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * The class reads the Graal binary dump format. All model object creation or property value
 * computation / logic is delegated to the {@link ModelBuilder} class. While the BinaryReader should
 * change seldom, together with Graal runtime, the ModelBuilder can be adapted or subclassed to
 * provide different ways how to process the binary data.
 * <p/>
 * Uses {@link BinarySource} to actually read the underlying stream. The Source can report positions
 * to the Builder.
 * <p/>
 * The Reader obtains initial {@link ConstantPool} from the builder; it also allows the Builder to
 * replace ConstantPool in the reader (useful for partial reading).
 */
public class BinaryReader implements GraphParser, ModelControl {
    private static final Logger LOG = Logger.getLogger(BinaryReader.class.getName());

    private final Logger instLog;

    private final DataSource dataSource;
    private NameTranslator nameTranslator;

    private final Deque<byte[]> hashStack;
    private int folderLevel;

    private ConstantPool constantPool;

    private final Builder builder;

    private static final class ErrorReporter {
        /**
         * Accumulates names of objects as they are parsed out of stream Used to report context for
         * irrecoverable errors which happen during reading BGV stream.
         */
        private final List<Supplier<String>> namesStack = new ArrayList<>();
        /**
         * The last name pop-ed off the {@link #namesStack}. Used as context to report an error at
         * the start or in the prologue of a next object.
         */
        private Supplier<String> lastName = null;

        /**
         * Part of object being read. Will be printed as a context information.
         */
        private Supplier<String> detail = null;

        public static Supplier<String> initialFollowing(Supplier<String> lastName) {
            return () -> String.format("< after %s >", lastName.get());
        }

        public static String addDetail(String message, Supplier<String> detail) {
            return String.format("%s during %s", message, detail.get());
        }

        public static String addPreceding(String message, Supplier<String> lastName) {
            return String.format("%s. Previous object: %s", message, lastName.get());
        }

        public void pushInitialContext(Supplier<String> init) {
            if (lastName == null) {
                pushContext(init);
            } else {
                pushContext(initialFollowing(lastName));
                lastName = null;
            }
        }

        public void pushContext(Function<Object, String> messageFactory, Object messageDetail) {
            pushContext(() -> messageFactory.apply(messageDetail));
        }

        public void pushContext(Supplier<String> n) {
            namesStack.add(n);
            detail = null;
        }

        public void updateContext(String n) {
            namesStack.set(namesStack.size() - 1, () -> n);
            lastName = null;
        }

        public void popContext() {
            lastName = namesStack.remove(namesStack.size() - 1);
            detail = null;
        }

        public void setDetail(Supplier<String> messageFactory) {
            detail = messageFactory;
        }

        public void reportLoadingError(String message, Builder builder) {
            List<String> parents = new ArrayList<>();
            String detailedMessage = message;
            try {
                for (Supplier<String> c : namesStack) {
                    parents.add(c.get());
                }
                if (detail != null) {
                    detailedMessage = addDetail(detailedMessage, detail);
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            if (lastName != null) {
                detailedMessage = addPreceding(detailedMessage, lastName);
            }
            builder.reportLoadingError(detailedMessage, parents);
        }
    }

    private static final class ContextStrings {
        private static String propertyGraph(Object s) {
            return String.format("Property %s graph", s);
        }

        private static String unknownObjectType(int i) {
            return String.format("Binary stream corrupted, unknown root object type: %d", i);
        }

        private static String firstGroup() {
            return "<first group>";
        }

        private static String firstGraph() {
            return "<first graph>";
        }

        private static String properties() {
            return "Properties";
        }

        private static String inProperty(Object key) {
            return String.format("Property %s", key);
        }

        private static String nodes() {
            return "Nodes";
        }

        private static String blocks() {
            return "Blocks";
        }

        private static String loadingGraphErrorMessage(String message) {
            return String.format("Corrupted graph data: %s, loading terminated", message);
        }

        private static String inBlock(Object id) {
            return String.format("Block %s", id);
        }

        private static String inNode(Object id) {
            return String.format("Node %s", id);
        }

        private static String nodeProperties() {
            return "Node properties";
        }

        private static String edges() {
            return "Edges";
        }
    }

    private final ErrorReporter reporter;

    public abstract static class Member implements LengthToString {
        public final Klass holder;
        public final int accessFlags;
        public final String name;

        private Member(Klass holder, String name, int accessFlags) {
            assert holder != null : "GraphElements.methodDeclaringClass must not return null!";
            assert name != null;
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

    public static final class Method extends Member {
        public final Signature signature;
        public final byte[] code;

        /* package-private */
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

    public static final class Field extends Member {
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

    public static final class EnumKlass extends Klass {
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

    public static final class EnumValue implements LengthToString {
        public EnumKlass enumKlass;
        public int ordinal;

        EnumValue(EnumKlass enumKlass, int ordinal) {
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

    @SuppressWarnings("this-escape")
    public BinaryReader(DataSource dataSource, Builder builder) {
        this.dataSource = dataSource;
        this.builder = builder;
        this.reporter = new ErrorReporter();
        this.constantPool = builder.getConstantPool();
        this.hashStack = new LinkedList<>();
        this.instLog = Logger.getLogger(LOG.getName() + "." + Integer.toHexString(System.identityHashCode(dataSource)));
        // allow the builder to reconfigure the reader.
        builder.setModelControl(this);
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

    private static boolean assertObjectType(Class<?> klass, int type) {
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
        assert assertObjectType(klass, type) : "Wrong object type : " + klass + " != " + type;
        Object obj;
        switch (type) {
            case POOL_CLASS: {
                String name = dataSource.readString();
                String conv = nameTranslator == null ? null : nameTranslator.translate(name);
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
                    obj = new EnumKlass(name, values);
                } else if (klasstype == KLASS) {
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
                if (dataSource.getMajorVersion() < 6) {
                    String fileName = readPoolObject(String.class);
                    int line = -1; // in sync with GraalVM, 0 can be a valid line #
                    if (fileName != null) {
                        line = dataSource.readInt();
                    }
                    LocationStackFrame parent = readPoolObject(LocationStackFrame.class);
                    obj = LocationCache.createFrame(method, bci,
                                    LocationCache.fileLineStratum(fileName, line), parent);
                } else {
                    ArrayList<LocationStratum> infos = new ArrayList<>();
                    while (true) { // TERMINATION ARGUMENT: stack traces can reach arbitrary depth
                        String uri = readPoolObject(String.class);
                        if (uri == null) {
                            break;
                        }
                        String language = dataSource.readString();
                        int line = dataSource.readInt();
                        int startOffset = dataSource.readInt();
                        int endOffset = dataSource.readInt();

                        infos.add(LocationCache.createStratum(uri, null, language, line, startOffset, endOffset));
                    }
                    if (infos.isEmpty()) {
                        // no file information is available, must add at least one strata for "Java"
                        LocationCache.fileLineStratum(null, -1);
                    }
                    LocationStackFrame parent = readPoolObject(LocationStackFrame.class);
                    infos.trimToSize();
                    obj = LocationCache.createFrame(method, bci, infos, parent);
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
                break;
            }
            default:
                throw new IOException("unknown pool type");
        }
        return constantPool.addPoolEntry(index, obj, -1);
    }

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
                reporter.pushContext(ContextStrings::propertyGraph, key); // NOI18N
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
            while (true) { // TERMINATION ARGUMENT: finite length of data source will result in
                           // EOFException eventually.
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
        reporter.popContext();
    }

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
                    reporter.reportLoadingError(ContextStrings.unknownObjectType(type), builder);
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

    protected Group parseGroup() throws IOException {
        reporter.pushInitialContext(ContextStrings::firstGroup);
        Group group = builder.startGroup();
        String name = readPoolObject(String.class);
        String shortName = readPoolObject(String.class);
        reporter.updateContext(name);
        Method method = readPoolObject(Method.class);
        int bci = dataSource.readInt();
        builder.setGroupName(name, shortName);
        parseProperties();
        builder.setMethod(name, shortName, bci, method);
        return group;
    }

    private InputGraph parseGraph() throws IOException {
        reporter.pushInitialContext(ContextStrings::firstGraph);
        nameTranslator = builder.prepareNameTranslator();
        if (dataSource.getMajorVersion() < 2) {
            String title = readPoolObject(String.class);
            reporter.updateContext(title);
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
        reporter.updateContext(sb.toString());
        return parseGraph(dumpId, format, args, true);
    }

    private void parseProperties() throws IOException {
        reporter.pushContext(ContextStrings::properties);
        parseProperties(builder::setProperty);
        reporter.popContext();
    }

    private void parseProperties(BiConsumer<String, Object> propertyConsumer) throws IOException {
        int propCount = dataSource.readShort();
        if (dataSource.getMajorVersion() > 7) {
            propCount = (propCount == Character.MAX_VALUE ? dataSource.readInt() : propCount);
        }
        builder.setPropertySize(propCount);
        for (int j = 0; j < propCount; j++) {
            String key = readPoolObject(String.class);
            reporter.pushContext(ContextStrings::inProperty, key);
            Object value = readPropertyObject(key);
            propertyConsumer.accept(key, value);
            reporter.popContext();
        }
    }

    private void computeGraphDigest() {
        byte[] d = dataSource.finishDigest();
        builder.graphContentDigest(d);
        hashStack.pop();
        hashStack.push(d);
    }

    private InputGraph parseGraph(String title, boolean toplevel) throws IOException {
        int index = title.indexOf(":");
        if (index == -1) {
            return parseGraph(-1, title, new Object[0], toplevel);
        } else {
            return parseGraph(Integer.parseInt(title.substring(0, index)), title.substring(index + 1).trim(), new Object[0], toplevel);
        }
    }

    private InputGraph parseGraph(int dumpId, String format, Object[] args, boolean toplevel) throws IOException {
        InputGraph g = builder.startGraph(dumpId, format, args);
        try {
            parseProperties();
            reporter.setDetail(ContextStrings::nodes);
            builder.startGraphContents(g);
            dataSource.startDigest();
            parseNodes();
            reporter.setDetail(ContextStrings::blocks);
            parseBlocks();
            if (toplevel) {
                computeGraphDigest();
            }
        } catch (SkipRootException ex) {
            throw ex;
        } catch (IOException | RuntimeException | Error ex) {
            reporter.reportLoadingError(ContextStrings.loadingGraphErrorMessage(ex.toString()), builder);
            throw ex;
        } finally {
            // graph does NOT contain terminator byte, in case we encounter a SkipRootException or
            // EOF
            // we have to finish the graph.
            reporter.popContext();
            g = builder.endGraph();
        }
        return g;
    }

    private void parseBlocks() throws IOException {
        try {
            int blockCount = dataSource.readInt();
            for (int i = 0; i < blockCount; i++) {
                int id = dataSource.readInt();
                reporter.pushContext(ContextStrings::inBlock, id); // NOI18N
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
                reporter.popContext();
            }
        } finally {
            // graph does NOT contain terminator byte, in case we encounter a SkipRootException or
            // EOF
            // we have to finish the graph.
            builder.makeBlockEdges();
        }
    }

    private void createEdges(int id, int startNum, List<? extends Port> portList, EdgeBuilder factory) throws IOException {
        int portNum = 0;
        for (Port p : portList) {
            if (p.isList) {
                int size = dataSource.readShort();
                for (int j = 0; j < size; j++) {
                    int in = dataSource.readInt();
                    p.ids.add(in);
                    factory.edge(p, in, id, (char) (startNum + portNum), j);
                    portNum++;
                }
            } else {
                int in = dataSource.readInt();
                p.ids.add(in);
                factory.edge(p, in, id, (char) (startNum + portNum), -1);
                portNum++;
            }
            p.ids = new ArrayList<>();
        }
    }

    interface EdgeBuilder {
        void edge(Port p, int from, int to, char num, int index);
    }

    private void parseNodes() throws IOException {
        try {
            int count = dataSource.readInt();
            for (int i = 0; i < count; i++) {
                int id = dataSource.readInt();
                reporter.pushContext(ContextStrings::inNode, id);
                NodeClass nodeClass = readPoolObject(NodeClass.class);
                int preds = dataSource.readByte();
                builder.startNode(id, preds > 0, nodeClass);
                reporter.setDetail(ContextStrings::nodeProperties);
                parseProperties(builder::setNodeProperty);
                reporter.setDetail(ContextStrings::edges);
                createEdges(id, preds, nodeClass.inputs, builder::inputEdge);
                createEdges(id, 0, nodeClass.sux, builder::successorEdge);
                builder.setNodeName(nodeClass);
                builder.endNode(id);
                reporter.popContext();
            }
        } finally {
            // graph does NOT contain terminator byte, in case we encounter a SkipRootException or
            // EOF
            // we have to finish the graph.
            builder.makeGraphEdges();
        }
    }

    protected void loadDocumentProperties() throws IOException {
        if (dataSource.getMajorVersion() < 7) {
            throw new IllegalStateException("Document properties unexpected in version < 7");
        }
        try {
            builder.startDocumentHeader();
            parseProperties();
        } finally {
            builder.endDocumentHeader();
        }
    }

    @Override
    public final ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * Used during reading, to compact, reset or change constant pool. Use with great care, wrong
     * constant pool may damage the rest of reading process.
     */
    @Override
    public void setConstantPool(ConstantPool cp) {
        this.constantPool = cp;
    }
}
