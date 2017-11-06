void setupStdin(const char *str) {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fprintf(file, str); /* scanf input */
  fclose(file);
  freopen(name, "r", stdin);
}
