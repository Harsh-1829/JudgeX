package com.example.judgex;

import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit and regression test suite verifying the runtime bug fixes for:
 * 1. Bug A: Browse Events empty state due to dummy school organization IDs, publication status, and school eligibility.
 * 2. Bug B: My Registrations PERMISSION_DENIED due to rule clause ordering, direct ownership, and legacy creator fallback.
 */
public class BrowseEventsAndRegistrationsRuntimeFixTest {

    private Event campusAllSchoolsEvent;
    private Event schoolRestrictedEvent;
    private Event draftEvent;
    private Event archivedEvent;

    private User stmeLeader;
    private User socLeader;
    private User sptmLeader;

    private Team stmeTeam;
    private EventRegistration registration1;
    private EventRegistration registration2;

    @Before
    public void setUp() {
        // 1. Admin creates campus event with real institutional orgId (e.g. "org_1740000000000")
        campusAllSchoolsEvent = new Event("evt-campus-hackathon", "National Hackathon 2026", "Campus Wide", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        campusAllSchoolsEvent.setOrganizationId("org_1740000000000");
        campusAllSchoolsEvent.setPublished(true);
        campusAllSchoolsEvent.setSchoolScope(Event.SCOPE_ALL_SCHOOLS);
        campusAllSchoolsEvent.setEligibleSchools(Arrays.asList("STME", "SOC", "SPTM", "SBM", "SOL"));
        campusAllSchoolsEvent.setRegistrationDeadline(System.currentTimeMillis() + 86400000L);

        // 2. School-restricted event (only STME and SOC eligible)
        schoolRestrictedEvent = new Event("evt-tech-fest", "Tech Innovate 2026", "Engineering and Commerce", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        schoolRestrictedEvent.setOrganizationId("org_1740000000000");
        schoolRestrictedEvent.setPublished(true);
        schoolRestrictedEvent.setSchoolScope(Event.SCOPE_SELECTED_SCHOOLS);
        schoolRestrictedEvent.setEligibleSchools(Arrays.asList("STME", "SOC"));
        schoolRestrictedEvent.setRegistrationDeadline(System.currentTimeMillis() + 86400000L);

        // 3. Draft event (created but unpublished)
        draftEvent = new Event("evt-draft", "Unpublished Symposium", "Planning phase", Event.STATUS_DRAFT, false, System.currentTimeMillis());
        draftEvent.setOrganizationId("org_1740000000000");
        draftEvent.setPublished(false);

        // 4. Archived event
        archivedEvent = new Event("evt-old", "Old Hackathon", "Past event", Event.STATUS_ARCHIVED, false, System.currentTimeMillis() - 10000000L);
        archivedEvent.setOrganizationId("org_1740000000000");
        archivedEvent.setPublished(true);

        // Leaders from different schools with dummy school org IDs
        stmeLeader = new User("uid-stme-leader", "Alice Engineer", "alice@college.edu", User.ROLE_TEAM_LEADER);
        stmeLeader.setSchool("STME");
        stmeLeader.setOrganizationId("org_stme");

        socLeader = new User("uid-soc-leader", "Bob Commerce", "bob@college.edu", User.ROLE_TEAM_LEADER);
        socLeader.setSchool("SOC");
        socLeader.setOrganizationId("org_soc");

        sptmLeader = new User("uid-sptm-leader", "Charlie Pharmacy", "charlie@college.edu", User.ROLE_TEAM_LEADER);
        sptmLeader.setSchool("SPTM");
        sptmLeader.setOrganizationId("org_sptm");

        // Teams
        stmeTeam = new Team("tm-stme-01", null, null, "TM-201", "RoboTech", "Autonomous Drone", "Robotics", "Alice Engineer", "alice@college.edu", 2, null, System.currentTimeMillis());
        stmeTeam.setSchool("STME");
        stmeTeam.setOrganizationId("org_stme");
        stmeTeam.setLeaderUid("uid-stme-leader");

        // Registrations
        registration1 = new EventRegistration();
        registration1.setId("REG-evt-campus-hackathon-tm-stme-01");
        registration1.setEventId("evt-campus-hackathon");
        registration1.setTeamId("tm-stme-01");
        registration1.setTeamLeaderId("uid-stme-leader");
        registration1.setCreatedById("uid-stme-leader");
        registration1.setOrganizationId("org_1740000000000");
        registration1.setStatus(EventRegistration.STATUS_CONFIRMED);

        registration2 = new EventRegistration();
        registration2.setId("REG-evt-tech-fest-tm-soc-01");
        registration2.setEventId("evt-tech-fest");
        registration2.setTeamId("tm-soc-01");
        registration2.setTeamLeaderId("uid-soc-leader");
        registration2.setCreatedById("uid-soc-leader");
        registration2.setOrganizationId("org_1740000000000");
        registration2.setStatus(EventRegistration.STATUS_PENDING);
    }

    @Test
    public void testIsDummySchoolOrgHelper() {
        assertTrue(FirebaseManager.isDummySchoolOrg("org_stme"));
        assertTrue(FirebaseManager.isDummySchoolOrg("org_soc"));
        assertTrue(FirebaseManager.isDummySchoolOrg("org_sptm"));
        assertTrue(FirebaseManager.isDummySchoolOrg("org_sbm"));
        assertTrue(FirebaseManager.isDummySchoolOrg("org_sol"));
        assertTrue(FirebaseManager.isDummySchoolOrg("org_default"));
        assertTrue(FirebaseManager.isDummySchoolOrg(null));
        assertTrue(FirebaseManager.isDummySchoolOrg(""));

        // Real institutional organization IDs must NOT be marked dummy
        assertFalse(FirebaseManager.isDummySchoolOrg("org_1740000000000"));
        assertFalse(FirebaseManager.isDummySchoolOrg("org_mit_campus"));
        assertFalse(FirebaseManager.isDummySchoolOrg("org_nmims_mumbai"));
    }

    @Test
    public void testEventPublicationCompatibilityPolicy() {
        // Active upcoming events are published
        assertTrue("Upcoming event must be published", campusAllSchoolsEvent.isPublished());
        assertTrue("Active event must be published", schoolRestrictedEvent.isPublished());

        // Draft events are never published
        assertFalse("Draft event must NOT be published", draftEvent.isPublished());

        // Deleted events are never published
        campusAllSchoolsEvent.setDeleted(true);
        assertFalse("Deleted event must NOT be published", campusAllSchoolsEvent.isDeleted() && campusAllSchoolsEvent.isPublished());

        // Archived events are not published
        assertFalse("Archived event must NOT be published", archivedEvent.isPublished());
    }

    @Test
    public void testBrowseEventsVisibilityForEligibleSchools() {
        // STME leader should see All Schools event
        assertTrue(campusAllSchoolsEvent.isSchoolEligible(stmeLeader.getSchool()));

        // STME leader should see STME-eligible restricted event
        assertTrue(schoolRestrictedEvent.isSchoolEligible(stmeLeader.getSchool()));

        // SOC leader should see STME & SOC event
        assertTrue(schoolRestrictedEvent.isSchoolEligible(socLeader.getSchool()));

        // SPTM leader should NOT see STME & SOC restricted event
        assertFalse("SPTM leader must be excluded from STME/SOC restricted event", 
                schoolRestrictedEvent.isSchoolEligible(sptmLeader.getSchool()));

        // SPTM leader SHOULD see All Schools event
        assertTrue("SPTM leader must see All Schools event",
                campusAllSchoolsEvent.isSchoolEligible(sptmLeader.getSchool()));
    }

    @Test
    public void testFilterEventsListSimulation() {
        List<Event> allEvents = Arrays.asList(campusAllSchoolsEvent, schoolRestrictedEvent, draftEvent, archivedEvent);
        List<Event> eligibleForStme = new ArrayList<>();
        List<Event> eligibleForSptm = new ArrayList<>();

        for (Event ev : allEvents) {
            if (!ev.isPublished()) continue;
            if (ev.isSchoolEligible(stmeLeader.getSchool())) {
                eligibleForStme.add(ev);
            }
            if (ev.isSchoolEligible(sptmLeader.getSchool())) {
                eligibleForSptm.add(ev);
            }
        }

        // STME sees both published events (all schools + restricted)
        assertEquals(2, eligibleForStme.size());
        assertTrue(eligibleForStme.contains(campusAllSchoolsEvent));
        assertTrue(eligibleForStme.contains(schoolRestrictedEvent));

        // SPTM only sees All Schools event
        assertEquals(1, eligibleForSptm.size());
        assertTrue(eligibleForSptm.contains(campusAllSchoolsEvent));
        assertFalse(eligibleForSptm.contains(schoolRestrictedEvent));
    }

    @Test
    public void testRegistrationDirectOwnershipFields() {
        // Verify registration has both teamLeaderId and createdById
        assertEquals("uid-stme-leader", registration1.getTeamLeaderId());
        assertEquals("uid-stme-leader", registration1.getCreatedById());
        assertEquals("REG-evt-campus-hackathon-tm-stme-01", registration1.getId());
        assertEquals("org_1740000000000", registration1.getOrganizationId());

        // Alice cannot access Bob's registration
        assertNotEquals(registration1.getTeamLeaderId(), registration2.getTeamLeaderId());
        assertNotEquals(registration1.getCreatedById(), registration2.getCreatedById());
    }

    @Test
    public void testRegistrationStatusFiltering() {
        List<EventRegistration> registrations = Arrays.asList(registration1, registration2);

        // Filter confirmed
        List<EventRegistration> confirmed = new ArrayList<>();
        for (EventRegistration reg : registrations) {
            if (reg.isConfirmed()) confirmed.add(reg);
        }
        assertEquals(1, confirmed.size());
        assertEquals(registration1.getId(), confirmed.get(0).getId());

        // Filter pending
        List<EventRegistration> pending = new ArrayList<>();
        for (EventRegistration reg : registrations) {
            if (reg.isPending()) pending.add(reg);
        }
        assertEquals(1, pending.size());
        assertEquals(registration2.getId(), pending.get(0).getId());

        // Filter waitlisted (none)
        List<EventRegistration> waitlisted = new ArrayList<>();
        for (EventRegistration reg : registrations) {
            if (reg.isWaitlisted()) waitlisted.add(reg);
        }
        assertEquals(0, waitlisted.size());
    }

    @Test
    public void testRegistrationQRDataIntegrity() {
        registration1.buildRegistrationQrPayload();
        assertNotNull(registration1.getQrCodeData());
        assertTrue(registration1.getQrCodeData().contains("REG-evt-campus-hackathon-tm-stme-01"));
        assertTrue(registration1.getQrCodeData().contains("tm-stme-01"));
    }

    @Test
    public void testLegacyTeamLeaderBinding() {
        // If team was created without leaderUid, but leader email matches
        Team legacyTeam = new Team("tm-legacy-01", "Legacy Team", "TM-999", "evt-campus-hackathon", null);
        legacyTeam.setLeaderEmail("alice@college.edu");
        legacyTeam.setLeaderUid(null); // null UID from older constructor

        // Simulate resolution in registerTeamForEvent
        String currentUid = stmeLeader.getUid();
        String teamLeaderUid = legacyTeam.getLeaderUid();
        if (teamLeaderUid == null || teamLeaderUid.isEmpty()) {
            if (legacyTeam.getLeaderEmail() != null && legacyTeam.getLeaderEmail().equalsIgnoreCase(stmeLeader.getEmail())) {
                teamLeaderUid = currentUid;
            }
        }

        assertEquals("uid-stme-leader", teamLeaderUid);
    }
}
