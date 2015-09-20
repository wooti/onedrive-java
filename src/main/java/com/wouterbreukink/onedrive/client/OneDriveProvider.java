package com.wouterbreukink.onedrive.client;

import com.google.api.client.http.HttpContent;
import com.wouterbreukink.onedrive.client.authoriser.AuthorisationProvider;
import com.wouterbreukink.onedrive.client.facets.FileSystemInfoFacet;
import com.wouterbreukink.onedrive.client.resources.Drive;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public interface OneDriveProvider {

    // Read only operations

    Drive getDefaultDrive() throws IOException;

    OneDriveItem getRoot() throws IOException;

    OneDriveItem[] getChildren(OneDriveItem parent) throws IOException;

    OneDriveItem getPath(String path) throws IOException;

    // Write operations

    OneDriveItem replaceFile(OneDriveItem parent, File file, String remoteFilename) throws IOException;
    
    OneDriveItem replaceEncryptedFile(OneDriveItem parent, HttpContent httpContent, FileSystemInfoFacet fsi, String remoteFilename) throws IOException;

    OneDriveItem uploadFile(OneDriveItem parent, File file, String remoteFilename) throws IOException;
    
    OneDriveItem uploadEncryptedFile(OneDriveItem parent, HttpContent httpContent, FileSystemInfoFacet fsi, String remoteFilename) throws IOException;

    OneDriveUploadSessionBase startUploadSession(OneDriveItem parent, File file, String remoteFilename) throws IOException;
    
    OneDriveUploadSessionBase startEncryptedUploadSession(OneDriveItem parent, File file, String remoteFilename) throws IOException;

    void uploadChunk(OneDriveUploadSessionBase session) throws IOException;

    OneDriveItem updateFile(OneDriveItem item, Date createdDate, Date modifiedDate) throws IOException;
    
    OneDriveItem updateFile(OneDriveItem item, FileSystemInfoFacet fsi) throws IOException;

    OneDriveItem createFolder(OneDriveItem parent, String name) throws IOException;

    void download(OneDriveItem item, File target) throws IOException;

    void delete(OneDriveItem remoteFile) throws IOException;

    class FACTORY {

        public static OneDriveProvider readOnlyApi(AuthorisationProvider authoriser) {
            return new ROOneDriveProvider(authoriser);
        }

        public static OneDriveProvider readWriteApi(AuthorisationProvider authoriser) {
            return new RWOneDriveProvider(authoriser);
        }
    }
}
