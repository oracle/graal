/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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


#ifdef _WIN64
/*
 * The following functions are an identical copy of the functions in file TimeZone_md.c found at
 * https://github.com/graalvm/labs-openjdk-11/blob/67ddc3bcadd985ea26997457aec6696f21caf154/src/java.base/windows/native/libjava/TimeZone_md.c
 * With the exceptions of the commented functions this file has not been modified from its original.
 *
 * Support for the new tzmappings format, introduced in JDK 12, has been added by backporting
 * JDK-8209167. So, depending on the value of the JDK_VER macro, the resulting code will support
 * either the old format or the new format, but not both.
 */
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>

#define VALUE_UNKNOWN           0
#define VALUE_KEY               1
#define VALUE_MAPID             2
#define VALUE_GMTOFFSET         3

#define MAX_ZONE_CHAR           256
#define MAX_MAPID_LENGTH        32
#define MAX_REGION_LENGTH       4

#define NT_TZ_KEY               "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Time Zones"
#define WIN_TZ_KEY              "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Time Zones"
#define WIN_CURRENT_TZ_KEY      "System\\CurrentControlSet\\Control\\TimeZoneInformation"

typedef struct _TziValue {
    LONG        bias;
    LONG        stdBias;
    LONG        dstBias;
    SYSTEMTIME  stdDate;
    SYSTEMTIME  dstDate;
} TziValue;

/*
 * Registry key names
 */
static void *keyNames[] = {
    (void *) L"StandardName",
    (void *) "StandardName",
    (void *) L"Std",
    (void *) "Std"
};

/*
 * Indices to keyNames[]
 */
#define STANDARD_NAME           0
#define STD_NAME                2

/*
 * Calls RegQueryValueEx() to get the value for the specified key. If
 * the platform is NT, 2000 or XP, it calls the Unicode
 * version. Otherwise, it calls the ANSI version and converts the
 * value to Unicode. In this case, it assumes that the current ANSI
 * Code Page is the same as the native platform code page (e.g., Code
 * Page 932 for the Japanese Windows systems.
 *
 * `keyIndex' is an index value to the keyNames in Unicode
 * (WCHAR). `keyIndex' + 1 points to its ANSI value.
 *
 * Returns the status value. ERROR_SUCCESS if succeeded, a
 * non-ERROR_SUCCESS value otherwise.
 */
static LONG
getValueInRegistry(HKEY hKey,
                   int keyIndex,
                   LPDWORD typePtr,
                   LPBYTE buf,
                   LPDWORD bufLengthPtr)
{
    LONG ret;
    DWORD bufLength = *bufLengthPtr;
    char val[MAX_ZONE_CHAR];
    DWORD valSize;
    int len;

    *typePtr = 0;
    ret = RegQueryValueExW(hKey, (WCHAR *) keyNames[keyIndex], NULL,
                           typePtr, buf, bufLengthPtr);
    if (ret == ERROR_SUCCESS && *typePtr == REG_SZ) {
        return ret;
    }

    valSize = sizeof(val);
    ret = RegQueryValueExA(hKey, (char *) keyNames[keyIndex + 1], NULL,
                           typePtr, val, &valSize);
    if (ret != ERROR_SUCCESS) {
        return ret;
    }
    if (*typePtr != REG_SZ) {
        return ERROR_BADKEY;
    }

    len = MultiByteToWideChar(CP_ACP, MB_ERR_INVALID_CHARS,
                              (LPCSTR) val, -1,
                              (LPWSTR) buf, bufLength/sizeof(WCHAR));
    if (len <= 0) {
        return ERROR_BADKEY;
    }
    return ERROR_SUCCESS;
}

/*
 * Produces custom name "GMT+hh:mm" from the given bias in buffer.
 */
static void customZoneName(LONG bias, char *buffer) {
    LONG gmtOffset;
    int sign;

    if (bias > 0) {
        gmtOffset = bias;
        sign = -1;
    } else {
        gmtOffset = -bias;
        sign = 1;
    }
    if (gmtOffset != 0) {
        sprintf(buffer, "GMT%c%02d:%02d",
                ((sign >= 0) ? '+' : '-'),
                gmtOffset / 60,
                gmtOffset % 60);
    } else {
        strcpy(buffer, "GMT");
    }
}

