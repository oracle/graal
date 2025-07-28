/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources.CompressedGlobTrie;

import static com.oracle.svm.util.GlobUtils.LEVEL_IDENTIFIER;
import static com.oracle.svm.util.GlobUtils.STAR;
import static com.oracle.svm.util.GlobUtils.STAR_STAR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.util.GlobUtils;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.StringUtil;

/**
 * This data structure represents an immutable, Trie-like data structure that stores glob patterns
 * without redundancies. Once created, it cannot be changed, but only queried.
 *
 * The structure can be created using {@link CompressedGlobTrieBuilder#build(List)} where list
 * parameter represents list of glob patterns given in any order. At the very beginning, all given
 * globs will be validated using {@link GlobUtils#validatePattern(String)}. If all globs were
 * correct, they will be classified (with
 * {@link CompressedGlobTrieBuilder#classifyPatterns(List, List, List, List)}) and sorted (using
 * {@link CompressedGlobTrieBuilder#comparePatterns(GlobWithInfo, GlobWithInfo)} as the comparator
 * function). This preprocessing phase allows incremental structure build, going from the most
 * general glob pattern to the more specific ones. Furthermore, when trying to add a new glob, the
 * structure will first check if the given glob can be matched with some glob that already exists in
 * the structure (NOTE: possibly more than one glob pattern from the structure can match the new
 * pattern). When that is not the case, a new pattern will be added using
 * {@link CompressedGlobTrieBuilder#addNewBranch}. This function will attempt identical matching
 * (where wildcards have no special semantics) to move as deep in the structure as possible. Once it
 * cannot proceed with the existing branches, the function will append rest of the pattern as the
 * new branch. At the end, each node (not necessarily leaf) that represents end of the glob, can
 * store additional content.
 *
 * When created, the structure can be used to either check if the given text can be matched with
 * globs from the structure (using {@link #match(GlobTrieNode, String)}) or to fetch all additional
 * content from globs that can match given text (using
 * {@link #getHostedOnlyContentIfMatched(GlobTrieNode, String)}).
 */
public class CompressedGlobTrie {

    public record GlobWithInfo<C>(String pattern, C additionalContent) {
    }

    /*
     * Data transfer object that points to the next matched child instance and its index in the
     * pattern parts list
     */
    private record MatchedNode<C>(GlobTrieNode<C> child, int lastMatchedChildIndex) {
    }

    /*
     * In case we have a complex level like: .../bar*Test*set/... its representation in the trie
     * will be: bar* -> Test* -> set; In order to find all the nodes (on the same level) we need to
     * squash them and return the number of squashed parts. Example: foo/bar*Test*set/1.txt pattern
     * will be transformed into the following list of parts: [foo, bar*, Test*, set, 1.txt] in the
     * first phase of the algorithm. Based on this list we don't know where the level, which starts
     * with bar*, ends. To figure that out, we call a helper function which returns bar*Test*set and
     * the number 3 because it squashed "bar*", "Test*" and "set" into one part.
     */
    private record SquashedParts(String squashedPart, int numberOfSquashedParts) {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static class CompressedGlobTrieBuilder<C> {
        /**
         * Builds an immutable CompressedGlobTrie structure from glob patterns given in any order
         * with possibly additional context.
         */
        public static <C> GlobTrieNode<C> build(List<GlobWithInfo<C>> patterns) {
            GlobTrieNode<C> root = new GlobTrieNode<>();
            /* classify patterns in groups */
            List<GlobWithInfo<C>> doubleStarPatterns = new ArrayList<>();
            List<GlobWithInfo<C>> starPatterns = new ArrayList<>();
            List<GlobWithInfo<C>> noStarPatterns = new ArrayList<>();

            List<String> invalidPatternsErrors = classifyPatterns(patterns, doubleStarPatterns, starPatterns, noStarPatterns);
            if (!invalidPatternsErrors.isEmpty()) {
                invalidPatternsErrors.forEach(LogUtils::warning);
            }

            /* sort patterns in the groups based on generality */
            doubleStarPatterns.sort(CompressedGlobTrieBuilder::comparePatterns);
            starPatterns.sort(CompressedGlobTrieBuilder::comparePatterns);

            /*
             * add patterns from the most general to more specific one. This allows us to discard
             * some patterns that are already covered with more generic one that was added before
             */
            doubleStarPatterns.forEach(pattern -> addPattern(root, pattern));
            starPatterns.forEach(pattern -> addPattern(root, pattern));
            noStarPatterns.forEach(pattern -> addPattern(root, pattern));

            return root;
        }

