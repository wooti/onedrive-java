package com.wouterbreukink.onedrive.io;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public interface FileSystemProvider {
    void delete(File file) throws IOException;

    File createFolder(File file, String name) throws IOException;

    File createFile(File file, String name) throws IOException;

    void replaceFile(File original, File replacement) throws IOException;

    void setAttributes(File downloadFile, Date created, Date lastModified) throws IOException;

    boolean verifyCrc(File file, long crc) throws IOException;

    /**
     * Get the CRC32 Checksum for a file
     *
     * @param file The file to check
     * @return The CRC32 checksum of the file
     * @throws IOException
     */
    long getChecksum(File file) throws IOException;
}
