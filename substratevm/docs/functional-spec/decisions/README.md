# Functional Decisions

This directory contains product behavior decisions and tradeoffs shared by Native Image functional
specifications, together with decisions for individual specifications.

- [§FD-001-hotspot-compatible-semantics-by-default](hotspot-compatible-semantics-by-default.md#fd-001-hotspot-compatible-semantics-by-default-match-hotspot-without-additional-flags)
  — Match HotSpot Without Additional Flags
- [§FD-002-reachability-independent-runtime-semantics](reachability-independent-runtime-semantics.md#fd-002-reachability-independent-runtime-semantics-keep-run-time-semantics-independent-of-reachability)
  — Keep Run-Time Semantics Independent of Reachability
- [§FD-003-provider-enumeration-reports-usable-providers](provider-enumeration-reports-usable-providers.md#fd-003-provider-enumeration-reports-usable-providers-enumerate-only-usable-security-providers)
  — Enumerate Only Usable Security Providers

## Structure

Give each decision a title that states the chosen behavior.
Record its status and decision date before the first section, and link any decision it supersedes.
The status is Proposed, Accepted, or Superseded.
For a historical decision, mark an unrecorded date or participant as unknown rather than inferring
approval from commit authorship.

The Native Image Grund schema requires these sections in this order:

1. **Reason:** Explain the user problem, relevant constraints and evidence, and why the chosen
   behavior best satisfies them.
   Combine context and rationale here, and state the decisive tradeoff explicitly.
2. **Decision:** State the observable behavior, its scope, and exceptions.
   Link to the functional-specification points that define the full contract.
3. **Alternatives Considered:** For each serious alternative, describe its observable behavior,
   benefit, and the reason it was not chosen.
   Include keeping the current behavior when it is viable.
   If no viable alternative was identified, explain why.
4. **Consequences:** Describe benefits, accepted drawbacks, compatibility effects, and obligations.
   Identify assumptions whose failure would justify revisiting the decision when applicable.
5. **Participants:** Distinguish decision makers from people consulted.
   Record the author when useful and unresolved objections when known.
   An unrecorded objection history is not evidence that everyone agreed.

An optional **Evidence and Discussion** section follows Participants.
Link to relevant issues, experiments, user reports, and discussion, while keeping the essential
reasoning in Reason so that external links are supplementary.
Status, date, and participant roles are authoring conventions; Grund enforces the section titles,
presence, and order.

Use stable named section addresses, including for subsections such as `alternatives.feature-flags`.
Keep examples under the section they explain, such as `decision.example`.
Scale each section to the decision's complexity.
The functional specification owns the complete current behavior contract, and architecture records
explain implementation choices.
When an accepted choice changes, add a successor decision and mark the earlier record Superseded,
with a link to its replacement, preserving the earlier reasoning.

## Template

```markdown
# FD-NNN-slug: State the Chosen Behavior

- Status: Proposed | Accepted | Superseded
- Decision date: YYYY-MM-DD, or Unknown (not recorded).
- Supersedes: <linked decision, if applicable>

## reason: Reason

Explain the user problem, constraints, evidence, and why this choice wins.
Prioritize <benefit> over <competing benefit> because <reason>.

## decision: Decision

State the observable behavior, scope, and exceptions.
Link to the functional-specification points that define the full contract.

## alternatives: Alternatives Considered

### alternatives.option-name: Name the Alternative

- Behavior: <what users would observe>
- Benefit: <why it is a credible option>
- Reason not chosen: <the decisive constraint or tradeoff>

## consequences: Consequences

Describe benefits, accepted drawbacks, compatibility effects, and obligations.
State any conditions that would justify revisiting the decision.

## participants: Participants

- Decision makers: <names, or Unknown (not recorded)>
- Consulted: <names and relevant roles, or Unknown (not recorded)>
- Author: <name, if useful>
- Unresolved objections: <position and reasoning, if known; otherwise Not recorded>

## evidence: Evidence and Discussion

Optional: link supporting issues, experiments, user reports, and discussion.
```
