/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.file;


import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.file.RelativePath.RelativeFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * This class implements the building of index of a zip archive and access to
 * its context. It also uses a prebuilt index if available.
 * It supports invocations where it will serialize an optimized zip index file
 * to disk.
 * <p>
 * In order to use a secondary index file, set "usezipindex" in the Options
 * object when JavacFileManager is invoked. (You can pass "-XDusezipindex" on
 * the command line.)
 * <p>
 * Location where to look for/generate optimized zip index files can be
 * provided using "-XDcachezipindexdir=<directory>". If this flag is not
 * provided, the default location is the value of the "java.io.tmpdir" system
 * property.
 * <p>
 * If "-XDwritezipindexfiles" is specified, there will be new optimized index
 * file created for each archive, used by the compiler for compilation, at the
 * location specified by the "cachezipindexdir" option.
 * <p>
 * If system property nonBatchMode option is specified the compiler will use
 * timestamp checking to reindex the zip files if it is needed. In batch mode
 * the timestamps are not checked and the compiler uses the cached indexes.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class ZipFileIndex {
    public final static long NOT_MODIFIED = Long.MIN_VALUE;
    private static final String MIN_CHAR = String.valueOf(Character.MIN_VALUE);
    private static final String MAX_CHAR = String.valueOf(Character.MAX_VALUE);
    private static boolean NON_BATCH_MODE = System.getProperty("nonBatchMode") != null;// TODO: Use -XD compiler switch for this.
    // ZipFileIndex data entries
    // 压缩文件实体
    final File zipFile;
    final RelativeDirectory symbolFilePrefix;
    private final int symbolFilePrefixLength;
    private final boolean usePreindexedCache;
    private final String preindexedCacheLocation;
    long zipFileLastModified = NOT_MODIFIED;
    long lastReferenceTimeStamp = NOT_MODIFIED;
    // directories中保存了压缩包中相对路径到DirectoryEntry对象的映射关系
    private Map<RelativeDirectory, DirectoryEntry> directories =
            Collections.<RelativeDirectory, DirectoryEntry>emptyMap();
    private Set<RelativeDirectory> allDirs =
            Collections.<RelativeDirectory>emptySet();
    private Reference<File> absFileRef;
    private RandomAccessFile zipRandomFile;
    private Entry[] entries;
    private boolean readFromIndex = false;
    private File zipIndexFile = null;
    private boolean triedToReadIndex = false;
    private boolean hasPopulatedData = false;
    private boolean writeIndex = false;
    // 字符串映射，相对路径软引用对象
    private Map<String, SoftReference<RelativeDirectory>> relativeDirectoryCache =
            new HashMap<String, SoftReference<RelativeDirectory>>();
    /*
     * Inflate using the java.util.zip.Inflater class
     */
    private SoftReference<Inflater> inflaterRef;

    ZipFileIndex(File zipFile, RelativeDirectory symbolFilePrefix, boolean writeIndex,
                 boolean useCache, String cacheLocation) throws IOException {
        this.zipFile = zipFile;
        this.symbolFilePrefix = symbolFilePrefix;
        symbolFilePrefixLength = (symbolFilePrefix == null ? 0 :
                symbolFilePrefix.getPath().getBytes("UTF-8").length);
        this.writeIndex = writeIndex;
        usePreindexedCache = useCache;
        preindexedCacheLocation = cacheLocation;

        if (zipFile != null) {
            zipFileLastModified = zipFile.lastModified();
        }

        // Validate integrity of the zip file
        checkIndex();
    }

    /**
     * return the two bytes buf[pos], buf[pos+1] as an unsigned integer in little
     * endian format.
     */
    private static int get2ByteLittleEndian(byte[] buf, int pos) {
        return (buf[pos] & 0xFF) + ((buf[pos + 1] & 0xFF) << 8);
    }

    /**
     * return the 4 bytes buf[i..i+3] as an integer in little endian format.
     */
    private static int get4ByteLittleEndian(byte[] buf, int pos) {
        return (buf[pos] & 0xFF) + ((buf[pos + 1] & 0xFF) << 8) +
                ((buf[pos + 2] & 0xFF) << 16) + ((buf[pos + 3] & 0xFF) << 24);
    }

    public synchronized boolean isOpen() {
        return (zipRandomFile != null);
    }

    @Override
    public String toString() {
        return "ZipFileIndex[" + zipFile + "]";
    }

    // Just in case...
    @Override
    protected void finalize() throws Throwable {
        closeFile();
        super.finalize();
    }

    private boolean isUpToDate() {
        if (zipFile != null
                && ((!NON_BATCH_MODE) || zipFileLastModified == zipFile.lastModified())
                && hasPopulatedData) {
            return true;
        }

        return false;
    }

    /**
     * Here we need to make sure that the ZipFileIndex is valid. Check the timestamp of the file and
     * if its the same as the one at the time the index was build we don't need to reopen anything.
     */
    // 读取压缩包相关内容
    // 调用checkIndex()方法确保压缩包内容已经被读取并且是最新的
    private void checkIndex() throws IOException {
        boolean isUpToDate = true;
        if (!isUpToDate()) {
            closeFile();
            isUpToDate = false;
        }

        if (zipRandomFile != null || isUpToDate) {
            lastReferenceTimeStamp = System.currentTimeMillis();
            return;
        }

        hasPopulatedData = true;

        if (readIndex()) {
            lastReferenceTimeStamp = System.currentTimeMillis();
            return;
        }

        directories = Collections.<RelativeDirectory, DirectoryEntry>emptyMap();
        allDirs = Collections.<RelativeDirectory>emptySet();

        try {
            // checkIndex()方法首先调用openFile()方法初始化zipRandomFile变量，
            // 然后将zipRandomFile封装为ZipDirectory对象并调用buildIndex()方法建立读取索引，
            // 这样就可以高效读取压缩包相关的内容了
            // 初始化zipRandomFile变量
            openFile();
            long totalLength = zipRandomFile.length();
            // ZipDirectory类是ZipFileIndex类内定义的一个私有成员类，
            // 这个类中的相关方法将按照压缩包的格式从zipRandomFile中读取压缩包中的目录和文件，
            // 然后保存到ZipFileIndex类中一个全局私有的变量entries中，供其他方法查
            ZipDirectory directory = new ZipDirectory(zipRandomFile, 0L, totalLength, this);
            // 读取压缩包中的具体内容
            // 为压缩包建立读取索引
            directory.buildIndex();
        } finally {
            if (zipRandomFile != null) {
                closeFile();
            }
        }

        lastReferenceTimeStamp = System.currentTimeMillis();
    }

    private void openFile() throws FileNotFoundException {
        if (zipRandomFile == null && zipFile != null) {
            zipRandomFile = new RandomAccessFile(zipFile, "r");
        }
    }

    private void cleanupState() {
        // Make sure there is a valid but empty index if the file doesn't exist
        entries = Entry.EMPTY_ARRAY;
        directories = Collections.<RelativeDirectory, DirectoryEntry>emptyMap();
        zipFileLastModified = NOT_MODIFIED;
        allDirs = Collections.<RelativeDirectory>emptySet();
    }

    public synchronized void close() {
        writeIndex();
        closeFile();
    }

    private void closeFile() {
        if (zipRandomFile != null) {
            try {
                zipRandomFile.close();
            } catch (IOException ex) {
            }
            zipRandomFile = null;
        }
    }

    /**
     * Returns the ZipFileIndexEntry for a path, if there is one.
     */
    // 通过相对路径查找文件
    synchronized Entry getZipIndexEntry(RelativePath path) {
        try {
            // 读取压缩包相关内容
            checkIndex();
            DirectoryEntry de = directories.get(path.dirname());
            String lookFor = path.basename();
            return (de == null) ? null : de.getEntry(lookFor);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns a javac List of filenames within a directory in the ZipFileIndex.
     */
    // 通过相对路径path查找所有的文件，以列表的形式返回所有文件的名称
    public synchronized com.sun.tools.javac.util.List<String> getFiles(RelativeDirectory path) {
        try {
            // 读取压缩包相关内容
            // 调用checkIndex()方法确保压缩包内容已经被读取并且是最新的
            checkIndex();

            DirectoryEntry de = directories.get(path);
            com.sun.tools.javac.util.List<String> ret = de == null ? null : de.getFiles();

            if (ret == null) {
                return com.sun.tools.javac.util.List.<String>nil();
            }
            return ret;
        } catch (IOException e) {
            return com.sun.tools.javac.util.List.<String>nil();
        }
    }

    public synchronized List<String> getDirectories(RelativeDirectory path) {
        try {
            checkIndex();

            DirectoryEntry de = directories.get(path);
            com.sun.tools.javac.util.List<String> ret = de == null ? null : de.getDirectories();

            if (ret == null) {
                return com.sun.tools.javac.util.List.<String>nil();
            }

            return ret;
        } catch (IOException e) {
            return com.sun.tools.javac.util.List.<String>nil();
        }
    }

    public synchronized Set<RelativeDirectory> getAllDirectories() {
        try {
            checkIndex();
            if (allDirs == Collections.EMPTY_SET) {
                allDirs = new HashSet<RelativeDirectory>(directories.keySet());
            }

            return allDirs;
        } catch (IOException e) {
            return Collections.<RelativeDirectory>emptySet();
        }
    }

    /**
     * Tests if a specific path exists in the zip.  This method will return true
     * for file entries and directories.
     *
     * @param path A path within the zip.
     * @return True if the path is a file or dir, false otherwise.
     */
    public synchronized boolean contains(RelativePath path) {
        try {
            checkIndex();
            return getZipIndexEntry(path) != null;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean isDirectory(RelativePath path) throws IOException {
        // The top level in a zip file is always a directory.
        if (path.getPath().length() == 0) {
            lastReferenceTimeStamp = System.currentTimeMillis();
            return true;
        }

        checkIndex();
        return directories.get(path) != null;
    }

    public synchronized long getLastModified(RelativeFile path) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null) {
            throw new FileNotFoundException();
        }
        return entry.getLastModified();
    }

    public synchronized int length(RelativeFile path) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null) {
            throw new FileNotFoundException();
        }

        if (entry.isDir) {
            return 0;
        }

        byte[] header = getHeader(entry);
        // entry is not compressed?
        if (get2ByteLittleEndian(header, 8) == 0) {
            return entry.compressedSize;
        } else {
            return entry.size;
        }
    }

    public synchronized byte[] read(RelativeFile path) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null) {
            throw new FileNotFoundException("Path not found in ZIP: " + path.path);
        }
        return read(entry);
    }

    synchronized byte[] read(Entry entry) throws IOException {
        openFile();
        byte[] result = readBytes(entry);
        closeFile();
        return result;
    }

    public synchronized int read(RelativeFile path, byte[] buffer) throws IOException {
        Entry entry = getZipIndexEntry(path);
        if (entry == null) {
            throw new FileNotFoundException();
        }
        return read(entry, buffer);
    }

    //----------------------------------------------------------------------------
    // Zip utilities
    //----------------------------------------------------------------------------

    synchronized int read(Entry entry, byte[] buffer)
            throws IOException {
        int result = readBytes(entry, buffer);
        return result;
    }

    private byte[] readBytes(Entry entry) throws IOException {
        byte[] header = getHeader(entry);
        int csize = entry.compressedSize;
        byte[] cbuf = new byte[csize];
        zipRandomFile.skipBytes(get2ByteLittleEndian(header, 26) + get2ByteLittleEndian(header, 28));
        zipRandomFile.readFully(cbuf, 0, csize);

        // is this compressed - offset 8 in the ZipEntry header
        if (get2ByteLittleEndian(header, 8) == 0) {
            return cbuf;
        }

        int size = entry.size;
        byte[] buf = new byte[size];
        if (inflate(cbuf, buf) != size) {
            throw new ZipException("corrupted zip file");
        }

        return buf;
    }

    /**
     *
     */
    private int readBytes(Entry entry, byte[] buffer) throws IOException {
        byte[] header = getHeader(entry);

        // entry is not compressed?
        if (get2ByteLittleEndian(header, 8) == 0) {
            zipRandomFile.skipBytes(get2ByteLittleEndian(header, 26) + get2ByteLittleEndian(header, 28));
            int offset = 0;
            int size = buffer.length;
            while (offset < size) {
                int count = zipRandomFile.read(buffer, offset, size - offset);
                if (count == -1) {
                    break;
                }
                offset += count;
            }
            return entry.size;
        }

        int csize = entry.compressedSize;
        byte[] cbuf = new byte[csize];
        zipRandomFile.skipBytes(get2ByteLittleEndian(header, 26) + get2ByteLittleEndian(header, 28));
        zipRandomFile.readFully(cbuf, 0, csize);

        int count = inflate(cbuf, buffer);
        if (count == -1) {
            throw new ZipException("corrupted zip file");
        }

        return entry.size;
    }

    private byte[] getHeader(Entry entry) throws IOException {
        zipRandomFile.seek(entry.offset);
        byte[] header = new byte[30];
        zipRandomFile.readFully(header);
        if (get4ByteLittleEndian(header, 0) != 0x04034b50) {
            throw new ZipException("corrupted zip file");
        }
        if ((get2ByteLittleEndian(header, 6) & 1) != 0) {
            throw new ZipException("encrypted zip file"); // offset 6 in the header of the ZipFileEntry
        }
        return header;
    }

    private int inflate(byte[] src, byte[] dest) {
        Inflater inflater = (inflaterRef == null ? null : inflaterRef.get());

        // construct the inflater object or reuse an existing one
        if (inflater == null) {
            inflaterRef = new SoftReference<Inflater>(inflater = new Inflater(true));
        }

        inflater.reset();
        inflater.setInput(src);
        try {
            return inflater.inflate(dest);
        } catch (DataFormatException ex) {
            return -1;
        }
    }

    /* ----------------------------------------------------------------------------
     * ZipDirectory
     * ----------------------------------------------------------------------------*/

    /**
     * Returns the last modified timestamp of a zip file.
     *
     * @return long
     */
    public long getZipFileLastModified() throws IOException {
        synchronized (this) {
            checkIndex();
            return zipFileLastModified;
        }
    }

    private boolean readIndex() {
        if (triedToReadIndex || !usePreindexedCache) {
            return false;
        }

        boolean ret = false;
        synchronized (this) {
            triedToReadIndex = true;
            RandomAccessFile raf = null;
            try {
                File indexFileName = getIndexFile();
                raf = new RandomAccessFile(indexFileName, "r");

                long fileStamp = raf.readLong();
                if (zipFile.lastModified() != fileStamp) {
                    ret = false;
                } else {
                    directories = new HashMap<RelativeDirectory, DirectoryEntry>();
                    int numDirs = raf.readInt();
                    for (int nDirs = 0; nDirs < numDirs; nDirs++) {
                        int dirNameBytesLen = raf.readInt();
                        byte[] dirNameBytes = new byte[dirNameBytesLen];
                        raf.read(dirNameBytes);

                        RelativeDirectory dirNameStr = getRelativeDirectory(new String(dirNameBytes, "UTF-8"));
                        DirectoryEntry de = new DirectoryEntry(dirNameStr, this);
                        de.numEntries = raf.readInt();
                        de.writtenOffsetOffset = raf.readLong();
                        directories.put(dirNameStr, de);
                    }
                    ret = true;
                    zipFileLastModified = fileStamp;
                }
            } catch (Throwable t) {
                // Do nothing
            } finally {
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (Throwable tt) {
                        // Do nothing
                    }
                }
            }
            if (ret == true) {
                readFromIndex = true;
            }
        }

        return ret;
    }

    private boolean writeIndex() {
        boolean ret = false;
        if (readFromIndex || !usePreindexedCache) {
            return true;
        }

        if (!writeIndex) {
            return true;
        }

        File indexFile = getIndexFile();
        if (indexFile == null) {
            return false;
        }

        RandomAccessFile raf = null;
        long writtenSoFar = 0;
        try {
            raf = new RandomAccessFile(indexFile, "rw");

            raf.writeLong(zipFileLastModified);
            writtenSoFar += 8;

            List<DirectoryEntry> directoriesToWrite = new ArrayList<DirectoryEntry>();
            Map<RelativeDirectory, Long> offsets = new HashMap<RelativeDirectory, Long>();
            raf.writeInt(directories.keySet().size());
            writtenSoFar += 4;

            for (RelativeDirectory dirName : directories.keySet()) {
                DirectoryEntry dirEntry = directories.get(dirName);

                directoriesToWrite.add(dirEntry);

                // Write the dir name bytes
                byte[] dirNameBytes = dirName.getPath().getBytes("UTF-8");
                int dirNameBytesLen = dirNameBytes.length;
                raf.writeInt(dirNameBytesLen);
                writtenSoFar += 4;

                raf.write(dirNameBytes);
                writtenSoFar += dirNameBytesLen;

                // Write the number of files in the dir
                List<Entry> dirEntries = dirEntry.getEntriesAsCollection();
                raf.writeInt(dirEntries.size());
                writtenSoFar += 4;

                offsets.put(dirName, new Long(writtenSoFar));

                // Write the offset of the file's data in the dir
                dirEntry.writtenOffsetOffset = 0L;
                raf.writeLong(0L);
                writtenSoFar += 8;
            }

            for (DirectoryEntry de : directoriesToWrite) {
                // Fix up the offset in the directory table
                long currFP = raf.getFilePointer();

                long offsetOffset = offsets.get(de.dirName).longValue();
                raf.seek(offsetOffset);
                raf.writeLong(writtenSoFar);

                raf.seek(currFP);

                // Now write each of the files in the DirectoryEntry
                List<Entry> list = de.getEntriesAsCollection();
                for (Entry zfie : list) {
                    // Write the name bytes
                    byte[] zfieNameBytes = zfie.name.getBytes("UTF-8");
                    int zfieNameBytesLen = zfieNameBytes.length;
                    raf.writeInt(zfieNameBytesLen);
                    writtenSoFar += 4;
                    raf.write(zfieNameBytes);
                    writtenSoFar += zfieNameBytesLen;

                    // Write isDir
                    raf.writeByte(zfie.isDir ? (byte) 1 : (byte) 0);
                    writtenSoFar += 1;

                    // Write offset of bytes in the real Jar/Zip file
                    raf.writeInt(zfie.offset);
                    writtenSoFar += 4;

                    // Write size of the file in the real Jar/Zip file
                    raf.writeInt(zfie.size);
                    writtenSoFar += 4;

                    // Write compressed size of the file in the real Jar/Zip file
                    raf.writeInt(zfie.compressedSize);
                    writtenSoFar += 4;

                    // Write java time stamp of the file in the real Jar/Zip file
                    raf.writeLong(zfie.getLastModified());
                    writtenSoFar += 8;
                }
            }
        } catch (Throwable t) {
            // Do nothing
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                }
            } catch (IOException ioe) {
                // Do nothing
            }
        }

        return ret;
    }

    public boolean writeZipIndex() {
        synchronized (this) {
            return writeIndex();
        }
    }

    private File getIndexFile() {
        if (zipIndexFile == null) {
            if (zipFile == null) {
                return null;
            }

            zipIndexFile = new File((preindexedCacheLocation == null ? "" : preindexedCacheLocation) +
                    zipFile.getName() + ".index");
        }

        return zipIndexFile;
    }

    public File getZipFile() {
        return zipFile;
    }

    File getAbsoluteFile() {
        File absFile = (absFileRef == null ? null : absFileRef.get());
        if (absFile == null) {
            absFile = zipFile.getAbsoluteFile();
            absFileRef = new SoftReference<File>(absFile);
        }
        return absFile;
    }

    // 通过软引用来尽可能地缓存已经创建好的RelativeDirectory对象，
    // 如果无法从relativeDirectoryCache成员变量中获取缓存的对象，
    // 就创建一个新的对象并保存到relativeDirectoryCache中
    private RelativeDirectory getRelativeDirectory(String path) {
        RelativeDirectory rd;
        SoftReference<RelativeDirectory> ref = relativeDirectoryCache.get(path);
        if (ref != null) {
            rd = ref.get();
            if (rd != null) {
                return rd;
            }
        }
        rd = new RelativeDirectory(path);
        relativeDirectoryCache.put(path, new SoftReference<RelativeDirectory>(rd));
        return rd;
    }

    /**
     * ------------------------------------------------------------------------
     * DirectoryEntry class
     * -------------------------------------------------------------------------
     */

    // 表示具体的目录
    static class DirectoryEntry {
        private boolean filesInited;
        private boolean directoriesInited;
        private boolean zipFileEntriesInited;
        private boolean entriesInited;

        private long writtenOffsetOffset = 0;

        private RelativeDirectory dirName;

        private com.sun.tools.javac.util.List<String> zipFileEntriesFiles = com.sun.tools.javac.util.List.<String>nil();
        private com.sun.tools.javac.util.List<String> zipFileEntriesDirectories = com.sun.tools.javac.util.List.<String>nil();
        private com.sun.tools.javac.util.List<Entry> zipFileEntries = com.sun.tools.javac.util.List.<Entry>nil();

        private List<Entry> entries = new ArrayList<Entry>();

        private ZipFileIndex zipFileIndex;

        private int numEntries;

        DirectoryEntry(RelativeDirectory dirName, ZipFileIndex index) {
            filesInited = false;
            directoriesInited = false;
            entriesInited = false;

            this.dirName = dirName;
            zipFileIndex = index;
        }

        private com.sun.tools.javac.util.List<String> getFiles() {
            if (!filesInited) {
                initEntries();
                for (Entry e : entries) {
                    if (!e.isDir) {
                        zipFileEntriesFiles = zipFileEntriesFiles.append(e.name);
                    }
                }
                filesInited = true;
            }
            return zipFileEntriesFiles;
        }

        private com.sun.tools.javac.util.List<String> getDirectories() {
            if (!directoriesInited) {
                initEntries();
                for (Entry e : entries) {
                    if (e.isDir) {
                        zipFileEntriesDirectories = zipFileEntriesDirectories.append(e.name);
                    }
                }
                directoriesInited = true;
            }
            return zipFileEntriesDirectories;
        }

        private com.sun.tools.javac.util.List<Entry> getEntries() {
            if (!zipFileEntriesInited) {
                initEntries();
                zipFileEntries = com.sun.tools.javac.util.List.nil();
                for (Entry zfie : entries) {
                    zipFileEntries = zipFileEntries.append(zfie);
                }
                zipFileEntriesInited = true;
            }
            return zipFileEntries;
        }

        // 查找具体的文件
        private Entry getEntry(String rootName) {
            initEntries();
            int index = Collections.binarySearch(entries, new Entry(dirName, rootName));
            if (index < 0) {
                return null;
            }

            return entries.get(index);
        }

        // ZipFileIndex类中entries数组内容填充到当前DirectoryEntry对象的entries列表中
        // （注意ZipFileIndex中同名的entries变量是Entry数组类型，
        // 而DirectoryEntry类中的entries是List<Entry>类型）。
        /*
        1:当前DirectoryEntry对象所代表的相对路径下所有的目录或文件填充到当前的entries列表中，然后循环entries列表；
        2:果不为目录则追加到类型为List<String>的zipFileEntriesFiles成员变量中，最后返回zipFileEntriesFiles的值即可
        3：这就是获取到的相对路径下的所有文件名称
         */
        private void initEntries() {
            if (entriesInited) {
                return;
            }

            if (!zipFileIndex.readFromIndex) {
                // 由于ZipFileIndex对象zipFileIndex中的entries数组元素是有序的，
                // 因而可以根据要查找的dirName，
                // 直接使用二分查找算法找到符合条件的数组的起始与结束位置的下标，
                // 然后将相关的信息填充到 DirectoryEntry对象的entries列表中
                int from = -Arrays.binarySearch(zipFileIndex.entries,
                        new Entry(dirName, ZipFileIndex.MIN_CHAR)) - 1;
                int to = -Arrays.binarySearch(zipFileIndex.entries,
                        new Entry(dirName, MAX_CHAR)) - 1;

                for (int i = from; i < to; i++) {
                    entries.add(zipFileIndex.entries[i]);
                }
            } else {
                File indexFile = zipFileIndex.getIndexFile();
                if (indexFile != null) {
                    RandomAccessFile raf = null;
                    try {
                        raf = new RandomAccessFile(indexFile, "r");
                        raf.seek(writtenOffsetOffset);

                        for (int nFiles = 0; nFiles < numEntries; nFiles++) {
                            // Read the name bytes
                            int zfieNameBytesLen = raf.readInt();
                            byte[] zfieNameBytes = new byte[zfieNameBytesLen];
                            raf.read(zfieNameBytes);
                            String eName = new String(zfieNameBytes, "UTF-8");

                            // Read isDir
                            boolean eIsDir = raf.readByte() == (byte) 0 ? false : true;

                            // Read offset of bytes in the real Jar/Zip file
                            int eOffset = raf.readInt();

                            // Read size of the file in the real Jar/Zip file
                            int eSize = raf.readInt();

                            // Read compressed size of the file in the real Jar/Zip file
                            int eCsize = raf.readInt();

                            // Read java time stamp of the file in the real Jar/Zip file
                            long eJavaTimestamp = raf.readLong();

                            Entry rfie = new Entry(dirName, eName);
                            rfie.isDir = eIsDir;
                            rfie.offset = eOffset;
                            rfie.size = eSize;
                            rfie.compressedSize = eCsize;
                            rfie.javatime = eJavaTimestamp;
                            entries.add(rfie);
                        }
                    } catch (Throwable t) {
                        // Do nothing
                    } finally {
                        try {
                            if (raf != null) {
                                raf.close();
                            }
                        } catch (Throwable t) {
                            // Do nothing
                        }
                    }
                }
            }

            entriesInited = true;
        }

        List<Entry> getEntriesAsCollection() {
            initEntries();

            return entries;
        }
    }


    static class Entry implements Comparable<Entry> {
        public static final Entry[] EMPTY_ARRAY = {};

        // Directory related
        // 相对路径
        RelativeDirectory dir;
        // 是不是一个文件夹
        boolean isDir;

        // File related
        // isDir为true,name是文件夹名
        // isDir为false,name是文件名
        String name;

        int offset;
        int size;
        int compressedSize;
        long javatime;

        private int nativetime;

        public Entry(RelativePath path) {
            this(path.dirname(), path.basename());
        }

        public Entry(RelativeDirectory directory, String name) {
            dir = directory;
            this.name = name;
        }

        // based on dosToJavaTime in java.util.Zip, but avoiding the
        // use of deprecated Date constructor
        private static long dosToJavaTime(int dtime) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, ((dtime >> 25) & 0x7f) + 1980);
            c.set(Calendar.MONTH, ((dtime >> 21) & 0x0f) - 1);
            c.set(Calendar.DATE, ((dtime >> 16) & 0x1f));
            c.set(Calendar.HOUR_OF_DAY, ((dtime >> 11) & 0x1f));
            c.set(Calendar.MINUTE, ((dtime >> 5) & 0x3f));
            c.set(Calendar.SECOND, ((dtime << 1) & 0x3e));
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }

        public String getName() {
            return new RelativeFile(dir, name).getPath();
        }

        public String getFileName() {
            return name;
        }

        public long getLastModified() {
            if (javatime == 0) {
                javatime = dosToJavaTime(nativetime);
            }
            return javatime;
        }

        void setNativeTime(int natTime) {
            nativetime = natTime;
        }

        public boolean isDirectory() {
            return isDir;
        }

        @Override
        public int compareTo(Entry other) {
            RelativeDirectory otherD = other.dir;
            if (dir != otherD) {
                int c = dir.compareTo(otherD);
                if (c != 0) {
                    return c;
                }
            }
            return name.compareTo(other.name);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            Entry other = (Entry) o;
            return dir.equals(other.dir) && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (dir != null ? dir.hashCode() : 0);
            hash = 97 * hash + (name != null ? name.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            return isDir ? ("Dir:" + dir + " : " + name) :
                    (dir + ":" + name);
        }
    }

    static final class ZipFormatException extends IOException {
        private static final long serialVersionUID = 8000196834066748623L;

        protected ZipFormatException(String message) {
            super(message);
        }

        protected ZipFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /*
     * Exception primarily used to implement a failover, used exclusively here.
     */
    // ZipDirectory类是ZipFileIndex类内定义的一个私有成员类，
    // 这个类中的相关方法将按照压缩包的格式从zipRandomFile中读取压缩包中的目录和文件，
    // 然后保存到ZipFileIndex类中一个全局私有的变量entries中，供其他方法查询
    private class ZipDirectory {
        // 读取File header的数量并保存到zipDir字节数组最开始的两个字节，也就是数组下标0和1的位置
        // 然后将第一个到最后一个File header的字节内容存储到zipDir字节数组中从下标为2开始的位置
        // 这样就相当于为要读取的压缩包内容建立了索引
        byte[] zipDir;
        RandomAccessFile zipRandomFile = null;
        ZipFileIndex zipFileIndex = null;
        private RelativeDirectory lastDir;
        private int lastStart;
        private int lastLen;

        public ZipDirectory(RandomAccessFile zipRandomFile, long start, long end, ZipFileIndex index) throws IOException {
            this.zipRandomFile = zipRandomFile;
            zipFileIndex = index;
            hasValidHeader();
            findCENRecord(start, end);
        }

        /*
         * the zip entry signature should be at offset 0, otherwise allow the
         * calling logic to take evasive action by throwing ZipFormatException.
         */
        private boolean hasValidHeader() throws IOException {
            long pos = zipRandomFile.getFilePointer();
            try {
                if (zipRandomFile.read() == 'P') {
                    if (zipRandomFile.read() == 'K') {
                        if (zipRandomFile.read() == 0x03) {
                            if (zipRandomFile.read() == 0x04) {
                                return true;
                            }
                        }
                    }
                }
            } finally {
                zipRandomFile.seek(pos);
            }
            throw new ZipFormatException("invalid zip magic");
        }

        /*
         * Reads zip file central directory.
         * For more details see readCEN in zip_util.c from the JDK sources.
         * This is a Java port of that function.
         */
        private void findCENRecord(long start, long end) throws IOException {
            long totalLength = end - start;
            int endbuflen = 1024;
            byte[] endbuf = new byte[endbuflen];
            long endbufend = end - start;

            // There is a variable-length field after the dir offset record. We need to do consequential search.
            while (endbufend >= 22) {
                if (endbufend < endbuflen) {
                    endbuflen = (int) endbufend;
                }
                long endbufpos = endbufend - endbuflen;
                zipRandomFile.seek(start + endbufpos);
                zipRandomFile.readFully(endbuf, 0, endbuflen);
                int i = endbuflen - 22;
                // 让i指向End of central directory record中Signature（签名）的第一个字节位置
                // Signature是一个固定的值“\x50\x4b\x05\x06”
                while (i >= 0 &&
                        !(endbuf[i] == 0x50 &&
                                endbuf[i + 1] == 0x4b &&
                                endbuf[i + 2] == 0x05 &&
                                endbuf[i + 3] == 0x06 &&
                                endbufpos + i + 22 +
                                        get2ByteLittleEndian(endbuf, i + 20) == totalLength)) {
                    i--;
                }
                // 此时的i已经指向End of central directory record中Signature（签名）的第一个字节位置
                if (i >= 0) {
                    // 初始化zipDir
                    // get4ByteLittleEndian: 获取中央目录字节数的大小(压缩文件的大小)
                    // 数组头两个字节要保存File header的数量，因而要加2
                    zipDir = new byte[get4ByteLittleEndian(endbuf, i + 12) + 2];
                    // 读取File header数量
                    zipDir[0] = endbuf[i + 10];
                    zipDir[1] = endbuf[i + 11];

                    int sz = get4ByteLittleEndian(endbuf, i + 16);
                    // a negative offset or the entries field indicates a
                    // potential zip64 archive
                    if (sz < 0 || get2ByteLittleEndian(zipDir, 0) == 0xffff) {
                        throw new ZipFormatException("detected a zip64 archive");
                    }
                    zipRandomFile.seek(start + sz);
                    // 读取所有File header的内容并保存到zipDir数组中
                    zipRandomFile.readFully(zipDir, 2, zipDir.length - 2);
                    return;
                } else {
                    endbufend = endbufpos + 21;
                }
            }
            throw new ZipException("cannot read zip file");
        }

        private void buildIndex() throws IOException {
            // 调用get2ByteLittleEndian()方法读取zipDir数组中前两个字节中保存的File header数量
            int entryCount = get2ByteLittleEndian(zipDir, 0);

            // Add each of the files
            if (entryCount > 0) {
                directories = new HashMap<RelativeDirectory, DirectoryEntry>();
                /*
                entryList：
                RelativeDirectory(name=MANIFEST.MF,dir=META-INF/,isDir=false)
                RelativeDirectory(name=C.class,dir=com/compiler/,isDir=false)
                RelativeDirectory(name=B.class,dir=com/compiler/,isDir=false)
                RelativeDirectory(name=A.class,dir=com/compiler/,isDir=false)
                 */
                ArrayList<Entry> entryList = new ArrayList<Entry>();
                int pos = 2;
                for (int i = 0; i < entryCount; i++) {
                    // readEntry()方法来读取压缩包中的内容
                    // 将目录保存到directories集合中，将文件保存到entryList列表中
                    pos = readEntry(pos, entryList, directories);
                }

                // Add the accumulated dirs into the same list
                for (RelativeDirectory d : directories.keySet()) {
                    // use shared RelativeDirectory objects for parent dirs
                    RelativeDirectory parent = getRelativeDirectory(d.dirname().getPath());
                    String file = d.basename();
                    // 将RelativeDirectory(相对路径)对象封装为Entry对象
                    Entry zipFileIndexEntry = new Entry(parent, file);
                    zipFileIndexEntry.isDir = true;
                    entryList.add(zipFileIndexEntry);
                }
                /*
                entries:
                Entry(name=META-INF,dir=,isDir=true)
                Entry(name=com,dir=,isDir=true)
                Entry(name=MANIFEST.MF,dir=META-INF/,isDir=false)
                Entry(name=compiler,dir=com/,isDir=true)
                Entry(name=A.class/,dir=com/compiler/,isDir=false)
                Entry(name=B.class,dir=com/compiler/,isDir=false)
                Entry(name=C.class,dir=com/compiler/,isDir=false)
                 */
                entries = entryList.toArray(new Entry[entryList.size()]);
                // 对所有Entry对象根据路径(path)进行排序
                // 排序后即可以使用二分法进行快速查找
                Arrays.sort(entries);
                // 读取压缩包的内容就准备好了
            } else {
                cleanupState();
            }
        }

        /*
        主要通过读取压缩包中央目录区的每个File header来获取信息
        格式查看：文件相关实现类图2-7
        尽可能地重用RelativeDirectory对象，也就是相同的path使用同一个RelativeDirectory对象来表示
         */
        private int readEntry(int pos, List<Entry> entryList,
                              Map<RelativeDirectory, DirectoryEntry> directories) throws IOException {
            if (get4ByteLittleEndian(zipDir, pos) != 0x02014b50) {
                throw new ZipException("cannot read zip file entry");
            }
            // 其中，dirStart被初始化为pos+46，因为方法参数pos指向File header中Signature的首字节
            int dirStart = pos + 46;
            // dirStart指向了File name的首字节
            int fileStart = dirStart;
            // fileEnd就是fileStart加上Uncompressedsize的值
            // get2ByteLittleEndian(zipDir,pos+28)就是获取File name length的值
            int fileEnd = fileStart + get2ByteLittleEndian(zipDir, pos + 28);
            /*
            com/compiler/A.     class
            |            |          |
            dirStart fileStart fileEnd
             */

            // 过滤掉特殊的路径“META-INF/sym/rt.jar/”
            if (zipFileIndex.symbolFilePrefixLength != 0 &&
                    ((fileEnd - fileStart) >= symbolFilePrefixLength)) {
                dirStart += zipFileIndex.symbolFilePrefixLength;
                fileStart += zipFileIndex.symbolFilePrefixLength;
            }
            // Force any '\' to '/'. Keep the position of the last separator.
            // 将字符 '\\' 替换为 '/'并使用fileStart保存最后一个分隔符后的起始位置
            for (int index = fileStart; index < fileEnd; index++) {
                byte nextByte = zipDir[index];
                if (nextByte == (byte) '\\') {
                    zipDir[index] = (byte) '/';
                    fileStart = index + 1;
                } else if (nextByte == (byte) '/') {
                    fileStart = index + 1;
                }
            }
// -------------------------------------------文件及目录读取代码
            RelativeDirectory directory = null;
            if (fileStart == dirStart) {
                // 获取相对路径对象
                directory = getRelativeDirectory("");
            } else if (lastDir != null && lastLen == fileStart - dirStart - 1) {
                int index = lastLen - 1;
                while (zipDir[lastStart + index] == zipDir[dirStart + index]) {
                    if (index == 0) {
                        directory = lastDir;
                        break;
                    }
                    index--;
                }
            }

            // Sub directories
            if (directory == null) {
                lastStart = dirStart;
                lastLen = fileStart - dirStart - 1;

                directory = getRelativeDirectory(new String(zipDir, dirStart, lastLen, "UTF-8"));
                lastDir = directory;

                // Enter also all the parent directories
                RelativeDirectory tempDirectory = directory;

                while (directories.get(tempDirectory) == null) {
                    directories.put(tempDirectory, new DirectoryEntry(tempDirectory, zipFileIndex));
                    if (tempDirectory.path.indexOf("/") == tempDirectory.path.length() - 1) {
                        break;
                    } else {
                        // use shared RelativeDirectory objects for parent dirs
                        tempDirectory = getRelativeDirectory(tempDirectory.dirname().getPath());
                    }
                }
            } else {
                if (directories.get(directory) == null) {
                    directories.put(directory, new DirectoryEntry(directory, zipFileIndex));
                }
            }

            // For each dir create also a file
            // 说明读取的是一个文件
            if (fileStart != fileEnd) {
                Entry entry = new Entry(directory,
                        new String(zipDir, fileStart, fileEnd - fileStart, "UTF-8"));

                entry.setNativeTime(get4ByteLittleEndian(zipDir, pos + 12));
                entry.compressedSize = get4ByteLittleEndian(zipDir, pos + 20);
                entry.size = get4ByteLittleEndian(zipDir, pos + 24);
                entry.offset = get4ByteLittleEndian(zipDir, pos + 42);
                entryList.add(entry);
            }
// -------------------------------------------文件及目录读取代码
            // 返回读取下一个File header的起始位置
            return pos + 46 +
                    get2ByteLittleEndian(zipDir, pos + 28) +
                    get2ByteLittleEndian(zipDir, pos + 30) +
                    get2ByteLittleEndian(zipDir, pos + 32);
        }
    }
}
