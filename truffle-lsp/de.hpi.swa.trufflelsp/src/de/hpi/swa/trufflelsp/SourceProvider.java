package de.hpi.swa.trufflelsp;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.source.Source;

public class SourceProvider implements LoadSourceListener, LoadSourceSectionListener {
    protected Map<String, Map<URI, SourceWrapper>> langId2loadedSources = new LinkedHashMap<>();

    public SourceWrapper getLoadedSource(String langId, URI uri) {
        Map<URI, SourceWrapper> uri2sources = this.langId2loadedSources.get(langId);
        return uri2sources == null ? null : uri2sources.get(uri);
    }

    public void onLoad(LoadSourceEvent event) {
        // This method is only called for every "new" Source, i.e. if I add a character via the
        // editor, this is called, but when I remove the character, it is not called again, because
        // the text of the Source is then identical again to the previous Source
        // Disabling Source caching does not help...
        Source source = event.getSource();
        initLang(source.getLanguage());
        Map<URI, SourceWrapper> uri2SourceWrapper = this.langId2loadedSources.get(source.getLanguage());
        uri2SourceWrapper.put(source.getURI(), new SourceWrapper(source));
    }

    public void onLoad(LoadSourceSectionEvent event) {
        String langId = event.getNode().getRootNode().getLanguageInfo().getId();
        Source source = event.getSourceSection().getSource();
        URI uri = source.getURI();
        Map<URI, SourceWrapper> uri2SourceWrapper = this.langId2loadedSources.get(langId);
        SourceWrapper sourceWrapper = uri2SourceWrapper.get(uri);
        if (sourceWrapper == null || !sourceWrapper.getSource().equals(source)) {
            // This is needed here to store the last parsed Source
            // See comment onLoad(LoadSourceEvent)
            sourceWrapper = new SourceWrapper(source);
            uri2SourceWrapper.put(source.getURI(), sourceWrapper);
        }
    }

    private void initLang(String langId) {
        if (!this.langId2loadedSources.containsKey(langId)) {
            this.langId2loadedSources.put(langId, new LinkedHashMap<>());
        }
    }

    public void remove(String langId, URI uri) {
        if (this.langId2loadedSources.containsKey(langId) && this.langId2loadedSources.get(langId).containsKey(uri)) {
            this.langId2loadedSources.get(langId).remove(uri);
        }
    }

    public boolean containsKey(Object key) {
        return this.langId2loadedSources.containsKey(key);
    }
}
