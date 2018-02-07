package com.oracle.truffle.api.instrumentation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AccessorInstrumentHandler;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Base class for tags. A tag subclass can be used to {@link InstrumentableNode#hasTag(Class)} mark
 * AST nodes can be .
 *
 * @see StandardTags For the standard set of tags
 * @since 0.32
 */
public abstract class Tag {

    protected Tag() {
        throw new AssertionError("No tag instances allowed.");
    }

    /**
     * Finds a provided tag by the language using its {@link Tag.Identifier identifier}. If the
     * language implementation class is not yet loaded then this method with force the loading.
     * Therefore it is not recommended to iterate over the entire list of languages and request all
     * provided tags. It is guaranteed that there is only one provided tag class per tag identifier
     * and language. For different languages the same tag id might refer to different tag classes.
     *
     * @since 0.32
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends Tag> findProvidedTag(LanguageInfo language, String tagId) {
        Objects.requireNonNull(language);
        Objects.requireNonNull(tagId);
        EngineSupport engine = AccessorInstrumentHandler.engineAccess();
        if (engine == null) {
            return null;
        }
        Class<? extends TruffleLanguage<?>> lang = engine.getLanguageClass(language);
        ProvidedTags tags = lang.getAnnotation(ProvidedTags.class);
        if (tags != null) {
            for (Class<? extends Tag> tag : (Class<? extends Tag>[]) tags.value()) {
                String alias = getIdentifier(tag);
                if (alias != null && alias.equals(tagId)) {
                    return tag;
                }
            }
        }
        return null;
    }

    /**
     * Returns the alias of a particular tag or <code>null</code> if no alias was specified for this
     * tag.
     *
     * @param tag the tag to return the alias for.
     * @return the alias string
     * @since 0.32
     */
    public static String getIdentifier(Class<? extends Tag> tag) {
        Objects.requireNonNull(tag);
        Tag.Identifier alias = tag.getAnnotation(Tag.Identifier.class);
        if (alias != null) {
            return alias.value();
        }
        return null;
    }

    /**
     * Annotation applied to {@link Tag} subclasses to specify the tag identifier. The tag
     * identifier can be used to {@link Tag#findProvidedTag(LanguageInfo, String) find} and load tag
     * classes using by tools.
     *
     * @since 0.32
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = TYPE)
    protected @interface Identifier {

        /**
         *
         * @since 0.32
         */
        String value();

    }

}
