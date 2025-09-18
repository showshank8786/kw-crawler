package org.kwcrawler;

public enum DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADED_NOT_FOUND,
    DOWNLOADED_FOUND,
    BROKEN;

    public boolean correctlyDownloaded() {
        return this == DOWNLOADED_NOT_FOUND || this == DOWNLOADED_FOUND;
    }

    public boolean notDownloadedOrBroken() {
        return this == NOT_DOWNLOADED || this == BROKEN;
    }

    public boolean notFound() {
        return this == DOWNLOADED_NOT_FOUND;
    }
}

