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

import javax.xml.parsers.ParserConfigurationException;

import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import org.graalvm.polyglot.io.ByteSequence;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class Xar {

    private final XarHeader header;
    private final XarTOC toc;
    private final ByteSequence heap;

    public Xar(XarHeader header, XarTOC toc, ByteSequence heap) {
        this.header = header;
        this.toc = toc;
        this.heap = heap;
    }

    public XarHeader getHeader() {
        return header;
    }

    public XarTOC getToc() {
        return toc;
    }

    public ByteSequence getHeap() {
        return heap;
    }

    public static Xar create(ByteSequence bytes) {
        ObjectFileReader data = new ObjectFileReader(bytes, false);
        // magic
        data.getInt();

        XarHeader header = XarHeader.create(data);
        XarTOC toc = XarTOC.create(data, header);
        ByteSequence heap = data.slice();

        return new Xar(header, toc, heap);
    }

    public ByteSequence extractBitcode() {
        List<XarFile> files = getToc().getFiles();
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
            throw new LLVMParserException("No Bitcode file in embedded archive!");
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

        private static XarFile create(Node node) {
            String name = null;
            String fileType = null;
            long offset = -1;
            long size = -1;
            long length = -1;

            for (Node child = node.getChildNodes().item(0); child != null; child = child.getNextSibling()) {
                switch (child.getNodeName()) {
                    case "name":
                        name = child.getTextContent();
                        break;
                    case "file-type":
                        fileType = child.getTextContent();
                        break;
                    case "data":
                        for (Node dataNode = child.getChildNodes().item(0); dataNode != null; dataNode = dataNode.getNextSibling()) {
                            switch (dataNode.getNodeName()) {
                                case "offset":
                                    offset = Long.parseUnsignedLong(dataNode.getTextContent());
                                    break;
                                case "length":
                                    length = Long.parseUnsignedLong(dataNode.getTextContent());
                                    break;
                                case "size":
                                    size = Long.parseUnsignedLong(dataNode.getTextContent());
                                    break;
                            }
                        }
                        break;
                }
            }
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

    private static final class XarTOC {

        // uses fully qualified name to prevent mx to add "require javax.xml" when compiling on JDK9
        private static final javax.xml.parsers.DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        private final Document tableOfContents;

        private XarTOC(Document toc) {
            this.tableOfContents = toc;
        }

        public List<XarFile> getFiles() {
            NodeList files = tableOfContents.getElementsByTagName("file");
            List<XarFile> xarFiles = new ArrayList<>();
            for (int i = 0; i < files.getLength(); i++) {
                xarFiles.add(XarFile.create(files.item(i)));
            }
            return xarFiles;
        }

        public static XarTOC create(ObjectFileReader data, XarHeader header) {
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
                Document xmlTOC = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder().parse(new ByteArrayInputStream(uncompressedData));
                return new XarTOC(xmlTOC);
            } catch (SAXException | IOException | ParserConfigurationException e1) {
                throw new LLVMParserException("Could not parse xar table of contents xml!");
            }

        }
    }
}
