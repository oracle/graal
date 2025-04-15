package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a name of a memory ( stack/heap ) location via the path that is used to access it
 * e.g a.b[0].c, where 'a' is the base variable and b[0].c is the access path
 */
public final class AccessPath {

    private final AccessPathBase base;
    private final List<AccessPathElement> elements;

    public AccessPath(AccessPathBase base) {
        this.base = base;
        this.elements = new ArrayList<>();
    }

    public AccessPath(AccessPathBase base, List<AccessPathElement> elements) {
        this.base = base;
        this.elements = elements;
    }

    public AccessPath(Node node) {
        this.base = new PlaceHolderAccessPathBase(node.toString());
        this.elements = new ArrayList<>();
    }

    public AccessPathBase getBase() {
        return base;
    }

    public List<AccessPathElement> getElements() {
        return elements;
    }

    /**
     * Create a new AccessPath with the same base variable and elements as the given AccessPath
     *
     * @param fieldName String
     * @return AccessPath
     */
    public AccessPath appendField(String fieldName, int modifiers) {
        AccessPath newPath = new AccessPath(base);
        newPath.elements.addAll(this.elements);
        newPath.elements.add(new FieldAccess(fieldName, modifiers));
        return newPath;
    }

    public AccessPath appendArrayAccess(String index) {
        AccessPath newPath = new AccessPath(base);
        newPath.elements.addAll(this.elements);
        newPath.elements.add(new ArrayAccess(index));
        return newPath;
    }

    public AccessPath appendAccesses(List<AccessPathElement> accesses) {
        AccessPath newPath = new AccessPath(base);
        newPath.elements.addAll(this.elements);
        newPath.elements.addAll(accesses);
        return newPath;
    }

    public static AccessPath getAccessPathFromAccessFieldNode(AccessFieldNode accessFieldNode) {
        ResolvedJavaField field = accessFieldNode.field();
        if (field.isStatic()) {
            /* For static fields, use the declaring class as the base variable */
            AccessPathBase base = new ClassAccessPathBase(field.getDeclaringClass());
            return new AccessPath(base).appendField(field.getName(), field.getModifiers());
        }

        if (accessFieldNode.object().getNodeSourcePosition() != null) {
            ResolvedJavaType declaringClass = field.getDeclaringClass();
            NodeSourcePosition nodeSourcePosition = accessFieldNode.object().getNodeSourcePosition();
            AccessPathBase base = new ObjectAccessPathBase(declaringClass, nodeSourcePosition);
            return new AccessPath(base).appendField(field.getName(), field.getModifiers());
        }

        /* Here, we don't know the source position of the object, this means that it has to be an actual parameter,
         * we leave this case to the user implementations, because there is no possible way of knowing, how do their
         * domains handle replacing formal parameters with actual parameters. */
        return null;
    }

    public boolean isPrefixOf(AccessPath other) {
        if (!base.equals(other.base)) {
            return false;
        }

        if (elements.size() > other.elements.size()) {
            return false;
        }

        for (int i = 0; i < elements.size(); i++) {
            if (!elements.get(i).equals(other.elements.get(i))) {
                return false;
            }
        }
        return true;
    }

    public List<AccessPathElement> removePrefix(AccessPath prefix) {
        if (prefix.isPrefixOf(this)) {
            return new ArrayList<>(elements.subList(prefix.elements.size(), elements.size()));
        }
        return null;
    }

    public AccessPath replacePrefix(AccessPath prefix, AccessPath replaceWith) {
        List<AccessPathElement> remainingElements = removePrefix(prefix);
        if (remainingElements == null) {
            return null;
        }

        AccessPath newPath = new AccessPath(replaceWith.base);
        newPath.elements.addAll(replaceWith.elements);
        newPath.elements.addAll(remainingElements);
        return newPath;
    }

    /**
     * Create a new access path by taking a subset of the elements
     *
     * @param endIndex The exclusive end index of the elements to include
     * @return A new access path with only the elements up to endIndex
     */
    public AccessPath getPrefix(int endIndex) {
        if (endIndex < 0 || endIndex > elements.size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + endIndex);
        }

        AccessPath newPath = new AccessPath(base);
        newPath.elements.addAll(elements.subList(0, endIndex));
        return newPath;
    }

    /**
     * Create a new access path by taking a subset of the elements
     *
     * @param startIndex The inclusive start index of the elements to include
     * @return A new access path with only the elements from startIndex onwards
     */
    public AccessPath getSuffix(int startIndex) {
        if (startIndex < 0 || startIndex > elements.size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + startIndex);
        }

        AccessPath newPath = new AccessPath(base);
        newPath.elements.addAll(elements.subList(startIndex, elements.size()));
        return newPath;
    }

    /**
     * Create an AccessPath from an AllocatedObjectNode.
     *
     * @param allocatedObject AllocatedObjectNode
     * @return AccessPath representing the allocatedObject
     */
    public static AccessPath fromAllocatedObject(AllocatedObjectNode allocatedObject) {
        ResolvedJavaType type = allocatedObject.getVirtualObject().type();
        NodeSourcePosition nodeSourcePosition = allocatedObject.getVirtualObject().getNodeSourcePosition();
        AccessPathBase base = new ObjectAccessPathBase(type, nodeSourcePosition);
        return new AccessPath(base);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(base.toString());
        for (AccessPathElement element : elements) {
            sb.append(element.toString());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AccessPath that = (AccessPath) o;
        return Objects.equals(base, that.base) && Objects.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, elements);
    }
}
