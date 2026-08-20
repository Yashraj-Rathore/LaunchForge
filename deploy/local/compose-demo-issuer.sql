-- Adapt only the isolated container demo to the configured Keycloak issuer.
-- The generated source seed retains localhost for host-run integration tests.
UPDATE organization_memberships
   SET oidc_issuer = :'demo_oidc_issuer'
 WHERE organization_id = '10000000-0000-0000-0000-000000000001'
   AND oidc_issuer = 'http://localhost:8081/realms/launchforge';
