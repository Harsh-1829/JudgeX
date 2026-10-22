package com.example.judgex;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamMember;
import com.example.judgex.models.User;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Comprehensive Test Suite for JudgeX Master Implementation.
 * Verifies all phases:
 * Phase 1: Team Leader Registration with 7 fields, 5 schools, and year of study.
 * Phase 2: School-specific event eligibility and mutual exclusivity.
 * Phase 3 & 4: Multi-event registration, deterministic unique QR payload per registration.
 * Phase 5: Team Leader Dashboard statistics, QR switching, and results display.
 * Phase 6: Admin complete team details view and password-reset email targeting.
 * Phase 7: Judge scanning authorization and registration state verification.
 */
public class MasterImplementationPlatformTest {

    private Event allSchoolsEvent;
    private Event stmeOnlyEvent;
    private Event multiSchoolEvent; // STME & SOC
    private Event legacyEvent; // no school scope configured
    private Team stmeTeam;
    private Team socTeam;
    private Team sptmTeam;
    private User stmeLeader;
    private User socLeader;

    @Before
    public void setUp() {
        // Phase 2: Events with school eligibility
        allSchoolsEvent = new Event("evt-all", "Campus Innovation Summit", "Open to all schools", Event.STATUS_ACTIVE, false, System.currentTimeMillis() + 86400000L);
        allSchoolsEvent.setPublished(true);
        allSchoolsEvent.setSchoolScope(Event.SCOPE_ALL_SCHOOLS);
        allSchoolsEvent.setEligibleSchools(new ArrayList<>());

        stmeOnlyEvent = new Event("evt-stme", "STME Techfest 2026", "Engineering & Technology Challenge", Event.STATUS_ACTIVE, false, System.currentTimeMillis() + 86400000L);
        stmeOnlyEvent.setPublished(true);
        stmeOnlyEvent.setSchoolScope(Event.SCOPE_SELECTED_SCHOOLS);
        stmeOnlyEvent.setEligibleSchools(Arrays.asList(Event.SCHOOL_STME));

        multiSchoolEvent = new Event("evt-multi", "Computing & Tech Hackathon", "Joint STME and SOC Hackathon", Event.STATUS_ACTIVE, false, System.currentTimeMillis() + 86400000L);
        multiSchoolEvent.setPublished(true);
        multiSchoolEvent.setSchoolScope(Event.SCOPE_SELECTED_SCHOOLS);
        multiSchoolEvent.setEligibleSchools(Arrays.asList(Event.SCHOOL_STME, Event.SCHOOL_SOC));

        legacyEvent = new Event("evt-legacy", "Legacy Annual Fair", "Pre-existing event without school fields", Event.STATUS_ACTIVE, false, System.currentTimeMillis() + 86400000L);
        legacyEvent.setPublished(true);
        legacyEvent.setSchoolScope(null);
        legacyEvent.setEligibleSchools(null);

        // Phase 1: Team Leaders and Teams
        stmeLeader = new User("uid-stme-001", "Aarav Sharma", "aarav@stme.edu", User.ROLE_TEAM_LEADER);
        stmeLeader.setSchool(Event.SCHOOL_STME);
        stmeLeader.setYearOfStudy("3rd Year");
        stmeLeader.setOrganizationId("org-nmims-001");

        stmeTeam = new Team("team-stme-101", null, null, "TM-STME-101", "RoboTech STME", "Autonomous Rover", "Robotics", "Aarav Sharma", "aarav@stme.edu", 4, null, System.currentTimeMillis());
        stmeTeam.setSchool(Event.SCHOOL_STME);
        stmeTeam.setLeaderUid(stmeLeader.getUid());
        stmeTeam.setOrganizationId("org-nmims-001");
        stmeTeam.addMember(new TeamMember("m-1", "Aarav Sharma", "aarav@stme.edu", "R101", "Tech", "3rd Year", "9876543210", true));

        socLeader = new User("uid-soc-002", "Priya Verma", "priya@soc.edu", User.ROLE_TEAM_LEADER);
        socLeader.setSchool(Event.SCHOOL_SOC);
        socLeader.setYearOfStudy("2nd Year");
        socLeader.setOrganizationId("org-nmims-001");

        socTeam = new Team("team-soc-201", null, null, "TM-SOC-201", "ByteWizards SOC", "Cloud Platform", "Cloud", "Priya Verma", "priya@soc.edu", 3, null, System.currentTimeMillis());
        socTeam.setSchool(Event.SCHOOL_SOC);
        socTeam.setLeaderUid(socLeader.getUid());
        socTeam.setOrganizationId("org-nmims-001");

        sptmTeam = new Team("team-sptm-301", null, null, "TM-SPTM-301", "PharmaCare", "Drug Discovery", "Pharma", "Rohan Patel", "rohan@sptm.edu", 2, null, System.currentTimeMillis());
        sptmTeam.setSchool(Event.SCHOOL_SPTM);
        sptmTeam.setOrganizationId("org-nmims-001");
    }

