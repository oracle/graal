package org.graalvm.bisect.util;

import java.util.Iterator;

public final class IteratorUtil {
    public static <T> Iterator<Pair<T>> zipLongest(Iterator<T> lhs, Iterator<T> rhs) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return lhs.hasNext() || rhs.hasNext();
            }

            @Override
            public Pair<T> next() {
                if (lhs.hasNext() && rhs.hasNext()) {
                    return new Pair<>(lhs.next(), rhs.next());
                } else if (lhs.hasNext()) {
                    return new Pair<>(lhs.next(), null);
                } else {
                    return new Pair<>(null, rhs.next());
                }
            }
        };
    }
}
