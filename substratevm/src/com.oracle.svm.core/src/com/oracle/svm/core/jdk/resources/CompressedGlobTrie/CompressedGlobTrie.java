/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.jdk.resources.CompressedGlobTrie.GlobTrieNode.LEVEL_IDENTIFIER;
import static com.oracle.svm.core.jdk.resources.CompressedGlobTrie.GlobTrieNode.STAR;
import static com.oracle.svm.core.jdk.resources.CompressedGlobTrie.GlobTrieNode.STAR_STAR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.StringUtil;

/**
 * This data structure represents an immutable, Trie-like data structure that stores glob patterns
 * without redundancies. Once created, it cannot be changed, but only queried.
 *
 * The structure can be created using {@link CompressedGlobTrieBuilder#build(List)} where list
 * parameter represents list of glob patterns given in any order. At the very beginning, all given
 * globs will be validated using {@link CompressedGlobTrieBuilder#validatePattern(String)}. If all
 * globs were correct, they will be classified (with
 * {@link CompressedGlobTrieBuilder#classifyPatterns(List, List, List, List)}) and sorted (using
 * {@link CompressedGlobTrieBuilder#comparePatterns(GlobWithInfo, GlobWithInfo)} as the comparator
 * function). This preprocessing phase allows incremental structure build, going from the most
 * general glob pattern to the more specific ones. Furthermore, when trying to add a new glob, the
 * structure will first check if the given glob can be matched with some glob that already exists in
 * the structure (NOTE: possibly more than one glob pattern from the structure can match the new
 * pattern). When that is not the case, a new pattern will be added using
 * {@link CompressedGlobTrieBuilder#addNewBranch(GlobTrieNode, List, int, String)}. This function
 * will attempt identical matching (where wildcards have no special semantics) to move as deep in
 * the structure as possible. Once it cannot proceed with the existing branches, the function will
 * append rest of the pattern as the new branch. At the end, each node (not necessarily leaf) that
 * represents end of the glob, can store additional content.
 *
 * When created, the structure can be used to either check if the given text can be matched with
 * globs from the structure (using {@link #match(GlobTrieNode, String)}) or to fetch all additional
 * content from globs that can match given text (using
 * {@link #getAdditionalContentIfMatched(GlobTrieNode, String)}).
 */
public class CompressedGlobTrie {

    public record GlobWithInfo(String pattern, String additionalContent) {
    }

