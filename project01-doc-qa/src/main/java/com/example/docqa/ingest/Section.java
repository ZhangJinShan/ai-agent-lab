package com.example.docqa.ingest;

/** 一个小节 = 一个标题 + 它下面的正文（到下一个标题为止） */
public record Section(String heading, String text) {
}
