# Conformance Status

Last verified: 2026-07-06

Command:

```bash
./conformance-test/run-conformance.sh all --verbose
```

Executed on Windows through Git Bash:

```powershell
& 'C:\Program Files\Git\usr\bin\bash.exe' -lc 'cd /e/focus-sdk && ./conformance-test/run-conformance.sh all --verbose'
```

Runner: `@modelcontextprotocol/conformance@0.1.16`

## Result

The full configured conformance command completed with exit code `0`.

| Suite | Scenario result directories | Failures |
| --- | ---: | ---: |
| Server | 30 | 0 |
| Client core | 4 | 0 |
| Client auth | 17 | 0 |
| Total | 51 | 0 |

Check-level status summary:

| Status | Count |
| --- | ---: |
| SUCCESS | 275 |
| INFO | 396 |

The expected-failure baseline was empty at the time of this run:
`conformance-test/conformance-baseline.yml`.

## Notes

- The `conformance-test/results/` directory is generated output and is not
  committed. Rerun the command above to regenerate the detailed JSON traces.
- This status reflects the scenarios executed by
  `conformance-test/run-conformance.sh` for runner `0.1.16`; it is not a
  substitute for future official SDK Working Group tier review.
