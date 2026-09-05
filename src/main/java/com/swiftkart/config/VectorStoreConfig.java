package com.swiftkart.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class VectorStoreConfig {
	@Bean
	@Profile({ "vector-simple", "!vector-chroma" })
	public VectorStore simpleVectorStore(
			EmbeddingModel embeddingModel,
			@Value("${swiftkart.rag.vector-store-path:./data/vectorstore.json}") String vectorStorePath) {
		Path path = Path.of(vectorStorePath).toAbsolutePath();
		try {
			Files.createDirectories(path.getParent());
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to create vector store directory: " + path.getParent(), e);
		}

		SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
		File vectorStoreFile = path.toFile();
		if (Files.isRegularFile(path) && vectorStoreFile.length() > 2) {
			vectorStore.load(vectorStoreFile);
		}
		return vectorStore;
	}

	@Bean
	@Profile("vector-chroma")
	public VectorStore chromaVectorStore(
			EmbeddingModel embeddingModel,
			ChromaApi chromaApi,
			ChromaVectorStoreProperties properties) {
		return ChromaVectorStore.builder(chromaApi, embeddingModel)
				.tenantName(properties.getTenantName())
				.databaseName(properties.getDatabaseName())
				.collectionName(properties.getCollectionName())
				.initializeSchema(properties.isInitializeSchema())
				.build();
	}

}

