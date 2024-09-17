/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#include <locale.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "locale_str.h"

#ifdef _WIN64
#include <windows.h>
#include <Wincon.h>

#define PROPSIZE 9      // eight-letter + null terminator
#define SNAMESIZE 86    // max number of chars for LOCALE_SNAME is 85
#endif

/* Take an array of string pairs (map of key->value) and a string (key).
 * Examine each pair in the map to see if the first string (key) matches the
 * string.  If so, store the second string of the pair (value) in the value and
 * return 1.  Otherwise do nothing and return 0.  The end of the map is
 * indicated by an empty string at the start of a pair (key of "").
 */
static int mapLookup(char* map[], const char* key, char** value) {
    int i;
    for (i = 0; strcmp(map[i], ""); i += 2){
        if (!strcmp(key, map[i])){
            *value = map[i + 1];
            return 1;
        }
    }
    return 0;
}

static locale_props_t *allocateJavaProps() {
    locale_props_t* sprops = malloc(sizeof(locale_props_t));
    sprops->language = NULL;
    sprops->country = NULL;
    sprops->variant = NULL;
    sprops->script = NULL;
    sprops->extensions = NULL;
    return sprops;
}

/*
 * Copied and adapted from java.base/unix/native/libjava/java_props_md.c. We are not calling
 * these functions via static linking of libjava, because we only need a small subset of the
 * functionalities that exist in the original method.
 */
static int ParseLocale(locale_props_t * sprops, int cat) {
    setlocale(LC_ALL, "");
    char **std_language = &(sprops->language);
    char **std_country = &(sprops->country);
    char **std_variant = &(sprops->variant);
    char **std_script = &(sprops->script);
    char **std_extensions = &(sprops->extensions);

    char *temp = NULL;
    char *language = NULL, *country = NULL, *variant = NULL,
            *encoding = NULL;
    char *p, *encoding_variant, *old_temp, *old_ev;
    char *lc;

    /* Query the locale set for the category */

#ifdef MACOSX
    lc = setupMacOSXLocale(cat); // malloc'd memory, need to free
#else
    lc = setlocale(cat, NULL);
#endif

#ifndef __linux__
    if (lc == NULL) {
        return 0;
    }

    temp = malloc(strlen(lc) + 1);
    if (temp == NULL) {
#ifdef MACOSX
        free(lc); // malloced memory
#endif
        // JNU_ThrowOutOfMemoryError(env, NULL);
        return 0;
    }

    if (cat == LC_CTYPE) {
        /*
         * Workaround for Solaris bug 4201684: Xlib doesn't like @euro
         * locales. Since we don't depend on the libc @euro behavior,
         * we just remove the qualifier.
         * On Linux, the bug doesn't occur; on the other hand, @euro
         * is needed there because it's a shortcut that also determines
         * the encoding - without it, we wouldn't get ISO-8859-15.
         * Therefore, this code section is Solaris-specific.
         */
        strcpy(temp, lc);
        p = strstr(temp, "@euro");
        if (p != NULL) {
            *p = '\0';
            setlocale(LC_ALL, temp);
        }
    }
#else
    if (lc == NULL || !strcmp(lc, "C") || !strcmp(lc, "POSIX")) {
        lc = "en_US";
    }

    temp = malloc(strlen(lc) + 1);
    if (temp == NULL) {
        // JNU_ThrowOutOfMemoryError(env, NULL);
        return 0;
    }

#endif

    /*
     * locale string format in Solaris is
     * <language name>_<country name>.<encoding name>@<variant name>
     * <country name>, <encoding name>, and <variant name> are optional.
     */

    strcpy(temp, lc);
#ifdef MACOSX
    free(lc); // malloced memory
#endif
    /* Parse the language, country, encoding, and variant from the
     * locale.  Any of the elements may be missing, but they must occur
     * in the order language_country.encoding@variant, and must be
     * preceded by their delimiter (except for language).
     *
     * If the locale name (without .encoding@variant, if any) matches
     * any of the names in the locale_aliases list, map it to the
     * corresponding full locale name.  Most of the entries in the
     * locale_aliases list are locales that include a language name but
     * no country name, and this facility is used to map each language
     * to a default country if that's possible.  It's also used to map
     * the Solaris locale aliases to their proper Java locale IDs.
     */

    encoding_variant = malloc(strlen(temp)+1);
    if (encoding_variant == NULL) {
        free(temp);
        // JNU_ThrowOutOfMemoryError(env, NULL);
        return 0;
    }

    if ((p = strchr(temp, '.')) != NULL) {
        strcpy(encoding_variant, p); /* Copy the leading '.' */
        *p = '\0';
    } else if ((p = strchr(temp, '@')) != NULL) {
        strcpy(encoding_variant, p); /* Copy the leading '@' */
        *p = '\0';
    } else {
        *encoding_variant = '\0';
    }

    if (mapLookup(locale_aliases, temp, &p)) {
        old_temp = temp;
        temp = realloc(temp, strlen(p)+1);
        if (temp == NULL) {
            free(old_temp);
            free(encoding_variant);
            // JNU_ThrowOutOfMemoryError(env, NULL);
            return 0;
        }
        strcpy(temp, p);
        old_ev = encoding_variant;
        encoding_variant = realloc(encoding_variant, strlen(temp)+1);
        if (encoding_variant == NULL) {
            free(old_ev);
            free(temp);
            // JNU_ThrowOutOfMemoryError(env, NULL);
            return 0;
        }
        // check the "encoding_variant" again, if any.
        if ((p = strchr(temp, '.')) != NULL) {
            strcpy(encoding_variant, p); /* Copy the leading '.' */
            *p = '\0';
        } else if ((p = strchr(temp, '@')) != NULL) {
            strcpy(encoding_variant, p); /* Copy the leading '@' */
            *p = '\0';
        }
    }

    language = temp;
    if ((country = strchr(temp, '_')) != NULL) {
        *country++ = '\0';
    }

    p = encoding_variant;
    if ((encoding = strchr(p, '.')) != NULL) {
        p[encoding++ - p] = '\0';
        p = encoding;
    }
    if ((variant = strchr(p, '@')) != NULL) {
        p[variant++ - p] = '\0';
    }

    /* Normalize the language name */
    if (std_language != NULL) {
        *std_language = "en";
        if (language != NULL && mapLookup(language_names, language, std_language) == 0) {
            *std_language = malloc(strlen(language)+1);
            if (*std_language == NULL) {
                free(encoding_variant);
                // JNU_ThrowOutOfMemoryError(env, NULL);
                return 0;
            }
            strcpy(*std_language, language);
        }
    }

    /* Normalize the country name */
    if (std_country != NULL && country != NULL) {
        if (mapLookup(country_names, country, std_country) == 0) {
            *std_country = malloc(strlen(country)+1);
            if (*std_country == NULL) {
                free(encoding_variant);
                // JNU_ThrowOutOfMemoryError(env, NULL);
                return 0;
            }
            strcpy(*std_country, country);
        }
    }

    /* Normalize the script and variant name.  Note that we only use
     * variants listed in the mapping array; others are ignored.
     */
    if (variant != NULL) {
        if (std_script != NULL) {
            mapLookup(script_names, variant, std_script);
        }

        if (std_variant != NULL) {
            mapLookup(variant_names, variant, std_variant);
        }
    }

    free(temp);
    free(encoding_variant);

    return 1;
}

