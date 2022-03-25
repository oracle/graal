package org.graalvm.bisect.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class SetUtil {
    public static <T> Set<T> intersection(Collection<T> lhs, Collection<T> rhs) {
        Set<T> common = new HashSet<>(lhs);
        common.retainAll(rhs);
        return common;
    }

    public static <T> Set<T> difference(Collection<T> lhs, Collection<T> rhs) {
        Set<T> diff = new HashSet<>(lhs);
        diff.removeAll(rhs);
        return diff;
    }
}