/*
 * Gets the current time zone entry in the "Time Zones" registry.
 *
 * Note that the winMapID parameter is only used if JDK_VER <= 11.
 */
static int getWinTimeZone(char *winZoneName, char *winMapID)
{
    DYNAMIC_TIME_ZONE_INFORMATION dtzi;
    DWORD timeType;
    DWORD bufSize;
    DWORD val;
    HANDLE hKey = NULL;
    LONG ret;
    ULONG valueType;

    /*
     * Get the dynamic time zone information so that time zone redirection
     * can be supported. (see JDK-7044727)
     */
    timeType = GetDynamicTimeZoneInformation(&dtzi);
    if (timeType == TIME_ZONE_ID_INVALID) {
        goto err;
    }

    /*
     * Make sure TimeZoneKeyName is available from the API call. If
     * DynamicDaylightTime is disabled, return a custom time zone name
     * based on the GMT offset. Otherwise, return the TimeZoneKeyName
     * value.
     */
    if (dtzi.TimeZoneKeyName[0] != 0) {
        if (dtzi.DynamicDaylightTimeDisabled) {
            customZoneName(dtzi.Bias, winZoneName);
            return VALUE_GMTOFFSET;
        }
        wcstombs(winZoneName, dtzi.TimeZoneKeyName, MAX_ZONE_CHAR);
        return VALUE_KEY;
    }

    /*
     * If TimeZoneKeyName is not available, check whether StandardName
     * is available to fall back to the older API GetTimeZoneInformation.
     * If not, directly read the value from registry keys.
     */
    if (dtzi.StandardName[0] == 0) {
        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, WIN_CURRENT_TZ_KEY, 0,
                           KEY_READ, (PHKEY)&hKey);
        if (ret != ERROR_SUCCESS) {
            goto err;
        }

        /*
         * Determine if auto-daylight time adjustment is turned off.
         */
        bufSize = sizeof(val);
        ret = RegQueryValueExA(hKey, "DynamicDaylightTimeDisabled", NULL,
                               &valueType, (LPBYTE) &val, &bufSize);
        if (ret != ERROR_SUCCESS) {
            goto err;
        }
        /*
         * Return a custom time zone name if auto-daylight time adjustment
         * is disabled.
         */
        if (val == 1) {
            customZoneName(dtzi.Bias, winZoneName);
            (void) RegCloseKey(hKey);
            return VALUE_GMTOFFSET;
        }

        bufSize = MAX_ZONE_CHAR;
        ret = RegQueryValueExA(hKey, "TimeZoneKeyName", NULL,
                               &valueType, (LPBYTE) winZoneName, &bufSize);
        if (ret != ERROR_SUCCESS) {
            goto err;
        }
        (void) RegCloseKey(hKey);
        return VALUE_KEY;
    } else {
        /*
         * Fall back to GetTimeZoneInformation
         */
        TIME_ZONE_INFORMATION tzi;
        HANDLE hSubKey = NULL;
        DWORD nSubKeys, i;
        ULONG valueType;
        TCHAR subKeyName[MAX_ZONE_CHAR];
        TCHAR szValue[MAX_ZONE_CHAR];
        WCHAR stdNameInReg[MAX_ZONE_CHAR];
        TziValue tempTzi;
        WCHAR *stdNamePtr = tzi.StandardName;
        DWORD valueSize;
        int onlyMapID;

        timeType = GetTimeZoneInformation(&tzi);
        if (timeType == TIME_ZONE_ID_INVALID) {
            goto err;
        }

        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, WIN_CURRENT_TZ_KEY, 0,
                           KEY_READ, (PHKEY)&hKey);
        if (ret == ERROR_SUCCESS) {
            /*
             * Determine if auto-daylight time adjustment is turned off.
             */
            bufSize = sizeof(val);
            ret = RegQueryValueExA(hKey, "DynamicDaylightTimeDisabled", NULL,
                                   &valueType, (LPBYTE) &val, &bufSize);
            if (ret == ERROR_SUCCESS) {
                if (val == 1 && tzi.DaylightDate.wMonth != 0) {
                    (void) RegCloseKey(hKey);
                    customZoneName(tzi.Bias, winZoneName);
                    return VALUE_GMTOFFSET;
                }
            }

            /*
             * Win32 problem: If the length of the standard time name is equal
             * to (or probably longer than) 32 in the registry,
             * GetTimeZoneInformation() on NT returns a null string as its
             * standard time name. We need to work around this problem by
             * getting the same information from the TimeZoneInformation
             * registry.
             */
            if (tzi.StandardName[0] == 0) {
                bufSize = sizeof(stdNameInReg);
                ret = getValueInRegistry(hKey, STANDARD_NAME, &valueType,
                                         (LPBYTE) stdNameInReg, &bufSize);
                if (ret != ERROR_SUCCESS) {
                    goto err;
                }
                stdNamePtr = stdNameInReg;
            }
            (void) RegCloseKey(hKey);
        }

        /*
         * Open the "Time Zones" registry.
         */
        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, NT_TZ_KEY, 0, KEY_READ, (PHKEY)&hKey);
        if (ret != ERROR_SUCCESS) {
            ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, WIN_TZ_KEY, 0, KEY_READ, (PHKEY)&hKey);
            /*
             * If both failed, then give up.
             */
            if (ret != ERROR_SUCCESS) {
                return VALUE_UNKNOWN;
            }
        }

        /*
         * Get the number of subkeys of the "Time Zones" registry for
         * enumeration.
         */
        ret = RegQueryInfoKey(hKey, NULL, NULL, NULL, &nSubKeys,
                              NULL, NULL, NULL, NULL, NULL, NULL, NULL);
        if (ret != ERROR_SUCCESS) {
            goto err;
        }

        /*
         * Compare to the "Std" value of each subkey and find the entry that
         * matches the current control panel setting.
         */
        onlyMapID = 0;
        for (i = 0; i < nSubKeys; ++i) {
            DWORD size = sizeof(subKeyName);
            ret = RegEnumKeyEx(hKey, i, subKeyName, &size, NULL, NULL, NULL, NULL);
            if (ret != ERROR_SUCCESS) {
                goto err;
            }
            ret = RegOpenKeyEx(hKey, subKeyName, 0, KEY_READ, (PHKEY)&hSubKey);
            if (ret != ERROR_SUCCESS) {
                goto err;
            }

            size = sizeof(szValue);
            ret = getValueInRegistry(hSubKey, STD_NAME, &valueType,
                                     szValue, &size);
            if (ret != ERROR_SUCCESS) {
                /*
                 * NT 4.0 SP3 fails here since it doesn't have the "Std"
                 * entry in the Time Zones registry.
                 */
                RegCloseKey(hSubKey);
                onlyMapID = 1;
                ret = RegOpenKeyExW(hKey, stdNamePtr, 0, KEY_READ, (PHKEY)&hSubKey);
                if (ret != ERROR_SUCCESS) {
                    goto err;
                }
                break;
            }

            if (wcscmp((WCHAR *)szValue, stdNamePtr) == 0) {
                /*
                 * Some localized Win32 platforms use a same name to
                 * different time zones. So, we can't rely only on the name
                 * here. We need to check GMT offsets and transition dates
                 * to make sure it's the registry of the current time
                 * zone.
                 */
                DWORD tziValueSize = sizeof(tempTzi);
                ret = RegQueryValueEx(hSubKey, "TZI", NULL, &valueType,
                                      (unsigned char *) &tempTzi, &tziValueSize);
                if (ret == ERROR_SUCCESS) {
                    if ((tzi.Bias != tempTzi.bias) ||
                        (memcmp((const void *) &tzi.StandardDate,
                                (const void *) &tempTzi.stdDate,
                                sizeof(SYSTEMTIME)) != 0)) {
                        goto out;
                    }

                    if (tzi.DaylightBias != 0) {
                        if ((tzi.DaylightBias != tempTzi.dstBias) ||
                            (memcmp((const void *) &tzi.DaylightDate,
                                    (const void *) &tempTzi.dstDate,
                                    sizeof(SYSTEMTIME)) != 0)) {
                            goto out;
                        }
                    }
                }

                /*
                 * found matched record, terminate search
                 */
                strcpy(winZoneName, subKeyName);
                break;
            }
        out:
            (void) RegCloseKey(hSubKey);
        }

