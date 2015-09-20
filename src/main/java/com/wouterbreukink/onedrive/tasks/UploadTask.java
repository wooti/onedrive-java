package com.wouterbreukink.onedrive.tasks;

import com.google.api.client.util.Preconditions;
import com.wouterbreukink.onedrive.client.OneDriveItem;
import com.wouterbreukink.onedrive.client.OneDriveUploadSessionBase;
import com.wouterbreukink.onedrive.client.facets.FileSystemInfoFacet;
import com.wouterbreukink.onedrive.encryption.EncryptedFileContent;
import com.wouterbreukink.onedrive.encryption.EncryptionProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

import static com.wouterbreukink.onedrive.CommandLineOpts.getCommandLineOpts;
import static com.wouterbreukink.onedrive.LogUtils.readableFileSize;
import static com.wouterbreukink.onedrive.LogUtils.readableTime;

public class UploadTask extends Task {

    private static final Logger log = LogManager.getLogger(UploadTask.class.getName());

    private final OneDriveItem parent;
    private final File localFile;
    private final String remoteFilename;
    private final boolean replace;    

    public UploadTask(TaskOptions options, OneDriveItem parent, File localFile, boolean replace) 
    {
        super(options);

        this.parent = Preconditions.checkNotNull(parent);
        this.localFile = Preconditions.checkNotNull(localFile);
        
        if (getCommandLineOpts().isEncryptionEnabled())
        {	
        	remoteFilename = EncryptionProvider.getEncryptionProvider()
        			.encryptFilename(localFile.getName());
        }
        else
        	remoteFilename = localFile.getName(); 
    			        
        this.replace = replace;

        if (!parent.isDirectory()) {
            throw new IllegalArgumentException("Specified parent is not a folder");
        }
    }
    

    public int priority() {
        return 50;
    }

    @Override
    public String toString() {
        return "Upload " + parent.getFullName() + localFile.getName() + " to " + parent.getFullName() + remoteFilename;
    }

    @Override
    protected void taskBody() throws IOException {

        if (isIgnored(localFile)) {
            reporter.skipped();
            return;
        }

        if (localFile.isDirectory()) {
            OneDriveItem newParent = api.createFolder(parent, remoteFilename);

            //noinspection ConstantConditions
            for (File f : localFile.listFiles()) {
                queue.add(new UploadTask(getTaskOptions(), newParent, f, false));
            }
        } else {

            if (isSizeInvalid(localFile)) {
                reporter.skipped();
                return;
            }

            long startTime = System.currentTimeMillis();

            OneDriveItem response;
            if (localFile.length() > getCommandLineOpts().getSplitAfter() * 1024 * 1024) {

            	OneDriveUploadSessionBase session;
                if (getCommandLineOpts().isEncryptionEnabled())
                	session = api.startEncryptedUploadSession(parent, localFile, remoteFilename);                	
                else
                	session = api.startUploadSession(parent, localFile, remoteFilename);
                	

                while (!session.isComplete()) {
                    long startTimeInner = System.currentTimeMillis();

                    api.uploadChunk(session);

                    long elapsedTimeInner = System.currentTimeMillis() - startTimeInner;
                    
                    log.info(String.format("Uploaded chunk (progress %.1f%%) of %s (%s/s) from file %s to file <onedrive>%s",
                    		((double) session.getTotalUploaded() / session.getRemoteFileLength()) * 100,
                            readableFileSize(session.getLastUploaded()),
                            elapsedTimeInner > 0 ? readableFileSize(session.getLastUploaded() / (elapsedTimeInner / 1000d)) : 0,
                            localFile.getAbsolutePath(),
                            parent.getFullName() + remoteFilename));                    
                }

                response = session.getItem();

            } else {
            	
            	if (getCommandLineOpts().isEncryptionEnabled())
            	{
            		EncryptedFileContent efc = new EncryptedFileContent(null, localFile);
        			FileSystemInfoFacet fsi = new FileSystemInfoFacet(localFile);
        			response = replace ? 
            				api.replaceEncryptedFile(parent, efc , fsi, remoteFilename) : 
            				api.uploadEncryptedFile(parent, efc, fsi, remoteFilename);            		
            	}
            	else
            	{
            		response = replace ? 
            				api.replaceFile(parent, localFile, remoteFilename) : 
            				api.uploadFile(parent, localFile, remoteFilename);
            	}
            }

            long elapsedTime = System.currentTimeMillis() - startTime;

            log.info(String.format("Uploaded %s in %s (%s/s) from file %s to %s file <onedrive>%s",
            		readableFileSize(localFile.length()),
                    readableTime(elapsedTime),
                    elapsedTime > 0 ? readableFileSize(localFile.length() / (elapsedTime / 1000d)) : 0,
                    localFile.getAbsolutePath(),
                    replace ? "replace" : "new",
                    response.getFullName()));            

            reporter.fileUploaded(replace, localFile.length());
        }
    }
}

