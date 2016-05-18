struct stat;

int __xstat(int version, const char *path, struct stat *buf);

int __fxstat(int version, int fd, struct stat *buf);

int __lxstat(int version, const char *path, struct stat *buf);

int stat(const char *path, struct stat *buf) { return __xstat(1, path, buf); }

int fstat(int fd, struct stat *buf) { return __fxstat(1, fd, buf); }

int lstat(const char *path, struct stat *buf) { return __lxstat(1, path, buf); }
