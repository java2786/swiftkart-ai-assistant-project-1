

## Missing or broken production assumptions
These are the main blockers:

1. `SPRING_PROFILES_ACTIVE=prod` is not enough
- There is no `application-prod.properties` in the repo.
- The existing Chroma config is under a profile named `vector-chroma`, not `prod`.
- So the app will not automatically switch to Chroma if only `prod` is active.

2. Default vector store is `simple`, not `chroma`
- `application.properties` sets `spring.ai.vectorstore.type=simple`.
- That means the compose override for Chroma will not be used unless the app profile is correctly selected.

3. `localhost` is wrong inside Docker
- `application-vector-chroma.properties` uses `http://localhost`
- Inside the app container, that means “this container,” not the Chroma service.
- The compose file fixes this with `http://chromadb`, but only if Chroma mode is active.

4. H2 is local file DB, not external Postgres
- This is okay for a single EC2 instance, but it means the app is not fully stateless across container re-creation unless the `app_data` volume remains mounted.

5. Web search is optional but not guaranteed
- `WebSearchTool.java` gracefully degrades if `TAVILY_API_KEY` is blank.
- But the tool becomes non-functional without it.

---

## Bottom line

The app is currently designed for:
- H2 file database on disk,
- optional Chroma vector store via profile-based config,
- OpenAI API access, and
- optional Tavily web search.

The most important pre-production gap is this: the Docker compose file expects Chroma to be active, but the project’s active config still defaults to a SimpleVectorStore unless the correct profile/property combination is applied. In a fresh EC2 Docker deployment, this is the likeliest source of runtime confusion or silent feature mismatch.

If you want, I can turn this audit into a corrected production-ready compose and application config set next.