        /*
         * this function should be called only when we are certain that all possible wildcards are
         * actually escaped (i.e. only after validatePattern function)
         */
        private static String unescapePossibleWildcards(String pattern) {
            String unescapedPattern = pattern;
            for (char esc : GlobUtils.ALWAYS_ESCAPED_GLOB_WILDCARDS) {
                unescapedPattern = unescapedPattern.replace("\\" + esc, String.valueOf(esc));
            }

            return unescapedPattern;
        }

        private static <C> void addPattern(GlobTrieNode<C> root, GlobWithInfo<C> pattern) {
            String unescapedPattern = unescapePossibleWildcards(pattern.pattern());
            List<GlobTrieNode<C>> parts = getPatternParts(unescapedPattern);

            /*
             * if this pattern can be matched with some other existing pattern, we should just
             * update content of leafs
             */
            List<GlobTrieNode<C>> reachableNodes = new ArrayList<>();
            getAllPatterns(root, parts, 0, reachableNodes);
            if (!reachableNodes.isEmpty()) {
                /* new pattern is a part of some existing patterns in the existing Trie */
                for (GlobTrieNode<C> node : reachableNodes) {
                    /*
                     * Both pattern and additionalContent are already present in the trie, so we can
                     * skip this pattern
                     */
                    if (node.getHostedOnlyContent().stream().anyMatch(c -> c.equals(pattern.additionalContent()))) {
                        return;
                    }
                }
            }

            addPattern(root, parts, 0, pattern.additionalContent());
        }

        private static <C> void addPattern(GlobTrieNode<C> root, List<GlobTrieNode<C>> parts, int i, C additionalInfo) {
            if (patternReachedEnd(i, parts)) {
                root.setLeaf();
                root.addHostedOnlyContent(additionalInfo);
                return;
            }

            GlobTrieNode<C> nextPart = parts.get(i);
            boolean canProceed = simplePatternMatch(root, nextPart);
            if (canProceed) {
                /* we had progress, try to match rest of the pattern */
                addPattern(root.getChild(nextPart.getContent()), parts, i + 1, additionalInfo);
                return;
            }

            /*
             * we reached the lowest point in the Trie we could go, therefore we need to have a new
             * branch from the current root we are at right now
             */
            addNewBranch(root, parts, i, additionalInfo);
        }

        private static <C> void addNewBranch(GlobTrieNode<C> root, List<GlobTrieNode<C>> parts, int i, C additionalInfo) {
            /* sanity check */
            if (parts.isEmpty() || i >= parts.size()) {
                return;
            }

            GlobTrieNode<C> newNode = null;
            /* we matched pattern parts until i-th pattern part, so just add rest */
            for (int j = i; j < parts.size(); j++) {
                GlobTrieNode<C> part = parts.get(j);
                if (newNode == null) {
                    newNode = root.addChild(part.getContent(), part);
                    continue;
                }

                newNode = newNode.addChild(part.getContent(), part);
            }

            /* mark end of pattern and populate additional info */
            newNode.setLeaf();
            newNode.addHostedOnlyContent(additionalInfo);
        }

