static int oldStdin;
static char name[L_tmpnam];

void setupStdin(const char *str) {
  FILE *file = fopen(tmpnam(name), "w");
  if (file == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  fprintf(file, str); /* scanf input */
  fclose(file);
  oldStdin = dup(0);
  freopen(name, "r", stdin);
}

void cleanupStdin() {
  fclose(stdin);
  dup2(oldStdin, 0);
  close(oldStdin);
  stdin = fdopen(0, "r");
  unlink(name);
}
