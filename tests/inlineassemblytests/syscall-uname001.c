#include "nanolibc.h"

int main(void) {
  struct utsname name;
  int result = uname(&name);
  if (result < 0) {
    perror("uname failed");
    return 1;
  }

  printf("sysname:  '%s'\n"
         "nodename: '%s'\n"
         "release:  '%s'\n"
         "version:  '%s'\n"
         "machine:  '%s'\n"
         "domain:   '%s'\n",
         name.sysname, name.nodename, name.release, name.version, name.machine, name.domainname);

  return 0;
}