        private static <C> List<String> classifyPatterns(List<GlobWithInfo<C>> patterns,
                        List<GlobWithInfo<C>> doubleStar,
                        List<GlobWithInfo<C>> singleStar,
                        List<GlobWithInfo<C>> noStar) {
            List<String> invalidPatterns = new ArrayList<>();
            for (GlobWithInfo<C> patternWithInfo : patterns) {
                /* validate patterns */
                String error = GlobUtils.validatePattern(patternWithInfo.pattern());
                if (!error.isEmpty()) {
                    if (patternWithInfo.additionalContent() instanceof ClassLoaderSupport.ConditionWithOrigin conditionWithOrigin) {
                        invalidPatterns.add(error + "Pattern is from: " + conditionWithOrigin.origin());
                    } else {
                        invalidPatterns.add(error);
                    }

                    continue;
                }

                String pattern = patternWithInfo.pattern();
                if (pattern.indexOf(STAR_STAR) != -1) {
                    doubleStar.add(patternWithInfo);
                } else if (pattern.indexOf(STAR.charAt(0)) != -1) {
                    singleStar.add(patternWithInfo);
                } else {
                    noStar.add(patternWithInfo);
                }
            }

            return invalidPatterns;
        }

        private static int comparePatterns(GlobWithInfo<?> n1, GlobWithInfo<?> n2) {
            String s1 = n1.pattern();
            String s2 = n2.pattern();

            List<String> s1Levels = Arrays.stream(s1.split(LEVEL_IDENTIFIER)).toList();
            List<String> s2Levels = Arrays.stream(s2.split(LEVEL_IDENTIFIER)).toList();

            int i = 0;
            while (i < s1Levels.size() && i < s2Levels.size()) {
                String s1Level = s1Levels.get(i);
                String s2Level = s2Levels.get(i);

                int s1DoubleStar = s1Level.indexOf(STAR_STAR);
                int s2DoubleStar = s2Level.indexOf(STAR_STAR);

                /* check if current levels contains ** */
                if (s1DoubleStar != -1 || s2DoubleStar != -1) {
                    if (s1DoubleStar == -1) {
                        return 1;
                    }

                    if (s2DoubleStar == -1) {
                        return -1;
                    }

                    /* both patterns have ** on the same level */
                    i++;
                    continue;
                }

                /* check if current levels contains * */
                int s1Star = s1Level.indexOf(STAR.charAt(0));
                int s2Star = s2Level.indexOf(STAR.charAt(0));
                if (s1Star != -1 && s2Star != -1) {
                    int len1Stars = StringUtil.numberOfCharsInString(STAR.charAt(0), s1Level);
                    int len2Stars = StringUtil.numberOfCharsInString(STAR.charAt(0), s2Level);

                    /* calculate number of characters that are not stars */
                    int len1 = s1Level.length() - len1Stars;
                    int len2 = s2Level.length() - len2Stars;

                    /*
                     * more letters => more specific pattern. Explanation: 1. If we don't have same
                     * words we don't care even if we make a mistake here, because the patterns will
                     * end up in a different branches 2. if we have same words where some characters
                     * are replaced with *, we are favouring patterns with fewer letters - case when
                     * number of letters is not the same. 3. if we have same number of letters that
                     * means either we have same words with different numbers of * (we are favouring
                     * pattern with more *) or same/different words with stars on different places
                     * (we don't care because they will end up in separate branches, but we will
                     * favour shorter patters)
                     */
                    if (len1 == len2) {
                        /* patterns have same number of letters */
                        if (len1Stars != len2Stars) {
                            /* favour the one with more stars */
                            return len2Stars - len1Stars;
                        }

                        /* same number of stars, recursively proceed only if they are the same */
                        if (s1Level.equals(s2Level)) {
                            i++;
                            continue;
                        }

                        /*
                         * patterns are different, so we don't care who comes first, but it is
                         * always better to give an advantage to shorter pattern
                         */
                        return s1Levels.size() - s2Levels.size();
                    }

                    /* give a favour to pattern with fewer letters as it is more general */
                    return len1 - len2;
                }

                /* both patterns don't have * */
                if (s1Star == -1 && s2Star == -1) {
                    if (s1Level.compareTo(s2Level) == 0) {
                        /*
                         * patterns will end in the same branch so recursively decide who comes
                         * first
                         */
                        i++;
                        continue;
                    }

                    /*
                     * patterns will end up in separate branches, so we don't care to compare them,
                     * just give an advantage to shorter patterns because they could be extended
                     * later
                     */
                    return s1Levels.size() - s2Levels.size();
                }

                /* one contains star and the other doesn't */
                return s1Star == -1 ? 1 : -1;
            }

            return s1Levels.size() - s2Levels.size();
        }
    }

