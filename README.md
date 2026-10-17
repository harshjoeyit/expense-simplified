## App

### Dev setup
1. Running the CLI - mvn spring-boot:run

## For later 
1. Transaction comment may have typos (mikk for milk), or have a different language (doodh for milk), 
   or have a regional/local meaning (tuk-tuk, auto for a uber/rapido ride). The app can invoke LLM and optinally provide merchant name to infer the meaning of comment
2. Try uploading data (csv) into notion database - expense.

## Setup
1. Copy src/main/resources/mapping-rules.example.yml to src/main/resources/mapping-rules.yml with your rules
2. Set logging level - export LOGGING_LEVEL_IO_GITHUB_HARSHJOEYIT=DEBUG

## Docs
1. Scapia CC stmt pdf to csv: https://psibi.in/federal-cc-parser/
2. Parse to return ParseResults rather than List<NormalizedTransactions>
     ParseResult
       ├── transactions
       ├── issues
       └── summary
3. 