#if JDK_VER <= 11
        /*
         * Get the "MapID" value of the registry to be able to eliminate
         * duplicated key names later.
         */
        valueSize = MAX_MAPID_LENGTH;
        ret = RegQueryValueExA(hSubKey, "MapID", NULL, &valueType, winMapID, &valueSize);
        (void) RegCloseKey(hSubKey);
        (void) RegCloseKey(hKey);

        if (ret != ERROR_SUCCESS) {
            /*
             * Vista doesn't have mapID. VALUE_UNKNOWN should be returned
             * only for Windows NT.
             */
            if (onlyMapID == 1) {
                return VALUE_UNKNOWN;
            }
        }
#else
        (void) RegCloseKey(hKey);
#endif
    }

    return VALUE_KEY;

 err:
    if (hKey != NULL) {
        (void) RegCloseKey(hKey);
    }
    return VALUE_UNKNOWN;
}

/*
 * Reads a line from the buffer in the same way as fgets would do from a file.
 *
 * Returns the number of characters read or -1 in case of an error.
 */
static int SVM_readBufferUntilNewLine(char *dst, int num, const char *source, int source_len) {
    int read;

    if (dst == NULL || num < 0 ||
        source == NULL || source_len < 0) {
        return -1;
    }

    if (num == 0) {
        return 0;
    }

    read = 0;
    num = min(num, source_len + 1);
    while(--num) {
        dst[read] = source[read];
        if (dst[read++] == '\n') {
            break;
        }
    }
    dst[read] = '\0';

    return read;
}

