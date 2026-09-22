# LaunchForge Implementation Checklist

Use `PROJECT_STATUS.md` as the status source of truth. This checklist is a quick navigation aid.

## Before coding

- [x] Run Implementation Prompt 00
- [x] Review proposed exact versions
- [x] Resolve documentation contradictions found by Prompt 00
- [x] Approve M0 only

## M0 Foundation

- [x] LF-0001
- [x] LF-0002
- [x] LF-0003
- [x] LF-0004
- [x] LF-0005

## M1 Tenancy and identity

- [x] LF-0101
- [x] LF-0102
- [x] LF-0103
- [x] LF-0104
- [x] LF-0105

## M2 Control plane

- [x] LF-0201
- [x] LF-0202
- [x] LF-0203
- [x] LF-0204
- [x] LF-0205
- [x] LF-0206
- [x] LF-0207

## M3 Java evaluator/SDK

- [x] LF-0301
- [x] LF-0302
- [x] LF-0303
- [x] LF-0304
- [x] LF-0305
- [x] LF-0306
- [x] LF-0307

**Resume checkpoint A**

## M4 Config Edge/SSE

- [x] LF-0401
- [x] LF-0402
- [x] LF-0403
- [x] LF-0404
- [x] LF-0405
- [x] LF-0406

## M5 JavaScript/React SDKs

- [x] LF-0501
- [x] LF-0502
- [x] LF-0503
- [x] LF-0504
- [x] LF-0505

## M6 Admin console

- [x] LF-0601
- [x] LF-0602
- [x] LF-0603
- [x] LF-0604
- [x] LF-0605
- [x] LF-0606

## M7 Kafka/Redis

- [x] LF-0701
- [x] LF-0702
- [x] LF-0703
- [x] LF-0704
- [x] LF-0705
- [x] LF-0706

**Resume/interview checkpoint B**

## M8 Optional analytics

- [x] LF-0801
- [x] LF-0802
- [x] LF-0803
- [x] LF-0804
- [x] LF-0805

## M9 Security

- [x] LF-0901
- [x] LF-0902
- [x] LF-0903
- [x] LF-0904
- [x] LF-0905
- [x] LF-0906

## M10 Reliability/performance

- [x] LF-1001
- [x] LF-1002
- [x] LF-1003
- [x] LF-1004
- [x] LF-1005
- [x] LF-1006

**Flagship portfolio checkpoint C**

## M11 Containers/Helm

- [x] LF-1101
- [x] LF-1102
- [x] LF-1103
- [x] LF-1104

## M12 CI/CD

- [x] LF-1201
- [x] LF-1202
- [x] LF-1203
- [x] LF-1204
- [x] LF-1205

## M13 Demo/pilot

- [x] LF-1301
- [x] LF-1302
- [x] LF-1303
- [x] LF-1304
- [x] LF-1305

## Final review

- [x] Run Prompt 15
- [x] Record staged finding IDs in the final review
- [x] Correct P15-01 authenticated Redis materialization provenance and ACL isolation
- [x] Correct P15-02 scheduler isolation
- [x] Correct P15-03 Kafka and Redis transport security
- [ ] Correct P15-04 hosted change and deployment controls — deferred to final hosted-release review
- [x] Correct P15-05 canonical JSON variation byte sizing
- [x] Correct P15-06 canonical snapshot size ceiling
- [x] Correct P15-07 browser analytics request timeout
- [x] Correct P15-08 compound tenant and key-lineage integrity
- [x] Correct P15-09 organization capability claim
- [x] Correct P15-10 poison-row reconciliation isolation
- [x] Correct P15-11 Event Worker production database defaults
- [x] Correct P15-12 stale package manifest
- [x] Correct P15-13 forward-JDK Mockito instrumentation and runtime patch alignment
- [x] Complete all approved code/documentation corrections; P15-04 remains explicitly deferred
- [ ] Clean-clone demo validation
- [ ] Verify every resume claim
