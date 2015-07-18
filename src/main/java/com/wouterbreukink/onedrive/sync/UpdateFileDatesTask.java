package com.wouterbreukink.onedrive.sync;

import com.wouterbreukink.onedrive.client.OneDriveAPI;
import com.wouterbreukink.onedrive.client.OneDriveAPIException;
import com.wouterbreukink.onedrive.client.resources.Item;
import com.wouterbreukink.onedrive.io.FileSystemProvider;
import jersey.repackaged.com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.wouterbreukink.onedrive.CommandLineOpts.getCommandLineOpts;

public class UpdateFileDatesTask extends Task {

    private static final Logger log = LogManager.getLogger(UpdateFileDatesTask.class.getName());
    private final Item remoteFile;
    private final File localFile;

    public UpdateFileDatesTask(TaskQueue queue, OneDriveAPI api, FileSystemProvider fileSystem, Item remoteFile, File localFile) {

        super(queue, api, fileSystem);

        this.remoteFile = Preconditions.checkNotNull(remoteFile);
        this.localFile = Preconditions.checkNotNull(localFile);
    }

    public int priority() {
        return 50;
    }

    @Override
    public String toString() {
        return "Update properties for " + remoteFile.getFullName();
    }

    @Override
    protected void taskBody() throws IOException, OneDriveAPIException {

        switch (getCommandLineOpts().getDirection()) {
            case UP:
                BasicFileAttributes attr = Files.readAttributes(localFile.toPath(), BasicFileAttributes.class);
                // Timestamp rounded to the nearest second
                Date localCreatedDate = new Date(attr.creationTime().to(TimeUnit.SECONDS) * 1000);
                Date localModifiedDate = new Date(attr.lastModifiedTime().to(TimeUnit.SECONDS) * 1000);
                api.updateFile(remoteFile, localCreatedDate, localModifiedDate);

            case DOWN:
                BasicFileAttributeView attributes = Files.getFileAttributeView(localFile.toPath(), BasicFileAttributeView.class);
                FileTime lastModified = FileTime.fromMillis(remoteFile.getFileSystemInfo().getLastModifiedDateTime().getTime());
                FileTime created = FileTime.fromMillis(remoteFile.getFileSystemInfo().getCreatedDateTime().getTime());
                attributes.setTimes(lastModified, lastModified, created);
                break;
            default:
                throw new IllegalStateException("Unsupported direction " + getCommandLineOpts().getDirection());
        }
    }
}

