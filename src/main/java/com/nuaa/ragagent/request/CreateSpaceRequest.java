package com.nuaa.ragagent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateSpaceRequest {

    @NotBlank(message = "Space name cannot be blank")
    @Size(max = 100, message = "Space name length cannot exceed 100")
    private String name;

    @Size(max = 500, message = "Space description length cannot exceed 500")
    private String description;

    public String getName() {
        return name;
    }

    public CreateSpaceRequest setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public CreateSpaceRequest setDescription(String description) {
        this.description = description;
        return this;
    }
}