/*
 * Index values for the mapping table.
 */
#if JDK_VER <= 11
#define TZ_WIN_NAME     0
#define TZ_MAPID        1
#define TZ_REGION       2
#define TZ_JAVA_NAME    3

#define TZ_NITEMS       4       /* number of items (fields) */
#else
#define TZ_WIN_NAME     0
#define TZ_REGION       1
#define TZ_JAVA_NAME    2

#define TZ_NITEMS       3       /* number of items (fields) */
#endif

/*
 * Looks up the mapping table (tzmappings) and returns a Java time
 * zone ID (e.g., "America/Los_Angeles") if found. Otherwise, NULL is
 * returned.
 *
 * value_type is one of the following values:
 *      VALUE_KEY for exact key matching
 *      VALUE_MAPID for MapID (this is
 *      required for the old Windows, such as NT 4.0 SP3).
 *
 * The following function differs from the original by accepting a buffer and reading
 * tzmappings data from it. The original function opens and reads such data from a file.
 *
 * Note that the mapID parameter is only used if JDK_VER <= 11.
 */
static char *SVM_matchJavaTZ(const char *tzmappings, int value_type, char *tzName,
                             char *mapID, int tzmappingsLen)
{
    int line;
    char *javaTZName = NULL;
    char *items[TZ_NITEMS];
    char lineBuffer[MAX_ZONE_CHAR * 4];
    int offset = 0;
    const char* errorMessage = "unknown error";
    int currLocation;
    int readChars;
#if JDK_VER <= 11
    int IDmatched = 0;
    int noMapID = *mapID == '\0';       /* no mapID on Vista and later */
#else
    char region[MAX_REGION_LENGTH];

    // Get the user's location
    if (GetGeoInfo(GetUserGeoID(GEOCLASS_NATION),
            GEO_ISO2, region, MAX_REGION_LENGTH, 0) == 0) {
        // If GetGeoInfo fails, fallback to LCID's country
        LCID lcid = GetUserDefaultLCID();
        if (GetLocaleInfo(lcid,
                          LOCALE_SISO3166CTRYNAME, region, MAX_REGION_LENGTH) == 0 &&
            GetLocaleInfo(lcid,
                          LOCALE_SISO3166CTRYNAME2, region, MAX_REGION_LENGTH) == 0) {
            region[0] = '\0';
        }
    }
#endif

    line = 0;
    currLocation = 0;
    readChars = 0;
    // Reads from buffer and not from file
    while((readChars = SVM_readBufferUntilNewLine(lineBuffer, sizeof(lineBuffer),
                                                  tzmappings + currLocation,
                                                  tzmappingsLen - currLocation)) > 0) {
        char *start, *idx, *endp;
        int itemIndex = 0;

        currLocation += readChars;
        line++;
        start = idx = lineBuffer;
        endp = &lineBuffer[sizeof(lineBuffer)];

        /*
         * Ignore comment and blank lines.
         */
        if (*idx == '#' || *idx == '\n') {
            continue;
        }

        for (itemIndex = 0; itemIndex < TZ_NITEMS; itemIndex++) {
            items[itemIndex] = start;
            while (*idx && *idx != ':') {
                if (++idx >= endp) {
                    errorMessage = "premature end of line";
                    offset = (int)(idx - lineBuffer);
                    goto illegal_format;
                }
            }
            if (*idx == '\0') {
                errorMessage = "illegal null character found";
                offset = (int)(idx - lineBuffer);
                goto illegal_format;
            }
            *idx++ = '\0';
            start = idx;
        }

        if (*idx != '\n') {
            errorMessage = "illegal non-newline character found";
            offset = (int)(idx - lineBuffer);
            goto illegal_format;
        }

#if JDK_VER <= 11
        if (noMapID || strcmp(mapID, items[TZ_MAPID]) == 0) {
            /*
             * When there's no mapID, we need to scan items until the
             * exact match is found or the end of data is detected.
             */
            if (!noMapID) {
                IDmatched = 1;
            }
            if (strcmp(items[TZ_WIN_NAME], tzName) == 0) {
                /*
                 * Found the time zone in the mapping table.
                 */
                javaTZName = _strdup(items[TZ_JAVA_NAME]);
                break;
            }
        } else {
            if (IDmatched == 1) {
                /*
                 * No need to look up the mapping table further.
                 */
                break;
            }
        }
#else
        /*
         * We need to scan items until the
         * exact match is found or the end of data is detected.
         */
        if (strcmp(items[TZ_WIN_NAME], tzName) == 0) {
            /*
             * Found the time zone in the mapping table.
             * Check the region code and select the appropriate entry
             */
            if (strcmp(items[TZ_REGION], region) == 0 ||
                strcmp(items[TZ_REGION], "001") == 0) {
                javaTZName = _strdup(items[TZ_JAVA_NAME]);
                break;
            }
        }
#endif
    }

    if (readChars == -1) {
        errorMessage = "failed to read line";
        goto illegal_format;
    }

    return javaTZName;

 illegal_format:
    jio_fprintf(stderr, "Illegal format in tzmappings file: %s at line %d, offset %d.\n",
                errorMessage, line, offset);
    return NULL;
}

