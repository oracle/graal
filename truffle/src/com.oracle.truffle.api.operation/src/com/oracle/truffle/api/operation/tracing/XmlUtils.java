package com.oracle.truffle.api.operation.tracing;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class XmlUtils {
    public static void readStartElement(XMLStreamReader rd, String name) throws XMLStreamException {
        String readName = readStartElement(rd);
        assert readName.equals(name);
    }

    public static String readStartElement(XMLStreamReader rd) throws XMLStreamException {
        int state = rd.next();
        assert state == XMLStreamReader.START_ELEMENT;
        return rd.getName().getLocalPart();
    }

    public static void readEndElement(XMLStreamReader rd, String name) throws XMLStreamException {
        int state = rd.next();
        assert state == XMLStreamReader.END_ELEMENT : "got " + state + " " + rd.getName();
        assert rd.getName().getLocalPart().equals(name);
    }

    public static String readCharacters(XMLStreamReader rd) throws XMLStreamException {
        int state = rd.next();
        assert state == XMLStreamReader.CHARACTERS;
        return rd.getText();
    }

    public static int readCharactersInt(XMLStreamReader rd) throws XMLStreamException {
        return Integer.parseInt(readCharacters(rd));
    }

    public static long readCharactersLong(XMLStreamReader rd) throws XMLStreamException {
        return Long.parseLong(readCharacters(rd));
    }

    public static boolean readCharactersBoolean(XMLStreamReader rd) throws XMLStreamException {
        String v = readCharacters(rd);
        return v.toLowerCase().equals("true");
    }

    public static String readAttribute(XMLStreamReader rd, String name) throws XMLStreamException {
        return rd.getAttributeValue(null, name);
    }

    public static int readAttributeInt(XMLStreamReader rd, String name) throws XMLStreamException {
        return Integer.parseInt(readAttribute(rd, name));
    }

    public static long readAttributeLong(XMLStreamReader rd, String name) throws XMLStreamException {
        return Long.parseLong(readAttribute(rd, name));
    }
}
