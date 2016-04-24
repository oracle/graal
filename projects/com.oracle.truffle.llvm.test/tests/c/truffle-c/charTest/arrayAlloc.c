#define E 0
#define ESE 1
#define SE 2
#define S 3
#define SW 4
#define WSW 5
#define W 6
#define WNW 7
#define NW 8
#define N 9
#define NE 10
#define ENE 11
#define PIVOT 12

char piece_def[10][4] = { { E, E, E, SE },
                          { SE, E, NE, E },
                          { E, E, SE, SW },
                          { E, E, SW, SE },
                          { SE, E, NE, S },
                          { E, E, SW, E },
                          { E, SE, SE, NE },
                          { E, SE, SE, W },
                          { E, SE, E, E },
                          { E, E, E, SW } };

int main() { return piece_def[2][3]; }
