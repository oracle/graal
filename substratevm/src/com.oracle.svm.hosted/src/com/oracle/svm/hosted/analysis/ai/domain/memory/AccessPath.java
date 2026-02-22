package com.oracle.svm.hosted.analysis.ai.domain.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable access-path representation used by AbsMemory.
 * Root is a typed root (local, static class, alloc-site, placeholder).
 * Fields is a sequence of field names or array markers.
 */
public final class AccessPath {

    public enum RootKind { LOCAL, STATIC_CLASS, ALLOC_SITE, PLACEHOLDER }

    private final RootKind rootKind;
    private final String rootName; // e.g., local name, class name for static, alloc id, or placeholder id
    private final List<String> fields; // immutable list of field names, array indices may be represented as "[i]" or "[*]"

    private AccessPath(RootKind rootKind, String rootName, List<String> fields) {
        this.rootKind = Objects.requireNonNull(rootKind);
        this.rootName = Objects.requireNonNull(rootName);
        this.fields = List.copyOf(fields);
    }

    public static AccessPath forLocal(String localName) {
        return new AccessPath(RootKind.LOCAL, Objects.requireNonNull(localName), Collections.emptyList());
    }

    public static AccessPath forStaticClass(String className) {
        return new AccessPath(RootKind.STATIC_CLASS, Objects.requireNonNull(className), Collections.emptyList());
    }

    public static AccessPath forAllocSite(String allocId) {
        return new AccessPath(RootKind.ALLOC_SITE, Objects.requireNonNull(allocId), Collections.emptyList());
    }

    public static AccessPath forAllocSiteWithContext(String allocSiteId, String contextSignature) {
        String name = Objects.requireNonNull(allocSiteId) + "@" + Objects.requireNonNull(contextSignature);
        return new AccessPath(RootKind.ALLOC_SITE, name, Collections.emptyList());
    }

    public static AccessPath forPlaceholder(String placeholder) {
        return new AccessPath(RootKind.PLACEHOLDER, Objects.requireNonNull(placeholder), Collections.emptyList());
    }

    public RootKind getRootKind() {
        return rootKind;
    }

    public String getRootName() {
        return rootName;
    }

    public List<String> getFields() {
        return fields;
    }

    public boolean isRootLocal() {
        return rootKind == RootKind.LOCAL;
    }

    public boolean isStaticRoot() {
        return rootKind == RootKind.STATIC_CLASS;
    }

    public AccessPath appendField(String field) {
        Objects.requireNonNull(field);
        List<String> newFields = new ArrayList<>(fields.size() + 1);
        newFields.addAll(fields);
        newFields.add(field);
        return new AccessPath(rootKind, rootName, newFields);
    }

    public AccessPath appendArrayIndex(int index) {
        return appendField("[" + index + "]");
    }

    public AccessPath appendArrayWildcard() {
        return appendField("[*]");
    }

    public int depth() {
        return fields.size();
    }

    /**
     * Truncate path to maximum of k fields. If original fields.size() > k,
     * returned path contains first k fields followed by "*" marker.
     */
    public AccessPath truncate(int k) {
        if (k < 0) throw new IllegalArgumentException("k must be >= 0");
        if (fields.size() <= k) return this;
        List<String> newFields = new ArrayList<>(k + 1);
        for (int i = 0; i < k; i++) newFields.add(fields.get(i));
        newFields.add("*");
        return new AccessPath(rootKind, rootName, newFields);
    }

    /**
     * True if this path starts with given prefix (root kind and name must match, and fields prefix matched).
     */
    public boolean startsWith(AccessPath prefix) {
        if (prefix == null) return false;
        if (this.rootKind != prefix.rootKind) return false;
        if (!this.rootName.equals(prefix.rootName)) return false;
        List<String> pfields = prefix.fields;
        if (pfields.size() > this.fields.size()) return false;
        for (int i = 0; i < pfields.size(); i++) {
            if (!Objects.equals(this.fields.get(i), pfields.get(i))) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(rootKind).append(":").append(rootName).append("}");
        if (fields.isEmpty()) return sb.toString();
        for (String f : fields) sb.append('.').append(f);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessPath)) return false;
        AccessPath that = (AccessPath) o;
        return this.rootKind == that.rootKind && this.rootName.equals(that.rootName) && this.fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootKind, rootName, fields);
    }
}
