import re
from string import Template


patterns = [
    r'()\2',
    r'()\378',
    r'()\777',
    r'(\1)',
    r'(?<=()\1)',
    r'()(?P=1)',
    r'(?P<1)',
    r'(?P<1>)',
    r'[]',
    r'[a-',
    r'[b-a]',
    r'\x',
    r'\x1',
    r'\u111',
    r'\U1111',
    r'\U1111111',
    r'\U11111111',
    r'\N1',
    r'\N{1',
    r'\N{}',
    r'\N{a}',
    r'x{2,1}',
    r'x**',
    r'^*',
    r'\A*',
    r'\Z*',
    r'\b*',
    r'\B*',
    r'(?',
    r'(?P',
    r'(?P<',
    r'(?Px',
    r'(?<',
    r'(?x',
    r'(?P<>)',
    r'(?P<?>)',
    r'(?P=a)',
    r'(#',
    r'(',
    r'(?i',
    r'(?L',
    r'(?t:)',
    r'(?-t:)',
    r'(?-:)',
    r'(?ij:)',
    r'(?i-i:)',
]

print("    @Test")
print("    public void testSyntaxErrors() {")
for pattern in patterns:
    try:
        re.compile(pattern)
    except re.error as e:
        msg = e.__str__()
        position_msg = ' at position '
        i = msg.find(position_msg)
        error_msg = msg[:i]
        position = int(msg[i + len(position_msg):])
        print('        expectSyntaxError("%s", "", "%s", %d);' % (pattern, error_msg, position))
        continue
    raise RuntimeError("no exception was thrown " + pattern)


print("    }")
