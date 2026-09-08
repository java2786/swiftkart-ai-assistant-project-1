package com.swiftkart.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Compatibility facade for the application's existing RAG API.
 * The backing VectorStore is configured as a persistent SimpleVectorStore.
 */
@Component
public class InMemoryRagStore {
    private final VectorStore vectorStore;
    private final List<String> addedDocumentIds = new ArrayList<>();

    public InMemoryRagStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    /**
     * Add a document chunk to the RAG store.
     * 
     * @param content The document chunk text
     * @param metadata Metadata about the chunk (e.g., filename, source)
     */
    public void addChunk(String content, Map<String, String> metadata) {
        Map<String, Object> documentMetadata = new HashMap<>(metadata);
        Document document = new Document(content, documentMetadata);
        vectorStore.add(List.of(document));
        addedDocumentIds.add(document.getId());
    }
    
    /**
     * Search for relevant chunks based on semantic similarity.
     * 
     * @param query The search query
     * @param topK Number of top results to return
     * @return List of relevant chunks ranked by similarity
     */
    public List<String> search(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.1)
                        .build())
                .stream()
                .map(Document::getText)
                .toList();
    }
    
    /**
     * Get total number of chunks in the store.
     */
    public int size() {
        return addedDocumentIds.size();
    }
    
    /**
     * Clear all chunks.
     */
    public void clear() {
        if (!addedDocumentIds.isEmpty()) {
            vectorStore.delete(new ArrayList<>(addedDocumentIds));
            addedDocumentIds.clear();
        }
    }
}
