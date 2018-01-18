#include <stdlib.h>

#define SHORT_MSB_SET 0x8000;
#define SHORT_LSB_SET 0x0001;

#define INT_MSB_SET 0x80000000;
#define INT_LSB_SET 0x00000001;

#define LONG_MSB_SET 0x8000000000000000L;
#define LONG_LSB_SET 0x0000000000000001L;

char C1 = 'A';
char C2 = 'a';
char C3 = '0';
char C4 = '+';
    
short S1 = SHORT_MSB_SET;
short S2 = SHORT_LSB_SET;
unsigned short S3 = SHORT_MSB_SET;
unsigned short S4 = SHORT_LSB_SET;

int I1 = INT_MSB_SET;
int I2 = INT_LSB_SET;
unsigned int I3 = INT_MSB_SET;
unsigned int I4 = INT_LSB_SET;

long L1 = LONG_MSB_SET;
long L2 = LONG_LSB_SET;
unsigned long L3 = LONG_MSB_SET;
unsigned long L4 = LONG_LSB_SET;

float F1 = 0.0f;
float F2 = 1.0f;
float F3 = -1.0f;
float F4 = 1.25f;
float F5 = -1.25f;

double D1 = 0.0;
double D2 = 1.0;
double D3 = -1.0;
double D4 = 1.25;
double D5 = -1.25;

int main()
{
    char c1 = 'A';
    char c2 = 'a';
    char c3 = '0';
    char c4 = '+';
    
    short s1 = SHORT_MSB_SET;
    short s2 = SHORT_LSB_SET;
    unsigned short s3 = SHORT_MSB_SET;
    unsigned short s4 = SHORT_LSB_SET;

    int i1 = INT_MSB_SET;
    int i2 = INT_LSB_SET;
    unsigned int i3 = INT_MSB_SET;
    unsigned int i4 = INT_LSB_SET;

    long int l1 = LONG_MSB_SET;
    long int l2 = LONG_LSB_SET;
    long unsigned int l3 = LONG_MSB_SET;
    long unsigned int l4 = LONG_LSB_SET;

    float f1 = 0.0f;
    float f2 = 1.0f;
    float f3 = -1.0f;
    float f4 = 1.25f;
    float f5 = -1.25f;

    double d1 = 0.0;
    double d2 = 1.0;
    double d3 = -1.0;
    double d4 = 1.25;
    double d5 = -1.25;

    return 0;
}