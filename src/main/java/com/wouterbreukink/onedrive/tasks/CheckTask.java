package com.wouterbreukink.onedrive.tasks;

import com.google.api.client.util.Maps;
import com.google.api.client.util.Preconditions;
import com.wouterbreukink.onedrive.client.OneDriveItem;
import com.wouterbreukink.onedrive.encryption.EncryptionException;
import com.wouterbreukink.onedrive.encryption.EncryptionProvider;
import com.wouterbreukink.onedrive.filesystem.FileSystemProvider;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import static com.wouterbreukink.onedrive.CommandLineOpts.getCommandLineOpts;

public class CheckTask extends Task {

    private final OneDriveItem remoteFile;
    private final File localFile;
    private EncryptionProvider encryptionProvider;

    public CheckTask(TaskOptions options, OneDriveItem remoteFile, File localFile) {
        super(options);
        this.remoteFile = Preconditions.checkNotNull(remoteFile);
        this.localFile = Preconditions.checkNotNull(localFile);
        if (getCommandLineOpts().isEncryptionEnabled())
        {
        	encryptionProvider = new EncryptionProvider(getCommandLineOpts().getEncryptionKey());
        }
    }

    public int priority() {
        return 10;
    }

    @Override
    public String toString() {
        return String.format("Checking %s %s", remoteFile.isDirectory() ? "folder" : "file", remoteFile.getFullName());
    }

    @Override
    protected void taskBody() throws IOException {

        if (localFile.isDirectory() && remoteFile.isDirectory()) { // If we are syncing folders

            OneDriveItem[] remoteFiles = api.getChildren(remoteFile);

            // Index the local files
            Map<String, File> localFileCache = Maps.newHashMap();
            //noinspection ConstantConditions
            for (File file : localFile.listFiles()) {
                localFileCache.put(file.getName(), file);
            }

            // Iterate over all the remote files
            for (OneDriveItem remoteFile : remoteFiles) {

                if (remoteFile.isDirectory() && !getCommandLineOpts().isRecursive()) {
                    continue;
                }

                String remoteFileName = remoteFile.getName();
                if (getCommandLineOpts().isEncryptionEnabled())
                {
                	try 
                	{
						remoteFileName = encryptionProvider.decryptFilename(remoteFileName);
					}
                	catch (EncryptionException e) 
                	{
                		throw new IllegalStateException("Cannot decrypt remote filename <" + remoteFileName + ">");
					}                	
                }
                
                File localFile = localFileCache.remove(remoteFileName);
                processChild(remoteFile, localFile);
            }

            // Iterate over any local files we've not matched yet
            for (File localFile : localFileCache.values()) {

                if (localFile.isDirectory() && !getCommandLineOpts().isRecursive()) {
                    continue;
                }

                processChild(null, localFile);
            }

            return;

        }

        // Skip if the file size is too big or if the file is ignored
        switch (getCommandLineOpts().getDirection()) {
            case UP:
                if (isSizeInvalid(localFile) || isIgnored(localFile)) {
                    reporter.skipped();
                    return;
                }
                break;
            case DOWN:
                if (isSizeInvalid(remoteFile) || isIgnored(remoteFile)) {
                    reporter.skipped();
                    return;
                }
                break;
        }

        if (localFile.isFile() && !remoteFile.isDirectory()) { // If we are syncing files

            // Check if the remote file matches the local file
            FileSystemProvider.FileMatch match = fileSystem.verifyMatch(
                    localFile, remoteFile.getCrc32(),
                    remoteFile.getSize(),
                    remoteFile.getCreatedDateTime(),
                    remoteFile.getLastModifiedDateTime());

            switch (match) {
                case NO:
                    switch (getCommandLineOpts().getDirection()) {
                        case UP:
                            queue.add(new UploadTask(getTaskOptions(), remoteFile.getParent(), localFile, true));
                            break;
                        case DOWN:
                            queue.add(new DownloadTask(getTaskOptions(), localFile.getParentFile(), remoteFile, true));
                            break;
                        default:
                            throw new IllegalStateException("Unsupported direction " + getCommandLineOpts().getDirection());
                    }
                    break;
                case CRC:
                    queue.add(new UpdatePropertiesTask(getTaskOptions(), remoteFile, localFile));
                    break;
                case YES:
                    reporter.same();
                    break;
            }

        } else { // Resolve cases where remote and local disagree over whether the item is a file or folder
            switch (getCommandLineOpts().getDirection()) {
                case UP:
                    new DeleteTask(getTaskOptions(), remoteFile).taskBody(); // Execute immediately
                    queue.add(new UploadTask(getTaskOptions(), remoteFile.getParent(), localFile, true));
                    break;
                case DOWN:
                    new DeleteTask(getTaskOptions(), localFile).taskBody(); // Execute immediately
                    queue.add(new DownloadTask(getTaskOptions(), localFile.getParentFile(), remoteFile, true));
                    break;
                default:
                    throw new IllegalStateException("Unsupported direction " + getCommandLineOpts().getDirection());
            }
        }
    }

    private void processChild(OneDriveItem remoteFile, File localFile) {

        if (remoteFile == null && localFile == null) {
            throw new IllegalArgumentException("Must specify at least one file");
        }

        if (remoteFile != null && isIgnored(remoteFile) || localFile != null && isIgnored((localFile))) {
            reporter.skipped();
            return;
        }

        boolean remoteOnly = localFile == null;
        boolean localOnly = remoteFile == null;

        // Case 1: We only have the file remotely
        if (remoteOnly) {
            switch (getCommandLineOpts().getDirection()) {
                case UP:
                    queue.add(new DeleteTask(getTaskOptions(), remoteFile));
                    break;
                case DOWN:
                    queue.add(new DownloadTask(getTaskOptions(), this.localFile, remoteFile, false));
                    break;
                default:
                    throw new IllegalStateException("Unsupported direction " + getCommandLineOpts().getDirection());
            }
        }

        // Case 2: We only have the file locally
        else if (localOnly) {
            switch (getCommandLineOpts().getDirection()) {
                case UP:
                    queue.add(new UploadTask(getTaskOptions(), this.remoteFile, localFile, false));
                    break;
                case DOWN:
                    queue.add(new DeleteTask(getTaskOptions(), localFile));
                    break;
                default:
                    throw new IllegalStateException("Unsupported direction " + getCommandLineOpts().getDirection());
            }
        }

        // Case 3: We have the file in both locations
        else {
            queue.add(new CheckTask(getTaskOptions(), remoteFile, localFile));
        }
    }
}