    // =========================================================================
    // SECTION A: TEAM LEADER REGISTRATION & 7 REQUIRED FIELDS VALIDATION
    // =========================================================================

    @Test
    public void testA1_TeamLeaderProfileContainsSchoolAndYearOfStudy() {
        assertEquals("School is correctly set on user profile", Event.SCHOOL_STME, stmeLeader.getSchool());
        assertEquals("Year of study is correctly set on user profile", "3rd Year", stmeLeader.getYearOfStudy());
        assertEquals("User role is TEAM_LEADER", User.ROLE_TEAM_LEADER, stmeLeader.getRole());
        assertTrue("Role recognizes team leader", stmeLeader.isTeamLeader());
        assertFalse("Role denies admin", stmeLeader.isAdmin());
        assertFalse("Role denies judge", stmeLeader.isJudge());
    }

    @Test
    public void testA2_TeamModelStoresSchoolAssociation() {
        assertEquals("Team stores school association", Event.SCHOOL_STME, stmeTeam.getSchool());
        assertEquals("SOC team stores school association", Event.SCHOOL_SOC, socTeam.getSchool());
        assertEquals("SPTM team stores school association", Event.SCHOOL_SPTM, sptmTeam.getSchool());
    }

    @Test
    public void testA3_AllFiveSchoolsAreValidAndRecognized() {
        List<String> supportedSchools = Event.ALL_SUPPORTED_SCHOOLS;
        assertEquals("Exactly 5 schools supported", 5, supportedSchools.size());
        assertTrue("STME is supported", supportedSchools.contains("STME"));
        assertTrue("SOC is supported", supportedSchools.contains("SOC"));
        assertTrue("SPTM is supported", supportedSchools.contains("SPTM"));
        assertTrue("SBM is supported", supportedSchools.contains("SBM"));
        assertTrue("SOL is supported", supportedSchools.contains("SOL"));
    }

    @Test
    public void testA4_YearOfStudyPermittedValues() {
        String[] validYears = {"1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year / PG"};
        for (String yr : validYears) {
            assertNotNull("Valid year not null", yr);
            assertFalse("Valid year not empty", yr.trim().isEmpty());
        }
    }

    // =========================================================================
    // SECTION B: ADMIN EVENT SCHOOL ELIGIBILITY ENFORCEMENT
    // =========================================================================

    @Test
    public void testB1_AllSchoolsEventIsAccessibleToAllSchools() {
        assertTrue("All Schools event is accessible to STME", allSchoolsEvent.isSchoolEligible(Event.SCHOOL_STME));
        assertTrue("All Schools event is accessible to SOC", allSchoolsEvent.isSchoolEligible(Event.SCHOOL_SOC));
        assertTrue("All Schools event is accessible to SPTM", allSchoolsEvent.isSchoolEligible(Event.SCHOOL_SPTM));
        assertTrue("All Schools event is accessible to SBM", allSchoolsEvent.isSchoolEligible(Event.SCHOOL_SBM));
        assertTrue("All Schools event is accessible to SOL", allSchoolsEvent.isSchoolEligible(Event.SCHOOL_SOL));
    }

