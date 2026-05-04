package com.nuaa.ragagent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateDocumentRequest {

    private Long spaceId;

    @NotBlank(message = "Document title cannot be blank")
    @Size(max = 255, message = "Document title length cannot exceed 255")
    private String title;

    @NotBlank(message = "Document content cannot be blank")
    private String content;

    @Size(max = 64, message = "Source type length cannot exceed 64")
    private String sourceType;

    @Size(max = 512, message = "Source URI length cannot exceed 512")
    private String sourceUri;

    public Long getSpaceId() {
        return spaceId;
    }

    public UpdateDocumentRequest setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public UpdateDocumentRequest setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getContent() {
        return content;
    }

    public UpdateDocumentRequest setContent(String content) {
        this.content = content;
        return this;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UpdateDocumentRequest setSourceType(String sourceType) {
        this.sourceType = sourceType;
        return this;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public UpdateDocumentRequest setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
        return this;
    }
}