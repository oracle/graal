#include <polyglot.h>
#include <wchar.h>

uint64_t test_get_string_size(void *str) {
    return polyglot_get_string_size(str);
}

int test_as_string_ascii(void *str) {
    char buffer[100];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "ascii");
    if (strncmp(buffer, "Hello, World!", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

int test_as_string_utf8(void *str) {
    char buffer[100];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "utf-8");
    if (strncmp(buffer, "test unicode äáç€", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

int test_as_string_utf32(void *str) {
    wchar_t buffer[100];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "utf-32le");
    if (wcsncmp(buffer, L"test unicode äáç€", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

int test_as_string_overflow(void *str) {
    char buffer[5];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "ascii");
    if (strncmp(buffer, "Hello", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

void *test_from_string(int variant) {
    static char ascii[] = "Hello, from Native!\0There is more!";
    static char utf8[] = "unicode from native ☺\0stuff after zero ☹";
    static wchar_t utf32[] = L"utf-32 works too ☺\0also with zero ☹";

    switch (variant) {
        case 1:
            return polyglot_from_string(ascii, "ascii");
        case 2:
            return polyglot_from_string_n(ascii, sizeof(ascii), "ascii");
        case 3:
            return polyglot_from_string(utf8, "utf-8");
        case 4:
            return polyglot_from_string_n(utf8, sizeof(utf8), "utf-8");
        case 5:
            return polyglot_from_string(utf32, "utf-32le");
        case 6:
            return polyglot_from_string_n(utf32, sizeof(utf32), "utf-32le");
    }
    return NULL;
}
