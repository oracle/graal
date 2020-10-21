#include <dlfcn.h>

void *dlopen(const char *filename, int flags);
int dlclose(void *handle);
void *dlmopen (Lmid_t lmid, const char *filename, int flags);
void *dlsym(void *handle, const char *symbol);
