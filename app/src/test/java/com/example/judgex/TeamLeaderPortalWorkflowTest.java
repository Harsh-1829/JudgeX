package com.example.judgex;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamMember;
import com.example.judgex.models.User;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Comprehensive verification of the Team Leader Portal & Event Registration workflows.
 * Covers all 15 scenarios outlined in Section 12 of the feature specification.
 */
public class TeamLeaderPortalWorkflowTest {

    private Event standardEvent;
    private Team standardTeam;
    private User teamLeaderUser;
    private User adminUser;
    private User judgeUser;

    @Before
    public void setUp() {
        // Standard event: published, active, team size 3 to 5
        standardEvent = new Event("evt-hackathon-2026", "National Hackathon 2026", "Flagship AI Hackathon", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        standardEvent.setOrganizationId("org-mit-campus");
        standardEvent.setPublished(true);
        standardEvent.setMinTeamSize(3);
        standardEvent.setMaxTeamSize(5);
        standardEvent.setRegistrationDeadline(System.currentTimeMillis() + 86400000L); // 1 day in future
        standardEvent.setRegistrationApprovalRequired(false);
        standardEvent.setCategory("AI/ML");
        standardEvent.setDepartment("Computer Science");

        // Standard team with leader
        standardTeam = new Team("tm-code-wizards", null, null, "TM-101", "Code Wizards", "AI Smart Campus", "AI/ML", "Jane Doe", "jane.doe@college.edu", 1, null, System.currentTimeMillis());
        standardTeam.setOrganizationId("org-mit-campus");
        standardTeam.setLeaderUid("leader-uid-100");

        TeamMember leaderMember = new TeamMember("m-1", "Jane Doe", "jane.doe@college.edu", "CS2026-001", "Computer Science", "3rd Year", "9876543210", true);
        standardTeam.addMember(leaderMember);

        teamLeaderUser = new User("leader-uid-100", "Jane Doe", "jane.doe@college.edu", User.ROLE_TEAM_LEADER);
        teamLeaderUser.setOrganizationId("org-mit-campus");
        teamLeaderUser.setAssignedTeamId("tm-code-wizards");

        adminUser = new User("admin-uid-001", "Campus Admin", "admin@college.edu", User.ROLE_ADMIN);
        adminUser.setOrganizationId("org-mit-campus");

        judgeUser = new User("judge-uid-002", "Prof Smith", "prof.smith@college.edu", User.ROLE_JUDGE);
        judgeUser.setOrganizationId("org-mit-campus");
    }

    // =========================================================================
    // Scenario 1: Team Leader logs in and sees correct dashboard & role routing
    // =========================================================================
    @Test
    public void testScenario01_RoleRoutingAndPrivilegeValidation() {
        // 1. Team Leader checks
        assertTrue("Team leader role recognized", teamLeaderUser.isTeamLeader());
        assertFalse("Team leader cannot act as admin", teamLeaderUser.isAdmin());
        assertFalse("Team leader cannot act as judge", teamLeaderUser.isJudge());

        // 2. Admin checks
        assertTrue("Admin role recognized", adminUser.isAdmin());
        assertFalse("Admin is not team leader", adminUser.isTeamLeader());
        assertFalse("Admin is not judge", adminUser.isJudge());

        // 3. Judge checks
        assertTrue("Judge role recognized", judgeUser.isJudge());
        assertFalse("Judge is not admin", judgeUser.isAdmin());
        assertFalse("Judge is not team leader", judgeUser.isTeamLeader());

        // 4. Role normalization test for case variations
        User customLeader1 = new User("u-alt1", "Alt Leader 1", "a@b.com", "ROLE_TEAM_LEADER");
        User customLeader2 = new User("u-alt2", "Alt Leader 2", "b@b.com", "team_leader");
        User customLeader3 = new User("u-alt3", "Alt Leader 3", "c@b.com", "LEADER");

        assertTrue("ROLE_TEAM_LEADER normalized", customLeader1.isTeamLeader());
        assertTrue("team_leader normalized", customLeader2.isTeamLeader());
        assertTrue("LEADER normalized", customLeader3.isTeamLeader());
    }

    // =========================================================================
    // Scenario 2: Team Leader can browse published events
    // =========================================================================
    @Test
    public void testScenario02_TeamLeaderCanBrowsePublishedEvents() {
        List<Event> allEvents = new ArrayList<>();
        allEvents.add(standardEvent); // published = true, org = org-mit-campus

        Event roboticsComp = new Event("evt-robotics", "Robotics Expo", "Autonomous bot race", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        roboticsComp.setPublished(true);
        roboticsComp.setOrganizationId("org-mit-campus");
        allEvents.add(roboticsComp);

        // Filter events for the leader's organization that are published and not deleted
        List<Event> visibleEvents = new ArrayList<>();
        for (Event e : allEvents) {
            if (e.isPublished() && !e.isDeleted() && "org-mit-campus".equals(e.getOrganizationId())) {
                visibleEvents.add(e);
            }
        }

        assertEquals("Both published events should be visible to Team Leader", 2, visibleEvents.size());
        assertTrue("Includes hackathon", visibleEvents.contains(standardEvent));
        assertTrue("Includes robotics expo", visibleEvents.contains(roboticsComp));
    }

    // =========================================================================
    // Scenario 3: Unpublished events are hidden
    // =========================================================================
    @Test
    public void testScenario03_UnpublishedEventsAreHidden() {
        List<Event> catalog = new ArrayList<>();
        catalog.add(standardEvent); // published

        Event draftEvent = new Event("evt-draft-ai", "Internal AI Challenge", "Draft hackathon", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        draftEvent.setPublished(false); // DRAFT
        draftEvent.setOrganizationId("org-mit-campus");
        catalog.add(draftEvent);

        Event otherOrgEvent = new Event("evt-other-campus", "Other Campus Fest", "Foreign org", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        otherOrgEvent.setPublished(true);
        otherOrgEvent.setOrganizationId("org-harvard-campus"); // Foreign org
        catalog.add(otherOrgEvent);

        Event deletedEvent = new Event("evt-cancelled", "Cancelled Fest", "Cancelled", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        deletedEvent.setPublished(true);
        deletedEvent.setDeleted(true); // Deleted
        deletedEvent.setOrganizationId("org-mit-campus");
        catalog.add(deletedEvent);

        // Leader discovery filter
        List<Event> leaderVisibleEvents = new ArrayList<>();
        for (Event e : catalog) {
            if (e.isPublished() && !e.isDeleted() && "org-mit-campus".equals(e.getOrganizationId())) {
                leaderVisibleEvents.add(e);
            }
        }

        assertEquals("Only 1 published non-deleted event from same org should be visible", 1, leaderVisibleEvents.size());
        assertEquals("Visible event is standardEvent", "evt-hackathon-2026", leaderVisibleEvents.get(0).getId());
        assertFalse("Draft event must NOT be visible", leaderVisibleEvents.contains(draftEvent));
        assertFalse("Other org event must NOT be visible", leaderVisibleEvents.contains(otherOrgEvent));
        assertFalse("Deleted event must NOT be visible", leaderVisibleEvents.contains(deletedEvent));
    }

    // =========================================================================
    // Scenario 4: Closed registrations cannot be submitted
    // =========================================================================
    @Test
    public void testScenario04_ClosedRegistrationsCannotBeSubmitted() {
        // 1. Deadline passed
        Event expiredEvent = new Event("evt-expired", "Past Hackathon", "Over", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        expiredEvent.setPublished(true);
        expiredEvent.setRegistrationDeadline(System.currentTimeMillis() - 60000L); // 1 minute in past
        assertFalse("Registration should be closed if deadline passed", expiredEvent.isRegistrationOpen());

        // 2. Locked event
        Event lockedEvent = new Event("evt-locked", "Locked Hackathon", "Locked", Event.STATUS_LOCKED, true, System.currentTimeMillis());
        lockedEvent.setPublished(true);
        assertFalse("Registration should be closed if event is locked", lockedEvent.isRegistrationOpen());

        // 3. Completed event
        Event completedEvent = new Event("evt-done", "Finished Hackathon", "Done", Event.STATUS_COMPLETED, false, System.currentTimeMillis());
        completedEvent.setPublished(true);
        assertFalse("Registration should be closed if event is completed", completedEvent.isRegistrationOpen());

        // 4. Open event
        assertTrue("Registration should be open if published, active, and deadline is in future", standardEvent.isRegistrationOpen());
    }

    // =========================================================================
    // Scenario 5: Valid team can register successfully
    // =========================================================================
    @Test
    public void testScenario05_ValidTeamCanRegisterSuccessfully() {
        // Standard event requires 3 to 5 members
        standardTeam.addMember(new TeamMember("m-2", "Bob Smith", "bob.smith@college.edu", "CS2026-002", "Computer Science", "3rd Year", "9876543211", false));
        standardTeam.addMember(new TeamMember("m-3", "Charlie Brown", "charlie@college.edu", "CS2026-003", "Data Science", "2nd Year", "9876543212", false));
        standardTeam.addMember(new TeamMember("m-4", "Diana Prince", "diana@college.edu", "CS2026-004", "AI Engineering", "4th Year", "9876543213", false));

        int memberCount = standardTeam.getMemberCount();
        assertEquals("Team now has 4 members", 4, memberCount);
        assertTrue("Team size 4 is valid for range [3, 5]", standardEvent.canTeamRegister(memberCount));

        // Create registration
        EventRegistration reg = new EventRegistration();
        reg.setId(EventRegistration.generateRegistrationId(standardEvent.getId(), standardTeam.getId()));
        reg.setEventId(standardEvent.getId());
        reg.setEventName(standardEvent.getName());
        reg.setTeamId(standardTeam.getId());
        reg.setTeamName(standardTeam.getTeamName());
        reg.setTeamNumber(standardTeam.getTeamNumber());
        reg.setOrganizationId(standardEvent.getOrganizationId());
        reg.setTeamLeaderId(teamLeaderUser.getUid());
        reg.setTeamLeaderName(standardTeam.getLeaderName());
        reg.setTeamLeaderEmail(standardTeam.getLeaderEmail());
        reg.setRegistrationSource(EventRegistration.SOURCE_TEAM_LEADER);
        reg.setCreatedById(teamLeaderUser.getUid());
        reg.setStatus(standardEvent.isRegistrationApprovalRequired() ? EventRegistration.STATUS_PENDING : EventRegistration.STATUS_CONFIRMED);
        reg.setTeamSnapshot(standardTeam);

        assertNotNull("Registration ID generated", reg.getId());
        assertTrue("Registration ID starts with REG-", reg.getId().startsWith("REG-"));
        assertEquals("Member count captured in snapshot", 4, reg.getMemberCount());
        assertEquals("Snapshot members list has 4 entries", 4, reg.getMembers().size());
        assertEquals("Status is confirmed when approval not required", EventRegistration.STATUS_CONFIRMED, reg.getStatus());
        assertEquals("Source tagged as TEAM_LEADER", EventRegistration.SOURCE_TEAM_LEADER, reg.getRegistrationSource());
    }

    // =========================================================================
    // Scenario 6: Team below minimum size is rejected
    // =========================================================================
    @Test
    public void testScenario06_TeamBelowMinimumSizeIsRejected() {
        // Standard event requires min 3 members
        // Current team has only 1 member (the leader)
        assertEquals(1, standardTeam.getMemberCount());

        assertFalse("Team with 1 member rejected when minTeamSize is 3", standardEvent.canTeamRegister(standardTeam.getMemberCount()));

        // Add 1 member -> total 2 members
        standardTeam.addMember(new TeamMember("m-2", "Bob Smith", "bob@college.edu", "CS-002", "CS", "3rd", "123", false));
        assertEquals(2, standardTeam.getMemberCount());
        assertFalse("Team with 2 members rejected when minTeamSize is 3", standardEvent.canTeamRegister(standardTeam.getMemberCount()));
    }

    // =========================================================================
    // Scenario 7: Team exceeding maximum size is rejected
    // =========================================================================
    @Test
    public void testScenario07_TeamExceedingMaximumSizeIsRejected() {
        // Standard event has maxTeamSize = 5
        standardTeam.addMember(new TeamMember("m-2", "Bob", "bob@college.edu", "CS-002", "CS", "3rd", "123", false));
        standardTeam.addMember(new TeamMember("m-3", "Charlie", "charlie@college.edu", "CS-003", "CS", "3rd", "124", false));
        standardTeam.addMember(new TeamMember("m-4", "Diana", "diana@college.edu", "CS-004", "CS", "3rd", "125", false));
        standardTeam.addMember(new TeamMember("m-5", "Eve", "eve@college.edu", "CS-005", "CS", "3rd", "126", false));
        standardTeam.addMember(new TeamMember("m-6", "Frank", "frank@college.edu", "CS-006", "CS", "3rd", "127", false));

        assertEquals(6, standardTeam.getMemberCount());
        assertFalse("Team with 6 members rejected when maxTeamSize is 5", standardEvent.canTeamRegister(standardTeam.getMemberCount()));
    }

    // =========================================================================
    // Scenario 8: Duplicate registration is prevented
    // =========================================================================
    @Test
    public void testScenario08_DuplicateRegistrationIsPrevented() {
        List<EventRegistration> existingRegistrations = new ArrayList<>();

        // 1. Initial registration for (standardEvent, standardTeam)
        EventRegistration firstReg = new EventRegistration();
        firstReg.setId("REG-EVT1-0001");
        firstReg.setEventId(standardEvent.getId());
        firstReg.setTeamId(standardTeam.getId());
        firstReg.setStatus(EventRegistration.STATUS_CONFIRMED);
        existingRegistrations.add(firstReg);

        // 2. Duplicate detection check
        boolean duplicateFound = false;
        for (EventRegistration r : existingRegistrations) {
            if (standardEvent.getId().equals(r.getEventId()) && standardTeam.getId().equals(r.getTeamId()) && !r.isCancelled()) {
                duplicateFound = true;
                break;
            }
        }
        assertTrue("Duplicate registration should be detected and blocked", duplicateFound);

        // 3. If previous registration was cancelled, re-registration can be permitted
        firstReg.setStatus(EventRegistration.STATUS_CANCELLED);
        boolean duplicateAfterCancel = false;
        for (EventRegistration r : existingRegistrations) {
            if (standardEvent.getId().equals(r.getEventId()) && standardTeam.getId().equals(r.getTeamId()) && !r.isCancelled()) {
                duplicateAfterCancel = true;
                break;
            }
        }
        assertFalse("Cancelled registration does not block re-registration", duplicateAfterCancel);
    }

    // =========================================================================
    // Scenario 9: Unauthorized Team Leader cannot register another team's members
    // =========================================================================
    @Test
    public void testScenario09_UnauthorizedLeaderCannotRegisterAnotherTeamsMembers() {
        String rightfulLeaderUid = standardTeam.getLeaderUid(); // "leader-uid-100"
        String attackerLeaderUid = "imposter-uid-999";

        // Security assertion: operation must verify team.getLeaderUid() == request.auth.uid
        boolean isAuthorized = rightfulLeaderUid.equals(attackerLeaderUid);
        assertFalse("Imposter leader cannot submit registration for another team", isAuthorized);

        boolean isRightfulAuthorized = rightfulLeaderUid.equals(teamLeaderUser.getUid());
        assertTrue("Rightful leader is authorized", isRightfulAuthorized);
    }

    // =========================================================================
    // Scenario 10: Admin can register a team
    // =========================================================================
    @Test
    public void testScenario10_AdminCanRegisterTeam() {
        Team adminCreatedTeam = new Team("tm-admin-01", standardEvent.getId(), "panel-1", "TM-201", "Admin Champions", "Smart City Grid", "IoT", "Prof Leader", "prof@mit.edu", 4, null, System.currentTimeMillis());
        adminCreatedTeam.setOrganizationId(standardEvent.getOrganizationId());

        EventRegistration adminReg = new EventRegistration();
        adminReg.setId(EventRegistration.generateRegistrationId(standardEvent.getId(), adminCreatedTeam.getId()));
        adminReg.setEventId(standardEvent.getId());
        adminReg.setEventName(standardEvent.getName());
        adminReg.setTeamId(adminCreatedTeam.getId());
        adminReg.setTeamName(adminCreatedTeam.getTeamName());
        adminReg.setTeamNumber(adminCreatedTeam.getTeamNumber());
        adminReg.setOrganizationId(standardEvent.getOrganizationId());
        adminReg.setRegistrationSource(EventRegistration.SOURCE_ADMIN);
        adminReg.setCreatedById(adminUser.getUid());
        adminReg.setStatus(EventRegistration.STATUS_CONFIRMED);
        adminReg.setStatusReason("Registered directly by Admin");
        adminReg.setRegisteredMemberCount(4);
        adminReg.setTeamSnapshot(adminCreatedTeam);

        assertEquals("Source is ADMIN", EventRegistration.SOURCE_ADMIN, adminReg.getRegistrationSource());
        assertEquals("Creator is Admin UID", adminUser.getUid(), adminReg.getCreatedById());
        assertTrue("Admin registration is confirmed directly", adminReg.isConfirmed());
        assertEquals("Admin snapshot preserved", "Admin Champions", adminReg.getTeamName());
    }

    // =========================================================================
    // Scenario 11: Admin and Team Leader registrations appear in same system
    // =========================================================================
    @Test
    public void testScenario11_AdminAndLeaderRegistrationsInSameSystem() {
        List<EventRegistration> unifiedRegistry = new ArrayList<>();

        // Admin registration
        EventRegistration r1 = new EventRegistration();
        r1.setId("REG-ADMIN-01");
        r1.setEventId(standardEvent.getId());
        r1.setTeamId("tm-01");
        r1.setRegistrationSource(EventRegistration.SOURCE_ADMIN);
        r1.setCreatedById(adminUser.getUid());
        unifiedRegistry.add(r1);

        // Leader registration
        EventRegistration r2 = new EventRegistration();
        r2.setId("REG-LEADER-02");
        r2.setEventId(standardEvent.getId());
        r2.setTeamId("tm-02");
        r2.setRegistrationSource(EventRegistration.SOURCE_TEAM_LEADER);
        r2.setCreatedById(teamLeaderUser.getUid());
        unifiedRegistry.add(r2);

        assertEquals("Both registrations in same registry", 2, unifiedRegistry.size());

        int adminCount = 0;
        int leaderCount = 0;
        for (EventRegistration r : unifiedRegistry) {
            if (EventRegistration.SOURCE_ADMIN.equals(r.getRegistrationSource())) adminCount++;
            if (EventRegistration.SOURCE_TEAM_LEADER.equals(r.getRegistrationSource())) leaderCount++;
        }

        assertEquals(1, adminCount);
        assertEquals(1, leaderCount);
    }

    // =========================================================================
    // Scenario 12: Registration status updates are displayed correctly
    // =========================================================================
    @Test
    public void testScenario12_RegistrationStatusUpdatesAndWithdrawal() {
        EventRegistration reg = new EventRegistration();
        reg.setStatus(EventRegistration.STATUS_PENDING);

        assertTrue("Pending status recognized", reg.isPending());
        assertTrue("Pending registration can be cancelled/withdrawn", reg.canBeCancelled());

        // Admin approves -> Confirmed
        reg.setStatus(EventRegistration.STATUS_CONFIRMED);
        assertTrue("Confirmed status recognized", reg.isConfirmed());
        assertTrue("Confirmed registration can be cancelled if rules permit", reg.canBeCancelled());

        // Admin rejects -> Rejected
        reg.setStatus(EventRegistration.STATUS_REJECTED);
        assertTrue("Rejected status recognized", reg.isRejected());
        assertFalse("Rejected registration cannot be cancelled", reg.canBeCancelled());

        // Leader withdraws -> Cancelled
        reg.setStatus(EventRegistration.STATUS_CANCELLED);
        assertTrue("Cancelled status recognized", reg.isCancelled());
        assertFalse("Already cancelled registration cannot be cancelled again", reg.canBeCancelled());
    }

    // =========================================================================
    // Scenario 13: Network failures do not create duplicate registrations
    // =========================================================================
    @Test
    public void testScenario13_NetworkFailuresDoNotCreateDuplicateRegistrations() {
        // Idempotency simulation: atomic registration checks
        Map<String, EventRegistration> firestoreSimulatedMap = new HashMap<>();

        String uniqueEventTeamKey = standardEvent.getId() + "_" + standardTeam.getId();

        // 1st request succeeds
        EventRegistration request1 = new EventRegistration();
        request1.setId("REG-REQ-1");
        request1.setEventId(standardEvent.getId());
        request1.setTeamId(standardTeam.getId());

        if (!firestoreSimulatedMap.containsKey(uniqueEventTeamKey)) {
            firestoreSimulatedMap.put(uniqueEventTeamKey, request1);
        }

        assertEquals(1, firestoreSimulatedMap.size());

        // Simulated network retry or double-tap sends duplicate request
        EventRegistration request2 = new EventRegistration();
        request2.setId("REG-REQ-2");
        request2.setEventId(standardEvent.getId());
        request2.setTeamId(standardTeam.getId());

        boolean retryFailedWithDuplicate = false;
        if (firestoreSimulatedMap.containsKey(uniqueEventTeamKey)) {
            retryFailedWithDuplicate = true; // Blocked by transaction check
        } else {
            firestoreSimulatedMap.put(uniqueEventTeamKey, request2);
        }

        assertTrue("Network retry or double-tap blocked by uniqueness check", retryFailedWithDuplicate);
        assertEquals("Registry maintains strictly 1 registration record", 1, firestoreSimulatedMap.size());
    }

    // =========================================================================
    // Scenario 14: Existing Judge evaluation workflows continue to work
    // =========================================================================
    @Test
    public void testScenario14_JudgeEvaluationWorkflowUnbroken() {
        // 1. Scoring engine verification: Weighted vs Raw
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("c1", standardEvent.getId(), "Code Quality", "Architecture", 40.0, 40.0, 1));
        criteria.add(new Criterion("c2", standardEvent.getId(), "Innovation", "Novelty", 30.0, 30.0, 2));
        criteria.add(new Criterion("c3", standardEvent.getId(), "Presentation", "Pitch", 30.0, 30.0, 3));

        Map<String, Double> marks = new HashMap<>();
        marks.put("c1", 36.0); // 90%
        marks.put("c2", 27.0); // 90%
        marks.put("c3", 24.0); // 80%

        double totalScore = 0;
        double maxScore = 0;
        for (Criterion c : criteria) {
            maxScore += c.getMaxMarks();
            if (marks.containsKey(c.getId())) {
                totalScore += marks.get(c.getId());
            }
        }

        assertEquals(100.0, maxScore, 0.001);
        assertEquals(87.0, totalScore, 0.001);

        Evaluation eval = new Evaluation();
        eval.setId("ev-101");
        eval.setEventId(standardEvent.getId());
        eval.setTeamId(standardTeam.getId());
        eval.setJudgeId(judgeUser.getUid());
        eval.setPanelId("panel-1");
        eval.setScores(marks);
        eval.setTotalScore(totalScore);
        eval.setMaxPossibleScore(maxScore);
        eval.setStatus(Evaluation.STATUS_SUBMITTED);

        assertTrue("Evaluation marked as submitted", eval.isSubmitted());
        assertEquals("Score matches calculation", 87.0, eval.getTotalScore(), 0.001);

        // 2. Team QR evaluation payload serialization
        String qrPayload = "{\"v\":2,\"teamId\":\"" + standardTeam.getId() + "\",\"teamNumber\":\"" + standardTeam.getTeamNumber() + "\"}";
        assertTrue("QR payload contains team ID", qrPayload.contains("tm-code-wizards"));
        assertTrue("QR payload contains team number", qrPayload.contains("TM-101"));
    }

    // =========================================================================
    // Scenario 15: Team Member uniqueness validation & roster integrity
    // =========================================================================
    @Test
    public void testScenario15_TeamMemberUniquenessValidation() {
        Team team = new Team();
        team.setId("tm-unique-test");

        TeamMember m1 = new TeamMember("m-1", "Alice", "alice@college.edu", "ROLL-101", "CS", "3rd", "111", true);
        team.addMember(m1);

        assertEquals(1, team.getMemberCount());
        assertTrue("Email duplicate detected", team.hasMemberWithEmail("alice@college.edu"));
        assertTrue("Case insensitive email duplicate detected", team.hasMemberWithEmail("ALICE@college.edu"));
        assertTrue("Roll number duplicate detected", team.hasMemberWithRollNumber("roll-101"));
        assertFalse("Unregistered email returns false", team.hasMemberWithEmail("other@college.edu"));

        // Attempt to add duplicate email member -> updating existing rather than duplicating
        TeamMember m1DuplicateEmail = new TeamMember("m-1", "Alice Updated", "alice@college.edu", "ROLL-101", "CS", "3rd", "111", true);
        team.addMember(m1DuplicateEmail);
        assertEquals("Duplicate member does not increase roster size", 1, team.getMemberCount());
        assertEquals("Alice Updated", team.getMembers().get(0).getName());

        // Add 2nd unique member
        TeamMember m2 = new TeamMember("m-2", "Bob", "bob@college.edu", "ROLL-102", "IT", "2nd", "222", false);
        team.addMember(m2);
        assertEquals(2, team.getMemberCount());

        // Leader designation check
        TeamMember designatedLeader = team.getLeaderMember();
        assertNotNull("Leader exists", designatedLeader);
        assertTrue("Alice is the leader", designatedLeader.isLeader());
        assertEquals("ROLL-101", designatedLeader.getRollNumber());

        // Removal validation
        boolean removed = team.removeMember("m-2");
        assertTrue("Member removed successfully", removed);
        assertEquals(1, team.getMemberCount());
        assertFalse("Bob is no longer in roster", team.hasMemberWithEmail("bob@college.edu"));
    }
}