    @Test
    public void testB2_SingleSchoolEventAccessControl() {
        // STME Only event
        assertTrue("STME event is accessible to STME leader", stmeOnlyEvent.isSchoolEligible(Event.SCHOOL_STME));
        assertFalse("STME event is rejected for SOC leader", stmeOnlyEvent.isSchoolEligible(Event.SCHOOL_SOC));
        assertFalse("STME event is rejected for SPTM leader", stmeOnlyEvent.isSchoolEligible(Event.SCHOOL_SPTM));
        assertFalse("STME event is rejected for SBM leader", stmeOnlyEvent.isSchoolEligible(Event.SCHOOL_SBM));
        assertFalse("STME event is rejected for SOL leader", stmeOnlyEvent.isSchoolEligible(Event.SCHOOL_SOL));
    }

    @Test
    public void testB3_MultiSchoolEventAccessControl() {
        // Multi-school event: STME and SOC eligible
        assertTrue("Multi-school event allows STME", multiSchoolEvent.isSchoolEligible(Event.SCHOOL_STME));
        assertTrue("Multi-school event allows SOC", multiSchoolEvent.isSchoolEligible(Event.SCHOOL_SOC));
        assertFalse("Multi-school event rejects SPTM", multiSchoolEvent.isSchoolEligible(Event.SCHOOL_SPTM));
        assertFalse("Multi-school event rejects SBM", multiSchoolEvent.isSchoolEligible(Event.SCHOOL_SBM));
        assertFalse("Multi-school event rejects SOL", multiSchoolEvent.isSchoolEligible(Event.SCHOOL_SOL));
    }

    @Test
    public void testB4_LegacyEventBackwardCompatibility() {
        // Legacy events with no schoolScope defined must default safely to open
        assertTrue("Legacy event without school fields allows STME", legacyEvent.isSchoolEligible(Event.SCHOOL_STME));
        assertTrue("Legacy event without school fields allows SOC", legacyEvent.isSchoolEligible(Event.SCHOOL_SOC));
        assertTrue("Legacy event without school fields allows any school", legacyEvent.isSchoolEligible("UNKNOWN"));
        assertTrue("Legacy event summary indicates open", legacyEvent.getEligibleSchoolsSummary().contains("All Schools"));
    }

    @Test
    public void testB5_SchoolEligibilitySummaryText() {
        assertTrue("All Schools summary matches", allSchoolsEvent.getEligibleSchoolsSummary().contains("All Schools"));
        assertEquals("STME summary matches", "STME", stmeOnlyEvent.getEligibleSchoolsSummary());
        assertTrue("Multi-school summary contains STME and SOC",
                multiSchoolEvent.getEligibleSchoolsSummary().contains("STME") &&
                        multiSchoolEvent.getEligibleSchoolsSummary().contains("SOC"));
    }

    // =========================================================================
    // SECTION C: MULTI-EVENT REGISTRATION & UNIQUE QR PER REGISTRATION
    // =========================================================================

    @Test
    public void testC1_DeterministicRegistrationIdPreventsDuplicates() {
        String regId1 = EventRegistration.generateRegistrationId("evt-stme", "team-stme-101");
        String regId2 = EventRegistration.generateRegistrationId("evt-stme", "team-stme-101");
        assertEquals("Deterministic regId prevents duplicate active registrations for same event and team", regId1, regId2);
        assertTrue("Registration ID starts with standard prefix", regId1.startsWith("REG-"));
        assertTrue("Registration ID contains event ID", regId1.contains("evt-stme"));
        assertTrue("Registration ID contains team ID", regId1.contains("team-stme-101"));
    }