    /*
     * Data transfer object that points to the next matched child instance and its index in the
     * pattern parts list
     */
    private record MatchedNode(GlobTrieNode child, int lastMatchedChildIndex) {
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
    public static class CompressedGlobTrieBuilder {
        /**
         * Builds an immutable CompressedGlobTrie structure from glob patterns given in any order
         * with possibly additional context.
         */
        public static GlobTrieNode build(List<GlobWithInfo> patterns) {
            GlobTrieNode root = new GlobTrieNode();
            /* classify patterns in groups */
            List<GlobWithInfo> doubleStarPatterns = new ArrayList<>();
            List<GlobWithInfo> starPatterns = new ArrayList<>();
            List<GlobWithInfo> noStarPatterns = new ArrayList<>();

            List<String> invalidPatterns = classifyPatterns(patterns, doubleStarPatterns, starPatterns, noStarPatterns);
            if (!invalidPatterns.isEmpty()) {
                StringBuilder sb = new StringBuilder("Error: invalid glob patterns found:" + System.lineSeparator());
                invalidPatterns.forEach(msg -> sb.append(msg).append(System.lineSeparator()));
                throw UserError.abort(sb.toString());
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

            root.trim();
            return root;
        }

        private static void addPattern(GlobTrieNode root, GlobWithInfo pattern) {
            List<GlobTrieNode> parts = getPatternParts(pattern.pattern());

            /*
             * if this pattern can be matched with some other existing pattern, we should just
             * update content of leafs
             */
            List<GlobTrieNode> reachableNodes = new ArrayList<>();
            getAllPatterns(root, parts, 0, reachableNodes);
            if (!reachableNodes.isEmpty()) {
                /* new pattern is a part of some existing patterns in the existing Trie */
                for (GlobTrieNode node : reachableNodes) {
                    /*
                     * Both pattern and additionalContent are already present in the trie, so we can
                     * skip this pattern
                     */
                    if (node.getAdditionalContent().stream().anyMatch(c -> c.equals(pattern.additionalContent()))) {
                        return;
                    }
                }
            }

            addPattern(root, parts, 0, pattern.additionalContent());
        }

        private static void addPattern(GlobTrieNode root, List<GlobTrieNode> parts, int i, String additionalInfo) {
            if (patternReachedEnd(i, parts)) {
                root.setLeaf();
                root.addAdditionalContent(additionalInfo);
                return;
            }

            GlobTrieNode nextPart = parts.get(i);
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

        private static void addNewBranch(GlobTrieNode root, List<GlobTrieNode> parts, int i, String additionalInfo) {
            /* sanity check */
            if (parts.isEmpty() || i >= parts.size()) {
                return;
            }

            GlobTrieNode newNode = null;
            /* we matched pattern parts until i-th pattern part, so just add rest */
            for (int j = i; j < parts.size(); j++) {
                GlobTrieNode part = parts.get(j);
                if (newNode == null) {
                    newNode = root.addChild(part.getContent(), part);
                    continue;
                }

                newNode = newNode.addChild(part.getContent(), part);
            }

            /* mark end of pattern and populate additional info */
            newNode.setLeaf();
            newNode.addAdditionalContent(additionalInfo);
        }

        private static List<String> classifyPatterns(List<GlobWithInfo> patterns,
                        List<GlobWithInfo> doubleStar,
                        List<GlobWithInfo> singleStar,
                        List<GlobWithInfo> noStar) {
            List<String> invalidPatterns = new ArrayList<>();
            for (GlobWithInfo patternWithInfo : patterns) {
                /* validate patterns */
                String error = validatePattern(patternWithInfo.pattern());
                if (!error.isEmpty()) {
                    invalidPatterns.add(error);
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

        private static int comparePatterns(GlobWithInfo n1, GlobWithInfo n2) {
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
     * Returns list of information from all glob patterns that could match given text.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static List<String> getAdditionalContentIfMatched(GlobTrieNode root, String text) {
        List<GlobTrieNode> matchedNodes = new ArrayList<>();
        getAllPatterns(root, getPatternParts(text), 0, matchedNodes);
        if (matchedNodes.isEmpty()) {
            /* text cannot be matched */
            return null;
        }

        List<String> additionalContexts = new ArrayList<>();
        matchedNodes.forEach(node -> additionalContexts.addAll(node.getAdditionalContent()));
        return additionalContexts;
    }

    /**
     * Returns whether given text can be matched with any glob pattern in the Trie or not.
     */
    public static boolean match(GlobTrieNode root, String text) {
        /* in this case text is a plain text without special meanings, so stars must be escaped */
        String escapedText = escapeAllStars(text);
        List<GlobTrieNode> tmp = new ArrayList<>();
        getAllPatterns(root, getPatternParts(escapedText), 0, tmp);
        return !tmp.isEmpty();
    }

    private static String escapeAllStars(String text) {
        return text.replace("*", "\\*");
    }

    private static final Pattern threeConsecutiveStarsRegex = Pattern.compile(".*[*]{3,}.*");
    private static final Pattern emptyLevelsRegex = Pattern.compile(".*/{2,}.*");

    public static String validatePattern(String pattern) {
        StringBuilder sb = new StringBuilder();

        if (pattern.isEmpty()) {
            sb.append("Pattern ").append(pattern).append(" : Pattern cannot be empty. ");
            return sb.toString();
        }

        // check if pattern contains more than 2 consecutive characters. Example: a/***/b
        if (threeConsecutiveStarsRegex.matcher(pattern).matches()) {
            sb.append("Pattern contains more than two consecutive * characters. ");
        }

        /* check if pattern contains empty levels. Example: a//b */
        if (emptyLevelsRegex.matcher(pattern).matches()) {
            sb.append("Pattern contains empty levels. ");
        }

        /* check unnecessary ** repetition */
        if (pattern.contains("**/**")) {
            sb.append("Pattern contains invalid sequence **/**. Valid pattern should have ** followed by something other than **. ");
        }

        // check if pattern contains ** without previous Literal parent. Example: */**/... or **/...
        List<GlobTrieNode> patternParts = getPatternParts(pattern);
        for (GlobTrieNode part : patternParts) {
            if (part instanceof LiteralNode) {
                break;
            }

            if (part instanceof DoubleStarNode) {
                sb.append("Pattern contains ** without previous literal. " +
                                "This pattern is too generic and therefore can match many resources. " +
                                "Please make the pattern more specific by adding non-generic level before ** level.");
            }
        }

        if (!sb.isEmpty()) {
            sb.insert(0, "Pattern " + pattern + " : ");
        }

        return sb.toString();
    }

    /**
     * Returns list of glob pattern parts that will represent nodes in final Trie. This function is
     * used as a helper function in tests as well, and therefore must remain public.
     */
    public static List<GlobTrieNode> getPatternParts(String glob) {
        String pattern = !glob.endsWith("/") ? glob : glob.substring(0, glob.length() - 1);
        List<GlobTrieNode> parts = new ArrayList<>();
        /* we are splitting patterns on levels */
        List<String> levels = Arrays.stream(pattern.split(LEVEL_IDENTIFIER)).toList();
        for (String level : levels) {
            if (level.equals(STAR_STAR)) {
                DoubleStarNode tmp = new DoubleStarNode();
                tmp.setNewLevel();
                parts.add(tmp);
                continue;
            }

            if (level.equals(STAR)) {
                /* special case when * is alone on one level */
                StarTrieNode tmp = new StarTrieNode(true);
                tmp.setNewLevel();
                parts.add(tmp);
                continue;
            }

            /* adding a*bc and a*dc patterns will produce: a* -> bc a* -> dc */
            int s = level.indexOf(STAR.charAt(0));
            if (s != -1) {
                /*
                 * this level contains at least one star, but maybe it has more. E.g.:
                 * something/a*b*c*d/else
                 */

                List<GlobTrieNode> thisLevelParts = new ArrayList<>();
                StringBuilder currentPart = new StringBuilder();
                StarCollectorMode currentMode = StarCollectorMode.NORMAL;
                for (char c : level.toCharArray()) {
                    currentPart.append(c);
                    if (c == STAR.charAt(0) && currentMode == StarCollectorMode.NORMAL) {
                        thisLevelParts.add(new StarTrieNode(currentPart.toString()));
                        currentPart.setLength(0);
                    }

                    currentMode = c == '\\' ? StarCollectorMode.ESCAPE : StarCollectorMode.NORMAL;
                }

                if (!currentPart.isEmpty()) {
                    /* this level ends with some literal node */
                    thisLevelParts.add(new LiteralNode(currentPart.toString()));
                }
                thisLevelParts.get(0).setNewLevel();
                parts.addAll(thisLevelParts);
                continue;
            }

            LiteralNode tmp = new LiteralNode(level);
            tmp.setNewLevel();
            parts.add(tmp);
        }

        return parts;
    }

    private enum StarCollectorMode {
        NORMAL,
        ESCAPE
    }

    private static void getAllPatterns(GlobTrieNode node, List<GlobTrieNode> parts, int i, List<GlobTrieNode> matches) {
        if (patternReachedEnd(i, parts)) {
            if (node.isLeaf()) {
                matches.add(node);
            }

            return;
        }

        /* get ** successors that could extend check */
        DoubleStarNode doubleStar = node.getDoubleStarNode();
        if (doubleStar != null) {
            for (MatchedNode child : getAllAvailablePaths(doubleStar, parts, i)) {
                getAllPatterns(child.child(), parts, child.lastMatchedChildIndex() + 1, matches);
            }
        }

        /* get * nodes that could match next level */
        SquashedParts sp = getThisLevel(parts, i);
        for (var child : node.getChildrenWithStar()) {
            for (GlobTrieNode c : matchOneLevel(child, sp.squashedPart())) {
                getAllPatterns(c, parts, i + sp.numberOfSquashedParts() + 1, matches);
            }
        }

        GlobTrieNode part = parts.get(i);
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
        GlobTrieNode child = node.getChild(part.getContent());
        if (child == null) {
            return;
        }

        /* we found simple match, proceed to next part with matched child */
        getAllPatterns(child, parts, i + 1, matches);
    }

    private static List<MatchedNode> getAllAvailablePaths(DoubleStarNode node, List<GlobTrieNode> parts, int i) {
        List<MatchedNode> successors = new ArrayList<>();
        /* maybe ** is leaf, so we should cover this pattern */
        if (node.isLeaf()) {
            return List.of(new MatchedNode(node, parts.size()));
        }

        /* checks if we skip some part (on index j) can we match with some child of node */
        for (int j = i; j < parts.size(); j++) {
            /* in case next part contains many stars, squash them into one node */
            SquashedParts sp = getThisLevel(parts, j);
            /* checks if any child of current node can match next part */
            for (StarTrieNode child : node.getChildrenWithStar()) {
                int finalJ = j;
                /* we can match next level with more than one pattern */
                successors.addAll(matchOneLevel(child, sp.squashedPart())
                                .stream()
                                .map(c -> new MatchedNode(c, finalJ + sp.numberOfSquashedParts()))
                                .toList());
            }

            GlobTrieNode part = parts.get(j);
            if (part instanceof StarTrieNode) {
                /*
                 * we are only checking trivial parts because we processed star nodes, and we don't
                 * want to interpret (for example) a* as a simple pattern
                 */
                continue;
            }

            GlobTrieNode child = node.getChild(part.getContent());
            /*
             * we are checking only parts that begins new level because in pattern a*b*c, last c is
             * LiteralNode, but it is a part of complex StarTrieNode
             */
            if (part.isNewLevel() && child != null) {
                successors.add(new MatchedNode(child, j));
            }
        }

        return successors;
    }

    private static int getIndexOfFirstUnescapedStar(String level) {
        StarCollectorMode currentMode = StarCollectorMode.NORMAL;
        for (int i = 0; i < level.length(); i++) {
            char c = level.charAt(i);
            if (c == STAR.charAt(0) && currentMode == StarCollectorMode.NORMAL) {
                return i;
            }

            currentMode = c == '\\' ? StarCollectorMode.ESCAPE : StarCollectorMode.NORMAL;
        }

        return -1;
    }

    private static List<GlobTrieNode> matchOneLevel(StarTrieNode node, String wholeLevel) {
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

        List<GlobTrieNode> potentialChildren = new ArrayList<>();
        for (LiteralNode child : node.getChildrenWithLiteral()) {
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
        for (StarTrieNode child : node.getChildrenWithStar()) {
            if (child.isNewLevel()) {
                // we won't try matching with this child since it is not on same level
                // example: a*/c* => c* is a child of a* but it is on the next level, so it can't be
                // used to match this new level
                continue;
            }

            /* if there is a repetition of certain * node we must check paths through all of them */
            StarTrieNode tmpChild = child;
            while (true) {
                String childContent = tmpChild.getContent();
                String childPrefix = childContent.substring(0, childContent.indexOf(STAR.charAt(0)));
                int nextOccurrence = remainingLevel.indexOf(childPrefix);
                if (nextOccurrence != -1) {
                    potentialChildren.addAll(matchOneLevel(tmpChild, remainingLevel.substring(nextOccurrence)));
                }

                /* check if current node has child with same content on same level */
                GlobTrieNode potentialChild = tmpChild.getChildFromSameLevel(tmpChild.getContent());
                if (potentialChild == null) {
                    /* we don't have more repeated star nodes so just quit the loop */
                    break;
                }

                /* move to next node that is repeated */
                tmpChild = (StarTrieNode) potentialChild;
            }
        }

        return potentialChildren;
    }

    private static boolean simplePatternMatch(GlobTrieNode root, GlobTrieNode part) {
        if (root instanceof StarTrieNode || root instanceof DoubleStarNode || part instanceof StarTrieNode || part instanceof DoubleStarNode) {
            return false;
        }

        return root.getChild(part.getContent()) != null;
    }

    private static SquashedParts getThisLevel(List<GlobTrieNode> parts, int begin) {
        StringBuilder sb = new StringBuilder(parts.get(begin).getContent());
        int numberOfSquashedParts = 0;
        /* collect all parts until we hit beginning of the new level */
        for (int i = begin + 1; i < parts.size(); i++) {
            GlobTrieNode nextPart = parts.get(i);
            if (nextPart.isNewLevel()) {
                break;
            }

            sb.append(nextPart.getContent());
            numberOfSquashedParts++;
        }

        return new SquashedParts(sb.toString(), numberOfSquashedParts);
    }

    private static boolean patternReachedEnd(int index, List<GlobTrieNode> parts) {
        return index >= parts.size();
    }
}
