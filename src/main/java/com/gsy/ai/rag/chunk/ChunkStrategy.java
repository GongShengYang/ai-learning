package com.gsy.ai.rag.chunk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChunkStrategy {

    @Value("${gsy.rag.chunk.max-length:800}")
    private int maxLength;

    @Value("${gsy.rag.chunk.overlap:100}")
    private int overlap;

    /**
     * 输入文本，输出切片列表（每片带起始偏移）
     */
    public List<ChunkResult> split(String text) {
        List<ChunkResult> result = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] paragraphs = normalized.split("\n\n+");
        int globalOffset = 0;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;
            if (para.length() <= maxLength) {
                result.add(new ChunkResult(para, globalOffset, globalOffset + para.length()));
                globalOffset += para.length() + 2;
                continue;
            }

            int start = 0;
            while (start < para.length()) {
                int end = Math.min(start + maxLength, para.length());

                // 切分点优化：尽量在标点/空格处
                if (end < para.length()) {
                    int lastSpace = para.lastIndexOf(' ', end);
                    int lastPunctuation = para.lastIndexOf('。', end);
                    int lastComma = para.lastIndexOf('，', end);

                    int cut = Math.max(lastSpace, Math.max(lastPunctuation, lastComma));

                    // 避免切得太短
                    if (cut > start + overlap) {
                        end = cut + 1;
                    }
                }

                if (end <= start) {
                    end = Math.min(start + maxLength, para.length());
                }

                String chunkText = para.substring(start, end);
                result.add(new ChunkResult(
                        chunkText,
                        globalOffset + start,
                        globalOffset + end
                ));

                // 关键修复：已经到段落末尾，必须直接结束
                if (end >= para.length()) {
                    break;
                }

                int nextStart = end - overlap;

                // 防止 overlap 配置过大导致不前进
                if (nextStart <= start) {
                    nextStart = end;
                }

                start = nextStart;
            }
            globalOffset += para.length() + 2;
        }
        return result;
    }

    public static class ChunkResult {
        private final String content;
        private final int startOffset;
        private final int endOffset;

        public ChunkResult(String content, int startOffset, int endOffset) {
            this.content = content;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public String getContent() { return content; }
        public int getStartOffset() { return startOffset; }
        public int getEndOffset() { return endOffset; }
    }
}