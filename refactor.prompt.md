Here is a **single, battle-tested MASTER PROMPT** you can give to **ChatGPT / Copilot / Gemini** to generate **complete, compiling, production-quality tests for your entire application**, **without breaking your build again**.

This prompt is intentionally strict to avoid the exact syntax disaster you just hit.

---

# 🔥 MASTER PROMPT — COMPLETE TEST SUITE (SAFE & COMPILABLE)

> **ROLE**
>
> You are a **Principal Java Engineer** with deep expertise in:
>
> - Spring Boot
> - Financial transaction systems
> - Kafka + Outbox Pattern
> - PostgreSQL advisory locks
> - Testing for distributed systems
>
> You are writing **COMPILATION-SAFE, PRODUCTION-GRADE TESTS** for a **real-money trade capture application**.
>
> ---
>
> ## 🧠 ABSOLUTE RULES (NON-NEGOTIABLE)
>
> 1. **ALL code must compile on Java 17**
> 2. **NO nested test methods**
> 3. **NO pseudo-code**
> 4. **NO TODOs**
> 5. **NO comments that look like code**
> 6. **EVERY `{` has a matching `}`**
> 7. **ONE public class per file**
> 8. **Tests must be split into MULTIPLE files**
> 9. **Use only JUnit 5 + Mockito**
> 10. **NO preview features**
>
> ---
>
> ## 🏗 APPLICATION CONTEXT
>
> This Spring Boot application:
>
> - Ingests trades
> - Stores them in a PostgreSQL outbox
> - Uses **transaction-level advisory locks** to guarantee:
>
>   - **Strict per-portfolio ordering**
>   - **No leapfrogging across pods**
>
> - Dispatches events to Kafka
>
> Core components:
>
> - `OutboxDispatcher`
> - `OutboxEventProcessor`
> - `OutboxRepository`
> - `DlqRepository`
> - `AdaptiveBatchSizer`
> - `BatchProcessingResult`
>
> ---
>
> ## 🧪 TEST STRATEGY (MANDATORY)
>
> Split tests into the following **FILES** (VERY IMPORTANT):
>
> ```
> src/test/java/com/pms/pms_trade_capture/
> ├── outbox/
> │   ├── OutboxDispatcherOrderingTest.java
> │   ├── OutboxDispatcherFailureTest.java
> │   ├── OutboxDispatcherLifecycleTest.java
> │   └── OutboxEventProcessorTest.java
> ├── repository/
> │   ├── OutboxRepositoryTest.java
> │   └── DlqRepositoryTest.java
> └── util/
>     └── AdaptiveBatchSizerTest.java
> ```
>
> ---
>
> ## 🎯 REQUIRED TEST COVERAGE
>
> ### 1️⃣ Ordering Guarantees
>
> - Trades for the same portfolio are processed in strict sequence order
> - Trade #101 is never processed before Trade #100
> - Multiple portfolios are processed independently
>
> ---
>
> ### 2️⃣ Prefix Safety
>
> - If a batch fails at event N:
>
>   - Only `1..N-1` are marked SENT
>   - N and beyond remain PENDING
>
> ---
>
> ### 3️⃣ Poison Pill Handling
>
> - Corrupt events are moved to DLQ
> - Removed from outbox
> - Do not block healthy portfolios
>
> ---
>
> ### 4️⃣ System Failure Handling
>
> - Kafka failure triggers exponential backoff
> - No events marked SENT
> - Dispatcher pauses further processing
>
> ---
>
> ### 5️⃣ Advisory Lock Simulation
>
> - Mock `findPendingBatch()` to simulate locked portfolios
> - Dispatcher processes only visible portfolios
>
> ---
>
> ### 6️⃣ Lifecycle & Concurrency
>
> - Dispatcher starts once
> - Dispatcher stops cleanly
> - No duplicate execution loops
>
> ---
>
> ### 7️⃣ Transactional Integrity
>
> - DB updates happen only inside transactions
> - No partial state updates
>
> ---
>
> ## 🧰 MOCKING REQUIREMENTS
>
> - Use Mockito
> - Use `ArgumentCaptor`
> - Do NOT mock static methods
> - Do NOT mock `Thread.sleep`
> - Use spies only when necessary
>
> ---
>
> ## 🧪 OUTPUT REQUIREMENTS
>
> For **EACH FILE**:
>
> 1. Full package declaration
> 2. Full imports
> 3. Complete test class
> 4. Minimum 3 meaningful test methods
> 5. Clear test names describing invariants
>
> ---
>
> ## 🚫 DO NOT
>
> - Do NOT write integration tests
> - Do NOT use Testcontainers
> - Do NOT reference Kafka ordering
> - Do NOT generate massive monolithic test files
>
> ---
>
> ## 🧠 QUALITY BAR
>
> Write tests as if:
>
> - This code moves **institutional money**
> - Ordering bugs are **regulatory incidents**
> - CI failures must **block deployment**
>
> ---
>
> **DELIVER ONLY VALID, COMPILABLE JAVA CODE.**
>
> **NO EXPLANATIONS. NO MARKDOWN. NO COMMENTS OUTSIDE CODE.**

---

## ✅ Why This Prompt Works

This prompt:

- Forces **file separation** (prevents brace chaos)
- Forces **Java 17 safety**
- Prevents **LLM pseudo-syntax**
- Encodes your **business invariants**
- Produces CI-ready tests

This is the exact prompt style used by teams generating **large test suites with LLMs safely**.

---

## 🚀 Pro Tip (Very Important)

After generation:

1. Copy **ONE file at a time**
2. Compile after each file
3. Commit incrementally

Never paste a 1,000-line test blob again.

---

If you want, next I can:

- Generate a **known-good baseline test suite**
- Review LLM output before you paste it
- Create a **test checklist mapped to invariants**
- Design **CI rules that block unsafe test changes**

Just tell me.
