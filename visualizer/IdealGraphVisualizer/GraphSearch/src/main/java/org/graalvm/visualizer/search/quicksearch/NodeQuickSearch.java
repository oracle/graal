/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search.quicksearch;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.visualizer.data.SuppressFBWarnings;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.search.Criteria;
import org.graalvm.visualizer.search.GraphSearchEngine;
import org.graalvm.visualizer.search.SimpleNodeProvider;
import org.graalvm.visualizer.search.ui.SearchResultsView;
import org.graalvm.visualizer.util.LookupHistory;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.Message;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Pair;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.RegexpPropertyMatcher;

public class NodeQuickSearch implements SearchProvider {
    /**
     * Length in chars of the displayed matched value.
     */
    private final int MAX_VALUE_LENGTH = 80;

    /**
     * The default property to search, if the user does not specify anything
     */
    private static final String DEFAULT_PROPERTY = PROPNAME_NAME;

    private static DiagramViewer getActiveGraphProvider() {
        DiagramViewerLocator vwr = Lookup.getDefault().lookup(DiagramViewerLocator.class);
        if (vwr == null) {
            return null;
        }
        return vwr.getActiveViewer();
    }

    /**
     * Method is called by infrastructure when search operation was requested. Implementors should
     * evaluate given request and fill response object with apropriate results
     *
     * @param request  Search request object that contains information what to search for
     * @param response Search response object that stores search results. Note that it's important
     *                 to react to return value of SearchResponse.addResult(...) method and stop
     *                 computation if false value is returned.
     */
    @Override
    @NbBundle.Messages({
            "# {0} - number of nodes",
            "# {1} - searched property name",
            "# {2} - searched value",
            "FMT_ExactMatchingNodes=Exactly ({0}) matching nodes ({1} = {2})",
            "# {0} - number of nodes",
            "# {1} - searched property name",
            "# {2} - searched value",
            "# {3} - graph name",
            "FMT_ExactMatchingNodesOther=Exactly ({0}) matching nodes ({1} = {2}) in {3}",
            "# {0} - number of nodes",
            "# {1} - searched property name",
            "# {2} - searched value",
            "FMT_PartialMatchingNodes=Partially ({0}) matching nodes ({1} = {2})",
            "# {0} - number of nodes",
            "# {1} - searched property name",
            "# {2} - searched value",
            "# {3} - graph name",
            "FMT_PartialMatchingNodesOther=Partially ({0}) matching nodes ({1} = {2}) in {3}",
            "# {0} - found property value",
            "# {1} - the found node ID",
            "# {2} - the found node name",
            "FMT_NodeResult={0} ({1}: {2})",
            "# {0} - found property value",
            "# {1} - the found node ID",
            "# {2} - the found node name",
            "# {3} - graph name",
            "FMT_NodeResultOther={0} ({1}: {2}) in {3}",
            "NAME_NoGraph=<none>",
            "# {0} - search query",
            "LABEL_OpenSearchResults=Open search for {0} in Node Searches window"
    })
    public void evaluate(SearchRequest request, SearchResponse response) {
        String query = request.getText();
        if (query.trim().isEmpty()) {
            return;
        }

        final String[] parts = query.split("=", 2);

        String name;
        String value;

        if (parts.length == 1) {
            name = DEFAULT_PROPERTY;
            value = Pattern.quote(parts[0]);
        } else {
            name = parts[0];
            value = parts[1];
        }

        if (value.isEmpty()) {
            value = ".*";
        }

        final InputGraphProvider p = LookupHistory.getLast(InputGraphProvider.class);
        if (p != null && p.getGraph() != null) {
            InputGraph matchGraph = p.getGraph();
            // Search the current graph
            Pair<List<InputNode>, List<InputNode>> matches = findMatches(name, value, matchGraph, response);

            boolean moreResults = true;
            final InputGraph theGraph = p.getGraph() != matchGraph ? matchGraph : null;
            final Set<InputNode> m = matches == null ? Collections.emptySet() : new HashSet<>(matches.second());

            if (!m.isEmpty()) {
                moreResults &= response.addResult(() -> {
                            final DiagramViewer comp = getActiveGraphProvider();
                            if (comp != null) {
                                if (theGraph != null) {
                                    comp.getModel().selectGraph(theGraph);
                                }
                                comp.setSelectedNodes(m);
                                comp.requestActive(true, false);
                            }
                        }, theGraph != null ?
                                Bundle.FMT_ExactMatchingNodesOther(m.size(), name, value,
                                        theGraph.getName()) :
                                Bundle.FMT_ExactMatchingNodes(m.size(), name, value)
                );

            }
            moreResults &= response.addResult(createSearchAction(p.getGraph(), p.getContainer(), name, value),
                    Bundle.LABEL_OpenSearchResults(value));
            if (!moreResults) {
                return;
            }
            if (matches != null) {
                // Single matches
                List<InputNode> allMatches = new ArrayList<>(matches.second());
                int exact = allMatches.size();
                allMatches.addAll(matches.first());
                int index = 0;

                for (final InputNode n : allMatches) {
                    String val = trimValue(value, n.getProperties().get(name).toString(), index < exact);
                    moreResults &= response.addResult(() -> {
                        final DiagramViewer comp = getActiveGraphProvider();
                        if (comp != null) {
                            final Set<InputNode> tmpSet = new HashSet<>();
                            tmpSet.add(n);
                            if (theGraph != null) {
                                comp.getModel().selectGraph(theGraph);
                            }
                            comp.setSelectedNodes(tmpSet);
                            comp.requestActive(true, false);
                        }
                    }, theGraph != null ?
                            Bundle.FMT_NodeResultOther(val,
                                    n.getId(), n.getProperties().get(PROPNAME_NAME),
                                    theGraph.getName()) :
                            Bundle.FMT_NodeResult(val,
                                    n.getId(), n.getProperties().get(PROPNAME_NAME)));
                    index++;
                    if (!moreResults) {
                        break;
                    }
                }
            }
        } else {
        }
    }

