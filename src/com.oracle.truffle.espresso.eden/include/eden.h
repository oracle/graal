#include <dlfcn.h>

void *dlopen(const char *filename, int flags);
int dlclose(void *handle);
void *dlmopen (Lmid_t lmid, const char *filename, int flags);
void *dlsym(void *handle, const char *symbol);

// On recent glibc, the locale data is initialized on thread creation.
// To emulate the same behavior and avoid crashes, Java threads created 
// outside the context must call this function on start.
void ctypeInit(void);
