package com.gsy.ai.dome.DO;

import lombok.Data;

@Data
public class DocumentChunk {
    private String content;

    private float[] vector;
}