    /**
     * Trims the Trie and makes it unmodifiable.
     */
    public static <C> void finalize(GlobTrieNode<C> root) {
        root.trim();
    }

    /**
     * Returns list of information from all glob patterns that could match given text.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <C> List<C> getHostedOnlyContentIfMatched(GlobTrieNode<C> root, String text) {
        List<GlobTrieNode<C>> matchedNodes = getAllMatchedNodes(root, text);
        if (matchedNodes.isEmpty()) {
            /* text cannot be matched */
            return null;
        }

        List<C> additionalContexts = new ArrayList<>();
        matchedNodes.forEach(node -> additionalContexts.addAll(node.getHostedOnlyContent()));
        return additionalContexts;
    }

    /**
     * Returns whether given text can be matched with any glob pattern in the Trie or not.
     */
    public static <C> boolean match(GlobTrieNode<C> root, String text) {
        return !getAllMatchedNodes(root, text).isEmpty();
    }

    private static <C> List<GlobTrieNode<C>> getAllMatchedNodes(GlobTrieNode<C> root, String text) {
        /* in this case text is a plain text without special meanings, so stars must be escaped */
        String escapedText = escapeAllStars(text);
        List<GlobTrieNode<C>> matchedNodes = new ArrayList<>();
        getAllPatterns(root, getPatternParts(escapedText), 0, matchedNodes);

        return matchedNodes;
    }

    private static String escapeAllStars(String text) {
        return text.replace("*", "\\*");
    }

    /**
     * Returns list of glob pattern parts that will represent nodes in final Trie. This function is
     * used as a helper function in tests as well, and therefore must remain public.
     */
    public static <C> List<GlobTrieNode<C>> getPatternParts(String glob) {
        String pattern = !glob.endsWith("/") ? glob : glob.substring(0, glob.length() - 1);
        List<List<GlobUtils.GlobToken>> tokens = GlobUtils.tokenize(pattern);
        List<GlobTrieNode<C>> parts = new ArrayList<>(tokens.size());

        for (List<GlobUtils.GlobToken> levelTokens : tokens) {
            List<GlobTrieNode<C>> thisLevelParts = new ArrayList<>(levelTokens.size());
            for (GlobUtils.GlobToken token : levelTokens) {
                thisLevelParts.add(switch (token.kind()) {
                    case STAR_STAR -> new DoubleStarNode<>();
                    case STAR -> new StarTrieNode<>(true);
                    case LITERAL_STAR -> new StarTrieNode<>(token.value());
                    case LITERAL -> new LiteralNode<>(token.value());
                });
            }
            thisLevelParts.getFirst().setNewLevel();
            parts.addAll(thisLevelParts);
        }

        return parts;
    }

    private static <C> void getAllPatterns(GlobTrieNode<C> node, List<GlobTrieNode<C>> parts, int i, List<GlobTrieNode<C>> matches) {
        if (patternReachedEnd(i, parts)) {
            if (node.isLeaf()) {
                matches.add(node);
            }

            return;
        }

        /* get ** successors that could extend check */
        DoubleStarNode<C> doubleStar = node.getDoubleStarNode();
        if (doubleStar != null) {
            for (MatchedNode<C> child : getAllAvailablePaths(doubleStar, parts, i)) {
                getAllPatterns(child.child(), parts, child.lastMatchedChildIndex() + 1, matches);
            }
        }

        /* get * nodes that could match next level */
        SquashedParts sp = getThisLevel(parts, i);
        for (var child : node.getChildrenWithStar()) {
            for (GlobTrieNode<C> c : matchOneLevel(child, sp.squashedPart())) {
                getAllPatterns(c, parts, i + sp.numberOfSquashedParts() + 1, matches);
            }
        }

        GlobTrieNode<C> part = parts.get(i);
        if (part instanceof StarTrieNode || part instanceof DoubleStarNode) {
            /*
             * next part is not simple text, and we didn't match it before, so we can't proceed
             * basically we are preventing simple matches of (for example): a* from trie and a* from
             * new pattern because it can be matched as a simple match if we look at the star as a
             * usual character.
             */
            return;
        }

        /* we don't have wildcards anymore, so try simple match */
        GlobTrieNode<C> child = node.getChild(part.getContent());
        if (child == null) {
            return;
        }

        /* we found simple match, proceed to next part with matched child */
        getAllPatterns(child, parts, i + 1, matches);
    }

    private static <C> List<MatchedNode<C>> getAllAvailablePaths(DoubleStarNode<C> node, List<GlobTrieNode<C>> parts, int i) {
        List<MatchedNode<C>> successors = new ArrayList<>();
        /* maybe ** is leaf, so we should cover this pattern */
        if (node.isLeaf()) {
            return List.of(new MatchedNode<>(node, parts.size()));
        }

        /* checks if we skip some part (on index j) can we match with some child of node */
        for (int j = i; j < parts.size(); j++) {
            /* in case next part contains many stars, squash them into one node */
            SquashedParts sp = getThisLevel(parts, j);
            /* checks if any child of current node can match next part */
            for (StarTrieNode<C> child : node.getChildrenWithStar()) {
                int finalJ = j;
                /* we can match next level with more than one pattern */
                successors.addAll(matchOneLevel(child, sp.squashedPart())
                                .stream()
                                .map(c -> new MatchedNode<>(c, finalJ + sp.numberOfSquashedParts()))
                                .toList());
            }

            GlobTrieNode<C> part = parts.get(j);
            if (part instanceof StarTrieNode) {
                /*
                 * we are only checking trivial parts because we processed star nodes, and we don't
                 * want to interpret (for example) a* as a simple pattern
                 */
                continue;
            }

            GlobTrieNode<C> child = node.getChild(part.getContent());
            /*
             * we are checking only parts that begins new level because in pattern a*b*c, last c is
             * LiteralNode, but it is a part of complex StarTrieNode
             */
            if (part.isNewLevel() && child != null) {
                successors.add(new MatchedNode<>(child, j));
            }
        }

        return successors;
    }

    private static int getIndexOfFirstUnescapedStar(String level) {
        boolean escaped = false;
        for (int i = 0; i < level.length(); i++) {
            char c = level.charAt(i);
            if (c == STAR.charAt(0) && !escaped) {
                return i;
            }

            escaped = c == '\\';
        }

        return -1;
    }

    private static <C> List<GlobTrieNode<C>> matchOneLevel(StarTrieNode<C> node, String wholeLevel) {
        if (node.isMatchingWholeLevel()) {
            return List.of(node);
        }

        /* match prefix first */
        String nodeContent = node.getContent();
        String prefix = nodeContent.substring(0, getIndexOfFirstUnescapedStar(nodeContent));
        if (!prefix.equals(STAR) && !wholeLevel.startsWith(prefix)) {
            /* can't match prefix */
            return Collections.emptyList();
        }

        /* we matched prefix, so we don't need to check prefix anymore */
        String remainingLevel = wholeLevel.substring(wholeLevel.indexOf(prefix) + prefix.length());

        if (!node.hasChildrenOnThisLevel()) {
            /*
             * we matched prefix, and this node is last on this level and ends with * (since node is
             * instance of StarTrieNode)
             */
            return List.of(node);
        }

        List<GlobTrieNode<C>> potentialChildren = new ArrayList<>();
        for (LiteralNode<C> child : node.getChildrenWithLiteral()) {
            if (child.isNewLevel()) {
                // we won't try matching with this child since it is not on same level
                // example: a*/c => c is a child of * but it is on the next level, so it can't be
                // used to match this new level
                continue;
            }

            String suffix = child.getContent();
            if (remainingLevel.endsWith(suffix)) {
                /*
                 * prefix was matched before, and now we matched suffix so * between prefix and
                 * suffix can match rest
                 */
                potentialChildren.add(child);
            }
        }

        /*
         * we need to go in deeper recursion with children that also contains star (like: a* -> b*)
         */
        for (StarTrieNode<C> child : node.getChildrenWithStar()) {
            if (child.isNewLevel()) {
                // we won't try matching with this child since it is not on same level
                // example: a*/c* => c* is a child of a* but it is on the next level, so it can't be
                // used to match this new level
                continue;
            }

            /* if there is a repetition of certain * node we must check paths through all of them */
            StarTrieNode<C> tmpChild = child;
            while (true) {
                String childContent = tmpChild.getContent();
                String childPrefix = childContent.substring(0, childContent.indexOf(STAR.charAt(0)));
                int nextOccurrence = remainingLevel.indexOf(childPrefix);
                if (nextOccurrence != -1) {
                    potentialChildren.addAll(matchOneLevel(tmpChild, remainingLevel.substring(nextOccurrence)));
                }

                /* check if current node has child with same content on same level */
                GlobTrieNode<C> potentialChild = tmpChild.getChildFromSameLevel(tmpChild.getContent());
                if (potentialChild == null) {
                    /* we don't have more repeated star nodes so just quit the loop */
                    break;
                }

                /* move to next node that is repeated */
                tmpChild = (StarTrieNode<C>) potentialChild;
            }
        }

        return potentialChildren;
    }

    private static <C> boolean simplePatternMatch(GlobTrieNode<C> root, GlobTrieNode<C> part) {
        if (root instanceof StarTrieNode || root instanceof DoubleStarNode || part instanceof StarTrieNode || part instanceof DoubleStarNode) {
            return false;
        }

        return root.getChild(part.getContent()) != null;
    }

    private static <C> SquashedParts getThisLevel(List<GlobTrieNode<C>> parts, int begin) {
        StringBuilder sb = new StringBuilder(parts.get(begin).getContent());
        int numberOfSquashedParts = 0;
        /* collect all parts until we hit beginning of the new level */
        for (int i = begin + 1; i < parts.size(); i++) {
            GlobTrieNode<C> nextPart = parts.get(i);
            if (nextPart.isNewLevel()) {
                break;
            }

            sb.append(nextPart.getContent());
            numberOfSquashedParts++;
        }

        return new SquashedParts(sb.toString(), numberOfSquashedParts);
    }

    private static <C> boolean patternReachedEnd(int index, List<GlobTrieNode<C>> parts) {
        return index >= parts.size();
    }

    public static <C> void removeNodes(GlobTrieNode<C> head, Predicate<C> shouldRemove) {
        List<C> contentToRemove = head.getHostedOnlyContent().stream().filter(shouldRemove).toList();
        head.removeHostedOnlyContent(contentToRemove);

        List<GlobTrieNode<C>> childrenToRemove = new ArrayList<>();
        for (GlobTrieNode<C> child : head.getChildren()) {
            removeNodes(child, shouldRemove);

            /* leaf without additional content should be removed */
            if (child.isLeaf() && child.getHostedOnlyContent().isEmpty()) {
                if (child.getChildren().isEmpty()) {
                    /* if it is the last node remove it physically */
                    childrenToRemove.add(child);
                } else {
                    /*
                     * the child still has children, but it terminated some pattern => don't remove
                     * it, just say that it doesn't terminate any pattern anymore
                     */
                    child.makeNodeInternal();
                }
                continue;
            }

            /* internal node without children should be removed */
            if (!child.isLeaf() && child.getChildren().isEmpty()) {
                childrenToRemove.add(child);
            }
        }

        head.removeChildren(childrenToRemove);
    }
}
