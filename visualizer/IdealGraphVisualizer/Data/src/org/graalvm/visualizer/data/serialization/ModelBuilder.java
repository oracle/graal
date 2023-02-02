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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputEdge;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputMethod;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.Property;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_BLOCK;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_CLASS;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_DUPLICATE;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_HAS_PREDECESSOR;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_ID;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_IDX;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_NAME;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_SHORT_NAME;
import static org.graalvm.visualizer.data.KnownPropertyValues.CLASS_ENDNODE;
import org.graalvm.visualizer.data.services.GroupCallback;
import org.graalvm.visualizer.data.serialization.BinaryReader.EnumValue;
import org.graalvm.visualizer.data.serialization.BinaryReader.Method;
import org.graalvm.visualizer.data.services.GraphClassifier;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.openide.util.NbBundle;
import java.util.*;

/**
 * Builds a model based on SAX-like events. The expected sequence of events is:
 * <ul>
 * <li><b>startGroup</b>, [properties], <b>startGroupContent</b>, groups|graphs,
 * <b>endGroup</b>
 * <li><b>startGraph</b>, [properties], [nodes], [blocks], [edges],
 * <b>makeGraphEdges</b>, [blocks],
 * <b>makeBlockEdges</b>, <b>endGraph</b>
 * <li><b>startNode</b>, [properties], [edges], <b>endNode</b>
 * </ul>
 * The Builder is overridable to allow customized processing of the incoming
 * stream, i.e. partial loading.
 * <p/>
 * createXXX methods are used as factories for data objects, so that specialized
 * implementations may be created. pushXXX must be used at the object start if
 * the object should be read and constructed. Otherwise all the events up to the
 * object close must be blocked/ignored.
 * <p/>
 *
 */
public class ModelBuilder implements Builder {
    private static final Logger LOG = Logger.getLogger(ModelBuilder.class.getName());

    public static final String NO_BLOCK = "noBlock"; // NOI18N

    private static final boolean INTERN = Boolean.getBoolean("IGV.internStrings"); // NOI18N

    private static String maybeIntern(String s) {
        if (INTERN) {
            if (s == null) {
                return null;
            }
            return s.intern();
        } else {
            return s;
        }
    }

    public static final class EdgeInfo {
        final int from;
        final int to;
        final char num;
        final String label;
        final String type;
        final boolean input;

        EdgeInfo(int from, int to) {
            this(from, to, (char) 0, null, null, false);
        }

        EdgeInfo(int from, int to, char num, String label, String type, boolean input) {
            this.from = from;
            this.to = to;
            this.label = maybeIntern(label);
            this.type = maybeIntern(type);
            this.num = num;
            this.input = input;
        }
        
        public int getFrom() {
            return from;
        }
        
        public int getTo() {
            return to;
        }
        
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (other.getClass() != EdgeInfo.class) {
                return false;
            }
            EdgeInfo otherEdge = (EdgeInfo) other;
            return from == otherEdge.from && to == otherEdge.to && num == otherEdge.num && input == otherEdge.input && Objects.equals(label, otherEdge.label) && Objects.equals(type, otherEdge.type);
        }

