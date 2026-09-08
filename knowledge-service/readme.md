<!-- docker pull chromadb/chroma:latest -->

docker run -d \
  --name swiftkart-chromadb-knowledge \
  -p 8001:8000 \
  -e IS_PERSISTENT=TRUE \
  -e ANONYMIZED_TELEMETRY=FALSE \
  -v chroma_knowledge_data:/chroma/chroma \
  chromadb/chroma:0.4.24
  


http://localhost:8082/swagger-ui.html

http://localhost:8082/v3/api-docs




