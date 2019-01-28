package org.graalvm.tools.lsp.server.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.options.OptionValues;
import org.graalvm.tools.lsp.instrument.LSOptions;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

public final class SourcePredicateBuilder {

    private final List<Predicate<Source>> predicates = new ArrayList<>();

    private SourcePredicateBuilder() {
    }

    /**
     * A special method to filter for either the URI of a {@link Source} or via the name. Some
     * languages create sources in a way that no URI is set, only the {@link Source}'s name which is
     * the path of the source.
     *
     * @param uri
     */
    public SourcePredicateBuilder uriOrTruffleName(URI uri) {
        SourcePredicate predicate = src -> src.getURI().equals(uri) || (src.getURI().getScheme().equals("truffle") && src.getName().equals(uri.getPath()));
        this.predicates.add(predicate);
        return this;
    }

    public SourcePredicateBuilder language(LanguageInfo languageInfo) {
        SourcePredicate predicate = src -> languageInfo.getId().equals(src.getLanguage()) ||
                        (src.getMimeType() != null && languageInfo.getMimeTypes().contains(src.getMimeType()));
        this.predicates.add(predicate);
        return this;
    }

    public SourcePredicateBuilder excludeInternalIgnoreOptions() {
        this.predicates.add(src -> !src.isInternal());
        return this;
    }

    public SourcePredicateBuilder excludeInternal(OptionValues options) {
        boolean includeInternal = options.get(LSOptions.IncludeInternalSources);
        SourcePredicate predicate = src -> (includeInternal || !src.isInternal());
        this.predicates.add(predicate);
        return this;
    }

    public SourcePredicateBuilder newestSource(TextDocumentSurrogateMap surrogateMap) {
        this.predicates.add(src -> surrogateMap.isSourceNewestInSurrogate(src));
        return this;
    }

    public SourcePredicate build() {
        Predicate<Source> predicate = predicates.stream().reduce((predicateA, predicateB) -> predicateA.and(predicateB)).get();
        return src -> predicate.test(src);
    }

    public static SourcePredicateBuilder newBuilder() {
        return new SourcePredicateBuilder();
    }
}
