---
name: security-review-gates
description: Focused security review checklist for auth, payment, webhook, and sensitive API paths.
---

# Security Review Gates

## Attach This Skill When

- Changes include JWT, auth filters/config, Stripe webhook, payment flow, or privileged endpoints.

## Security Gates

1. **AuthN/AuthZ Gate**
   - Verify protected endpoints require correct roles.
   - Verify public endpoints are intentionally public.

2. **Token Gate**
   - JWT parsing/validation path is strict and version-correct.
   - No insecure token shortcuts.

3. **Webhook Gate**
   - Signature verification and idempotency are enforced.
   - No transactional anti-pattern in webhook controller.

4. **Input Gate**
   - Validation exists on request DTOs.
   - No trust in client-side enforcement.

5. **Secrets/Config Gate**
   - No hardcoded secrets.
   - Sensitive config sourced from environment variables.

## Required Output

- Gate-by-gate PASS/BLOCK
- Top exploitable risks first
- Exact remediation steps
