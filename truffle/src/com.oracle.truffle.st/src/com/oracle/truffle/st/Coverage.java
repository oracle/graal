package com.oracle.truffle.st;

import org.graalvm.polyglot.SourceSection;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains per {@link com.oracle.truffle.api.source.Source} coverage by keeping track of loaded and
 * covered {@link com.oracle.truffle.api.source.SourceSection}s
 */
public class Coverage {
    private Set<SourceSection> loaded = new HashSet<>();
    private Set<SourceSection> covered = new HashSet<>();
}
