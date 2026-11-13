# High-risk paths (registration-assistant)

Prefer focusing review on:

1. `moonligh-platform` modules consumed here (`platform-security`, `platform-gateway`, `platform-model`, `platform-redis`, `platform-observability`)
2. `consult-identity/**` (auth, patients, schedules, occupancy)
3. `consult-registration-agent/**` (triage, pay/benefit orchestration, locks)
4. `consult-gateway/**` (product routes, URI topology, CORS)
5. `consult-payment/**` and `consult-benefit/**` (channel money / ledger)

Suggested commands:

```bash
mvn -B -pl consult-gateway,consult-identity/consult-identity-service,consult-registration-agent/consult-registration-agent-service,consult-payment,consult-benefit -am test
```

Note: framework source no longer lives in this repo; review platform changes in `moonligh-platform`.
