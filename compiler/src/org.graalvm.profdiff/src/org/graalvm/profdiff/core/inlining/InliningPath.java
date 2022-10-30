package org.graalvm.profdiff.core.inlining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.profdiff.core.optimization.Optimization;

public class InliningPath {
    public static final class PathElement {
        private final String methodName;

        private final int callsiteBCI;

        public PathElement(String methodName, int callsiteBCI) {
            this.methodName = methodName;
            this.callsiteBCI = callsiteBCI;
        }

        public boolean matches(PathElement otherElement) {
            if (!Objects.equals(methodName, otherElement.methodName)) {
                return false;
            }
            return callsiteBCI == Optimization.UNKNOWN_BCI || otherElement.callsiteBCI == Optimization.UNKNOWN_BCI || callsiteBCI == otherElement.callsiteBCI;
        }

        public String getMethodName() {
            return methodName;
        }

        public int getCallsiteBCI() {
            return callsiteBCI;
        }
    }

    private final List<PathElement> path;

    public static final InliningPath EMPTY = new InliningPath(List.of());

    private InliningPath(List<PathElement> path) {
        this.path = path;
    }

    public int size() {
        return path.size();
    }

    public PathElement get(int index) {
        return path.get(index);
    }

    public Iterable<PathElement> elements() {
        return path;
    }

    public static InliningPath ofEnclosingMethod(Optimization optimization) {
        if (optimization.getPosition() == null) {
            return EMPTY;
        }
        List<Pair<String, Integer>> pairs = new ArrayList<>();
        UnmodifiableMapCursor<String, Integer> cursor = optimization.getPosition().getEntries();
        while (cursor.advance()) {
            pairs.add(Pair.create(cursor.getKey(), cursor.getValue()));
        }
        Collections.reverse(pairs);
        List<PathElement> path = new ArrayList<>();
        int previousBCI = Optimization.UNKNOWN_BCI;
        for (Pair<String, Integer> pair : pairs) {
            path.add(new PathElement(pair.getLeft(), previousBCI));
            previousBCI = pair.getRight();
        }
        return new InliningPath(path);
    }

    public static InliningPath fromRootToNode(InliningTreeNode node) {
        List<PathElement> path = new ArrayList<>();
        while (node != null) {
            path.add(new PathElement(node.getName(), node.getBCI()));
            node = node.getParent();
        }
        Collections.reverse(path);
        return new InliningPath(path);
    }

    public boolean isPrefixOf(InliningPath otherPath) {
        if (path.size() > otherPath.path.size()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            if (!path.get(i).matches(otherPath.path.get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(InliningPath otherPath) {
        return path.size() == otherPath.size() && isPrefixOf(otherPath);
    }
}