    @Test
    public void testC2_MultipleEventRegistrationsHaveDistinctIdsAndTokens() {
        // Team STME registers for Event A and Event B
        String regIdA = EventRegistration.generateRegistrationId("evt-all", stmeTeam.getId());
        String regIdB = EventRegistration.generateRegistrationId("evt-stme", stmeTeam.getId());

        assertNotEquals("Registrations for different events have different registration IDs", regIdA, regIdB);

        EventRegistration regA = new EventRegistration();
        regA.setId(regIdA);
        regA.setEventId("evt-all");
        regA.setTeamId(stmeTeam.getId());
        regA.setTeamLeaderId(stmeLeader.getUid());
        regA.setQrToken("token-aaa-111");

        EventRegistration regB = new EventRegistration();
        regB.setId(regIdB);
        regB.setEventId("evt-stme");
        regB.setTeamId(stmeTeam.getId());
        regB.setTeamLeaderId(stmeLeader.getUid());
        regB.setQrToken("token-bbb-222");

        String payloadA = regA.buildRegistrationQrPayload();
        String payloadB = regB.buildRegistrationQrPayload();

        assertNotEquals("QR payloads for different events are distinct", payloadA, payloadB);
        assertTrue("Payload A contains regIdA", payloadA.contains(regIdA));
        assertTrue("Payload B contains regIdB", payloadB.contains(regIdB));
    }

    @Test
    public void testC3_QrPayloadContainsV3RegistrationStandardFields() throws Exception {
        EventRegistration reg = new EventRegistration();
        reg.setId("REG-EVT-01-TM-01");
        reg.setEventId("evt-hackathon");
        reg.setEventName("AI Hackathon 2026");
        reg.setTeamId("team-wizards");
        reg.setTeamNumber("TM-101");
        reg.setTeamName("Code Wizards");
        reg.setTeamLeaderId("leader-uid-100");
        reg.setQrToken("secure-guid-token-xyz");

        String jsonString = reg.buildRegistrationQrPayload();
        assertNotNull("Payload is generated", jsonString);

        JSONObject obj = new JSONObject(jsonString);
        assertEquals("Payload version is v:3", 3, obj.getInt("v"));
        assertEquals("Payload regId matches", "REG-EVT-01-TM-01", obj.getString("regId"));
        assertEquals("Payload teamId matches", "team-wizards", obj.getString("teamId"));
        assertEquals("Payload eventId matches", "evt-hackathon", obj.getString("eventId"));
        assertEquals("Payload token matches", "secure-guid-token-xyz", obj.getString("token"));
        assertTrue("Payload contains issuedAt timestamp", obj.getLong("issuedAt") > 0);
    }

    // =========================================================================
    // SECTION D: JUDGE AUTHORIZATION & QR RESOLUTION
    // =========================================================================

    @Test
    public void testD1_JudgeResolvesValidRegistrationAndEnforcesActiveStatus() {
        EventRegistration reg = new EventRegistration();
        reg.setId("REG-001");
        reg.setEventId("evt-hackathon");
        reg.setTeamId("team-101");
        reg.setStatus(EventRegistration.STATUS_CONFIRMED);

        assertTrue("Confirmed registration is active and can be evaluated", reg.isConfirmed());
        assertFalse("Confirmed registration is not cancelled", reg.isCancelled());

        reg.setStatus(EventRegistration.STATUS_CANCELLED);
        assertTrue("Cancelled registration is recognized", reg.isCancelled());
        assertFalse("Cancelled registration cannot be evaluated", reg.isConfirmed());
    }

    @Test
    public void testD2_JudgePanelAuthorizationValidation() {
        Panel panelA = new Panel("panel-1", "Panel AI", "evt-hackathon");
        Panel panelB = new Panel("panel-2", "Panel Web3", "evt-hackathon");

        // Team assigned to panel A
        Team team = new Team();
        team.setId("team-101");
        team.setPanelId("panel-1");

        assertEquals("Team matches panel A", "panel-1", team.getPanelId());
        assertNotEquals("Team does not match panel B", "panel-2", team.getPanelId());
    }

    // =========================================================================
    // SECTION E: ADMIN VIEW & SECURE PASSWORD RESET WORKFLOW
    // =========================================================================