extern char* getGMTOffsetID();

/*
 * Detects the platform time zone which maps to a Java time zone ID.
 *
 * The following function only differs from the original by calling a custom parsing
 * function called "SVM_matchJavaTZ" that accepts a data buffer.
 *
 * This function expects its argument to contain the whole tzmappings data and length as an arguments.
 * The original function expected a char array whose content was the path to JAVA_HOME
 */
char *SVM_FindJavaTZmd(const char *tzmappings, int length)
{
    char winZoneName[MAX_ZONE_CHAR];
    char winMapID[MAX_MAPID_LENGTH];
    char *std_timezone = NULL;
    int  result;

    winMapID[0] = 0;
    result = getWinTimeZone(winZoneName, winMapID);

    if (result != VALUE_UNKNOWN) {
        if (result == VALUE_GMTOFFSET) {
            std_timezone = _strdup(winZoneName);
        } else {
            std_timezone = SVM_matchJavaTZ(tzmappings, result,
                                       winZoneName, winMapID, length);
            if (std_timezone == NULL) {
                std_timezone = getGMTOffsetID();
            }
        }
    }
    return std_timezone;
}
#else
extern char* findJavaTZ_md(const char *);

char *SVM_FindJavaTZmd(const char *tzmappings, int length) {

    /*
     * For POSIX operating systems the original function
     * does not need the JAVA_HOME nor tzmappings. Except
     * for AIX (which is currently not supported in native image)
     *
     * We can safely call the original JDK function with java home set to
     * NULL. Note the JNI wrapper of the below function, checks JAVA_HOME
     * is not NULL and returns NULL if it is, stoppings us from directly
     * calling this function from java without some type of substitution.
     */
    return findJavaTZ_md((void *) 0);
}
#endif // _WIN64
