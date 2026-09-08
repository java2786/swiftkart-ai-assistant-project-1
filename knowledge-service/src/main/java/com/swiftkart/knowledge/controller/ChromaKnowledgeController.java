package com.swiftkart.knowledge.controller;

import com.swiftkart.knowledge.dto.DocumentResponse;
import com.swiftkart.knowledge.dto.IngestionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/chroma")
@Tag(name = "ChromaDB Knowledge", description = "Document Ingestion and Vector Similarity Search")
public class ChromaKnowledgeController {

    private final VectorStore vectorStore;

    public ChromaKnowledgeController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest Document", description = "Generates OpenAI embedding and stores the document into ChromaDB")
    public Map<String, Object> ingestDocument(@RequestBody IngestionRequest request) {
        Document document = new Document(request.content());
        vectorStore.add(List.of(document));

        return Map.of(
                "status", "INGESTED",
                "content", request.content());
    }

    @GetMapping("/search")
    @Operation(summary = "Similarity Search", description = "Queries ChromaDB via vector similarity search using OpenAI query embeddings")
    public List<DocumentResponse> search(@RequestParam("query") String query) {
        return vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(3)
                                .build())
                .stream()
                .map(doc -> new DocumentResponse(doc.getId(), doc.getText(), doc.getMetadata()))
                .toList();
    }
}