        public int hashCode() {
            return Objects.hash(from, to, label);
        }
    }

    private final GroupCallback callback;
    private final ParseMonitor monitor;
    private final DocumentFactory rootDocumentFactory;

    private GraphDocument rootDocument;
    private ConstantPool pool = new ConstantPool();
    private ModelControl control;

    private Properties.Entity entity;
    private Folder folder;

    private InputGraph currentGraph;
    private List<EdgeInfo> inputEdges;
    private List<EdgeInfo> successorEdges;
    private List<EdgeInfo> nodeEdges;
    private List<EdgeInfo> blockEdges;

    private InputNode currentNode;
    private String propertyObjectKey;
    private InputBlock currentBlock;
    
    private Properties newProperties;
    private Properties documentLevelProperties = Properties.newProperties();

    private GraphClassifier classifier = GraphClassifier.INSTANCE;
    
    private Object documentId;

    /**
     * Content digests for individual graph types.
     */
    private Map<String, byte[]> lastDigests = new HashMap<>();
    
    private Deque<Object> stack = new ArrayDeque<>();

    class Level {
        InputNode n = currentNode;
        String pk = propertyObjectKey;

        InputGraph g = currentGraph;
        Folder f = folder;
        
        void apply() {
            currentNode = n;
            currentGraph = g;
            folder = f;
            propertyObjectKey = pk;

            if (currentNode != null) {
                entity = currentNode;
            } else if (currentGraph != null) {
                entity = currentGraph;
            } else if (folder instanceof Properties.Entity) {
                entity = (Properties.Entity) folder;
            }
        }
    }

    public ModelBuilder(GraphDocument rootDocument, GroupCallback callback, ParseMonitor monitor) {
        this.callback = callback;
        this.rootDocument = rootDocument;
        this.monitor = monitor;
        this.folder = rootDocument;
        this.rootDocumentFactory = null;
    }
    
    public ModelBuilder(DocumentFactory factory, GroupCallback callback, ParseMonitor monitor) {
        this.callback = callback;
        this.rootDocument = null;
        this.folder = null;
        this.monitor = monitor;
        this.rootDocumentFactory = factory;
    }
    
    public <T extends ModelBuilder> T setDocumentId(Object id) {
        this.documentId = id;
        return (T)this;
    }

    void setGraphClassifier(GraphClassifier c) {
        this.classifier = c;
    }

    protected GraphClassifier getGraphClassifier() {
        return classifier;
    }

    @Override
    public final GraphDocument rootDocument() {
        return rootDocument;
    }

    public final Folder folder() {
        return folder;
    }

    public final InputGraph graph() {
        return currentGraph;
    }

    protected final InputNode node() {
        return currentNode;
    }

    private Folder getParent() {
        if (currentGraph != null) {
            return folder;
        } else {
            Object o = stack.peek();
            return o instanceof Folder ? (Folder) o : null;
        }
    }

    protected void popContext() {
        if (currentNode != null) {
            currentNode = null;
            propertyObjectKey = null;
            nodeEdges = null;
        } else if (currentGraph != null) {
            currentGraph = null;
            currentNode = null;
            inputEdges = null;
            successorEdges = null;
            blockEdges = null;
        }
        Object o;
        if (stack.isEmpty()) {
            o = folder = rootDocument;
        } else {
            o = stack.pop();
            if (o instanceof InputGraph) {
                currentGraph = (InputGraph) o;
            } else if (o instanceof InputNode) {
                currentNode = (InputNode) o;

                Object[] oo = (Object[]) stack.pop();
                currentGraph = (InputGraph) oo[0];
                inputEdges = (List) oo[1];
                successorEdges = (List) oo[2];
                nodeEdges = (List) oo[3];
            } else if (o instanceof Folder) {
                this.folder = (Folder) o;
                lastDigests = (Map<String, byte[]>)stack.pop();
            }
        }
        if (o instanceof Properties.Entity) {
            this.entity = (Properties.Entity) o;
        }
        this.newProperties = null;
    }

    private void pushContext() {
        this.newProperties = null;
        if (currentNode != null) {
            stack.push(new Object[]{currentGraph, inputEdges, successorEdges, nodeEdges});
            stack.push(currentNode);
            currentNode = null;
            currentGraph = null;
        } else if (currentGraph != null) {
            stack.push(currentGraph);
            currentNode = null;
        } else if (folder != null) {
            stack.push(lastDigests);
            stack.push(folder);
        }
    }

    @Override
    public void setPropertySize(int size) {
        getProperties().reserve(size);
    }
    
    @Override
    public void setProperty(String key, Object value) {
        getProperties().setProperty(key, value);
    }

    protected Properties getProperties() {
        if (newProperties != null) {
            return newProperties;
        } else {
            return entity.getProperties();
        }
    }
    
    protected Properties.Entity getEntity() {
        return entity;
    }

    @Override
    public BinaryMap prepareBinaryMap() {
        BinaryMap versions = BinaryMap.versions();
        Iterator<Property> it = ((Properties.Entity) (folder != null ? folder : entity)).getProperties().iterator();
        final String PREFIX = "version.";
        while (it.hasNext()) {
            Property p = it.next();
            if (p.getName().startsWith(PREFIX)) {
                versions.request(p.getName().substring(PREFIX.length()), p.getValue().toString());
            }
        }
        return versions;
    }

    @Override
    public void startNestedProperty(String propertyKey) {
        assert propertyObjectKey == null;
        this.propertyObjectKey = propertyKey;
    }

    protected final String getNestedProperty() {
        return propertyObjectKey;
    }

    protected final InputGraph pushGraph(InputGraph g) {
        pushContext();
        if (currentNode != null) {
            // nested graph -> no duplicates should be recorded or checked.
            lastDigests = new HashMap<>();
        }
        currentGraph = g;
        entity = g;
        inputEdges = new ArrayList<>();
        successorEdges = new ArrayList<>();
        return g;
    }
    
    protected void connectModifiedProperties(FolderElement g) {
        if (!(g instanceof Properties.MutableOwner)) {
            return;
        }
        Properties.MutableOwner mu = (Properties.MutableOwner)g;
        GraphDocument doc = g.getOwner();
        if (doc != null) {
            Properties props = doc.getModifiedProperties(g);
            if (props != null) {
                mu.updateProperties(props);
            }
        }
    }

    protected InputGraph createGraph(Properties.Entity parent, int dumpId, String format, Object[] args) {
        return new InputGraph(null, dumpId, format, args);
    }

    protected final InputGraph doCreateGraph(Properties.Entity parent, Object id, int dumpId, String format, Object[] args) {
        InputGraph g = new InputGraph(id, dumpId, format, args);
        connectModifiedProperties(g);
        return g;
    }
    
    /**
     * At the end of the graph, informs about the digest of the graph's contents.
     * The digest can be used to detect duplicities.
     * @param digest 
     */
    @Override
    public void graphContentDigest(byte[] digest) {
        InputGraph g = graph();
        if (g == null) {
            return;
        }
        String t = g.getGraphType();
        if (t == null) {
            t = ""; // NOI18N
        }
        byte[] prevDigest = lastDigests.put(t, digest);
        if (Arrays.equals(prevDigest, digest)) {
            markGraphDuplicate();
        }
    }

    @Override
    public void startGraphContents(InputGraph g) {
        if (g == null) {
            return;
        }
        String graphType = null;
        GraphClassifier cl = getGraphClassifier();
        if (cl != null) {
            graphType = classifier.classifyGraphType(g.getProperties());
        }

        if (graphType == null) {
            graphType = InputGraph.DEFAULT_TYPE;
        }
        g.setGraphType(graphType);
    }

    @Override
    @CheckForNull
    public InputGraph startGraph(int dumpId, String format, Object[] args) {
        if (monitor != null) {
            monitor.updateProgress();
        }
        InputNode n = currentNode;
        InputGraph g;

        if (n != null) {
            g = createGraph(n, n.getId(), propertyObjectKey, new Object[0]);
            propertyObjectKey = null;
        } else {
            g = createGraph((Group) folder, dumpId, format, args);
        }
        return pushGraph(g);
    }
    
    /**
     * Fake group ID. The fake group encloses a nested graph and its ID
     * is defined by the parent graph and the child graph IDs.
     */
    private static class FakeGID {
        private final Object outerId;
        private final Object nestedID;

        public FakeGID(Object outerId, Object nestedID) {
            this.outerId = outerId;
            this.nestedID = nestedID;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 17 * hash + Objects.hashCode(this.outerId);
            hash = 17 * hash + Objects.hashCode(this.nestedID);
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
            final FakeGID other = (FakeGID) obj;
            if (!Objects.equals(this.outerId, other.outerId)) {
                return false;
            }
            if (!Objects.equals(this.nestedID, other.nestedID)) {
                return false;
            }
            return true;
        }
    }

    @Override
    @CheckForNull
    public InputGraph endGraph() {
        InputGraph g = currentGraph;
        for (InputNode node : g.getNodes()) {
            node.internProperties();
        }
        popContext();
        if (currentNode != null) {
            new Group(null, new FakeGID(currentGraph.getID(), g.getID())).addElement(g);
            currentNode.addSubgraph(g);
        } else {
            registerToParent(folder, g);
        }
        return g;
    }

    @Override
    public void start() {
        if (monitor != null) {
            monitor.setState("Starting parsing");
        }
    }

    @Override
    public void end() {
        if (monitor != null) {
            monitor.setState("Finished parsing");
        }
    }

    protected final Group pushGroup(Group group, boolean startNew) {
        pushContext();
        entity = group;
        this.folder = group;
        if (startNew) {
            lastDigests = new HashMap<>();
        }
        return group;
    }

    protected Group createGroup(Folder parent) {
        return doCreateGroup(parent, null);
    }
    
    protected Group doCreateGroup(Folder parent, Object id) {
        Group g = new Group(parent, id);
        connectModifiedProperties(g);
        return g;
    }
    
    @Override
    @CheckForNull
    public Group startGroup() {
        Group group = createGroup(folder);
        Group g = pushGroup(group, true);
        return g;
    }
    
    protected void rootDocumentResolved(GraphDocument doc) {
        this.rootDocument = doc;
    }

    @Override
    public void startDocumentHeader() {
        Folder f = getParent();
        if (f != null) {
            if (!(f instanceof GraphDocument)) {
                throw new IllegalStateException("Document header not at root level.");
            }
            newProperties = ((GraphDocument)f).getProperties();
        } else {
            // note: if there are more document headers in the stream,
            // the last property value wins. All document-level property sets merge in the result.
            if (rootDocument == null) {
                newProperties = Properties.newProperties();
            } else {
                newProperties = rootDocument.getProperties();
            }
        }
    }

    @Override
    public void endDocumentHeader() {
        if (newProperties == null) {
            throw new IllegalStateException("Unexpected end document header");
        }
        documentLevelProperties = newProperties;
        newProperties = null;
    }
    
    private GraphDocument resolveDocument(Properties props, Group g) {
        if (rootDocument != null) {
            return rootDocument;
        }
        rootDocument = rootDocumentFactory.documentFor(documentId, props, g);
        
        if (rootDocument == null) {
            throw new IllegalStateException("Could not find a parent for group " + folder);
        }
        rootDocumentResolved(rootDocument);
        return rootDocument;
    }
    
    @Override
    public void startGroupContent() {
        assert folder instanceof Group;
        Group g = (Group) folder;
        Folder parent = getParent();
        // If the parent is null, it's not decided yet, and must be determined
        // based on the new group's properties. Failure to do so will
        // throw an exception.
        if (parent == null) {
            parent = resolveDocument(documentLevelProperties, g);
            g.setParent(parent);
        }
        if (callback != null && parent instanceof GraphDocument) {
            callback.started(g);
        }
        if (callback == null || parent instanceof Group) {
            registerToParent(parent, (FolderElement) folder);
        }
    }

    @Override
    public void endGroup() {
        popContext();
    }

    protected final int level() {
        return stack.size();
    }

    @Override
    public void markGraphDuplicate() {
        getProperties().setProperty(PROPNAME_DUPLICATE, "true"); // NOI18N
    }

    /**
     * Registers an item to its folder. Operation executes in
     * {@link #modelExecutor}.
     *
     * @param parent the folder
     * @param item the item.
     */
    protected void registerToParent(Folder parent, FolderElement item) {
        parent.addElement(item);
    }

    protected final void pushNode(InputNode node) {
        pushContext();
        entity = currentNode = node;
        nodeEdges = new ArrayList<>();
    }

    protected InputNode createNode(int id, NodeClass nodeClass) {
        return new InputNode(id, nodeClass);
    }

    @Override
    public void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass) {
        assert currentGraph != null;
        InputNode node = createNode(nodeId, nodeClass);
        // TODO -- intern strings for the numbers
        node.getProperties().setProperty(PROPNAME_IDX, Integer.toString(nodeId)); // NOI18N
        if (hasPredecessors) {
            node.getProperties().setProperty(PROPNAME_HAS_PREDECESSOR, "true"); // NOI18N
        }
        pushNode(node);
        registerToParent(currentGraph, node);
    }

    protected void registerToParent(InputGraph g, InputNode n) {
        g.addNode(n);
    }

    @Override
    public void endNode(int nodeId) {
        popContext();
    }

    static final Set<String> SYSTEM_PROPERTIES = new HashSet<>(Arrays.asList(
                    PROPNAME_HAS_PREDECESSOR,
                    PROPNAME_NAME,
                    PROPNAME_CLASS,
                    PROPNAME_ID,
                    PROPNAME_IDX, PROPNAME_BLOCK));

    @Override
    public void setGroupName(String name, String shortName) {
        assert folder instanceof Group;
        setProperty(PROPNAME_NAME, name);
        reportState(name);
    }

    protected final void reportState(String name) {
        if (monitor != null) {
            monitor.setState(name);
        }
    }

    protected final void reportProgress() {
        if (monitor != null) {
            monitor.updateProgress();
        }
    }

    @Override
    public void setNodeName(NodeClass nodeClass) {
        assert currentNode != null;
        getProperties().setProperty(PROPNAME_NAME, createName(nodeClass, nodeEdges, nodeClass.nameTemplate));
        getProperties().setProperty(PROPNAME_CLASS, nodeClass.className);
        switch (nodeClass.className) {
            case "BeginNode":
                getProperties().setProperty(PROPNAME_SHORT_NAME, "B");
                break;
            case CLASS_ENDNODE:
                getProperties().setProperty(PROPNAME_SHORT_NAME, "E");
                break;
        }
    }

    static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{(p|i)#([a-zA-Z0-9$_]+)(/(l|m|s))?\\}");

    private String createName(NodeClass nodeClass, List<EdgeInfo> edges, String template) {
        if (template.isEmpty()) {
            return nodeClass.toShortString();
        }
        Matcher m = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        Properties p = getProperties();
        while (m.find()) {
            String name = m.group(2);
            String type = m.group(1);
            String result;
            switch (type) {
                case "i":
                    StringBuilder inputString = new StringBuilder();
                    for (EdgeInfo edge : edges) {
                        if (edge.label.startsWith(name) && (name.length() == edge.label.length() || edge.label.charAt(name.length()) == '[')) {
                            if (inputString.length() > 0) {
                                inputString.append(", ");
                            }
                            inputString.append(edge.from);
                        }
                    }
                    result = inputString.toString();
                    break;
                case "p":
                    Object prop = p.get(name);
                    String length = m.group(4);
                    if (prop == null) {
                        result = "?";
                    } else if (length != null && prop instanceof LengthToString) {
                        LengthToString lengthProp = (LengthToString) prop;
                        switch (length) {
                            default:
                            case "l":
                                result = lengthProp.toString(Length.L);
                                break;
                            case "m":
                                result = lengthProp.toString(Length.M);
                                break;
                            case "s":
                                result = lengthProp.toString(Length.S);
                                break;
                        }
                    } else {
                        result = prop.toString();
                    }
                    break;
                default:
                    result = "#?#";
                    break;
            }

            // Escape '\' and '$' to not interfere with the regular expression.
            StringBuilder newResult = new StringBuilder();
            for (int i = 0; i < result.length(); ++i) {
                char c = result.charAt(i);
                if (c == '\\') {
                    newResult.append("\\\\");
                } else if (c == '$') {
                    newResult.append("\\$");
                } else {
                    newResult.append(c);
                }
            }
            result = newResult.toString();
            m.appendReplacement(sb, result);
        }
        m.appendTail(sb);
        return maybeIntern(sb.toString());
    }

    private static final String NOT_DATA = maybeIntern("!data.");

    @Override
    public void setNodeProperty(String key, Object value) {
        assert currentNode != null;
        if (!(value instanceof InputGraph)) {
            if (SYSTEM_PROPERTIES.contains(key)) {
                key = NOT_DATA + key;
            }
            setProperty(key, value);
        }
    }

    @Override
    public void inputEdge(Port p, int from, int to, char num, int index) {
        assert currentNode != null;
        String label = (p.isList && index >= 0) ? p.name + "[" + index + "]" : p.name;
        EnumValue type = ((TypedPort) p).type;
        EdgeInfo ei = new EdgeInfo(from, to, num, label, type == null ? null : type.toString(Length.S), true);
        inputEdges.add(ei);
        nodeEdges.add(ei);
    }

    @Override
    public void successorEdge(Port p, int from, int to, char num, int index) {
        assert currentNode != null;
        String label = (p.isList && index >= 0) ? p.name + "[" + index + "]" : p.name;
        EdgeInfo ei = new EdgeInfo(to, from, num, label, InputEdge.SUCCESSOR_EDGE_TYPE, false);
        successorEdges.add(ei);
        nodeEdges.add(ei);
    }

    protected InputEdge immutableEdge(char fromIndex, char toIndex, int from, int to, String label, String type) {
        return InputEdge.createImmutable(fromIndex, toIndex, from, to, label, type);
    }

    @NbBundle.Messages({
        "# {0} - edge label",
        "# {1} - endpoint node ID",
        "ERR_StartNodeNotExists=Start node for edge {0} does not exist: {0}",
        "# {0} - edge label",
        "# {1} - endpoint node ID",
        "ERR_EndNodeNotExists=End node for edge {0} does not exist: {0}"
    })
    @Override
    public void makeGraphEdges() {
        assert currentGraph != null;
        InputGraph graph = currentGraph;
        assert (inputEdges != null && successorEdges != null) || (graph.getNodes().isEmpty());

        Set<InputNode> nodesWithSuccessor = new HashSet<>();
        for (EdgeInfo e : successorEdges) {
            assert !e.input;
            char fromIndex = e.num;
            nodesWithSuccessor.add(graph.getNode(e.from));
            char toIndex = 0;
            if (currentGraph.getNode(e.from) == null) {
                reportLoadingError(Bundle.ERR_StartNodeNotExists(e.label, e.from));
            }
            if (currentGraph.getNode(e.to) == null) {
                reportLoadingError(Bundle.ERR_EndNodeNotExists(e.label, e.to));
            }
            graph.addEdge(immutableEdge(fromIndex, toIndex, e.from, e.to, e.label, e.type));
        }
        for (EdgeInfo e : inputEdges) {
            assert e.input;
            char fromIndex = (char) (nodesWithSuccessor.contains(graph.getNode(e.from)) ? 1 : 0);
            char toIndex = e.num;
            graph.addEdge(immutableEdge(fromIndex, toIndex, e.from, e.to, e.label, e.type));
        }
    }

    private String blockName(int id) {
        return id >= 0 ? Integer.toString(id) : NO_BLOCK;
    }

    @Override
    @CheckForNull
    public InputBlock startBlock(int id) {
        return startBlock(blockName(id));
    }

    @Override
    @CheckForNull
    public InputBlock startBlock(String name) {
        assert currentGraph != null;
        assert currentBlock == null;
        if (blockEdges == null) {
            // initialized lazily since if initialized in Graph, must be saved when
            // switching from Node to subgraph. Blocks do not nest any other structures.
            blockEdges = new ArrayList<>();
        }
        return currentBlock = currentGraph.addBlock(name);
    }

    @Override
    public void endBlock(int id) {
        currentBlock = null;
    }

    @Override
    @CheckForNull
    public Properties getNodeProperties(int nodeId) {
        assert currentGraph != null;
        return currentGraph.getNode(nodeId).getProperties();
    }

    @NbBundle.Messages({
        "# {0} - node ID",
        "# {1} - block name/id",
        "ERR_UnkownNodeInBlock=Adding unkown node {0} to block {1}"
    })
    protected boolean updateNodeBlock(int nodeId, String name) {
        final Properties properties = getNodeProperties(nodeId);
        if (properties == null) {
            reportLoadingError(Bundle.ERR_UnkownNodeInBlock(nodeId, name));
            return false;
        }
        final String oldBlock = properties.get(PROPNAME_BLOCK, String.class);
        if (oldBlock != null) {
            properties.setProperty(PROPNAME_BLOCK, oldBlock + ", " + name);
            return false;
        } else {
            properties.setProperty(PROPNAME_BLOCK, name);
            return true;
        }
    }

    @Override
    public void addNodeToBlock(int nodeId) {
        assert currentBlock != null;
        String name = currentBlock.getName();
        if (updateNodeBlock(nodeId, name)) {
            currentBlock.addNode(nodeId);
        }
    }

    @Override
    public void addBlockEdge(int from, int to) {
        blockEdges.add(new EdgeInfo(from, to));
    }

    @Override
    public void makeBlockEdges() {
        assert currentGraph != null;
        if (blockEdges != null) {
            for (EdgeInfo e : blockEdges) {
                String fromName = blockName(e.from);
                String toName = blockName(e.to);
                currentGraph.addBlockEdge(currentGraph.getBlock(fromName), currentGraph.getBlock(toName));
            }
        }
        currentGraph.ensureNodesInBlocks();
    }

    @Override
    public void setMethod(String name, String shortName, int bci, Method method) {
        assert currentNode == null;
        assert currentGraph == null;
        assert folder instanceof Group;

        Group g = (Group) folder;

        g.setMethod(new InputMethod(g, name, shortName, bci, method));
    }

    /**
     * Called during reading when the reader encounters beginning of a new
     * stream. All pending data should be reset.
     */
    @Override
    public void resetStreamData() {
        replacePool(getConstantPool().restart());
    }

    @Override
    @NonNull
    public ConstantPool getConstantPool() {
        return pool;
    }

    protected void replacePool(ConstantPool newPool) {
        this.pool = newPool;
        if (control != null) {
            control.setConstantPool(newPool);
        }
    }

    @Override
    public void setModelControl(ModelControl target) {
        this.control = target;
    }

    protected ConstantPool getReaderPool() {
        return control.getConstantPool();
    }

    @Override
    public void startRoot() {
        // no op
    }

    protected List<EdgeInfo> getInputEdges() {
        return inputEdges;
    }

    protected List<EdgeInfo> getSuccessorEdges() {
        return successorEdges;
    }

    protected List<EdgeInfo> getNodeEdges() {
        return nodeEdges;
    }

    public void reportLoadingError(String logMessage) {
        reportLoadingError(logMessage, null);
    }

    /**
     * Reports an error encountered during load. The error may be recoverable;
     * this method will mark the root document as erroneous.
     *
     * @param logMessage the error message, formatted.
     */
    @Override
    public void reportLoadingError(String logMessage, List<String> parentNames) {
        LOG.log(java.util.logging.Level.WARNING, logMessage);

        List<FolderElement> parents = new ArrayList<>();
        String name = null;

        StringBuilder indent = new StringBuilder("          "); // NOI18N
        Folder f = folder();
        if (currentGraph != null) {
            name = currentGraph.getName();
        }
        while (f != null) {
            FolderElement fe = (FolderElement) f;
            parents.add(fe);
            f = fe.getParent();
        }
        Collections.reverse(parents);
        if (parentNames == null) {
            parentNames = new ArrayList<>(parents.size());
            for (int i = parents.size() - 1; i >= 0; i--) {
                parentNames.add(parents.get(i).getName());
            }
        }
        if (name == null) {
            if (parentNames.isEmpty()) {
                name = "<none>"; // NOI18N
            } else {
                name = parentNames.get(parentNames.size() - 1);
                parentNames = parentNames.subList(0, parentNames.size() - 1);
                parents = parents.subList(0, Math.min(parents.size(), parentNames.size()));
            }
        }
        LOG.log(java.util.logging.Level.WARNING, "Location: {0}", name);
        for (int i = parents.size() - 1; i >= 0; i--) {
            indent.append("   -> "); // NOI18N
            LOG.log(java.util.logging.Level.WARNING, "{0} {1}", new Object[]{indent, parents.get(i).getName()});
        }
        ParseMonitor mon = getMonitor();
        if (mon != null) {
            mon.reportError(parents, parentNames, name, logMessage);
        }
        ReaderErrors.addError(
                        currentGraph != null ? currentGraph : folder(),
                        currentGraph != null ? folder : null,
                        logMessage
        );
    }

    protected ParseMonitor getMonitor() {
        return monitor;
    }
}
