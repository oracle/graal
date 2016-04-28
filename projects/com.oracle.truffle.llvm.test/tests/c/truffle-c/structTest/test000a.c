
struct str {
  char a;
  int b[];
} a3 = { 'o', { 1, 2 } };

/*
 *

struct {
  char a;
  int b[];
} a3 = {
  'o',
  {1, 2}
};

 * */

int main() { return a3.b[0] + a3.b[1]; }
