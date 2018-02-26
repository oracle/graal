int main() {
  volatile char dst[100];
  volatile char *a = "";
  __builtin_strcpy((char *)dst, (char *)a);
  if (dst[0] != '\0') {
    return 1;
  }
  volatile char *b = "asdf";
  __builtin_strcpy((char *)dst, (char *)b);
  if (dst[0] != 'a' || dst[4] != '\0') {
    return 1;
  }
  volatile char *c = "asdf\0asdf";
  __builtin_strcpy((char *)dst, (char *)c);
  if (dst[0] != 'a' || dst[4] != '\0') {
    return 1;
  }
  return 0;
}
