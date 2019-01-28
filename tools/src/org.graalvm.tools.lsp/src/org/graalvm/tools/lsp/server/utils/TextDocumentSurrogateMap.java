package org.graalvm.tools.lsp.server.utils;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

public class TextDocumentSurrogateMap {
    private final Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate = new HashMap<>();
    private final Map<String, List<String>> langId2CompletionTriggerCharacters;
    private final Map<String, LanguageInfo> mimeType2LangInfo;

    public TextDocumentSurrogateMap(Map<String, List<String>> langId2CompletionTriggerCharacters, Map<String, LanguageInfo> mimeType2LangInfo) {
        this.langId2CompletionTriggerCharacters = langId2CompletionTriggerCharacters;
        this.mimeType2LangInfo = mimeType2LangInfo;
    }

    public TextDocumentSurrogate get(URI uri) {
        return uri2TextDocumentSurrogate.get(uri);
    }

    public void put(URI uri, TextDocumentSurrogate surrogate) {
        uri2TextDocumentSurrogate.put(uri, surrogate);
    }

    public TextDocumentSurrogate getOrCreateSurrogate(URI uri, LanguageInfo languageInfo) {
        return uri2TextDocumentSurrogate.computeIfAbsent(uri,
                        (anUri) -> new TextDocumentSurrogate(anUri, languageInfo, getCompletionTriggerCharacters(languageInfo.getId())));
    }

    public TextDocumentSurrogate getOrCreateSurrogate(URI uri, Supplier<LanguageInfo> languageInfoSupplier) {
        return uri2TextDocumentSurrogate.computeIfAbsent(uri,
                        (anUri) -> {
                            LanguageInfo languageInfo = languageInfoSupplier.get();
                            return new TextDocumentSurrogate(anUri, languageInfo, getCompletionTriggerCharacters(languageInfo.getId()));
                        });
    }

    private List<String> getCompletionTriggerCharacters(String langId) {
        return langId2CompletionTriggerCharacters.get(langId);
    }

    public Set<String> getLanguage(String mimeType) {
        return mimeType2LangInfo.get(mimeType).getMimeTypes();
    }

    public Collection<TextDocumentSurrogate> getSurrogates() {
        return uri2TextDocumentSurrogate.values();
    }

    public boolean containsSurrogate(URI uri) {
        return uri2TextDocumentSurrogate.containsKey(uri);
    }

    public void remove(URI uri) {
        uri2TextDocumentSurrogate.remove(uri);
    }

    public boolean isSourceNewestInSurrogate(Source source) {
        TextDocumentSurrogate surrogate = get(source.getURI());
        if (surrogate != null) {
            return source.equals(surrogate.getSource());
        }
        return false;
    }
}
