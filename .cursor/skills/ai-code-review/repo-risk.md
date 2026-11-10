# This repository — risk paths and checks

Load this file during Scope and Security. Do not guess paths.

## Modules (split order for large diffs)

1. `consult-framework/consult-framework-security`
2. `consult-identity`
3. `consult-gateway`
4. `consult-framework/consult-framework-model`
5. `consult-registration-agent`
6. `consult-framework` (other submodules)
7. `consult-admin-web`
8. `deploy`
9. `.github`
10. `docs`

Root Java build: `mvn -B verify` (same as `.github/workflows/verify.yml`). Prefer module-scoped tests when the diff is narrow:

```text
mvn -B -pl consult-gateway -am test
mvn -B -pl consult-identity/consult-identity-service -am test
mvn -B -pl consult-registration-agent/consult-registration-agent-service -am test
mvn -B -pl consult-framework/consult-framework-security -am test
```

Admin web: `npm --prefix consult-admin-web run build`

## HIGH_RISK=yes if any changed file matches

- `consult-framework/consult-framework-security/**`
- `consult-identity/**`
- `consult-gateway/**` (`TokenGatewayFilter`, `RateLimitGatewayFilter`, `InFlightLimitGatewayFilter`, routing, CORS)
- `consult-framework/consult-framework-model/**` (model API keys, quotas, circuit breaker)
- `consult-admin-web/src/stores/auth.js` and login/account routes
- `consult-registration-agent/**/SafetyConfiguration.java` and safety / PII extractors
- `consult-framework/consult-framework-observability/**/SensitiveAttributeFilter.java`
- `deploy/**` (compose, `.env*`, secrets, collectors)
- `.github/workflows/**`
- SQL / native query concatenation, deserialization entrypoints, token or password literals

## Risk tier → human approvals (report only)

The change sets the tier, not who wrote it. Agent approval never counts as a person.

| Tier | Examples here | Humans |
|---|---|---|
| Low | `docs/**`, copy, comments, tests-only, additive UI behind a flag | 0 after Important/Security are clear |
| Medium | Ordinary agent/product behavior, Redis/cache, prompts, admin CRUD | 1 |
| High | Any HIGH_RISK path above; CI; deploy; auth; rate limit; secrets | 2 |

## Security checklist (required when HIGH_RISK=yes)

- New HTTP routes / gateway filters: authn present? authz on resource id (IDOR)?
- Tokens, API keys, passwords: hardcoded? logged? copied into OTel attributes?
- External input validated at the boundary (gateway/controller), not trusted in the agent loop
- Prompt / model output not executed as command or interpolated into SQL
- Rate limit / in-flight limit not weakened
- `SensitiveAttributeFilter` still drops 姓名/手机/证件/`Authorization`/模型密钥
- New dependencies: do not invent CVEs; only report if a scanner or lockfile advisory is actually run

## Failure modes this repo cares about

- Docs that lie about runtime behavior
- Gateway/config that misroutes or skips auth
- Observability that leaks PII into traces
- Agent changes that skip safety extraction or invent catalog/API signatures
