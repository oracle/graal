int main() {
  volatile char* a = "";
  if(__builtin_strlen((char*)a) != 0) {
    return 1;
  }
  volatile char* b = "asdf";
  if(__builtin_strlen((char*)b) != 4) {
    return 1;
  }
  volatile char* c = "asdf\0asdf";
  if(__builtin_strlen((char*)c) != 4) {
    return 1;
  }
  volatile char* d = "Hello World\n";
  if(__builtin_strlen((char*)d) != 12) {
    return 1;
  }
  return 0;
}
