/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.macho;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import org.graalvm.polyglot.io.ByteSequence;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;

public final class Xar {

    private final XarHeader header;
    private final List<XarFile> files;
    private final ByteSequence heap;

    public Xar(XarHeader header, List<XarFile> files, ByteSequence heap) {
        this.header = header;
        this.files = files;
        this.heap = heap;
    }

    public XarHeader getHeader() {
        return header;
    }

    public ByteSequence getHeap() {
        return heap;
    }

    public static Xar create(ByteSequence bytes) {
        ObjectFileReader data = new ObjectFileReader(bytes, false);
        // magic
        data.getInt();

        XarHeader header = XarHeader.create(data);
        List<XarFile> files = TocParser.parse(data, header);
        ByteSequence heap = data.slice();

        return new Xar(header, files, heap);
    }

    public ByteSequence extractBitcode() {
        XarFile embeddedBitcode = null;
        for (XarFile file : files) {
            if (file.fileType.equals("LTO")) {
                if (embeddedBitcode != null) {
                    throw new LLVMParserException("More than one Bitcode file in embedded archive!");
                }
                embeddedBitcode = file;
            }
        }
        if (embeddedBitcode == null) {
            // No Bitcode file in embedded archive!
            return null;
        }
        return heap.subSequence((int) embeddedBitcode.offset, (int) (embeddedBitcode.offset + embeddedBitcode.size));
    }

    public static final class XarHeader {
        private final short size;
        private final short version;
        private final long tocComprSize;
        private final long tocUncomprSize;
        private final int checksumAlgo;

        private XarHeader(short size, short version, long tocComprSize, long tocUncomprSize, int checksumAlgo) {
            super();
            this.size = size;
            this.version = version;
            this.tocComprSize = tocComprSize;
            this.tocUncomprSize = tocUncomprSize;
            this.checksumAlgo = checksumAlgo;
        }

        public short getSize() {
            return size;
        }

        public short getVersion() {
            return version;
        }

        public long getTocComprSize() {
            return tocComprSize;
        }

        public long getTocUncomprSize() {
            return tocUncomprSize;
        }

        public int getChecksumAlgo() {
            return checksumAlgo;
        }

        public static XarHeader create(ObjectFileReader data) {
            short size = data.getShort();
            short version = data.getShort();
            long tocComprSize = data.getLong();
            long tocUncomprSize = data.getLong();
            int checksumAlgo = data.getInt();

            return new XarHeader(size, version, tocComprSize, tocUncomprSize, checksumAlgo);
        }
    }

    private static final class XarFile {
        @SuppressWarnings("unused") private final String name;
        private final String fileType;
        private final long offset;
        private final long size;

        XarFile(String name, String fileType, long offset, long size) {
            this.size = size;
            this.offset = offset;
            this.fileType = fileType;
            this.name = name;
        }
    }

    private static final class XarFileBuilder {
        private String name = null;
        private String fileType = null;
        private long offset = -1;
        private long size = -1;
        private long length = -1;

        private XarFile create() {
            if (name == null) {
                throw new LLVMParserException("Missing name tag!");
            }
            if (fileType == null) {
                throw new LLVMParserException("Missing file-type tag!");
            }
            if (size < 0) {
                throw new LLVMParserException("Missing size tag!");
            }
            if (length < 0) {
                throw new LLVMParserException("Missing length tag!");
            }
            if (length != size) {
                throw new LLVMParserException("Length does not match size. (compressed files not supported)");
            }
            return new XarFile(name, fileType, offset, size);
        }
    }

    private static final class TocParser extends DefaultHandler {
        private XarFileBuilder fileBuilder = null;
        private String lastTag;
        private List<XarFile> files = new ArrayList<>();

        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
            if (qName.equals("file")) {
                if (fileBuilder != null) {
                    throw new LLVMParserException("Only flat xar archives are supported!");
                }
                fileBuilder = new XarFileBuilder();
            } else if (fileBuilder != null) {
                lastTag = qName;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (fileBuilder != null && lastTag != null) {
                switch (lastTag) {
                    case "name":
                        fileBuilder.name = new String(ch, start, length);
                        break;
                    case "file-type":
                        fileBuilder.fileType = new String(ch, start, length);
                        break;
                    case "offset":
                        fileBuilder.offset = Long.parseUnsignedLong(new String(ch, start, length));
                        break;
                    case "length":
                        fileBuilder.length = Long.parseUnsignedLong(new String(ch, start, length));
                        break;
                    case "size":
                        fileBuilder.size = Long.parseUnsignedLong(new String(ch, start, length));
                        break;
                }
            }
        }

        @Override
        public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
            if (qName.equals("file")) {
                if (fileBuilder != null) {
                    files.add(fileBuilder.create());
                }
                fileBuilder = null;
            }
            lastTag = null;
        }

        // uses fully qualified name to prevent mx to add "require java.xml" when compiling on JDK9
        private static final javax.xml.parsers.SAXParserFactory PARSER_FACTORY;

        static {
            PARSER_FACTORY = javax.xml.parsers.SAXParserFactory.newInstance();
            try {
                PARSER_FACTORY.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
                PARSER_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
                PARSER_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                PARSER_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                PARSER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                PARSER_FACTORY.setXIncludeAware(false);
                PARSER_FACTORY.setNamespaceAware(false);
            } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        private static List<XarFile> parse(ObjectFileReader data, XarHeader header) {
            int comprSize = (int) header.getTocComprSize();
            int uncomprSize = (int) header.getTocUncomprSize();

            byte[] compressedData = new byte[comprSize];
            data.get(compressedData);

            // decompress
            Inflater decompresser = new Inflater();
            decompresser.setInput(compressedData, 0, comprSize);
            byte[] uncompressedData = new byte[uncomprSize];
            try {
                decompresser.inflate(uncompressedData);
            } catch (DataFormatException e) {
                throw new LLVMParserException("DataFormatException when decompressing xar table of contents!");
            }
            decompresser.end();

            try {
                XMLReader xmlReader = PARSER_FACTORY.newSAXParser().getXMLReader();
                TocParser parser = new TocParser();
                xmlReader.setContentHandler(parser);
                xmlReader.parse(new InputSource(new ByteArrayInputStream(uncompressedData)));
                return parser.files;
            } catch (SAXException | IOException | javax.xml.parsers.ParserConfigurationException e1) {
                throw new LLVMParserException("Could not parse xar table of contents xml!");
            }
        }
    }
}
