package edu.college.nss.security;

import edu.college.nss.domain.Event;
import edu.college.nss.domain.NssUnit;
import edu.college.nss.domain.UnitMembership;
import edu.college.nss.domain.Volunteer;
import edu.college.nss.repository.EventRepository;
import edu.college.nss.repository.NssUnitRepository;
import edu.college.nss.repository.UnitMembershipRepository;
import edu.college.nss.repository.VolunteerRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("unitSecurity")
public class UnitSecurityService {

    private final NssUnitRepository unitRepository;
    private final VolunteerRepository volunteerRepository;
    private final UnitMembershipRepository membershipRepository;
    private final EventRepository eventRepository;

    public UnitSecurityService(
        NssUnitRepository unitRepository,
        VolunteerRepository volunteerRepository,
        UnitMembershipRepository membershipRepository,
        EventRepository eventRepository
    ) {
        this.unitRepository = unitRepository;
        this.volunteerRepository = volunteerRepository;
        this.membershipRepository = membershipRepository;
        this.eventRepository = eventRepository;
    }

    public boolean isGlobalManager(UserDetails principal) {
        if (principal == null) return false;
        return principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ADMIN") || a.equals("ROLE_ADMIN") ||
                           a.equals("FACULTY_COORDINATOR") || a.equals("ROLE_FACULTY_COORDINATOR"));
    }

    public boolean canManageUnit(UserDetails principal, UUID unitId) {
        if (principal == null || unitId == null) return false;
        if (isGlobalManager(principal)) return true;

        boolean isOfficer = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("PROGRAMME_OFFICER") || a.equals("ROLE_PROGRAMME_OFFICER"));

        if (!isOfficer) return false;

        return unitRepository.findById(unitId)
            .map(unit -> unit.getOfficer() != null &&
                         unit.getOfficer().getEmail().equalsIgnoreCase(principal.getUsername()))
            .orElse(false);
    }

    public boolean canManageVolunteer(UserDetails principal, UUID volunteerId) {
        if (principal == null || volunteerId == null) return false;
        if (isGlobalManager(principal)) return true;

        UnitMembership active = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteerId)
            .orElse(null);
        if (active == null || active.getUnit() == null) {
            return isGlobalManager(principal);
        }
        return canManageUnit(principal, active.getUnit().getUnitId());
    }

    public boolean canManageEvent(UserDetails principal, UUID eventId) {
        if (principal == null || eventId == null) return false;
        if (isGlobalManager(principal)) return true;

        return eventRepository.findById(eventId)
            .map(event -> event.getUnit() != null && canManageUnit(principal, event.getUnit().getUnitId()))
            .orElse(false);
    }
}
