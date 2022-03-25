package org.graalvm.bisect.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class ListUtil {
    public static <T> List<Pair<T>> zipLongest(Collection<T> lhs, Collection<T> rhs) {
        Iterator<T> lhsIter = lhs.iterator();
        Iterator<T> rhsIter = rhs.iterator();
        Iterator<Pair<T>> pairIterator = IteratorUtil.zipLongest(lhsIter, rhsIter);
        ArrayList<Pair<T>> arrayList = new ArrayList<>();
        pairIterator.forEachRemaining(arrayList::add);
        return arrayList;
    }
}
