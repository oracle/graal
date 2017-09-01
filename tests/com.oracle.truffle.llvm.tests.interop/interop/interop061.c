#include <truffle.h>

typedef struct rb_io_t {
  int mode;
  int fd;
} rb_io_t;

int main() {
  rb_io_t *managed = truffle_managed_malloc(sizeof(rb_io_t));
  
  managed->mode = 101;
  managed->fd = 102;
  
  return 0;
}