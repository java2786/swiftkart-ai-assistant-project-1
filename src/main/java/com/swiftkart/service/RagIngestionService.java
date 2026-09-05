package com.swiftkart.service;

import com.swiftkart.rag.InMemoryRagStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * RAG Ingestion Service - Loads and chunks policy documents at startup.
 * 
 * This service:
 * 1. Scans classpath:rag-docs/*.txt for policy documents
 * 2. Reads each document and splits into chunks (TokenTextSplitter)
 * 3. Generates embeddings and adds chunks to the active VectorStore
 * 4. Logs ingestion statistics for verification
 */
@Configuration
public class RagIngestionService {
    
    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    /**
     * ApplicationRunner that loads policy documents at application startup.
     * 
     * Chunks are created by splitting on paragraph boundaries.
    * Each chunk is embedded and stored in the active VectorStore for later retrieval.
     * 
    * @param ragStore The InMemoryRagStore facade backed by the active VectorStore
     * @param resourcePatternResolver Used to locate policy documents
     * @return An ApplicationRunner that executes the ingestion
     */
    @Bean
    public ApplicationRunner ragDocumentIngestion(
            InMemoryRagStore ragStore,
            VectorStore vectorStore,
            ResourcePatternResolver resourcePatternResolver,
            @Value("${swiftkart.rag.vector-store-path:./data/vectorstore.json}") String vectorStorePath) {
        return args -> {
            try {
                log.info("===== RAG INGESTION STARTING =====");

                Path persistencePath = Path.of(vectorStorePath);
                if (vectorStore instanceof org.springframework.ai.vectorstore.SimpleVectorStore
                        && Files.isRegularFile(persistencePath) && Files.size(persistencePath) > 2) {
                    log.info("Vector store already contains documents at '{}'; skipping ingestion", vectorStorePath);
                    return;
                }

                if (!(vectorStore instanceof org.springframework.ai.vectorstore.SimpleVectorStore)
                        && !ragStore.search("SwiftKart return and shipping policy", 1).isEmpty()) {
                    log.info("Active vector store already contains policy documents; skipping ingestion");
                    return;
                }
                
                // Load all .txt files from classpath:rag-docs/
                Resource[] resources = resourcePatternResolver.getResources("classpath:rag-docs/*.txt");
                
                if (resources.length == 0) {
                    log.warn("No policy documents found in classpath:rag-docs/");
                    return;
                }
                
                log.info("Found {} policy document(s) to ingest", resources.length);
                
                int totalChunks = 0;
                int documentCount = 0;
                
                // Process each document
                for (Resource resource : resources) {
                    try {
                        log.info("Loading document: {}", resource.getFilename());
                        
                        // Read file content
                        String content = StreamUtils.copyToString(
                            resource.getInputStream(),
                            StandardCharsets.UTF_8
                        );
                        
                        // Create chunks by splitting on paragraph boundaries (double newlines)
                        String[] chunkTexts = content.split("\\n\\n+");
                        int chunksBefore = ragStore.size();
                        
                        // Add each chunk to the RAG store
                        for (String chunkText : chunkTexts) {
                            if (!chunkText.trim().isEmpty()) {
                                Map<String, String> metadata = new HashMap<>();
                                metadata.put("filename", resource.getFilename());
                                metadata.put("source", "rag-docs");
                                
                                ragStore.addChunk(chunkText.trim(), metadata);
                                totalChunks++;
                            }
                        }
                        
                        int chunksAdded = ragStore.size() - chunksBefore;
                        documentCount++;
                        
                        log.info("  Document '{}': {} chunks created", 
                                resource.getFilename(), chunksAdded);
                        
                    } catch (Exception e) {
                        log.error("Error loading document: {}", resource.getFilename(), e);
                    }
                }
                
                // Log completion
                if (totalChunks > 0) {
                    if (vectorStore instanceof org.springframework.ai.vectorstore.SimpleVectorStore simpleVectorStore) {
                        simpleVectorStore.save(persistencePath.toFile());
                    }
                    log.info("Loaded {} documents into {} chunks", documentCount, totalChunks);
                } else {
                    log.warn("No chunks were created from the documents");
                }
                
                log.info("===== RAG INGESTION COMPLETE =====\n");
                
            } catch (Exception e) {
                log.error("Fatal error during RAG document ingestion", e);
                throw new RuntimeException("RAG ingestion failed", e);
            }
        };
    }
}
