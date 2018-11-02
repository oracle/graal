#include <dlfcn.h> 
#include <cstdio>
#include <cstdlib>

#define LIB_JVM "/usr/lib/jvm/java-8-openjdk/jre/lib/amd64/server/libjvm.so"

#define LIB_MOKAPOT "/home/mukel/Desktop/graal/espresso/src/com.oracle.truffle.espresso.mokapot/src/libroberto.so"

#define LIB_JAVA "/usr/lib/jvm/java-8-openjdk/jre/lib/amd64/libjava.so"

int main() {
    void* libjvm = dlopen(LIB_JVM, RTLD_GLOBAL | RTLD_LAZY);

    if (!libjvm) {
        fprintf(stderr, "libjvm error %s\n", dlerror());
        exit(EXIT_FAILURE);
    }

    puts("libjvm.so loaded!");

    // Load surrogate libjvm (MokaPot)
    void* mokapot = dlmopen(LM_ID_NEWLM, LIB_MOKAPOT, RTLD_LAZY);
    if (!mokapot) {
        fprintf(stderr, "mokapot error %s\n", dlerror());
        exit(EXIT_FAILURE);
    }

    puts("(mokapot) libjvm.so loaded!");

    Lmid_t mokapot_nmspc;
    if (dlinfo(mokapot, RTLD_DI_LMID, &mokapot_nmspc)) {
        fprintf(stderr, "%s\n", dlerror());
        exit(EXIT_FAILURE);
    }

    printf("mokapot namespace %d\n", mokapot_nmspc);

    // Load libjava with MokaPot (libjvm.so)
    void* libjava = dlmopen(mokapot_nmspc, LIB_JAVA, RTLD_LAZY);
     if (!libjava) {
        fprintf(stderr, "Error loading libjava.so : %s\n", dlerror());
        exit(EXIT_FAILURE);
    }

    int (*availableProcessors)(void*, void*);
    *(void **) (&availableProcessors) = dlsym(libjava, "Java_java_lang_Runtime_availableProcessors");
    printf("availableProcessors %p\n", availableProcessors);
    if (!availableProcessors) {
        fprintf(stderr, "availableProcessors not found: %s\n", dlerror());
        exit(EXIT_FAILURE);
    }

    printf("AvailableProcessors() -> %d\n", availableProcessors(NULL, NULL));

    return 0;
}