#ifdef _WIN64
/*
 * Copied and adapted from java.base/windows/native/libjava/java_props_md.c. We are not calling
 * these functions via static linking of libjava, because we only need a small subset of the
 * functionalities that exist in the original method.
 */
static int SetupI18nProps(LCID lcid, char** language, char** script, char** country, char** variant) {
    /* script */
    char tmp[SNAMESIZE];
    *script = malloc(PROPSIZE);
    if (*script == NULL) {
        return 0;
    }
    if (GetLocaleInfo(lcid,
                      LOCALE_SNAME, tmp, SNAMESIZE) == 0 ||
        sscanf(tmp, "%*[a-z\\-]%1[A-Z]%[a-z]", *script, &((*script)[1])) == 0 ||
        strlen(*script) != 4) {
        (*script)[0] = '\0';
    }

    /* country */
    *country = malloc(PROPSIZE);
    if (*country == NULL) {
        return 0;
    }
    if (GetLocaleInfo(lcid,
                      LOCALE_SISO3166CTRYNAME, *country, PROPSIZE) == 0 &&
        GetLocaleInfo(lcid,
                      LOCALE_SISO3166CTRYNAME2, *country, PROPSIZE) == 0) {
        (*country)[0] = '\0';
    }

    /* language */
    *language = malloc(PROPSIZE);
    if (*language == NULL) {
        return 0;
    }
    if (GetLocaleInfo(lcid,
                      LOCALE_SISO639LANGNAME, *language, PROPSIZE) == 0 &&
        GetLocaleInfo(lcid,
                      LOCALE_SISO639LANGNAME2, *language, PROPSIZE) == 0) {
            /* defaults to en_US */
            strcpy(*language, "en");
            strcpy(*country, "US");
        }

    /* variant */
    *variant = malloc(PROPSIZE);
    if (*variant == NULL) {
        return 0;
    }
    (*variant)[0] = '\0';

    /* handling for Norwegian */
    if (strcmp(*language, "nb") == 0) {
        strcpy(*language, "no");
        strcpy(*country , "NO");
    } else if (strcmp(*language, "nn") == 0) {
        strcpy(*language, "no");
        strcpy(*country , "NO");
        strcpy(*variant, "NY");
    }

    return 1;
}

void PopulateJavaProperties(locale_props_t *sprops, int isFormatLocale) {
    /*
    * query the system for the current system default locale
    * (which is a Windows LCID value),
    */
    LCID userDefaultLCID = GetUserDefaultLCID();
    LANGID userDefaultUILang = GetUserDefaultUILanguage();
    LCID userDefaultUILCID = MAKELCID(userDefaultUILang, SORTIDFROMLCID(userDefaultLCID));

    // Windows UI Language selection list only cares "language"
    // information of the UI Language. For example, the list
    // just lists "English" but it actually means "en_US", and
    // the user cannot select "en_GB" (if exists) in the list.
    // So, this hack is to use the user LCID region information
    // for the UI Language, if the "language" portion of those
    // two locales are the same.
    if (PRIMARYLANGID(LANGIDFROMLCID(userDefaultLCID)) ==
        PRIMARYLANGID(userDefaultUILang)) {
        userDefaultUILCID = userDefaultLCID;
    }

    SetupI18nProps(isFormatLocale ? userDefaultLCID : userDefaultUILCID,
                     &(sprops->language),
                     &(sprops->script),
                     &(sprops->country),
                     &(sprops->variant));
}
#endif

locale_props_t* parseDisplayLocale() {
    locale_props_t *sprops = allocateJavaProps();
#ifndef _WIN64
    int result = ParseLocale(sprops, LC_MESSAGES);
    if (!result) {
        sprops->language = "en";
    }
#else
    PopulateJavaProperties(sprops, 0);
#endif
    return sprops;
}

locale_props_t* parseFormatLocale() {
    locale_props_t *sprops = allocateJavaProps();
#ifndef _WIN64
    int result = ParseLocale(sprops, LC_CTYPE);
#else
    PopulateJavaProperties(sprops, 1);
#endif

    return sprops;
}
