package com.gsy.ai.service;

import org.springframework.stereotype.Service;

import java.io.IOException;


public interface RagDemoService {

    String getRagDemo(String question) throws IOException;
}