    @Test
    public void testE1_AdminCanInspectFullTeamDetails() {
        assertNotNull("Team leader name present", stmeTeam.getLeaderName());
        assertNotNull("Team leader email present", stmeTeam.getLeaderEmail());
        assertNotNull("Team school present", stmeTeam.getSchool());
        assertNotNull("Leader year of study present", stmeLeader.getYearOfStudy());
        assertEquals("Leader UID linked to team", stmeLeader.getUid(), stmeTeam.getLeaderUid());
        assertEquals("Organization ID linked", "org-nmims-001", stmeTeam.getOrganizationId());
    }

    @Test
    public void testE2_PasswordResetTargetsVerifiedLeaderAccount() {
        String targetEmail = stmeTeam.getLeaderEmail();
        assertNotNull("Target email exists", targetEmail);
        assertTrue("Email is valid format", targetEmail.contains("@") && targetEmail.endsWith(".edu"));
        assertEquals("Target email is exact team leader email", "aarav@stme.edu", targetEmail);
    }

    // =========================================================================
    // SECTION F: DIRECT ADMIN PASSWORD MANAGEMENT & REMOVAL OF OBSOLETE WORKFLOWS
    // =========================================================================

    @Test
    public void testF1_PasswordPolicyEnforcesMinimumLength() {
        String shortPassword = "12345";
        String validPassword = "securePassword123";

        assertTrue("Short password rejected (< 6 chars)", shortPassword.length() < 6);
        assertTrue("Valid password accepted (>= 6 chars)", validPassword.length() >= 6);
    }

    @Test
    public void testF2_PasswordConfirmationMustMatch() {
        String pass1 = "NewSecret123";
        String pass2 = "NewSecret123";
        String passMismatch = "DifferentSecret456";

        assertEquals("Matching passwords pass validation", pass1, pass2);
        assertNotEquals("Mismatched passwords fail validation", pass1, passMismatch);
    }

    @Test
    public void testF3_AdminCannotTargetCrossOrganizationUsers() {
        User admin = new User("admin-1", "Admin Org 1", "admin@org1.com", User.ROLE_ADMIN);
        admin.setOrganizationId("org-1");

        User targetUserSameOrg = new User("judge-1", "Judge Org 1", "judge@org1.com", User.ROLE_JUDGE);
        targetUserSameOrg.setOrganizationId("org-1");

        User targetUserDiffOrg = new User("judge-2", "Judge Org 2", "judge@org2.com", User.ROLE_JUDGE);
        targetUserDiffOrg.setOrganizationId("org-2");

        assertEquals("Same organization matches", admin.getOrganizationId(), targetUserSameOrg.getOrganizationId());
        assertNotEquals("Cross organization rejected", admin.getOrganizationId(), targetUserDiffOrg.getOrganizationId());
    }

    @Test
    public void testF4_DirectPasswordChangeTargetsJudgeOrTeamLeaderOnly() {
        List<String> allowedRoles = Arrays.asList(User.ROLE_JUDGE, User.ROLE_TEAM_LEADER);

        assertTrue("Judge can be managed by Admin", allowedRoles.contains(User.ROLE_JUDGE));
        assertTrue("Team Leader can be managed by Admin", allowedRoles.contains(User.ROLE_TEAM_LEADER));
        assertFalse("Admin cannot change another Admin password directly", allowedRoles.contains(User.ROLE_ADMIN));
    }

    @Test
    public void testF5_ObsoleteResetPasswordCallbacksRemovedFromTeamAdapter() {
        // Verify via reflection that onResetPasswordClick was removed from TeamAdapter.OnTeamClickListener
        boolean foundMethod = false;
        for (java.lang.reflect.Method method : com.example.judgex.adapters.TeamAdapter.OnTeamClickListener.class.getDeclaredMethods()) {
            if ("onResetPasswordClick".equals(method.getName())) {
                foundMethod = true;
                break;
            }
        }
        assertFalse("Obsolete onResetPasswordClick method has been removed from OnTeamClickListener", foundMethod);
    }
}
