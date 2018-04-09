package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

public class LocationFinderHelper {

    /**
     * Tries to find a node directly at or after the caret. If none found, then before the caret. Used
     * to find the nearest node to ask for env.findLocalScopes(node, null).
     */
    public static Node findNearest(Source source, SourceElement[] sourceElements, int line, int column, TruffleInstrument.Env env) {
        return SuspendableLocationFinder.findNearest(source, sourceElements, line, column, env);
    }

}