    private Runnable createSearchAction(InputGraph g, GraphContainer c, String name, String value) {
        return () -> {
            GraphSearchEngine engine = new GraphSearchEngine(c, g, new SimpleNodeProvider());
            SearchResultsView.addSearchResults(engine);

            RegexpPropertyMatcher matcher = new RegexpPropertyMatcher(name, value, false, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            engine.newSearch(new Criteria().setMatcher(matcher), false);
        };
    }

    // accessible for tests

    /**
     * Trims the value text, decorate if ellipsis. Only the matching line is cut off from multiline
     * values.
     */
    String trimValue(String searchPattern, String value, boolean full) {
        int from = 0;
        int to = value.length();
        if (!searchPattern.contains("\\n")) {
            Pattern p = Pattern.compile(searchPattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher m = p.matcher(value);
            if (full ? m.matches() : m.find()) {
                int s = m.start();
                int e = m.end();
                int f = value.lastIndexOf('\n', s);
                int t = value.indexOf('\n', e);
                value = value.substring(f + 1, t == -1 ? value.length() : t);

                from = s - f + 1;
                to = e - f + 1;
            }
        }
        if (value.length() < MAX_VALUE_LENGTH) {
            return value;
        }
        if ((to - from) > MAX_VALUE_LENGTH) {
            return value.substring(0, Math.min(MAX_VALUE_LENGTH, value.length())) + "...";
        }
        int x = Math.max(0, from - (MAX_VALUE_LENGTH - (to - from)) / 2);
        return (x > 0 ? "..." : "") + value.substring(x, Math.min(value.length(), x + MAX_VALUE_LENGTH)) + "...";
    }

    @NbBundle.Messages({
            "# {0} - exception message",
            "MSG_SearchExceptionOccured=An exception occurred during the search, perhaps due to a malformed query string:\n {0}",
            "DESC_SearchError=(Error during search)"
    })
    // test access only
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "Ignored unlikely just warning msg failure")
    Pair<List<InputNode>, List<InputNode>> findMatches(String name,
                                                       String value, InputGraph inputGraph, SearchResponse response) {
        try {
            RegexpPropertyMatcher matcher = new RegexpPropertyMatcher(
                    name, value, false, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            RegexpPropertyMatcher fullMatcher = new RegexpPropertyMatcher(
                    name, value, true, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Properties.PropertySelector<InputNode> selector = new Properties.PropertySelector<>(inputGraph.getNodes());
            // select all nodes incl. partial results
            List<InputNode> matches = selector.selectMultiple(matcher);

            // select exact results
            Properties.PropertySelector<InputNode> fullSelector = new Properties.PropertySelector<>(matches);
            List<InputNode> fullMatches = fullSelector.selectMultiple(fullMatcher);

            // order partial results after the exact ones
            if (matches.isEmpty() && fullMatches.isEmpty()) {
                return null;
            }
            matches.removeAll(fullMatches);
            return Pair.of(matches, fullMatches);
        } catch (Exception e) {
            final String msg = e.getMessage();
            response.addResult(() -> {
                Message desc = new NotifyDescriptor.Message(Bundle.MSG_SearchExceptionOccured(msg),
                        NotifyDescriptor.WARNING_MESSAGE);
                DialogDisplayer.getDefault().notify(desc);
            }, Bundle.DESC_SearchError());
        }
        return null;
    }
}
